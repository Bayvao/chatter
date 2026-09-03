package com.chatter.chatter.user.event;

import java.util.UUID;

import com.chatter.chatter.common.Ids;

/**
 * A change to a relationship, published for live relay to the people affected.
 *
 * <p>Consumed by {@code ContactEventBroadcaster} after commit, so nothing is
 * relayed for a transaction that rolled back — the same discipline
 * {@code MessageSent} follows.
 *
 * <p>{@code audienceId} is who should be told, kept separate from {@code actorId}
 * and {@code subjectId} because the audience is not always the other party: a
 * block is relayed only to the blocker's own sessions.
 */
public record ContactChanged(
        UUID eventId,
        Type type,
        UUID audienceId,
        UUID actorId,
        UUID subjectId) {

    public enum Type {
        /** A request was sent to you. */
        REQUESTED,
        /** A request you sent was accepted, or you accepted one. */
        ACCEPTED,
        /** A request was declined, or a pending one cancelled. */
        DECLINED,
        /** An accepted friendship was ended. */
        REMOVED,
        /** You blocked someone. Never relayed to the person blocked. */
        BLOCKED
    }

    /**
     * Builds an event addressed to one person.
     *
     * <p>Called from {@code ContactService} at each state change. One event per
     * recipient rather than one fanned out, because who hears about a change is
     * decided per type and is deliberately asymmetric.
     */
    public static ContactChanged to(UUID audienceId, Type type, UUID actorId, UUID subjectId) {
        return new ContactChanged(Ids.newId(), type, audienceId, actorId, subjectId);
    }
}
