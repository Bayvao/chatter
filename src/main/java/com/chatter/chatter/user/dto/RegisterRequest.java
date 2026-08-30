package com.chatter.chatter.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 64)
        @Pattern(regexp = "^[a-zA-Z0-9_.-]+$",
                message = "username may contain only letters, digits, '.', '_' and '-'")
        String username,

        @Email @Size(max = 320) String email,

        @NotBlank @Size(min = 8, max = 72) String password) {
}
