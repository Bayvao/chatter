package com.chatter.chatter.chat.exception;

import java.util.UUID;

import org.springframework.http.HttpStatus;

import com.chatter.chatter.common.exception.ApplicationException;

public class NotConnectedException extends ApplicationException {

    /**
     * Raised when opening a direct chat with someone who is not a contact.
     *
     * <p>Thrown by {@code ChatService.getOrCreateDirectChat}. Maps to 403.
     *
     * <p>This is the check that was missing: before it, a chat could be opened
     * with any user id at all, so a friend request appeared to be accepted the
     * moment it was sent.
     *
     * @param userId the user the caller is not connected to
     */
    public NotConnectedException(UUID userId) {
        super(HttpStatus.FORBIDDEN, "You must be contacts with user " + userId + " to start a chat");
    }
}
