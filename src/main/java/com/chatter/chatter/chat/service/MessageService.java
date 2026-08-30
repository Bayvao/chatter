package com.chatter.chatter.chat.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatter.chatter.chat.event.MessageSent;
import com.chatter.chatter.chat.exception.ChatNotFoundException;
import com.chatter.chatter.chat.model.Chat;
import com.chatter.chatter.chat.model.ChatCounter;
import com.chatter.chatter.chat.model.Message;
import com.chatter.chatter.chat.model.SenderSnapshot;
import com.chatter.chatter.chat.port.SenderDirectory;
import com.chatter.chatter.chat.repository.ChatCounterRepository;
import com.chatter.chatter.chat.repository.ChatRepository;
import com.chatter.chatter.chat.repository.MessageRepository;

@Service
@Transactional(readOnly = true)
public class MessageService {

    private final MessageRepository messageRepository;
    private final ChatRepository chatRepository;
    private final ChatCounterRepository counterRepository;
    private final ChatService chatService;
    private final SenderDirectory senderDirectory;
    private final ApplicationEventPublisher eventPublisher;

    public MessageService(MessageRepository messageRepository, ChatRepository chatRepository,
                           ChatCounterRepository counterRepository, ChatService chatService,
                           SenderDirectory senderDirectory, ApplicationEventPublisher eventPublisher) {
        this.messageRepository = messageRepository;
        this.chatRepository = chatRepository;
        this.counterRepository = counterRepository;
        this.chatService = chatService;
        this.senderDirectory = senderDirectory;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Message send(UUID chatId, UUID senderId, String content, UUID clientMsgId) {
        chatService.requireActiveMember(chatId, senderId);

        // Idempotency: a retry carrying the same client id returns the row it
        // already created rather than a duplicate.
        if (clientMsgId != null) {
            var existing = messageRepository.findByChatIdAndClientMsgId(chatId, clientMsgId);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        ChatCounter counter = counterRepository.lockByChatId(chatId)
                .orElseThrow(() -> new ChatNotFoundException(chatId));

        SenderDirectory.Sender sender = senderDirectory.lookup(senderId);
        Message message = Message.text(chatId, senderId, counter.nextSeq(), clientMsgId, content,
                new SenderSnapshot(sender.displayName(), sender.avatarUrl(), sender.version()));
        messageRepository.save(message);

        Chat chat = chatRepository.findById(chatId).orElseThrow(() -> new ChatNotFoundException(chatId));
        chat.setLastMessageAt(message.getCreatedAt());

        // You have by definition read what you just sent; without this your
        // own messages count towards your unread badge.
        chatService.markReadThrough(chatId, senderId, message.getSeq());

        // Delivered to subscribers only after this transaction commits.
        eventPublisher.publishEvent(MessageSent.from(message));

        return message;
    }

    public List<Message> history(UUID chatId, UUID requesterId, Long beforeSeq, int limit) {
        chatService.requireActiveMember(chatId, requesterId);
        return messageRepository.findPage(chatId, beforeSeq, PageRequest.of(0, limit));
    }

    /** Offline sync: everything the client has not seen, oldest first. */
    public List<Message> since(UUID chatId, UUID requesterId, long afterSeq) {
        chatService.requireActiveMember(chatId, requesterId);
        return messageRepository.findSince(chatId, afterSeq);
    }

    @Transactional
    public void markRead(UUID chatId, UUID requesterId, UUID messageId) {
        chatService.requireActiveMember(chatId, requesterId);

        messageRepository.findById(messageId)
                .filter(message -> message.getChatId().equals(chatId))
                .ifPresent(message -> {
                    message.markRead();
                    chatService.markReadThrough(chatId, requesterId, message.getSeq());
                });
    }

    /** Soft delete: the row and its seq stay so clients never see a gap. */
    @Transactional
    public void softDelete(UUID chatId, UUID requesterId, UUID messageId) {
        chatService.requireActiveMember(chatId, requesterId);

        messageRepository.findById(messageId)
                .filter(message -> message.getChatId().equals(chatId))
                .filter(message -> message.getSenderId().equals(requesterId))
                .ifPresent(message -> {
                    message.setDeletedAt(Instant.now());
                    message.setContent(null);
                });
    }
}
