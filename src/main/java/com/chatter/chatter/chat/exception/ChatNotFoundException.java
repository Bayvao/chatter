package com.chatter.chatter.chat.exception;

import java.util.UUID;

import org.springframework.http.HttpStatus;

import com.chatter.chatter.common.exception.ApplicationException;

public class ChatNotFoundException extends ApplicationException {

    public ChatNotFoundException(UUID chatId) {
        super(HttpStatus.NOT_FOUND, "Chat not found: " + chatId);
    }
}
