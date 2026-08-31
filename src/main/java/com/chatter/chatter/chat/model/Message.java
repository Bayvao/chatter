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

    /**
     * Builds a text message, ready to persist.
     *
     * <p>The only way a {@code Message} is created; called from
     * {@code MessageService.send} with a {@code seq} freshly allocated from the
     * chat's counter.
     *
     * <p>The id is assigned here rather than by the database, so the caller
     * holds it before the insert. The sender snapshot is copied in for the same
     * reason history needs no join into the user module.
     */
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

    /**
     * The delivery status as an enum.
     *
     * <p>Stored as a {@code short} ordinal so the column stays compact and
     * orderable — the comparisons in {@link #markDelivered} and
     * {@link #markRead} rely on that ordering. Used by {@code MessageDTO.from}
     * when rendering.
     */
    public MessageStatus status() {
        return MessageStatus.values()[status];
    }

    /**
     * Moves the message to DELIVERED, if it is not already further along.
     *
     * <p>Called by {@code MessageBroadcaster} once the message has reached at
     * least one recipient's client.
     *
     * <p>The guard makes status monotonic: a message already READ must not fall
     * back to DELIVERED when a second recipient's client connects.
     */
    public void markDelivered() {
        if (status < MessageStatus.DELIVERED.ordinal()) {
            status = (short) MessageStatus.DELIVERED.ordinal();
            deliveredAt = Instant.now();
        }
    }

    /**
     * Moves the message to READ, if it is not already.
     *
     * <p>Called by {@code MessageService.markRead} when a recipient's client
     * reports the message as seen. Monotonic for the same reason as
     * {@link #markDelivered} — this is what drives the sender's read receipt,
     * which must never move backwards.
     */
    public void markRead() {
        if (status < MessageStatus.READ.ordinal()) {
            status = (short) MessageStatus.READ.ordinal();
            readAt = Instant.now();
        }
    }
}
