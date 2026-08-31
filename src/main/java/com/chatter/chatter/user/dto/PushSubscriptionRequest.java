package com.chatter.chatter.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Mirrors the browser's {@code PushSubscription.toJSON()} shape, flattened:
 * {@code keys.p256dh} and {@code keys.auth} arrive as their own fields.
 */
public record PushSubscriptionRequest(
        @NotBlank @Size(max = 1024) String endpoint,
        @NotBlank String p256dh,
        @NotBlank String auth) {
}
