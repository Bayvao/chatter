package com.chatter.chatter.user.dto;

import java.time.Instant;

import com.chatter.chatter.user.model.Contact;
import com.chatter.chatter.user.model.User;

public record ContactDTO(UserDTO user, Instant addedAt, boolean favorite) {

    /**
     * Joins a contact row to the live user it points at.
     *
     * <p>Used by {@code ContactService.listContacts}. The user is passed in
     * rather than looked up here, because a contact stores only an id — and
     * reading it live is what makes a contact who has since changed their name
     * or avatar render with the current one.
     */
    public static ContactDTO from(Contact contact, User contactUser) {
        return new ContactDTO(UserDTO.from(contactUser), contact.getAddedAt(), contact.isFavorite());
    }
}
