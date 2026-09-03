package com.chatter.chatter.user.dto;

import java.time.Instant;
import java.util.UUID;

import com.chatter.chatter.user.model.ContactRequest;
import com.chatter.chatter.user.model.User;

/**
 * A pending request as the inbox renders it.
 *
 * <p>{@code user} is always the <em>other</em> party — the sender for an
 * incoming request, the recipient for an outgoing one — so one shape serves
 * both lists and the client never has to work out which id is theirs.
 */
public record ContactRequestDTO(UUID id, UserDTO user, Instant createdAt) {

    /**
     * Joins a request row to the live user on its far side.
     *
     * <p>Used by {@code ContactService.incomingRequests} and
     * {@code outgoingRequests}, which each pass whichever user the caller is
     * not.
     */
    public static ContactRequestDTO from(ContactRequest request, User otherUser) {
        return new ContactRequestDTO(request.getId(), UserDTO.from(otherUser), request.getCreatedAt());
    }
}
