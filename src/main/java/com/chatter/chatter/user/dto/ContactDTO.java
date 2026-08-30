package com.chatter.chatter.user.dto;

import java.time.Instant;

import com.chatter.chatter.user.model.Contact;
import com.chatter.chatter.user.model.User;

public record ContactDTO(UserDTO user, Instant addedAt, boolean blocked, boolean favorite) {

    public static ContactDTO from(Contact contact, User contactUser) {
        return new ContactDTO(UserDTO.from(contactUser), contact.getAddedAt(), contact.isBlocked(),
                contact.isFavorite());
    }
}
