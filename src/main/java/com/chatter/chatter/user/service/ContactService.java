package com.chatter.chatter.user.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatter.chatter.user.dto.ContactDTO;
import com.chatter.chatter.user.exception.ContactAlreadyExistsException;
import com.chatter.chatter.user.exception.ContactNotFoundException;
import com.chatter.chatter.user.model.Contact;
import com.chatter.chatter.user.repository.ContactRepository;

/**
 * A user's personal address book: who they have saved, favourited or blocked.
 *
 * <p>Contacts are one-directional and carry no permission weight — you do not
 * need to be someone's contact to message them. The list exists to make people
 * easy to find again, and the blocked flag to keep them out of search results.
 */
@Service
@Transactional(readOnly = true)
public class ContactService {

    private final ContactRepository contactRepository;
    private final UserService userService;

    public ContactService(ContactRepository contactRepository, UserService userService) {
        this.contactRepository = contactRepository;
        this.userService = userService;
    }

    /**
     * Saves someone to the caller's contact list.
     *
     * <p>Used by {@code UserController.addContact}, reached from the "Add
     * contact" button beside a search result. The target is loaded first purely
     * to validate it exists, so a typo'd id fails here rather than leaving a row
     * pointing at nobody.
     *
     * @throws IllegalArgumentException if adding yourself
     * @throws ContactAlreadyExistsException if already saved — the controller
     *         maps this to 409, which the frontend deliberately ignores
     */
    @Transactional
    public Contact addContact(UUID userId, UUID contactUserId) {
        if (userId.equals(contactUserId)) {
            throw new IllegalArgumentException("Cannot add yourself as a contact");
        }
        // Throws UserNotFoundException if the target does not exist.
        userService.getById(contactUserId);

        if (contactRepository.existsByUserIdAndContactUserId(userId, contactUserId)) {
            throw new ContactAlreadyExistsException();
        }

        return contactRepository.save(new Contact(userId, contactUserId));
    }

    /**
     * Deletes a contact outright.
     *
     * <p>Used by {@code UserController.removeContact}. This is a real delete,
     * not the blocked flag: removing forgets the person entirely, whereas
     * {@link #setBlocked} keeps the row so they stay suppressed from search.
     *
     * @throws ContactNotFoundException if the caller has no such contact
     */
    @Transactional
    public void removeContact(UUID userId, UUID contactUserId) {
        if (!contactRepository.existsByUserIdAndContactUserId(userId, contactUserId)) {
            throw new ContactNotFoundException(contactUserId);
        }
        contactRepository.deleteByUserIdAndContactUserId(userId, contactUserId);
    }

    /**
     * Blocks or unblocks a contact.
     *
     * <p>Used by {@code UserController.setBlocked}. A blocked contact is hidden
     * from {@link #listContacts} and filtered out of user search via
     * {@link #blockedIds}, but the row survives so unblocking restores it.
     *
     * <p>No explicit save: the entity is managed inside this transaction, so
     * the change is flushed on commit.
     *
     * @throws ContactNotFoundException if the caller has no such contact
     */
    @Transactional
    public void setBlocked(UUID userId, UUID contactUserId, boolean blocked) {
        Contact contact = contactRepository.findByUserIdAndContactUserId(userId, contactUserId)
                .orElseThrow(() -> new ContactNotFoundException(contactUserId));
        contact.setBlocked(blocked);
    }

    /**
     * Marks a contact as a favourite, or clears the mark.
     *
     * <p>Used by {@code UserController.setFavorite}. Purely a display hint for
     * ordering and pinning; it grants nothing and hides nothing.
     *
     * @throws ContactNotFoundException if the caller has no such contact
     */
    @Transactional
    public void setFavorite(UUID userId, UUID contactUserId, boolean favorite) {
        Contact contact = contactRepository.findByUserIdAndContactUserId(userId, contactUserId)
                .orElseThrow(() -> new ContactNotFoundException(contactUserId));
        contact.setFavorite(favorite);
    }

    /**
     * The caller's contact list, newest first, ready for rendering.
     *
     * <p>Used by {@code UserController.listContacts}, backing the Contacts tab.
     * Each row is joined against the live user record, so a contact who has
     * since changed their display name or avatar shows the current one.
     *
     * <p>Blocked contacts are excluded — they are still stored, just not listed.
     */
    public List<ContactDTO> listContacts(UUID userId) {
        return contactRepository.findByUserIdAndBlockedFalseOrderByAddedAtDesc(userId).stream()
                .map(contact -> ContactDTO.from(contact, userService.getById(contact.getContactUserId())))
                .toList();
    }

    /**
     * The ids this user has blocked, for filtering elsewhere.
     *
     * <p>Used by {@code UserController.search} to drop blocked people from
     * search results. Returned as bare ids rather than full contacts because
     * the caller only needs a set to test membership against.
     */
    public List<UUID> blockedIds(UUID userId) {
        return contactRepository.findBlockedContactIds(userId);
    }
}
