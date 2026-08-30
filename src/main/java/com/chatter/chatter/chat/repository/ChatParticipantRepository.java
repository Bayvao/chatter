package com.chatter.chatter.chat.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.chatter.chatter.chat.model.ChatParticipant;

@Repository
public interface ChatParticipantRepository extends JpaRepository<ChatParticipant, ChatParticipant.Key> {

    /** Write-boundary validation replacing the dropped messages -> users FK. */
    @Query("""
            select count(p) > 0 from ChatParticipant p
            where p.chatId = :chatId and p.userId = :userId and p.leftAt is null
            """)
    boolean isActiveMember(@Param("chatId") UUID chatId, @Param("userId") UUID userId);

    List<ChatParticipant> findByChatIdAndLeftAtIsNull(UUID chatId);

    Optional<ChatParticipant> findByChatIdAndUserId(UUID chatId, UUID userId);
}
