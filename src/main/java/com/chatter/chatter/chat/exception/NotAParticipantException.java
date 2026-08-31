package com.chatter.chatter.chat.exception;

import java.util.UUID;

import org.springframework.http.HttpStatus;

import com.chatter.chatter.common.exception.ApplicationException;

public class NotAParticipantException extends ApplicationException {

    /**
     * Raised when a user touches a chat they are not an active member of.
     *
     * <p>The authorization failure of the chat module: thrown by
     * {@code ChatService.requireActiveMember}, which guards every message
     * operation. Maps to 403 rather than 404 — the caller is authenticated, they
     * simply may not have this.
     *
     * <p>Also covers a member who has left: they keep their old history but may
     * not read or send anything new.
     *
     * @param chatId the chat being accessed
     * @param userId the user who is not a member of it
     */
    public NotAParticipantException(UUID chatId, UUID userId) {
        super(HttpStatus.FORBIDDEN, "User " + userId + " is not a participant of chat " + chatId);
    }
}
