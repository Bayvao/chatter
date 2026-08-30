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

    private Ids() {
    }

    public static UUID newId() {
        return V7.generate();
    }
}
