package com.chatter.chatter.config;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import com.chatter.chatter.chat.repository.ChatParticipantRepository;
import com.chatter.chatter.user.security.AuthenticatedUser;
import com.chatter.chatter.user.security.JwtTokenProvider;

/**
 * The WebSocket handshake is open at the HTTP layer, so authentication and
 * authorization both happen on STOMP frames here.
 *
 * <p>CONNECT establishes who the caller is from the JWT; SUBSCRIBE checks
 * that they may read the chat they are asking for. Without the second check,
 * any authenticated user could subscribe to any chat's topic.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Pattern CHAT_TOPIC = Pattern.compile("^/topic/chats/([0-9a-fA-F-]{36})$");

    private final JwtTokenProvider tokenProvider;
    private final ChatParticipantRepository participantRepository;

    public StompAuthChannelInterceptor(JwtTokenProvider tokenProvider,
                                        ChatParticipantRepository participantRepository) {
        this.tokenProvider = tokenProvider;
        this.participantRepository = participantRepository;
    }

    /**
     * Inspects every inbound STOMP frame before the broker sees it.
     *
     * <p>Registered on the client inbound channel by {@code WebSocketConfig}, so
     * this runs for all WebSocket traffic. Only two commands are gated —
     * CONNECT and SUBSCRIBE — because those are the two that grant access;
     * SEND and the rest ride on the session CONNECT already established.
     *
     * <p>Throwing from here is how a frame is refused: Spring turns the
     * exception into an ERROR frame and closes the session, which is why the
     * tests assert on a dropped connection rather than a returned error.
     */
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        switch (accessor.getCommand()) {
            case CONNECT -> authenticate(accessor);
            case SUBSCRIBE -> authorizeSubscription(accessor);
            default -> {
                // Other frames ride on the session established at CONNECT.
            }
        }

        return message;
    }

    /**
     * Establishes who the caller is, from the JWT on the CONNECT frame.
     *
     * <p>The token travels as a STOMP native header rather than an HTTP one,
     * because the browser WebSocket API cannot set request headers on a
     * handshake. That is also why the handshake itself is left unauthenticated
     * in {@code SecurityConfig} and the check lands here instead.
     *
     * <p>The principal is attached to the session, making it available to
     * {@code @MessageMapping} methods and to {@code PresenceEventListener}.
     *
     * @throws AccessDeniedException if the token is missing, malformed or
     *         expired — the session is then closed
     */
    private void authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new AccessDeniedException("Missing bearer token on STOMP CONNECT");
        }

        AuthenticatedUser principal = tokenProvider.parse(header.substring(BEARER_PREFIX.length()));
        if (principal == null) {
            throw new AccessDeniedException("Invalid or expired token on STOMP CONNECT");
        }

        accessor.setUser(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    /**
     * Refuses a SUBSCRIBE to a chat topic the caller is not a member of.
     *
     * <p>Without this, any authenticated user could subscribe to
     * {@code /topic/chats/{anyId}} and read a conversation they have nothing to
     * do with: authentication alone is not authorization.
     *
     * <p>Only chat topics are matched. Destinations that are not per-chat —
     * {@code /topic/presence}, the {@code /user/queue/...} destinations, which
     * Spring already scopes to the session — pass through untouched.
     *
     * @throws AccessDeniedException if the caller is not an active participant
     */
    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            return;
        }

        Matcher matcher = CHAT_TOPIC.matcher(destination);
        if (!matcher.matches()) {
            return;
        }

        AuthenticatedUser principal = principalOf(accessor);
        UUID chatId = UUID.fromString(matcher.group(1));

        if (principal == null || !participantRepository.isActiveMember(chatId, principal.id())) {
            throw new AccessDeniedException("Not a participant of chat " + chatId);
        }
    }

    /**
     * Digs the principal out of a STOMP session, or returns {@code null}.
     *
     * <p>Shared with {@code PresenceEventListener}, which needs the user behind
     * a connect or disconnect event and has only the raw message to work from.
     * Public for that reason — it is the one piece of this class used from
     * another package.
     *
     * <p>Returns null rather than throwing: a session that never completed
     * CONNECT has no principal, and both callers treat that as "nobody".
     */
    public static AuthenticatedUser principalOf(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof AuthenticatedUser principal) {
            return principal;
        }
        return null;
    }
}
