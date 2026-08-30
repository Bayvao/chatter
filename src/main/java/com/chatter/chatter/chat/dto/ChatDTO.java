package com.chatter.chatter.chat.dto;

import java.time.Instant;
import java.util.UUID;

public record ChatDTO(
        UUID id,
        boolean group,
        String title,
        String avatarUrl,
        UUID otherUserId,
        String otherUserName,
        Instant lastMessageAt,
        String lastMessage,
        long unreadCount) {
}
