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
     * The existing 1:1 chat between two users, if there is one.
     *
     * <p>Used by {@code ChatService.getOrCreateDirectChat}, and the reason
     * opening a chat twice reuses the conversation rather than forking history.
     *
     * <p>Found through participant rows rather than {@code user1_id}/
     * {@code user2_id} columns: those would need normalising by id order on
     * every read and could not extend to group chats, which share this table.
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

    /**
     * The 1:1 chat between two users, counting members who have left.
     *
     * <p>Used by {@code ChatService.getOrCreateDirectChat} in place of
     * {@link #findDirectChatBetween}, which requires both sides active and so
     * finds nothing once one has left — creating a second chat and splitting the
     * conversation. This finds the original so the caller can rejoin it.
     */
    @Query("""
            select c from Chat c
            where c.group = false
              and exists (select 1 from ChatParticipant p
                          where p.chatId = c.id and p.userId = :userA)
              and exists (select 1 from ChatParticipant p
                          where p.chatId = c.id and p.userId = :userB)
            """)
    Optional<Chat> findDirectChatBetweenIncludingLeft(@Param("userA") UUID userA, @Param("userB") UUID userB);

    /**
     * Every chat a user is currently in, most recently active first.
     *
     * <p>Backs the sidebar via {@code ChatService.listChatsFor}. Chats the user
     * has left drop out here, which is what makes leaving hide a conversation.
     *
     * <p>{@code nulls last} matters: a chat created but never used has no
     * {@code lastMessageAt}, and without it those would sort to the top on
     * Postgres, pushing live conversations down.
     */
    @Query("""
            select c from Chat c
            where exists (select 1 from ChatParticipant p
                          where p.chatId = c.id and p.userId = :userId and p.leftAt is null)
            order by c.lastMessageAt desc nulls last, c.createdAt desc
            """)
    List<Chat> findChatsForUser(@Param("userId") UUID userId);
}
