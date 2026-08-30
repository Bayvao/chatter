package com.chatter.chatter.user.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Size;

/**
 * Every field is optional; a null leaves the stored value untouched, so a
 * client can patch one field without resending the whole profile.
 */
public record UpdateProfileRequest(
        @Size(max = 80) String firstName,
        @Size(max = 80) String lastName,
        @Size(max = 512) String avatarUrl,
        @Size(max = 200) String statusText,
        @Size(max = 32) String phoneNumber,
        @Size(max = 500) String bio,
        LocalDate dateOfBirth,
        @Size(max = 120) String location,
        @Size(max = 512) String website) {
}
