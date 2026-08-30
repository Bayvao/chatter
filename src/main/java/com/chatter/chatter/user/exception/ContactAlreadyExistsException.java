package com.chatter.chatter.user.exception;

import org.springframework.http.HttpStatus;

import com.chatter.chatter.common.exception.ApplicationException;

public class ContactAlreadyExistsException extends ApplicationException {

    public ContactAlreadyExistsException() {
        super(HttpStatus.CONFLICT, "Contact already exists");
    }
}
