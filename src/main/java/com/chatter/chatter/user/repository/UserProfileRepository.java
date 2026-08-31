package com.chatter.chatter.user.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.chatter.chatter.user.model.UserProfile;

/**
 * The extended profile row, keyed by the user's own id.
 *
 * <p>No query methods of its own: every access is by primary key, so the
 * inherited {@code findById} and {@code save} are the whole interface.
 * {@code ProfileService} is the only caller.
 */
@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {
}
