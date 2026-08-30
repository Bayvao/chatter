package com.chatter.chatter.chat.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Denormalised copy of the sender's display fields. The chat module cannot
 * join to {@code app_user.users}, so it keeps this and refreshes it from
 * profile-change events.
 *
 * <p>{@code version} is the out-of-order guard: an update is applied only
 * when it is newer than what is stored, because events arrive at-least-once
 * and can land backwards.
 *
 * <p>Only snapshot fields that change rarely. Display name and avatar
 * qualify; status text does not — it would rewrite every message the user
 * ever sent.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SenderSnapshot {

    @Column(name = "sender_name", length = 120)
    private String name;

    @Column(name = "sender_avatar_url", length = 512)
    private String avatarUrl;

    @Column(name = "sender_version", nullable = false)
    private long version;
}
