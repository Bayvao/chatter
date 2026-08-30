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

@Service
@Transactional(readOnly = true)
public class ContactService {

    private final ContactRepository contactRepository;
    private final UserService userService;

    public ContactService(ContactRepository contactRepository, UserService userService) {
        this.contactRepository = contactRepository;
        this.userService = userService;
    }

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

    @Transactional
    public void removeContact(UUID userId, UUID contactUserId) {
        if (!contactRepository.existsByUserIdAndContactUserId(userId, contactUserId)) {
            throw new ContactNotFoundException(contactUserId);
        }
        contactRepository.deleteByUserIdAndContactUserId(userId, contactUserId);
    }

    @Transactional
    public void setBlocked(UUID userId, UUID contactUserId, boolean blocked) {
        Contact contact = contactRepository.findByUserIdAndContactUserId(userId, contactUserId)
                .orElseThrow(() -> new ContactNotFoundException(contactUserId));
        contact.setBlocked(blocked);
    }

    @Transactional
    public void setFavorite(UUID userId, UUID contactUserId, boolean favorite) {
        Contact contact = contactRepository.findByUserIdAndContactUserId(userId, contactUserId)
                .orElseThrow(() -> new ContactNotFoundException(contactUserId));
        contact.setFavorite(favorite);
    }

    /** Blocked contacts are excluded — they are still stored, just not listed. */
    public List<ContactDTO> listContacts(UUID userId) {
        return contactRepository.findByUserIdAndBlockedFalseOrderByAddedAtDesc(userId).stream()
                .map(contact -> ContactDTO.from(contact, userService.getById(contact.getContactUserId())))
                .toList();
    }

    public List<UUID> blockedIds(UUID userId) {
        return contactRepository.findBlockedContactIds(userId);
    }
}
