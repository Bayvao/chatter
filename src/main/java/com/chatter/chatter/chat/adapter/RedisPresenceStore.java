package com.chatter.chatter.chat.adapter;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.chatter.chatter.chat.port.PresenceStore;

/**
 * Redis-backed presence, active only when {@code app.presence.redis.enabled}
 * is true so a bare `bootRun` and the test suite need no Redis.
 *
 * <p>The online key carries the TTL; last-seen is a separate key with a long
 * expiry, because it must outlive the session it describes.
 */
@Component
@ConditionalOnProperty(name = "app.presence.redis.enabled", havingValue = "true")
public class RedisPresenceStore implements PresenceStore {

    private static final String ONLINE_PREFIX = "presence:online:";
    private static final String LAST_SEEN_PREFIX = "presence:last-seen:";
    private static final Duration LAST_SEEN_RETENTION = Duration.ofDays(30);

    private final StringRedisTemplate redis;

    public RedisPresenceStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The TTL is the whole point: if this instance dies without ever calling
     * {@link #markOffline}, Redis expires the key on its own and the user stops
     * showing as online. A status column could not do that.
     */
    @Override
    public void markOnline(UUID userId, Duration ttl) {
        redis.opsForValue().set(ONLINE_PREFIX + userId, Instant.now().toString(), ttl);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Two keys, because last-seen must outlive the session it describes:
     * the online key goes, and a separate long-lived key records when.
     */
    @Override
    public void markOffline(UUID userId) {
        redis.delete(ONLINE_PREFIX + userId);
        redis.opsForValue().set(LAST_SEEN_PREFIX + userId, Instant.now().toString(), LAST_SEEN_RETENTION);
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code EXPIRE} only extends a key that still exists, so an expired
     * session is not revived by a late heartbeat.
     */
    @Override
    public void refresh(UUID userId, Duration ttl) {
        redis.expire(ONLINE_PREFIX + userId, ttl);
    }

    /** {@inheritDoc} A key's mere existence is the answer; its value is unread. */
    @Override
    public boolean isOnline(UUID userId) {
        return Boolean.TRUE.equals(redis.hasKey(ONLINE_PREFIX + userId));
    }

    /** {@inheritDoc} Empty once the 30-day retention on the key has lapsed. */
    @Override
    public Optional<Instant> lastSeen(UUID userId) {
        return Optional.ofNullable(redis.opsForValue().get(LAST_SEEN_PREFIX + userId)).map(Instant::parse);
    }
}
