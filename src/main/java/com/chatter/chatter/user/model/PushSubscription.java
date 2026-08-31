package com.chatter.chatter.user.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.chatter.chatter.common.Ids;

/**
 * A browser's Web Push subscription. The {@code p256dhKey} and
 * {@code authSecret} are the client's half of the ECDH exchange — the server
 * encrypts each payload with them, so the push service relays ciphertext it
 * cannot read.
 */
@Entity
@Table(name = "push_subscriptions", schema = "app_user")
@Getter
@Setter
@NoArgsConstructor
public class PushSubscription {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 1024)
    private String endpoint;

    @Column(name = "p256dh_key", nullable = false)
    private String p256dhKey;

    @Column(name = "auth_secret", nullable = false)
    private String authSecret;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    /**
     * Records a browser's Web Push registration.
     *
     * <p>Called from {@code PushSubscriptionService.subscribe} when no row for
     * the endpoint exists yet.
     *
     * <p>The two keys are the browser's, not ours: {@code p256dhKey} is its
     * public key and {@code authSecret} a shared secret, and together they are
     * what {@code WebPushSender} encrypts each payload for.
     */
    public PushSubscription(UUID userId, String endpoint, String p256dhKey, String authSecret, String userAgent) {
        this.id = Ids.newId();
        this.userId = userId;
        this.endpoint = endpoint;
        this.p256dhKey = p256dhKey;
        this.authSecret = authSecret;
        this.userAgent = userAgent;
    }
}
