package com.chatter.chatter.user.exception;

import java.util.UUID;

import org.springframework.http.HttpStatus;

import com.chatter.chatter.common.exception.ApplicationException;

public class ContactRequestNotFoundException extends ApplicationException {

    /**
     * Raised when accepting or declining a request that is not outstanding.
     *
     * <p>Thrown by {@code ContactService.acceptRequest} and
     * {@code declineRequest}. Maps to 404 — covers both "never sent" and
     * "already answered", which are indistinguishable once the row is gone and
     * which the caller should treat the same way.
     *
     * @param otherUserId the other party in the request that was not found
     */
    public ContactRequestNotFoundException(UUID otherUserId) {
        super(HttpStatus.NOT_FOUND, "No pending contact request with user " + otherUserId);
    }
}
