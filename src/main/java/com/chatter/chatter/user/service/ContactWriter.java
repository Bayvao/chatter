package com.chatter.chatter.user.service;

import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.chatter.chatter.user.event.ContactChanged;
import com.chatter.chatter.user.exception.ContactAlreadyExistsException;
import com.chatter.chatter.user.exception.ContactRequestNotFoundException;
import com.chatter.chatter.user.model.Contact;
import com.chatter.chatter.user.model.ContactPair;
import com.chatter.chatter.user.model.ContactRequest;
import com.chatter.chatter.user.repository.ContactRepository;
import com.chatter.chatter.user.repository.ContactRequestRepository;

/**
 * The two writes that must each own their transaction, kept in a separate bean
 * so they are reached through the Spring proxy.
 *
 * <p>This split exists for one reason. {@code ContactService.sendRequest}
 * detects a lost race by catching the unique-constraint violation, but a failed
 * statement marks its transaction rollback-only — so the auto-accept that
 * follows cannot run in it. It needs a genuinely new transaction, and
 * {@code @Transactional} on a method the same bean calls internally is bypassed
 * entirely: the proxy is only applied on the way in from outside.
 *
 * <p>So {@code sendRequest} is not transactional itself. It calls in here for
 * each unit of work, and each unit commits or rolls back alone.
 */
@Component
public class ContactWriter {

    private final ContactRepository contactRepository;
    private final ContactRequestRepository requestRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ContactWriter(ContactRepository contactRepository, ContactRequestRepository requestRepository,
                          ApplicationEventPublisher eventPublisher) {
        this.contactRepository = contactRepository;
        this.requestRepository = requestRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Inserts a pending request, or fails on the unique {@code pair_key}.
     *
     * <p>Called by {@code ContactService.sendRequest}. {@code saveAndFlush}
     * rather than {@code save} so the violation surfaces here, inside this
     * transaction, instead of at an outer commit the caller could not catch.
     *
     * @throws org.springframework.dao.DataIntegrityViolationException if a
     *         request already exists between this pair, in either direction
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ContactRequest createRequest(UUID requesterId, UUID recipientId) {
        // Re-checked inside this transaction, not just by the caller. Between
        // the caller's check and here, a racing thread can have accepted a
        // crossing request and made the two of them contacts — which also frees
        // the pair_key, so the unique constraint would not catch it.
        if (contactRepository.existsByUserIdAndContactUserId(requesterId, recipientId)) {
            throw new ContactAlreadyExistsException();
        }

        ContactRequest request = requestRepository.saveAndFlush(new ContactRequest(requesterId, recipientId));

        eventPublisher.publishEvent(
                ContactChanged.to(recipientId, ContactChanged.Type.REQUESTED, requesterId, recipientId));
        return request;
    }

    /**
     * Deletes a request that should never have been created.
     *
     * <p>The compensating half of {@code ContactService.sendRequest}: an insert
     * can still win the pair_key in the instant after a racing thread made the
     * two of them contacts, and this removes the row rather than leaving a
     * phantom request between people who are already friends.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void discard(UUID requestId) {
        requestRepository.deleteById(requestId);
    }

    /**
     * Turns a pending request into the mutual friendship.
     *
     * <p>Called by {@code ContactService.acceptRequest}, and by its race path
     * when two people requested each other simultaneously. Deleting the request
     * and inserting both contact rows share one transaction, so a friendship is
     * never half-formed.
     *
     * <p>Both parties are told: the requester because they were waiting, and the
     * accepter because their other sessions must drop it from the inbox.
     *
     * @throws ContactRequestNotFoundException if another thread answered the
     *         request first — which is how simultaneous accepts settle
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void accept(UUID accepterId, UUID requesterId) {
        ContactRequest request = requestRepository.findByPairKey(ContactPair.keyOf(accepterId, requesterId))
                .orElseThrow(() -> new ContactRequestNotFoundException(requesterId));

        if (!request.isFrom(requesterId)) {
            // The caller's own outstanding request is cancelled, not accepted.
            throw new ContactRequestNotFoundException(requesterId);
        }

        requestRepository.delete(request);
        contactRepository.save(new Contact(accepterId, requesterId));
        contactRepository.save(new Contact(requesterId, accepterId));

        eventPublisher.publishEvent(
                ContactChanged.to(requesterId, ContactChanged.Type.ACCEPTED, accepterId, requesterId));
        eventPublisher.publishEvent(
                ContactChanged.to(accepterId, ContactChanged.Type.ACCEPTED, accepterId, requesterId));
    }
}
