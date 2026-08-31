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

    /**
     * Records a user as connected, for at most {@code ttl}.
     *
     * <p>Called by {@code PresenceEventListener} on STOMP CONNECT. The TTL is
     * the safety net: if the process dies before {@link #markOffline} runs, the
     * entry expires by itself.
     */
    void markOnline(UUID userId, Duration ttl);

    /**
     * Records a user as gone, and stamps their last-seen time.
     *
     * <p>Called by {@code PresenceEventListener} on STOMP disconnect — the
     * clean path, as opposed to the TTL expiring.
     */
    void markOffline(UUID userId);

    /**
     * Extends the TTL for a still-connected client.
     *
     * <p>Implementations must not revive an entry that has already expired: a
     * heartbeat arriving after the fact means the client was gone long enough to
     * be considered offline.
     */
    void refresh(UUID userId, Duration ttl);

    /**
     * Whether a user currently has a live connection.
     *
     * <p>Read by {@code MessageBroadcaster} for every recipient of every message
     * — it decides socket delivery versus a push notification — and by
     * {@code PresenceController} for the presence dots. On the hot path, so
     * implementations should keep it to a single lookup.
     */
    boolean isOnline(UUID userId);

    /**
     * When the user was last seen, absent if never recorded.
     *
     * <p>Shown as "last seen 3 hours ago" beside an offline contact. Empty for
     * someone who has never connected, or whose record has aged out — the UI
     * falls back to a plain "Offline".
     */
    Optional<Instant> lastSeen(UUID userId);
}
