package com.chatter.chatter.user.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.chatter.chatter.common.Ids;

/**
 * A friend request awaiting a decision.
 *
 * <p>Rows live only while a request is outstanding: accepting deletes this and
 * writes two {@link Contact} rows, declining just deletes it. So "pending" is
 * the existence of a row rather than a status to filter on, and the table stays
 * the size of the outstanding backlog rather than of all history.
 *
 * <p>{@link #pairKey} is the concurrency control — see {@link ContactPair}.
 */
@Entity
@Table(name = "contact_requests", schema = "app_user")
@Getter
@NoArgsConstructor
public class ContactRequest {

    @Id
    private UUID id;

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    /** Identical for alice->bob and bob->alice; uniquely indexed. */
    @Column(name = "pair_key", nullable = false, length = 73)
    private String pairKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /**
     * Opens a request from one user to another.
     *
     * <p>Called only from {@code ContactService.sendRequest}. The pair key is
     * derived here rather than passed in, so no caller can produce a row whose
     * key disagrees with its own two ids.
     */
    public ContactRequest(UUID requesterId, UUID recipientId) {
        this.id = Ids.newId();
        this.requesterId = requesterId;
        this.recipientId = recipientId;
        this.pairKey = ContactPair.keyOf(requesterId, recipientId);
    }

    /**
     * Whether this request was sent by the given user to the other one.
     *
     * <p>Used after a unique-constraint loss to tell the two races apart: a
     * surviving row in the *same* direction means a duplicate click, one in the
     * opposite direction means the two of them asked simultaneously.
     */
    public boolean isFrom(UUID userId) {
        return requesterId.equals(userId);
    }
}
