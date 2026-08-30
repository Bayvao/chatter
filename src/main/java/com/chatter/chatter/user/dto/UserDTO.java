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
