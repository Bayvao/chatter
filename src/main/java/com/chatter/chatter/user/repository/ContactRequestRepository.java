package com.chatter.chatter.user.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.chatter.chatter.user.model.ContactRequest;

@Repository
public interface ContactRequestRepository extends JpaRepository<ContactRequest, UUID> {

    /**
     * The one pending request between two users, in either direction.
     *
     * <p>The pre-check in {@code sendRequest}, and the re-read after losing the
     * unique constraint — which is how a crossing request is recognised and
     * auto-accepted.
     */
    Optional<ContactRequest> findByPairKey(String pairKey);

    /**
     * Requests addressed to a user, newest first.
     *
     * <p>Backs {@code GET /me/contacts/requests}, the inbox the Contacts tab
     * renders.
     */
    List<ContactRequest> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId);

    /**
     * Requests a user has sent and not yet had answered.
     *
     * <p>Backs {@code GET /me/contacts/requests/sent}, so a search result can
     * show "Requested" rather than offering to send a second one.
     */
    List<ContactRequest> findByRequesterIdOrderByCreatedAtDesc(UUID requesterId);
}
