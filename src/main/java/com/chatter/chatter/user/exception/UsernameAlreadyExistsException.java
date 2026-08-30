package com.chatter.chatter.user.exception;

import org.springframework.http.HttpStatus;

import com.chatter.chatter.common.exception.ApplicationException;

public class UsernameAlreadyExistsException extends ApplicationException {

    public UsernameAlreadyExistsException(String username) {
        super(HttpStatus.CONFLICT, "Username '" + username + "' is already taken");
    }
}
