package com.chatter.chatter.chat.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatter.chatter.chat.dto.ChatDTO;
import com.chatter.chatter.chat.exception.ChatNotFoundException;
import com.chatter.chatter.chat.exception.NotConnectedException;
import com.chatter.chatter.chat.exception.NotAParticipantException;
import com.chatter.chatter.chat.model.Chat;
import com.chatter.chatter.chat.model.ChatCounter;
import com.chatter.chatter.chat.model.ChatParticipant;
import com.chatter.chatter.chat.model.Message;
import com.chatter.chatter.chat.model.ParticipantRole;
import com.chatter.chatter.chat.port.RelationshipDirectory;
import com.chatter.chatter.chat.port.SenderDirectory;
import com.chatter.chatter.chat.repository.ChatCounterRepository;
import com.chatter.chatter.chat.repository.ChatParticipantRepository;
import com.chatter.chatter.chat.repository.ChatRepository;
import com.chatter.chatter.chat.repository.MessageRepository;

/**
 * Conversations and membership: creating a chat, checking who is allowed in it,
 * and assembling the chat-list view.
 *
 * <p>Reaches the user module only through {@link SenderDirectory}, never through
 * its repositories — the two modules share a database but not a table graph, so
 * a name lookup crosses the boundary through the port.
 */
@Service
@Transactional(readOnly = true)
public class ChatService {

    private final ChatRepository chatRepository;
    private final ChatParticipantRepository participantRepository;
    private final ChatCounterRepository counterRepository;
    private final MessageRepository messageRepository;
    private final SenderDirectory senderDirectory;
    private final RelationshipDirectory relationshipDirectory;

    public ChatService(ChatRepository chatRepository, ChatParticipantRepository participantRepository,
                        ChatCounterRepository counterRepository, MessageRepository messageRepository,
                        SenderDirectory senderDirectory, RelationshipDirectory relationshipDirectory) {
        this.chatRepository = chatRepository;
        this.participantRepository = participantRepository;
        this.counterRepository = counterRepository;
        this.messageRepository = messageRepository;
        this.senderDirectory = senderDirectory;
        this.relationshipDirectory = relationshipDirectory;
    }

    /**
     * Opens the 1:1 chat between two people, creating it on first contact.
     *
     * <p>Used by {@code ChatController.openDirectChat}, which the frontend calls
     * when you pick someone out of search. Deliberately idempotent: clicking a
     * person twice reuses the existing conversation instead of forking a second
     * one, so history never splits.
     *
     * <p>Requires the two to be contacts. Before that check existed, a chat
     * could be opened with any user id at all, so a friend request appeared to
     * be accepted the moment it was sent.
     *
     * <p>Looks for an existing chat <em>including</em> ones the caller has left,
     * and rejoins it. Searching only active pairs would find nothing after a
     * leave and create a second chat, splitting the conversation while the other
     * party still sits in the first.
     *
     * @throws IllegalArgumentException if both ids are the same
     * @throws NotConnectedException if the two are not contacts
     * @throws com.chatter.chatter.user.exception.UserNotFoundException if the
     *         other user does not exist
     */
    @Transactional
    public Chat getOrCreateDirectChat(UUID currentUserId, UUID otherUserId) {
        if (currentUserId.equals(otherUserId)) {
            throw new IllegalArgumentException("Cannot open a chat with yourself");
        }
        // Fails fast if the other user does not exist. Crossing the boundary
        // through the port, not by reaching into the user repository.
        senderDirectory.lookup(otherUserId);

        if (!relationshipDirectory.areConnected(currentUserId, otherUserId)) {
            throw new NotConnectedException(otherUserId);
        }

        return chatRepository.findDirectChatBetweenIncludingLeft(currentUserId, otherUserId)
                .map(chat -> rejoin(chat, currentUserId))
                .orElseGet(() -> createDirectChat(currentUserId, otherUserId));
    }

    /**
     * Clears {@code left_at} for anyone reopening a chat they had left.
     *
     * <p>Called from {@link #getOrCreateDirectChat}. A no-op for the ordinary
     * case where the caller never left, which is why it sits on the found-chat
     * path rather than behind a branch.
     */
    private Chat rejoin(Chat chat, UUID userId) {
        participantRepository.findByChatIdAndUserId(chat.getId(), userId)
                .ifPresent(ChatParticipant::rejoin);
        return chat;
    }

    /**
     * The other participant in a direct chat, if there is exactly one.
     *
     * <p>Used by {@code MessageService.send} to find who a 1:1 message is
     * actually going to, so the block between them can be checked. Empty for a
     * group, which has no single counterpart and is deliberately not gated on
     * any relationship.
     */
    public Optional<UUID> otherParticipant(UUID chatId, UUID userId) {
        List<UUID> others = participantRepository.findByChatIdAndLeftAtIsNull(chatId).stream()
                .map(ChatParticipant::getUserId)
                .filter(id -> !id.equals(userId))
                .toList();

        return others.size() == 1 ? Optional.of(others.get(0)) : Optional.empty();
    }

    /**
     * Removes the caller from a chat, hiding it without deleting anything.
     *
     * <p>Used by {@code ChatController.leaveChat}. Stamping {@code left_at} is
     * the whole behaviour, because every read already filters on it: the chat
     * leaves their list, {@link #requireActiveMember} starts failing so REST
     * send and history return 403, STOMP SUBSCRIBE is refused, and
     * {@code MessageBroadcaster} stops delivering to them.
     *
     * <p>Messages and {@code last_read_seq} survive, so reopening the chat
     * rejoins it with history intact rather than starting afresh.
     *
     * @throws NotAParticipantException if they are not an active member
     */
    @Transactional
    public void leaveChat(UUID chatId, UUID userId) {
        requireActiveMember(chatId, userId);
        participantRepository.findByChatIdAndUserId(chatId, userId).ifPresent(ChatParticipant::leave);
    }

    /**
     * Writes the three rows a new 1:1 conversation needs: the chat, both
     * participants, and its sequence counter.
     *
     * <p>Called only from {@link #getOrCreateDirectChat}, inside its
     * transaction, so a half-built chat can never be observed. The counter is
     * created here rather than lazily on first send, which keeps
     * {@link MessageService#send} to a plain increment of a row it knows exists.
     */
    private Chat createDirectChat(UUID currentUserId, UUID otherUserId) {
        Chat chat = chatRepository.save(Chat.directChat(currentUserId));

        participantRepository.save(new ChatParticipant(chat.getId(), currentUserId, ParticipantRole.MEMBER));
        participantRepository.save(new ChatParticipant(chat.getId(), otherUserId, ParticipantRole.MEMBER));
        // Created with the chat so sending only ever increments an existing row.
        counterRepository.save(new ChatCounter(chat.getId()));

        return chat;
    }

    /**
     * Loads a chat by id, or fails.
     *
     * <p>Used wherever a caller needs the chat itself rather than just
     * permission to use it. Note this says nothing about membership — pair it
     * with {@link #requireActiveMember} before showing anything to a user.
     *
     * @throws ChatNotFoundException if no such chat exists
     */
    public Chat getChatOrThrow(UUID chatId) {
        return chatRepository.findById(chatId).orElseThrow(() -> new ChatNotFoundException(chatId));
    }

    /**
     * The authorization gate for everything chat-shaped.
     *
     * <p>Called at the top of every {@link MessageService} operation, by
     * {@code ChatController} before returning history, and by
     * {@code StompAuthChannelInterceptor} on each STOMP SUBSCRIBE — that last
     * one is what stops a user subscribing to a conversation they are not in.
     *
     * <p>"Active" excludes members who have left: their {@code left_at} is set,
     * so they keep their old history but stop receiving anything new.
     *
     * @throws NotAParticipantException if the user is not an active member
     */
    public void requireActiveMember(UUID chatId, UUID userId) {
        if (!participantRepository.isActiveMember(chatId, userId)) {
            throw new NotAParticipantException(chatId, userId);
        }
    }

    /**
     * Every conversation this user is in, ready for the sidebar.
     *
     * <p>Used by {@code ChatController.listChats} on load and again on each
     * WebSocket reconnect. Each entry costs a few queries via {@link #toDto};
     * acceptable while a user has tens of chats, and the first thing to batch
     * if that stops being true.
     */
    public List<ChatDTO> listChatsFor(UUID userId) {
        List<ChatDTO> results = new ArrayList<>();

        for (Chat chat : chatRepository.findChatsForUser(userId)) {
            results.add(toDto(chat, userId));
        }
        return results;
    }

    /**
     * Renders one chat from the perspective of the person looking at it.
     *
     * <p>Used by {@link #listChatsFor} and by {@code ChatController} after
     * opening a chat. Perspective matters: a 1:1 conversation has no title of
     * its own, so the "other" participant — everyone who is not the caller — is
     * resolved to supply the name shown, and the unread count is the caller's
     * own.
     *
     * <p>A soft-deleted newest message yields a null preview rather than the
     * tombstone, so the sidebar does not advertise deleted text.
     */
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

    /**
     * How many messages this user has not read in a chat.
     *
     * <p>Used when building every {@link ChatDTO}, so it runs once per chat in
     * the sidebar and needs to stay cheap.
     *
     * <p>Arithmetic on {@code seq} rather than a {@code COUNT(*)} over the
     * messages table: the chat's last sequence minus the participant's read
     * cursor is the answer, from two indexed single-row reads that do not grow
     * with the size of the conversation. Clamped at zero, since a read cursor
     * ahead of the counter would otherwise show a negative badge.
     */
    public long unreadCount(UUID chatId, UUID userId) {
        long lastSeq = counterRepository.findById(chatId).map(ChatCounter::getLastSeq).orElse(0L);
        long lastReadSeq = participantRepository.findByChatIdAndUserId(chatId, userId)
                .map(ChatParticipant::getLastReadSeq)
                .orElse(0L);
        return Math.max(0, lastSeq - lastReadSeq);
    }

    /**
     * Advances a participant's read cursor to a sequence number.
     *
     * <p>Used by {@link MessageService#markRead} when a user reads a message,
     * and by {@link MessageService#send} for the sender's own message — you have
     * by definition read what you just sent, and without that your own messages
     * inflate your unread badge.
     *
     * <p>The cursor only ever moves forward; see
     * {@link ChatParticipant#markReadThrough}. Reading an older message must not
     * resurrect everything after it as unread.
     *
     * @throws NotAParticipantException if the user is not an active member
     */
    @Transactional
    public void markReadThrough(UUID chatId, UUID userId, long seq) {
        requireActiveMember(chatId, userId);
        participantRepository.findByChatIdAndUserId(chatId, userId)
                .ifPresent(participant -> participant.markReadThrough(seq));
    }
}
