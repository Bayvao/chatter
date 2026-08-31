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

    /**
     * Flattens the two profile tables into one response.
     *
     * <p>Used by the profile endpoints. The split exists because {@code users}
     * holds the fields messages snapshot and {@code user_profiles} everything
     * else, but a client has no reason to care — it sees one profile.
     *
     * <p>A null {@code profile} is expected, not exceptional: the extended row
     * is created lazily on first save, so a user who has never opened the form
     * has none, and every extended field comes back null.
     */
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
