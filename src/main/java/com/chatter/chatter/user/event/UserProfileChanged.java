package com.chatter.chatter.user.event;

import java.util.UUID;

import com.chatter.chatter.common.Ids;

/**
 * Published whenever a change touches the display fields the chat module keeps
 * a denormalised copy of. In-process today, a Kafka topic later — same shape.
 *
 * <p>{@code version} is the out-of-order guard: consumers apply an update only
 * when it is newer than what they hold, because a rename followed quickly by
 * another can otherwise land backwards and stick.
 */
public record UserProfileChanged(
        UUID eventId,
        UUID userId,
        long version,
        String displayName,
        String avatarUrl) {

    /**
     * Builds the event, assigning its own id.
     *
     * <p>Called by {@code ProfileService.updateProfile}, and only when a
     * snapshotted field actually changed — publishing on every save would
     * rewrite every message the user ever sent for nothing.
     *
     * <p>The {@code eventId} is generated here so the event is identifiable
     * before it is published: today that supports nothing, but once this becomes
     * a Kafka message it is what a consumer de-duplicates on.
     */
    public static UserProfileChanged of(UUID userId, long version, String displayName, String avatarUrl) {
        return new UserProfileChanged(Ids.newId(), userId, version, displayName, avatarUrl);
    }
}
