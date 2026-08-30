package com.chatter.chatter.user.exception;

import org.springframework.http.HttpStatus;

import com.chatter.chatter.common.exception.ApplicationException;

public class UserNotFoundException extends ApplicationException {

    public UserNotFoundException(String identifier) {
        super(HttpStatus.NOT_FOUND, "User not found: " + identifier);
    }
}
