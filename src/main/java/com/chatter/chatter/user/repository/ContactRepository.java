package com.chatter.chatter.user.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.chatter.chatter.user.model.Contact;

@Repository
public interface ContactRepository extends JpaRepository<Contact, Contact.Key> {

    /**
     * Every contact a user has saved, blocked ones included, newest first.
     *
     * <p>Unused today — {@link #findByUserIdAndBlockedFalseOrderByAddedAtDesc}
     * is what the Contacts tab calls. Kept for a "manage blocked users" screen,
     * which needs the blocked rows this one does not filter out.
     */
    List<Contact> findByUserIdOrderByAddedAtDesc(UUID userId);

    /**
     * A user's visible contacts, newest first.
     *
     * <p>Backs the Contacts tab via {@code ContactService.listContacts}. Blocked
     * rows are excluded here rather than deleted, so unblocking restores them.
     */
    List<Contact> findByUserIdAndBlockedFalseOrderByAddedAtDesc(UUID userId);

    /**
     * One contact row, for mutating it.
     *
     * <p>Used by {@code setBlocked} and {@code setFavorite}, which need the
     * managed entity rather than just its existence.
     */
    Optional<Contact> findByUserIdAndContactUserId(UUID userId, UUID contactUserId);

    /**
     * Whether one user has already saved another.
     *
     * <p>Guards {@code addContact} against duplicates and {@code removeContact}
     * against deleting nothing. Cheaper than loading the row when only the
     * answer is needed.
     */
    boolean existsByUserIdAndContactUserId(UUID userId, UUID contactUserId);

    /**
     * Removes a contact outright.
     *
     * <p>Used by {@code ContactService.removeContact}, after its existence check
     * — a delete of nothing would otherwise pass silently and report success.
     */
    void deleteByUserIdAndContactUserId(UUID userId, UUID contactUserId);

    /**
     * The ids this user has blocked.
     *
     * <p>Used by {@code UserController.search} to filter blocked people out of
     * results. Projects to bare ids rather than entities, since the caller only
     * builds a set to test membership against.
     */
    @Query("select c.contactUserId from Contact c where c.userId = :userId and c.blocked = true")
    List<UUID> findBlockedContactIds(@Param("userId") UUID userId);
}
