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

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        userOf(event.getMessage()).ifPresent(userId -> {
            presenceStore.markOnline(userId, PRESENCE_TTL);
            broadcast(userId, true);
        });
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        userOf(event.getMessage()).ifPresent(userId -> {
            presenceStore.markOffline(userId);
            broadcast(userId, false);
        });
    }

    private void broadcast(UUID userId, boolean online) {
        messagingTemplate.convertAndSend("/topic/presence",
                new PresenceDTO(userId, online, presenceStore.lastSeen(userId).orElse(null)));
    }

    private java.util.Optional<UUID> userOf(org.springframework.messaging.Message<?> message) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        AuthenticatedUser principal = principalOf(accessor);
        return java.util.Optional.ofNullable(principal).map(AuthenticatedUser::id);
    }
}
