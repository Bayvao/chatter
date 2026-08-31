package com.chatter.chatter.common;

import java.util.UUID;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;

/**
 * IDs are assigned in the service layer, never by
 * {@code @GeneratedValue(strategy = GenerationType.UUID)} — that produces
 * UUIDv4, whose randomness scatters B-tree inserts and bloats the index as
 * the table grows. UUIDv7 keeps a time-ordered prefix.
 *
 * <p>A time-ordered ID is a good primary key and a bad shard key: shard on
 * {@code hash(chat_id)}, not on the ID's time prefix.
 */
public final class Ids {

    private static final NoArgGenerator V7 = Generators.timeBasedEpochGenerator();

    /** Static-only holder; never instantiated. */
    private Ids() {
    }

    /**
     * A fresh UUIDv7, for any row this application creates.
     *
     * <p>Called from the entity factory methods ({@code User.create},
     * {@code Chat.directChat}, {@code Message.text}) and from the constructors
     * of {@code Contact}, {@code ChatParticipant} and {@code PushSubscription} —
     * every id in the schema comes from here.
     *
     * <p>The generator is thread-safe and shared, so concurrent requests may
     * call this freely.
     */
    public static UUID newId() {
        return V7.generate();
    }
}
