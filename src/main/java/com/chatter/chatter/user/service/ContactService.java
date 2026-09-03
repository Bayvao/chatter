package com.chatter.chatter.user.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatter.chatter.user.dto.ContactDTO;
import com.chatter.chatter.user.dto.ContactRequestDTO;
import com.chatter.chatter.user.event.ContactChanged;
import com.chatter.chatter.user.exception.ContactAlreadyExistsException;
import com.chatter.chatter.user.exception.ContactBlockedException;
import com.chatter.chatter.user.exception.ContactNotFoundException;
import com.chatter.chatter.user.exception.ContactRequestNotFoundException;
import com.chatter.chatter.user.model.Contact;
import com.chatter.chatter.user.model.ContactPair;
import com.chatter.chatter.user.model.ContactRequest;
import com.chatter.chatter.user.repository.ContactRepository;
import com.chatter.chatter.user.repository.ContactRequestRepository;

/**
 * Friendships and the requests that create them.
 *
 * <p>A friendship is <em>mutual</em> and is stored as two {@link Contact} rows,
 * one per direction, so each side keeps its own {@code blocked} and
 * {@code favorite} flags — Alice blocking Bob is not Bob blocking Alice. A
 * pending request is a single {@link ContactRequest} row that is deleted once
 * answered.
 *
 * <p>Being contacts is a precondition for opening a direct chat; see
 * {@code RelationshipDirectory}.
 */
@Service
@Transactional(readOnly = true)
public class ContactService {

    private final ContactRepository contactRepository;
    private final ContactRequestRepository requestRepository;
    private final ContactWriter writer;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;

    public ContactService(ContactRepository contactRepository, ContactRequestRepository requestRepository,
                           ContactWriter writer, UserService userService,
                           ApplicationEventPublisher eventPublisher) {
        this.contactRepository = contactRepository;
        this.requestRepository = requestRepository;
        this.writer = writer;
        this.userService = userService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Sends a friend request, or completes one that was already coming the
     * other way.
     *
     * <p>Used by {@code UserController.sendRequest}, from "Add friend" beside a
     * search result.
     *
     * <p>Deliberately <b>not</b> transactional. The insert may lose the unique
     * {@code pair_key} race, and a failed statement marks its transaction
     * rollback-only — so the work that follows has to happen in a fresh one.
     * Each unit is therefore delegated to {@link ContactWriter}, which is a
     * separate bean so the calls actually pass through the transactional proxy.
     *
     * <p>The pre-checks below are for the error message only; the unique
     * constraint is what guarantees one request per pair.
     *
     * @return the created request, or empty when the race resolved into an
     *         immediate friendship
     * @throws IllegalArgumentException if requesting yourself
     * @throws ContactAlreadyExistsException if already contacts or already asked
     * @throws ContactBlockedException if the recipient has blocked the caller
     */
    public Optional<ContactRequest> sendRequest(UUID requesterId, UUID recipientId) {
        if (requesterId.equals(recipientId)) {
            throw new IllegalArgumentException("Cannot send a contact request to yourself");
        }
        // Throws UserNotFoundException if the target does not exist.
        userService.getById(recipientId);

        if (contactRepository.existsByUserIdAndContactUserId(requesterId, recipientId)) {
            throw new ContactAlreadyExistsException();
        }
        // Read on the recipient's own row: they blocked us, not the reverse.
        if (isBlockedBy(recipientId, requesterId)) {
            throw new ContactBlockedException();
        }

        ContactRequest created;
        try {
            created = writer.createRequest(requesterId, recipientId);
        } catch (DataIntegrityViolationException e) {
            resolveRace(requesterId, recipientId);
            return Optional.empty();
        }

        // The insert can still land just after a racing thread accepted a
        // crossing request: deleting that request frees the pair_key, so the
        // constraint does not stop us. Compensate rather than leave a phantom
        // request standing between two people who are now contacts.
        if (contactRepository.existsByUserIdAndContactUserId(requesterId, recipientId)) {
            writer.discard(created.getId());
            throw new ContactAlreadyExistsException();
        }

        return Optional.of(created);
    }

    /**
     * Decides what losing the {@code pair_key} race meant.
     *
     * <p>Called only from {@link #sendRequest}'s catch block, and only after
     * that failed transaction has been rolled back and left behind. Two cases,
     * and they must be told apart because only one is an error:
     *
     * <ul>
     *   <li><b>Same direction</b> — the caller clicked twice, or retried. The
     *       surviving row is already theirs, so this is a duplicate: 409.</li>
     *   <li><b>Opposite direction</b> — the two of them asked each other at the
     *       same instant. Both want the connection, so accept rather than fail
     *       one of them arbitrarily.</li>
     * </ul>
     *
     * <p>A row already gone by the time we look means the winner was answered
     * in between; treat that as the duplicate case rather than looping.
     */
    private void resolveRace(UUID requesterId, UUID recipientId) {
        ContactRequest winner = requestRepository.findByPairKey(ContactPair.keyOf(requesterId, recipientId))
                .orElseThrow(ContactAlreadyExistsException::new);

        if (winner.isFrom(requesterId)) {
            throw new ContactAlreadyExistsException();
        }

        writer.accept(requesterId, recipientId);
    }

    /**
     * Accepts a request, creating the mutual friendship.
     *
     * <p>Used by {@code UserController.acceptRequest}. Delegates so the write
     * runs through the proxy in its own transaction, the same unit
     * {@link #resolveRace} reuses when two requests crossed.
     *
     * @throws ContactRequestNotFoundException if no request is outstanding
     */
    public void acceptRequest(UUID accepterId, UUID requesterId) {
        writer.accept(accepterId, requesterId);
    }

    /**
     * Declines a request addressed to the caller, or cancels one they sent.
     *
     * <p>Used by {@code UserController.declineRequest}. One method for both
     * because the effect is identical — the row goes — and the caller is a party
     * to it either way.
     *
     * @throws ContactRequestNotFoundException if no request is outstanding
     */
    @Transactional
    public void declineRequest(UUID actorId, UUID otherUserId) {
        requestRepository.delete(pendingBetween(actorId, otherUserId));

        eventPublisher.publishEvent(
                ContactChanged.to(otherUserId, ContactChanged.Type.DECLINED, actorId, otherUserId));
    }

    /**
     * Ends a friendship, from both sides.
     *
     * <p>Used by {@code UserController.removeContact}. Deletes both rows, since
     * a friendship is mutual: leaving the other half would let them keep you in
     * their list and keep opening chats with you.
     *
     * @throws ContactNotFoundException if they are not a contact
     */
    @Transactional
    public void removeContact(UUID userId, UUID contactUserId) {
        if (!contactRepository.existsByUserIdAndContactUserId(userId, contactUserId)) {
            throw new ContactNotFoundException(contactUserId);
        }
        contactRepository.deleteByUserIdAndContactUserId(userId, contactUserId);
        // The other side goes too, since a friendship is mutual — unless they
        // blocked us. A block must not be clearable by the person blocked, so
        // that row stays and keeps them out of our search results and requests.
        if (!isBlockedBy(contactUserId, userId)) {
            contactRepository.deleteByUserIdAndContactUserId(contactUserId, userId);
        }

        eventPublisher.publishEvent(
                ContactChanged.to(contactUserId, ContactChanged.Type.REMOVED, userId, contactUserId));
    }

    /**
     * Blocks or unblocks a contact.
     *
     * <p>Used by {@code UserController.setBlocked}. Hides them from the caller's
     * contact list and search results, and stops them sending a new request. The
     * row survives so unblocking restores it.
     *
     * <p>The event goes to the caller's own sessions only — never to the person
     * blocked. Telling someone they have been blocked hands a harasser the
     * signal that their target acted, which is the thing blocking exists to
     * prevent.
     *
     * @throws ContactNotFoundException if they are not a contact
     */
    @Transactional
    public void setBlocked(UUID userId, UUID contactUserId, boolean blocked) {
        Contact contact = contactRepository.findByUserIdAndContactUserId(userId, contactUserId)
                .orElseThrow(() -> new ContactNotFoundException(contactUserId));
        contact.setBlocked(blocked);

        eventPublisher.publishEvent(
                ContactChanged.to(userId, ContactChanged.Type.BLOCKED, userId, contactUserId));
    }

    /**
     * Marks a contact as a favourite, or clears the mark.
     *
     * <p>Used by {@code UserController.setFavorite}. A display hint for ordering
     * only; it grants nothing and hides nothing, so no event is published.
     *
     * @throws ContactNotFoundException if they are not a contact
     */
    @Transactional
    public void setFavorite(UUID userId, UUID contactUserId, boolean favorite) {
        Contact contact = contactRepository.findByUserIdAndContactUserId(userId, contactUserId)
                .orElseThrow(() -> new ContactNotFoundException(contactUserId));
        contact.setFavorite(favorite);
    }

    /**
     * The caller's accepted contacts, newest first.
     *
     * <p>Backs the Contacts tab. Blocked contacts are excluded — still stored,
     * just not listed.
     */
    public List<ContactDTO> listContacts(UUID userId) {
        return contactRepository.findByUserIdAndBlockedFalseOrderByAddedAtDesc(userId).stream()
                .map(contact -> ContactDTO.from(contact, userService.getById(contact.getContactUserId())))
                .toList();
    }

    /**
     * Requests waiting on the caller's decision.
     *
     * <p>Backs {@code GET /me/contacts/requests} and the Requests section of the
     * Contacts tab. This is the REST source of truth that the live STOMP relay
     * accelerates but does not replace — a request that arrives while the
     * recipient is offline is seen here on next load.
     */
    public List<ContactRequestDTO> incomingRequests(UUID userId) {
        return requestRepository.findByRecipientIdOrderByCreatedAtDesc(userId).stream()
                .map(request -> ContactRequestDTO.from(request, userService.getById(request.getRequesterId())))
                .toList();
    }

    /**
     * Requests the caller has sent and not yet had answered.
     *
     * <p>Lets a search result show "Requested" instead of offering to send a
     * second one that would only 409.
     */
    public List<ContactRequestDTO> outgoingRequests(UUID userId) {
        return requestRepository.findByRequesterIdOrderByCreatedAtDesc(userId).stream()
                .map(request -> ContactRequestDTO.from(request, userService.getById(request.getRecipientId())))
                .toList();
    }

    /**
     * Whether two users may open a direct chat.
     *
     * <p>Called through {@code RelationshipDirectory} by the chat module. Both
     * rows must exist and neither side may have blocked the other: a friendship
     * is mutual, and a half-deleted or blocked one is not a licence to start a
     * conversation.
     */
    public boolean areConnected(UUID userA, UUID userB) {
        return contactRepository.findByUserIdAndContactUserId(userA, userB)
                .filter(contact -> !contact.isBlocked())
                .flatMap(contact -> contactRepository.findByUserIdAndContactUserId(userB, userA))
                .filter(contact -> !contact.isBlocked())
                .isPresent();
    }

    /**
     * The ids this user has blocked, for filtering them out of search results.
     */
    public List<UUID> blockedIds(UUID userId) {
        return contactRepository.findBlockedContactIds(userId);
    }

    /** The outstanding request between two users, whichever way it points. */
    private ContactRequest pendingBetween(UUID userId, UUID otherUserId) {
        return requestRepository.findByPairKey(ContactPair.keyOf(userId, otherUserId))
                .orElseThrow(() -> new ContactRequestNotFoundException(otherUserId));
    }

    /** Whether {@code ownerId} has blocked {@code otherId}. */
    private boolean isBlockedBy(UUID ownerId, UUID otherId) {
        return contactRepository.findByUserIdAndContactUserId(ownerId, otherId)
                .map(Contact::isBlocked)
                .orElse(false);
    }
}
