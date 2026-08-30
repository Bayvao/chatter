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

    public static UserProfileChanged of(UUID userId, long version, String displayName, String avatarUrl) {
        return new UserProfileChanged(Ids.newId(), userId, version, displayName, avatarUrl);
    }
}
