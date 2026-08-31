package com.chatter.chatter.chat.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import com.chatter.chatter.chat.model.ChatCounter;

@Repository
public interface ChatCounterRepository extends JpaRepository<ChatCounter, UUID> {

    /**
     * Loads a chat's counter under a row lock, for allocating the next seq.
     *
     * <p>Called once per send by {@code MessageService.send}, and the only way
     * this row should ever be read for writing.
     *
     * <p>The lock serialises concurrent sends to the same chat so two writers
     * cannot hand out the same {@code seq}. It is held for the rest of the
     * transaction, which is why the send path keeps its work after this point
     * short — sends to the same conversation queue behind it.
     *
     * <p>A lock rather than {@code ON CONFLICT ... RETURNING}, which H2 does not
     * support and the test suite runs on H2.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ChatCounter c where c.chatId = :chatId")
    Optional<ChatCounter> lockByChatId(@Param("chatId") UUID chatId);
}
