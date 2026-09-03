package com.chatter.chatter.user.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.chatter.chatter.user.model.Contact;

@Repository
public interface ContactRepository extends JpaRepository<Contact, Contact.Key> {

    /**
     * A user's contacts, newest first.
     *
     * <p>Backs the Contacts tab via {@code ContactService.listContacts}, which
     * filters blocked people out afterwards — blocking now lives in its own
     * table, so it is no longer something this query can express.
     */
    List<Contact> findByUserIdOrderByAddedAtDesc(UUID userId);

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
     * <p>Guards {@code sendRequest} against duplicates and {@code removeContact}
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
}
