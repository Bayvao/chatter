package com.chatter.chatter.chat.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Participants rather than user1_id/user2_id: group chat then needs no second
 * migration, and this table is the membership check standing in for the
 * dropped {@code messages -> users} constraint.
 */
@Entity
@Table(name = "chat_participants", schema = "chat")
@IdClass(ChatParticipant.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class ChatParticipant {

    @Id
    @Column(name = "chat_id")
    private UUID chatId;

    /** No association — crosses the user boundary. */
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private short role;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt = Instant.now();

    @Column(name = "left_at")
    private Instant leftAt;

    /** Makes unread count arithmetic instead of a COUNT(*). */
    @Column(name = "last_read_seq", nullable = false)
    private long lastReadSeq;

    @Column(name = "muted_until")
    private Instant mutedUntil;

    public ChatParticipant(UUID chatId, UUID userId, ParticipantRole role) {
        this.chatId = chatId;
        this.userId = userId;
        this.role = (short) role.ordinal();
    }

    public void markReadThrough(long seq) {
        if (seq > lastReadSeq) {
            lastReadSeq = seq;
        }
    }

    public record Key(UUID chatId, UUID userId) implements Serializable {
    }
}
