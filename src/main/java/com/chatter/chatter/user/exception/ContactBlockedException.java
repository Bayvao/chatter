package com.chatter.chatter.user.exception;

import org.springframework.http.HttpStatus;

import com.chatter.chatter.common.exception.ApplicationException;

public class ContactBlockedException extends ApplicationException {

    /**
     * Raised when requesting someone who has blocked you.
     *
     * <p>Thrown by {@code ContactService.sendRequest}. Maps to 403 with a
     * deliberately vague message: confirming "this person blocked you" tells a
     * harasser their target acted, which is exactly what blocking is meant to
     * avoid.
     */
    public ContactBlockedException() {
        super(HttpStatus.FORBIDDEN, "Cannot send a contact request to this user");
    }
}
