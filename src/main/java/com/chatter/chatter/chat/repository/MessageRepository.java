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
     * Newest-first page, optionally anchored before a known seq. Soft-deleted
     * rows keep their seq so clients never see a gap, but carry no content.
     */
    @Query("""
            select m from Message m
            where m.chatId = :chatId
              and (:beforeSeq is null or m.seq < :beforeSeq)
            order by m.seq desc
            """)
    List<Message> findPage(@Param("chatId") UUID chatId, @Param("beforeSeq") Long beforeSeq, Pageable pageable);

    /** Offline sync as a range request: "I have through seq N, send the rest." */
    @Query("""
            select m from Message m
            where m.chatId = :chatId and m.seq > :afterSeq
            order by m.seq asc
            """)
    List<Message> findSince(@Param("chatId") UUID chatId, @Param("afterSeq") long afterSeq);

    Optional<Message> findByChatIdAndClientMsgId(UUID chatId, UUID clientMsgId);
}
