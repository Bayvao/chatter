package com.chatter.chatter.chat.exception;

import java.util.UUID;

import org.springframework.http.HttpStatus;

import com.chatter.chatter.common.exception.ApplicationException;

public class NotAParticipantException extends ApplicationException {

    public NotAParticipantException(UUID chatId, UUID userId) {
        super(HttpStatus.FORBIDDEN, "User " + userId + " is not a participant of chat " + chatId);
    }
}
