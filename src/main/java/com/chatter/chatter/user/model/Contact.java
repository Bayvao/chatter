package com.chatter.chatter.user.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Both sides are user-module data, so the foreign keys are kept in the schema.
 * The entity still models them as plain UUIDs rather than {@code @ManyToOne}
 * associations — a contact list needs ids, not lazy proxies, and this avoids
 * the N+1 that an association would invite when rendering the list.
 */
@Entity
@Table(name = "contacts", schema = "app_user")
@IdClass(Contact.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class Contact {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Column(name = "contact_user_id")
    private UUID contactUserId;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt = Instant.now();

    @Column(name = "is_blocked", nullable = false)
    private boolean blocked;

    @Column(name = "is_favorite", nullable = false)
    private boolean favorite;

    public Contact(UUID userId, UUID contactUserId) {
        this.userId = userId;
        this.contactUserId = contactUserId;
    }

    public record Key(UUID userId, UUID contactUserId) implements Serializable {
    }
}
