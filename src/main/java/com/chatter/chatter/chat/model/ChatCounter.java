package com.chatter.chatter.chat.model;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Per-chat sequence counter. Ordering by {@code created_at} is unsafe across
 * writers — clocks drift and two messages can share a millisecond — so total
 * order comes from this instead.
 *
 * <p>The row is created with the chat, so sending only ever locks and
 * increments an existing row. That avoids Postgres-specific
 * {@code INSERT ... ON CONFLICT ... RETURNING}, which H2 (used by the test
 * suite) cannot parse.
 */
@Entity
@Table(name = "chat_counters", schema = "chat")
@Getter
@NoArgsConstructor
public class ChatCounter {

    @Id
    @Column(name = "chat_id")
    private UUID chatId;

    @Column(name = "last_seq", nullable = false)
    private long lastSeq;

    public ChatCounter(UUID chatId) {
        this.chatId = chatId;
    }

    public long nextSeq() {
        return ++lastSeq;
    }
}
