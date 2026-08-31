package com.chatter.chatter.user.exception;

import org.springframework.http.HttpStatus;

import com.chatter.chatter.common.exception.ApplicationException;

public class EmailAlreadyExistsException extends ApplicationException {

    /**
     * Raised when registering an e-mail already on file.
     *
     * <p>Thrown by {@code UserService.register}, and only when an e-mail was
     * actually supplied — the field is optional, and blank ones are stored as
     * NULL precisely so many accounts may have none.
     *
     * @param email the e-mail that is taken
     */
    public EmailAlreadyExistsException(String email) {
        super(HttpStatus.CONFLICT, "Email '" + email + "' is already registered");
    }
}
