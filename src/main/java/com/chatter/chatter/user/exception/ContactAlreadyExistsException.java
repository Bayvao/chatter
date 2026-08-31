package com.chatter.chatter.user.exception;

import org.springframework.http.HttpStatus;

import com.chatter.chatter.common.exception.ApplicationException;

public class ContactAlreadyExistsException extends ApplicationException {

    /**
     * Raised when saving a contact the user already has.
     *
     * <p>Thrown by {@code ContactService.addContact} and mapped to 409, which
     * the frontend deliberately swallows — "Add contact" is fire-and-forget, and
     * already having them is not a failure worth showing.
     *
     * <p>Takes no argument: the caller already knows who they tried to add.
     */
    public ContactAlreadyExistsException() {
        super(HttpStatus.CONFLICT, "Contact already exists");
    }
}
