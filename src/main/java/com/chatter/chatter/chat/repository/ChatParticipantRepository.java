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

    /**
     * Whether a user is currently in a chat.
     *
     * <p>The authorization primitive of the whole chat module: called from
     * {@code ChatService.requireActiveMember} on every message operation, and
     * directly by {@code StompAuthChannelInterceptor} on each STOMP SUBSCRIBE.
     *
     * <p>Write-boundary validation replacing the deliberately dropped
     * {@code messages -> users} foreign key: the modules share a database but
     * not a table graph, so membership is checked rather than enforced by
     * constraint.
     *
     * <p>{@code leftAt is null} excludes former members — they keep their old
     * history but see nothing new.
     */
    @Query("""
            select count(p) > 0 from ChatParticipant p
            where p.chatId = :chatId and p.userId = :userId and p.leftAt is null
            """)
    boolean isActiveMember(@Param("chatId") UUID chatId, @Param("userId") UUID userId);

    /**
     * The chat's current members.
     *
     * <p>Used by {@code MessageBroadcaster} to decide who to notify, and by
     * {@code ChatService.toDto} to find the other party in a 1:1 chat.
     */
    List<ChatParticipant> findByChatIdAndLeftAtIsNull(UUID chatId);

    /**
     * One membership row, including that of a member who has left.
     *
     * <p>Used to read and advance the read cursor — {@code unreadCount} and
     * {@code markReadThrough} in {@code ChatService}.
     */
    Optional<ChatParticipant> findByChatIdAndUserId(UUID chatId, UUID userId);
}
