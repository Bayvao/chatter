package com.chatter.chatter.chat.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
        @NotNull UUID chatId,
        @NotBlank @Size(max = 8000) String content,
        /** Optional, but supplying it makes a retried send idempotent. */
        UUID clientMsgId) {
}
