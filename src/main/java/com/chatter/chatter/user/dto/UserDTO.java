package com.chatter.chatter.user.dto;

import java.util.UUID;

import com.chatter.chatter.user.model.User;

public record UserDTO(
        UUID id,
        String username,
        String email,
        String firstName,
        String lastName,
        String avatarUrl,
        String statusText,
        String displayName) {

    /**
     * Projects a user entity to its wire form.
     *
     * <p>Used wherever a user is returned — auth responses, search results, and
     * nested inside {@link ContactDTO}.
     *
     * <p>Deliberately omits the password hash, the profile-change version, and
     * the enabled and erased flags: a DTO rather than the entity is precisely
     * what keeps those off the wire.
     *
     * <p>{@code displayName} is computed rather than stored, so the client never
     * has to reimplement the "full name, else username" fallback.
     */
    public static UserDTO from(User user) {
        return new UserDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getAvatarUrl(),
                user.getStatusText(),
                user.getDisplayName());
    }
}
