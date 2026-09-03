package com.chatter.chatter.chat.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatter.chatter.chat.event.MessageSent;
import com.chatter.chatter.chat.exception.BlockedException;
import com.chatter.chatter.chat.exception.ChatNotFoundException;
import com.chatter.chatter.chat.model.Chat;
import com.chatter.chatter.chat.model.ChatCounter;
import com.chatter.chatter.chat.model.Message;
import com.chatter.chatter.chat.model.SenderSnapshot;
import com.chatter.chatter.chat.port.RelationshipDirectory;
import com.chatter.chatter.chat.port.SenderDirectory;
import com.chatter.chatter.chat.repository.ChatCounterRepository;
import com.chatter.chatter.chat.repository.ChatRepository;
import com.chatter.chatter.chat.repository.MessageRepository;

/**
 * Sending, reading and retracting messages — the write path of the chat module.
 *
 * <p>Every method starts by checking membership, so authorization is enforced
 * here rather than trusted from the controller: the same methods are reached
 * over REST and over STOMP, and only one of those goes through Spring Security's
 * filter chain.
 */
@Service
@Transactional(readOnly = true)
public class MessageService {

    private final MessageRepository messageRepository;
    private final ChatRepository chatRepository;
    private final ChatCounterRepository counterRepository;
    private final ChatService chatService;
    private final SenderDirectory senderDirectory;
    private final RelationshipDirectory relationshipDirectory;
    private final ApplicationEventPublisher eventPublisher;

    public MessageService(MessageRepository messageRepository, ChatRepository chatRepository,
                           ChatCounterRepository counterRepository, ChatService chatService,
                           SenderDirectory senderDirectory, RelationshipDirectory relationshipDirectory,
                           ApplicationEventPublisher eventPublisher) {
        this.messageRepository = messageRepository;
        this.chatRepository = chatRepository;
        this.counterRepository = counterRepository;
        this.chatService = chatService;
        this.senderDirectory = senderDirectory;
        this.relationshipDirectory = relationshipDirectory;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Persists a message and announces it.
     *
     * <p>The single send path: used by {@code ChatWebSocketController} for the
     * live socket and by {@code ChatController.sendMessage} for clients without
     * one. Four things happen in one transaction — the sequence number is
     * allocated, the row is written, the chat's {@code last_message_at} moves,
     * and the sender's own read cursor advances past it.
     *
     * <p>{@code clientMsgId} makes retries safe: a client that resends after a
     * dropped connection gets back the row it already created rather than a
     * duplicate. It is optional, and a null one simply skips that check.
     *
     * <p>The sender's name and avatar are copied onto the message as a snapshot,
     * so history renders without a join into the user module and stays correct
     * when a name later changes.
     *
     * <p>{@link MessageSent} is published, but delivery to subscribers happens
     * only after this transaction commits — see {@code MessageBroadcaster}.
     * Broadcasting before commit would let a rolled-back message reach a screen.
     *
     * @throws NotAParticipantException if the sender is not in the chat
     * @throws BlockedException if a block stands between the two parties
     * @throws ChatNotFoundException if the chat or its counter is missing
     */
    @Transactional
    public Message send(UUID chatId, UUID senderId, String content, UUID clientMsgId) {
        chatService.requireActiveMember(chatId, senderId);
        requireNotBlocked(chatId, senderId);

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

    /**
     * Refuses a message when a block stands between the two parties.
     *
     * <p>Membership alone was the only check here, which is why a conversation
     * carried on unchanged after someone was blocked: both sides were still
     * participants, so both kept sending.
     *
     * <p>Direct chats only. A group is not a relationship, and gating one on a
     * block between two of its members would be both wrong and, once groups
     * exist properly in Phase 4, expensive.
     *
     * <p>Costs one participant lookup plus one indexed block query per 1:1
     * send. The participant query is the same one {@code MessageBroadcaster}
     * already makes on this path.
     */
    private void requireNotBlocked(UUID chatId, UUID senderId) {
        chatService.otherParticipant(chatId, senderId)
                .filter(otherId -> relationshipDirectory.isBlockedEitherWay(senderId, otherId))
                .ifPresent(otherId -> {
                    throw new BlockedException(chatId);
                });
    }

    /**
     * A page of history, newest first.
     *
     * <p>Used by {@code ChatController.history} to fill the message pane, and
     * to page backwards as the user scrolls up: pass the lowest {@code seq} you
     * already hold as {@code beforeSeq}, or null for the newest page.
     *
     * <p>Keyset paging on {@code seq} rather than an offset, so inserting new
     * messages while the user scrolls cannot shift the page boundary and make a
     * message appear twice or not at all.
     *
     * @throws NotAParticipantException if the requester is not in the chat
     */
    public List<Message> history(UUID chatId, UUID requesterId, Long beforeSeq, int limit) {
        chatService.requireActiveMember(chatId, requesterId);
        return messageRepository.findPage(chatId, beforeSeq, PageRequest.of(0, limit));
    }

    /**
     * Everything after a cursor, oldest first — the catch-up query.
     *
     * <p>Used by {@code SyncController} when a client reconnects, and by the
     * REST {@code /messages/since} endpoint. This is why no offline queue
     * exists: every message is already durable with a total order, so "what did
     * I miss?" is just {@code seq > afterSeq}.
     *
     * <p>Oldest first, the opposite of {@link #history}, because these are
     * replayed into the view in the order they were originally sent.
     *
     * @throws NotAParticipantException if the requester is not in the chat
     */
    public List<Message> since(UUID chatId, UUID requesterId, long afterSeq) {
        chatService.requireActiveMember(chatId, requesterId);
        return messageRepository.findSince(chatId, afterSeq);
    }

    /**
     * Marks one message read, and advances the reader's cursor to it.
     *
     * <p>Used by {@code ChatController.markRead} when a message becomes visible
     * in the client. Both effects matter: the message's own status drives read
     * receipts for the sender, while the cursor is what clears the unread badge.
     *
     * <p>The chat id is checked against the message rather than trusted, so a
     * message id from a conversation the caller happens to belong to cannot be
     * used to touch a message in one they do not.
     *
     * @throws NotAParticipantException if the requester is not in the chat
     */
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

    /**
     * Retracts a message the caller sent.
     *
     * <p>Used by {@code ChatController.deleteMessage}. Only the sender may
     * delete, which is why the filter tests {@code senderId} as well as the
     * chat.
     *
     * <p>A soft delete: the row and its {@code seq} stay, and only the content
     * is cleared. Removing the row would put a hole in the sequence, and clients
     * treat a gap as "messages I have not fetched yet" — they would resync
     * forever chasing a message that no longer exists.
     *
     * @throws NotAParticipantException if the requester is not in the chat
     */
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
