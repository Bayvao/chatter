package com.chatter.chatter.user.exception;

import org.springframework.http.HttpStatus;

import com.chatter.chatter.common.exception.ApplicationException;

public class UsernameAlreadyExistsException extends ApplicationException {

    /**
     * Raised when registering a username someone already holds.
     *
     * <p>Thrown by {@code UserService.register}. Maps to 409, which the Register
     * screen shows as a field error.
     *
     * @param username the username that is taken
     */
    public UsernameAlreadyExistsException(String username) {
        super(HttpStatus.CONFLICT, "Username '" + username + "' is already taken");
    }
}
