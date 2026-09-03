package com.chatter.chatter.user.model;

import java.util.UUID;

/**
 * The canonical key for an unordered pair of users.
 *
 * <p>Two people can have at most one pending request between them, whichever of
 * them sent it. Ordering the ids before joining them makes that a plain UNIQUE
 * constraint the database enforces, instead of a check the application races on.
 */
public final class ContactPair {

    private ContactPair() {
    }

    /**
     * Builds the key stored on {@code contact_requests.pair_key}.
     *
     * <p>Called from the {@link ContactRequest} constructor and from
     * {@code ContactRequestRepository} lookups, so both sides of a comparison
     * are always derived the same way.
     *
     * <p>Sorted lexicographically rather than by {@link UUID#compareTo}, which
     * compares the two longs as *signed* — that still yields a stable total
     * order, but the textual form is what is stored, and sorting the two
     * representations differently would be an easy way to produce a key that
     * does not round-trip.
     */
    public static String keyOf(UUID a, UUID b) {
        String first = a.toString();
        String second = b.toString();
        return first.compareTo(second) <= 0 ? first + ":" + second : second + ":" + first;
    }
}
