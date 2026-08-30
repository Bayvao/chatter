package com.chatter.chatter.user.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.chatter.chatter.user.model.User;
import com.chatter.chatter.user.model.UserProfile;

public record ProfileDTO(
        UUID id,
        String username,
        String email,
        String firstName,
        String lastName,
        String avatarUrl,
        String statusText,
        String displayName,
        String phoneNumber,
        String bio,
        LocalDate dateOfBirth,
        String location,
        String website) {

    public static ProfileDTO from(User user, UserProfile profile) {
        return new ProfileDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getAvatarUrl(),
                user.getStatusText(),
                user.getDisplayName(),
                profile == null ? null : profile.getPhoneNumber(),
                profile == null ? null : profile.getBio(),
                profile == null ? null : profile.getDateOfBirth(),
                profile == null ? null : profile.getLocation(),
                profile == null ? null : profile.getWebsite());
    }
}
