package com.chatter.chatter.chat.dto;

import java.time.Instant;
import java.util.UUID;

import com.chatter.chatter.chat.model.Message;

public record MessageDTO(
        UUID id,
        UUID chatId,
        UUID senderId,
        String senderName,
        String senderAvatar,
        long seq,
        String content,
        String status,
        Instant createdAt,
        Instant deliveredAt,
        Instant readAt,
        boolean deleted) {

    /**
     * Projects a message to its wire form.
     *
     * <p>The single rendering path: REST history, the live broadcast, and sync
     * batches all go through here, so a message looks identical however it
     * arrives.
     *
     * <p>A deleted message keeps its id and {@code seq} — clients treat a gap in
     * the sequence as "not fetched yet" and would resync forever — but its
     * content is dropped and {@code deleted} set, which is what the UI renders
     * as a tombstone.
     */
    public static MessageDTO from(Message message) {
        boolean deleted = message.getDeletedAt() != null;
        return new MessageDTO(
                message.getId(),
                message.getChatId(),
                message.getSenderId(),
                message.getSender().getName(),
                message.getSender().getAvatarUrl(),
                message.getSeq(),
                deleted ? null : message.getContent(),
                message.status().name(),
                message.getCreatedAt(),
                message.getDeliveredAt(),
                message.getReadAt(),
                deleted);
    }
}
