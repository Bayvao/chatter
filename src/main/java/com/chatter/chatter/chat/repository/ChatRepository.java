package com.chatter.chatter.chat.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.chatter.chatter.chat.model.Chat;

@Repository
public interface ChatRepository extends JpaRepository<Chat, UUID> {

    /**
     * The direct chat between two users, found through participant rows
     * rather than user1_id/user2_id columns.
     */
    @Query("""
            select c from Chat c
            where c.group = false
              and exists (select 1 from ChatParticipant p
                          where p.chatId = c.id and p.userId = :userA and p.leftAt is null)
              and exists (select 1 from ChatParticipant p
                          where p.chatId = c.id and p.userId = :userB and p.leftAt is null)
            """)
    Optional<Chat> findDirectChatBetween(@Param("userA") UUID userA, @Param("userB") UUID userB);

    @Query("""
            select c from Chat c
            where exists (select 1 from ChatParticipant p
                          where p.chatId = c.id and p.userId = :userId and p.leftAt is null)
            order by c.lastMessageAt desc nulls last, c.createdAt desc
            """)
    List<Chat> findChatsForUser(@Param("userId") UUID userId);
}
