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
