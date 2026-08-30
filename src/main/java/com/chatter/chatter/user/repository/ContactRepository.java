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

    List<Contact> findByUserIdOrderByAddedAtDesc(UUID userId);

    List<Contact> findByUserIdAndBlockedFalseOrderByAddedAtDesc(UUID userId);

    Optional<Contact> findByUserIdAndContactUserId(UUID userId, UUID contactUserId);

    boolean existsByUserIdAndContactUserId(UUID userId, UUID contactUserId);

    void deleteByUserIdAndContactUserId(UUID userId, UUID contactUserId);

    /** Ids this user has blocked, used to filter them out of search results. */
    @Query("select c.contactUserId from Contact c where c.userId = :userId and c.blocked = true")
    List<UUID> findBlockedContactIds(@Param("userId") UUID userId);
}
