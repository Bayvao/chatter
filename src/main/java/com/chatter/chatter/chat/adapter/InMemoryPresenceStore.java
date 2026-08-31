package com.chatter.chatter.chat.adapter;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.chatter.chatter.chat.port.PresenceStore;

/**
 * The default {@link PresenceStore}: correct for a single instance, and what
 * the test suite runs against so no Redis is needed to verify presence
 * behaviour. {@link RedisPresenceStore} replaces it when Redis is configured.
 *
 * <p>Expiry is evaluated on read rather than by a background sweeper — there
 * is no separate process to keep alive, and a stale entry is invisible either
 * way.
 */
@Component
@ConditionalOnProperty(name = "app.presence.redis.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryPresenceStore implements PresenceStore {

    private record Entry(Instant expiresAt) {
        boolean live() {
            return Instant.now().isBefore(expiresAt);
        }
    }

    private final Map<UUID, Entry> online = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> lastSeen = new ConcurrentHashMap<>();

    @Override
    public void markOnline(UUID userId, Duration ttl) {
        online.put(userId, new Entry(Instant.now().plus(ttl)));
    }

    @Override
    public void markOffline(UUID userId) {
        online.remove(userId);
        lastSeen.put(userId, Instant.now());
    }

    @Override
    public void refresh(UUID userId, Duration ttl) {
        online.computeIfPresent(userId, (id, entry) -> new Entry(Instant.now().plus(ttl)));
    }

    @Override
    public boolean isOnline(UUID userId) {
        Entry entry = online.get(userId);
        if (entry == null) {
            return false;
        }
        if (entry.live()) {
            return true;
        }
        online.remove(userId);
        return false;
    }

    @Override
    public Optional<Instant> lastSeen(UUID userId) {
        return Optional.ofNullable(lastSeen.get(userId));
    }
}
