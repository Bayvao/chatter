package com.chatter.chatter.user.exception;

import java.util.UUID;

import org.springframework.http.HttpStatus;

import com.chatter.chatter.common.exception.ApplicationException;

public class ContactNotFoundException extends ApplicationException {

    /**
     * Raised when acting on a contact the user has not saved.
     *
     * <p>Thrown by {@code removeContact}, {@code setBlocked} and
     * {@code setFavorite}. Maps to 404 — without it a delete of nothing would
     * pass silently and report success.
     *
     * @param contactUserId the contact that is not in the caller's list
     */
    public ContactNotFoundException(UUID contactUserId) {
        super(HttpStatus.NOT_FOUND, "Contact not found: " + contactUserId);
    }
}
