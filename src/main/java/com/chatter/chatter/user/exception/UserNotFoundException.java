package com.chatter.chatter.user.exception;

import org.springframework.http.HttpStatus;

import com.chatter.chatter.common.exception.ApplicationException;

public class UserNotFoundException extends ApplicationException {

    /**
     * Raised when a lookup by id or username finds nothing.
     *
     * <p>Thrown by {@code UserService.getById} and {@code getByUsername}, and by
     * {@code UserSenderDirectory.lookup} — which is how opening a chat with a
     * nonexistent person is rejected.
     *
     * <p>Maps to 404. Note the message echoes the identifier, so this must not
     * reach an unauthenticated caller on the login path, where it would allow
     * account enumeration; {@code GlobalExceptionHandler} answers login failures
     * with a generic 401 instead.
     *
     * @param identifier the id or username that was not found
     */
    public UserNotFoundException(String identifier) {
        super(HttpStatus.NOT_FOUND, "User not found: " + identifier);
    }
}
