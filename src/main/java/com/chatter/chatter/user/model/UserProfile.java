package com.chatter.chatter.user.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Extended profile fields. The display fields the rest of the system reads
 * (username, first/last name, avatar, status text) stay on {@link User} —
 * this table holds only what nothing else joins against.
 *
 * <p>Keeps its foreign key to users: same aggregate, same module, never splits.
 */
@Entity
@Table(name = "user_profiles", schema = "app_user")
@Getter
@Setter
@NoArgsConstructor
public class UserProfile {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "phone_number", length = 32)
    private String phoneNumber;

    @Column(length = 500)
    private String bio;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(length = 120)
    private String location;

    @Column(length = 512)
    private String website;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public UserProfile(UUID userId) {
        this.userId = userId;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }
}
