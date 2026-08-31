package com.chatter.chatter.chat.exception;

import java.util.UUID;

import org.springframework.http.HttpStatus;

import com.chatter.chatter.common.exception.ApplicationException;

public class ChatNotFoundException extends ApplicationException {

    /**
     * Raised when a chat id resolves to nothing.
     *
     * <p>Thrown by {@code ChatService.getChatOrThrow} and by
     * {@code MessageService.send} when the chat or its sequence counter is
     * missing. Maps to 404.
     *
     * @param chatId the chat that does not exist
     */
    public ChatNotFoundException(UUID chatId) {
        super(HttpStatus.NOT_FOUND, "Chat not found: " + chatId);
    }
}
