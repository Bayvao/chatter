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

    public static AuthenticatedUser principalOf(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof AuthenticatedUser principal) {
            return principal;
        }
        return null;
    }
}
