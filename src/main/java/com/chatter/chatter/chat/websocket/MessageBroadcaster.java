package com.chatter.chatter.chat.websocket;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.chatter.chatter.chat.dto.MessageDTO;
import com.chatter.chatter.chat.event.MessageSent;
import com.chatter.chatter.chat.repository.MessageRepository;

/**
 * AFTER_COMMIT is the in-process stand-in for the transactional outbox: the
 * push only happens once the row is durable, so "delivered but never saved"
 * cannot occur. In Phase 5 this becomes a {@code @KafkaListener} with the
 * same body, and the send path does not change.
 */
@Component
public class MessageBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;
    private final MessageRepository messageRepository;

    public MessageBroadcaster(SimpMessagingTemplate messagingTemplate, MessageRepository messageRepository) {
        this.messagingTemplate = messagingTemplate;
        this.messageRepository = messageRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageSent(MessageSent event) {
        messageRepository.findById(event.messageId()).ifPresent(message ->
                messagingTemplate.convertAndSend("/topic/chats/" + event.chatId(), MessageDTO.from(message)));
    }
}
