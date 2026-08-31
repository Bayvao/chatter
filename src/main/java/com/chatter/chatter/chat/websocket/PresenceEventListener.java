package com.chatter.chatter.chat.websocket;

import java.time.Duration;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.chatter.chatter.chat.dto.PresenceDTO;
import com.chatter.chatter.chat.port.PresenceStore;
import com.chatter.chatter.user.security.AuthenticatedUser;

import static com.chatter.chatter.config.StompAuthChannelInterceptor.principalOf;

/**
 * Turns STOMP session lifecycle into presence. Connect and disconnect are the
 * only reliable signals we have — a client that vanishes without disconnecting
 * is caught by the TTL expiring instead.
 */
@Component
public class PresenceEventListener {

    /** Comfortably longer than the client heartbeat, so a brief stall does not flap. */
    public static final Duration PRESENCE_TTL = Duration.ofMinutes(5);

    private final PresenceStore presenceStore;
    private final SimpMessagingTemplate messagingTemplate;

    public PresenceEventListener(PresenceStore presenceStore, SimpMessagingTemplate messagingTemplate) {
        this.presenceStore = presenceStore;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Marks a user online when their STOMP session completes CONNECT.
     *
     * <p>Invoked by Spring for each connected session. Listens for
     * {@code SessionConnectedEvent} rather than the CONNECT frame itself, so
     * presence follows a session that was actually accepted — a frame rejected
     * by the auth interceptor never reaches here.
     */
    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        userOf(event.getMessage()).ifPresent(userId -> {
            presenceStore.markOnline(userId, PRESENCE_TTL);
            broadcast(userId, true);
        });
    }

    /**
     * Marks a user offline when their session ends, and records last-seen.
     *
     * <p>The clean path. A client that vanishes without disconnecting — a closed
     * laptop, a dropped network — never triggers this, and is caught by the TTL
     * expiring instead.
     *
     * <p>Note this does not account for a second open tab: the first tab to
     * close marks the user offline even though another session remains. Fixing
     * that means reference-counting sessions per user.
     */
    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        userOf(event.getMessage()).ifPresent(userId -> {
            presenceStore.markOffline(userId);
            broadcast(userId, false);
        });
    }

    /**
     * Publishes a presence change to everyone subscribed to
     * {@code /topic/presence}.
     *
     * <p>Drives the presence dots live, so a client only needs the REST endpoint
     * for its initial render.
     *
     * <p>A single global topic: every connected client hears about every user.
     * Fine at this size; at scale this wants narrowing to the people a viewer
     * actually shares a conversation with.
     */
    private void broadcast(UUID userId, boolean online) {
        messagingTemplate.convertAndSend("/topic/presence",
                new PresenceDTO(userId, online, presenceStore.lastSeen(userId).orElse(null)));
    }

    /**
     * Extracts the authenticated user id from a session lifecycle event.
     *
     * <p>Both listeners receive the raw message rather than a principal, so this
     * unwraps the STOMP headers via
     * {@code StompAuthChannelInterceptor.principalOf}.
     *
     * <p>Empty for a session with no principal, which is why both callers use
     * {@code ifPresent} — a half-established session has nobody to mark.
     */
    private java.util.Optional<UUID> userOf(org.springframework.messaging.Message<?> message) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        AuthenticatedUser principal = principalOf(accessor);
        return java.util.Optional.ofNullable(principal).map(AuthenticatedUser::id);
    }
}
