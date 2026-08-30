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
     * Row lock serialises concurrent sends to the same chat, so two writers
     * cannot hand out the same seq.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ChatCounter c where c.chatId = :chatId")
    Optional<ChatCounter> lockByChatId(@Param("chatId") UUID chatId);
}
