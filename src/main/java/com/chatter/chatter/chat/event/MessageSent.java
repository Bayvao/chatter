package com.chatter.chatter.chat.event;

import java.time.Instant;
import java.util.UUID;

import com.chatter.chatter.chat.model.Message;
import com.chatter.chatter.common.Ids;

/**
 * Published in-process today and consumed by the WebSocket broadcaster. In
 * Phase 5 the same record is written to an outbox row and relayed to Kafka,
 * partitioned by {@code chatId} to preserve per-chat ordering; only the
 * listener annotation changes.
 *
 * <p>{@code eventId} exists for consumer-side idempotency, because Kafka
 * delivers at-least-once. It is not optional even while consumers are local.
 */
public record MessageSent(
        UUID eventId,
        UUID messageId,
        UUID chatId,
        long seq,
        UUID senderId,
        String senderName,
        String content,
        Instant sentAt) {

    /**
     * Captures a just-saved message as an event.
     *
     * <p>Called by {@code MessageService.send} while still inside the send
     * transaction; {@code MessageBroadcaster} consumes it once that commits.
     *
     * <p>Carries the sender's snapshotted name rather than an id, so the
     * broadcaster can build a push notification without a second lookup.
     */
    public static MessageSent from(Message message) {
        return new MessageSent(
                Ids.newId(),
                message.getId(),
                message.getChatId(),
                message.getSeq(),
                message.getSenderId(),
                message.getSender().getName(),
                message.getContent(),
                message.getCreatedAt());
    }
}
