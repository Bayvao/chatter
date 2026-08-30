package com.chatter.chatter.chat.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.chatter.chatter.common.Ids;

/**
 * {@code chatId} and {@code senderId} are values, not object graphs. Dropping
 * {@code @ManyToOne} removes the cross-boundary FK, and with it lazy-loading
 * proxies, N+1 queries, {@code LazyInitializationException} and cascade
 * semantics nobody fully understood.
 */
@Entity
@Table(name = "messages", schema = "chat")
@Getter
@Setter
@NoArgsConstructor
public class Message {

    @Id
    private UUID id;

    @Column(name = "chat_id", nullable = false)
    private UUID chatId;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    /** Per-chat monotonic ordinal; the client syncs by range over this. */
    @Column(nullable = false)
    private long seq;

    /** Stable across retries, so a resend cannot duplicate the row. */
    @Column(name = "client_msg_id")
    private UUID clientMsgId;

    @Column(columnDefinition = "text")
    private String content;

    @Column(name = "content_type", nullable = false)
    private short contentType;

    @Embedded
    private SenderSnapshot sender = new SenderSnapshot();

    @Column(nullable = false)
    private short status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "read_at")
    private Instant readAt;

    /** Soft delete: a hole in seq makes clients re-sync forever. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    public static Message text(UUID chatId, UUID senderId, long seq, UUID clientMsgId, String content,
                                SenderSnapshot sender) {
        Message message = new Message();
        message.id = Ids.newId();
        message.chatId = chatId;
        message.senderId = senderId;
        message.seq = seq;
        message.clientMsgId = clientMsgId;
        message.content = content;
        message.contentType = (short) ContentType.TEXT.ordinal();
        message.sender = sender;
        message.status = (short) MessageStatus.SENT.ordinal();
        return message;
    }

    public MessageStatus status() {
        return MessageStatus.values()[status];
    }

    public void markDelivered() {
        if (status < MessageStatus.DELIVERED.ordinal()) {
            status = (short) MessageStatus.DELIVERED.ordinal();
            deliveredAt = Instant.now();
        }
    }

    public void markRead() {
        if (status < MessageStatus.READ.ordinal()) {
            status = (short) MessageStatus.READ.ordinal();
            readAt = Instant.now();
        }
    }
}
