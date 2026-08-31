package com.chatter.chatter.chat.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.chatter.chatter.chat.model.Message;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {

    /**
     * A newest-first page of a chat, optionally anchored before a known seq.
     *
     * <p>Used by {@code MessageService.history} for the initial view and for
     * paging back through the scrollback.
     *
     * <p>Keyset paging on {@code seq}, not an offset: messages arriving while
     * the user scrolls would shift an offset window and make a message appear
     * twice or be skipped.
     *
     * <p>Soft-deleted rows keep their seq so clients never see a gap, but carry
     * no content.
     */
    @Query("""
            select m from Message m
            where m.chatId = :chatId
              and (:beforeSeq is null or m.seq < :beforeSeq)
            order by m.seq desc
            """)
    List<Message> findPage(@Param("chatId") UUID chatId, @Param("beforeSeq") Long beforeSeq, Pageable pageable);

    /**
     * Everything after a cursor, oldest first.
     *
     * <p>Used by {@code MessageService.since}, which backs both
     * {@code SyncController} and the REST catch-up endpoint. Sync as a range
     * request — "I have through seq N, send the rest" — is what makes a separate
     * offline queue unnecessary.
     *
     * <p>Ascending, the opposite of {@link #findPage}, because these are
     * replayed in the order they were sent.
     */
    @Query("""
            select m from Message m
            where m.chatId = :chatId and m.seq > :afterSeq
            order by m.seq asc
            """)
    List<Message> findSince(@Param("chatId") UUID chatId, @Param("afterSeq") long afterSeq);

    /**
     * Finds a message by the id its sender's client generated.
     *
     * <p>The idempotency check in {@code MessageService.send}: a client
     * retrying after a dropped connection gets back the row it already created
     * instead of a duplicate. Backed by a unique index on
     * {@code (chat_id, client_msg_id)}, which is what makes the guarantee hold
     * under concurrency rather than merely usually.
     */
    Optional<Message> findByChatIdAndClientMsgId(UUID chatId, UUID clientMsgId);
}
