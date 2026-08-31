package com.chatter.chatter.chat.port;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Who is currently connected.
 *
 * <p>Presence is stored with a TTL rather than as a {@code status} column on
 * purpose: a server that crashes mid-session cannot leave a user stuck
 * "online", because the entry expires on its own. Clients refresh it with a
 * heartbeat while they stay connected.
 */
public interface PresenceStore {

    void markOnline(UUID userId, Duration ttl);

    void markOffline(UUID userId);

    /** Extends the TTL for a still-connected client. */
    void refresh(UUID userId, Duration ttl);

    boolean isOnline(UUID userId);

    /** When the user was last seen, absent if never recorded. */
    Optional<Instant> lastSeen(UUID userId);
}
