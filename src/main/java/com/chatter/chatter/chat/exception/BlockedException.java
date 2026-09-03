package com.chatter.chatter.chat.exception;

import java.util.UUID;

import org.springframework.http.HttpStatus;

import com.chatter.chatter.common.exception.ApplicationException;

public class BlockedException extends ApplicationException {

    /**
     * Raised when a block stands between the two parties to a direct chat.
     *
     * <p>Thrown by {@code MessageService.send}. Maps to 403.
     *
     * <p>This is the check whose absence let a conversation carry on after a
     * block: membership was verified, the relationship never was, so both
     * parties kept messaging as though nothing had happened.
     *
     * <p>The message names no one and says nothing about who blocked whom —
     * confirming that to the blocked party tells a harasser their target acted.
     *
     * @param chatId the conversation the message was addressed to
     */
    public BlockedException(UUID chatId) {
        super(HttpStatus.FORBIDDEN, "Messaging is unavailable in chat " + chatId);
    }
}
