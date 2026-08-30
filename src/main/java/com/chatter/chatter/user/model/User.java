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
 * The user module keeps its foreign keys — nothing here crosses a boundary.
 *
 * <p>Deliberately has no {@code @OneToMany} to chats or messages: those live
 * in the chat module and reference this table by plain uuid. An association
 * here would recreate exactly the coupling the architecture removes.
 */
@Entity
@Table(name = "users", schema = "app_user")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(unique = true, length = 320)
    private String email;

    @Column(name = "first_name", length = 80)
    private String firstName;

    @Column(name = "last_name", length = 80)
    private String lastName;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    @Column(name = "status_text", length = 200)
    private String statusText;

    /**
     * Bumped on every profile change and published in the event, so the chat
     * module's denormalised sender snapshot can discard out-of-order updates.
     * Not a JPA optimistic-lock version — intentionally not {@code @Version}.
     */
    @Column(nullable = false)
    private long version;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_seen")
    private Instant lastSeen;

    @Column(name = "erased_at")
    private Instant erasedAt;

    public static User create(String username, String email, String encodedPassword) {
        User user = new User();
        user.id = Ids.newId();
        user.username = username;
        user.email = email;
        user.password = encodedPassword;
        return user;
    }

    public String getDisplayName() {
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        }
        return username;
    }
}
