package com.chatter.chatter.user.exception;

import java.util.UUID;

import org.springframework.http.HttpStatus;

import com.chatter.chatter.common.exception.ApplicationException;

public class ContactNotFoundException extends ApplicationException {

    public ContactNotFoundException(UUID contactUserId) {
        super(HttpStatus.NOT_FOUND, "Contact not found: " + contactUserId);
    }
}
