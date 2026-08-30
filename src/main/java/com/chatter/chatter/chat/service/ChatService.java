package com.chatter.chatter.chat.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatter.chatter.chat.dto.ChatDTO;
import com.chatter.chatter.chat.exception.ChatNotFoundException;
import com.chatter.chatter.chat.exception.NotAParticipantException;
import com.chatter.chatter.chat.model.Chat;
import com.chatter.chatter.chat.model.ChatCounter;
import com.chatter.chatter.chat.model.ChatParticipant;
import com.chatter.chatter.chat.model.Message;
import com.chatter.chatter.chat.model.ParticipantRole;
import com.chatter.chatter.chat.port.SenderDirectory;
import com.chatter.chatter.chat.repository.ChatCounterRepository;
import com.chatter.chatter.chat.repository.ChatParticipantRepository;
import com.chatter.chatter.chat.repository.ChatRepository;
import com.chatter.chatter.chat.repository.MessageRepository;

@Service
@Transactional(readOnly = true)
public class ChatService {

    private final ChatRepository chatRepository;
    private final ChatParticipantRepository participantRepository;
    private final ChatCounterRepository counterRepository;
    private final MessageRepository messageRepository;
    private final SenderDirectory senderDirectory;

    public ChatService(ChatRepository chatRepository, ChatParticipantRepository participantRepository,
                        ChatCounterRepository counterRepository, MessageRepository messageRepository,
                        SenderDirectory senderDirectory) {
        this.chatRepository = chatRepository;
        this.participantRepository = participantRepository;
        this.counterRepository = counterRepository;
        this.messageRepository = messageRepository;
        this.senderDirectory = senderDirectory;
    }

    @Transactional
    public Chat getOrCreateDirectChat(UUID currentUserId, UUID otherUserId) {
        if (currentUserId.equals(otherUserId)) {
            throw new IllegalArgumentException("Cannot open a chat with yourself");
        }
        // Fails fast if the other user does not exist. Crossing the boundary
        // through the port, not by reaching into the user repository.
        senderDirectory.lookup(otherUserId);

        return chatRepository.findDirectChatBetween(currentUserId, otherUserId)
                .orElseGet(() -> createDirectChat(currentUserId, otherUserId));
    }

    private Chat createDirectChat(UUID currentUserId, UUID otherUserId) {
        Chat chat = chatRepository.save(Chat.directChat(currentUserId));

        participantRepository.save(new ChatParticipant(chat.getId(), currentUserId, ParticipantRole.MEMBER));
        participantRepository.save(new ChatParticipant(chat.getId(), otherUserId, ParticipantRole.MEMBER));
        // Created with the chat so sending only ever increments an existing row.
        counterRepository.save(new ChatCounter(chat.getId()));

        return chat;
    }

    public Chat getChatOrThrow(UUID chatId) {
        return chatRepository.findById(chatId).orElseThrow(() -> new ChatNotFoundException(chatId));
    }

    public void requireActiveMember(UUID chatId, UUID userId) {
        if (!participantRepository.isActiveMember(chatId, userId)) {
            throw new NotAParticipantException(chatId, userId);
        }
    }

    public List<ChatDTO> listChatsFor(UUID userId) {
        List<ChatDTO> results = new ArrayList<>();

        for (Chat chat : chatRepository.findChatsForUser(userId)) {
            results.add(toDto(chat, userId));
        }
        return results;
    }

    public ChatDTO toDto(Chat chat, UUID currentUserId) {
        UUID otherUserId = null;
        String otherUserName = null;

        if (!chat.isGroup()) {
            otherUserId = participantRepository.findByChatIdAndLeftAtIsNull(chat.getId()).stream()
                    .map(ChatParticipant::getUserId)
                    .filter(id -> !id.equals(currentUserId))
                    .findFirst()
                    .orElse(null);

            if (otherUserId != null) {
                otherUserName = senderDirectory.lookup(otherUserId).displayName();
            }
        }

        List<Message> latest = messageRepository.findPage(chat.getId(), null, PageRequest.of(0, 1));
        String lastMessage = latest.isEmpty() || latest.get(0).getDeletedAt() != null
                ? null
                : latest.get(0).getContent();

        return new ChatDTO(chat.getId(), chat.isGroup(), chat.getTitle(), chat.getAvatarUrl(),
                otherUserId, otherUserName, chat.getLastMessageAt(), lastMessage,
                unreadCount(chat.getId(), currentUserId));
    }

    /** Arithmetic on seq rather than a COUNT(*) over the messages table. */
    public long unreadCount(UUID chatId, UUID userId) {
        long lastSeq = counterRepository.findById(chatId).map(ChatCounter::getLastSeq).orElse(0L);
        long lastReadSeq = participantRepository.findByChatIdAndUserId(chatId, userId)
                .map(ChatParticipant::getLastReadSeq)
                .orElse(0L);
        return Math.max(0, lastSeq - lastReadSeq);
    }

    @Transactional
    public void markReadThrough(UUID chatId, UUID userId, long seq) {
        requireActiveMember(chatId, userId);
        participantRepository.findByChatIdAndUserId(chatId, userId)
                .ifPresent(participant -> participant.markReadThrough(seq));
    }
}
