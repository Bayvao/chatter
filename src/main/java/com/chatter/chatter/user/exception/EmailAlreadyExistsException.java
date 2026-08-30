package com.chatter.chatter.user.exception;

import org.springframework.http.HttpStatus;

import com.chatter.chatter.common.exception.ApplicationException;

public class EmailAlreadyExistsException extends ApplicationException {

    public EmailAlreadyExistsException(String email) {
        super(HttpStatus.CONFLICT, "Email '" + email + "' is already registered");
    }
}
