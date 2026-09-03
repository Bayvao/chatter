package com.chatter.chatter.chat.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.chatter.chatter.chat.dto.ChatDTO;
import com.chatter.chatter.chat.dto.MessageDTO;
import com.chatter.chatter.chat.dto.SendMessageRequest;
import com.chatter.chatter.chat.model.Chat;
import com.chatter.chatter.chat.model.Message;
import com.chatter.chatter.chat.service.ChatService;
import com.chatter.chatter.chat.service.MessageService;
import com.chatter.chatter.user.security.AuthenticatedUser;

/**
 * The REST face of the chat module: conversations, history and the non-socket
 * message operations.
 *
 * <p>Live sending goes over STOMP instead ({@code ChatWebSocketController});
 * what is here is everything a client needs on load, plus a fallback send.
 * Every route takes the caller from the security context, never from the path.
 */
@RestController
@RequestMapping("/api/chats")
public class ChatController {

    private static final int MAX_PAGE_SIZE = 200;

    private final ChatService chatService;
    private final MessageService messageService;

    public ChatController(ChatService chatService, MessageService messageService) {
        this.chatService = chatService;
        this.messageService = messageService;
    }

    /**
     * The caller's conversations, with previews and unread counts.
     *
     * <p>Used to populate the sidebar on load, and again after each WebSocket
     * reconnect so counts that moved while disconnected are corrected.
     */
    @GetMapping
    public List<ChatDTO> myChats(@AuthenticationPrincipal AuthenticatedUser principal) {
        return chatService.listChatsFor(principal.id());
    }

    /**
     * Opens the 1:1 chat with another user, creating it if this is the first
     * contact.
     *
     * <p>Used when picking someone out of search. Idempotent despite being a
     * POST — a second call returns the same conversation, so double-clicking
     * cannot fork history.
     *
     * @return 201 with the chat, whether it was just created or already existed
     */
    @PostMapping("/with/{userId}")
    public ResponseEntity<ChatDTO> openDirectChat(@AuthenticationPrincipal AuthenticatedUser principal,
                                                    @PathVariable UUID userId) {
        Chat chat = chatService.getOrCreateDirectChat(principal.id(), userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(chatService.toDto(chat, principal.id()));
    }

    /**
     * A page of history, newest first.
     *
     * <p>Used when opening a conversation, and again with {@code beforeSeq} set
     * to the oldest message held when scrolling back.
     *
     * <p>{@code limit} is clamped rather than trusted: an unbounded page size is
     * a denial-of-service handed to any authenticated client. {@code Math.min}
     * and {@code Math.max} rather than {@code Math.clamp} because CI compiles at
     * the JDK 17 API level, where the latter does not exist.
     */
    @GetMapping("/{chatId}/messages")
    public List<MessageDTO> history(@AuthenticationPrincipal AuthenticatedUser principal,
                                     @PathVariable UUID chatId,
                                     @RequestParam(required = false) Long beforeSeq,
                                     @RequestParam(defaultValue = "50") int limit) {
        int bounded = Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);
        return messageService.history(chatId, principal.id(), beforeSeq, bounded).stream()
                .map(MessageDTO::from)
                .toList();
    }

    /**
     * Everything after a cursor: "I have through seq N, send me the rest."
     *
     * <p>The REST equivalent of {@code SyncController}'s {@code /app/sync.start},
     * for a client that wants to catch up without a socket. The live frontend
     * uses the STOMP path instead, which handles all chats in one round trip.
     */
    @GetMapping("/{chatId}/messages/since")
    public List<MessageDTO> since(@AuthenticationPrincipal AuthenticatedUser principal,
                                   @PathVariable UUID chatId,
                                   @RequestParam long afterSeq) {
        return messageService.since(chatId, principal.id(), afterSeq).stream()
                .map(MessageDTO::from)
                .toList();
    }

    /**
     * Sends a message over plain HTTP.
     *
     * <p>Mainly for clients without a live socket, and what the Cucumber suite
     * uses to send without standing up a STOMP session. The WebSocket path is
     * the primary one; both funnel through the same service, so the message is
     * still broadcast to subscribers and still marked delivered.
     *
     * @return 201 with the stored message, including its assigned {@code seq}
     */
    @PostMapping("/messages")
    public ResponseEntity<MessageDTO> send(@AuthenticationPrincipal AuthenticatedUser principal,
                                            @Valid @RequestBody SendMessageRequest request) {
        Message message = messageService.send(request.chatId(), principal.id(), request.content(),
                request.clientMsgId());
        return ResponseEntity.status(HttpStatus.CREATED).body(MessageDTO.from(message));
    }

    /**
     * Marks a message read, clearing the unread badge up to that point.
     *
     * <p>Called by the client as messages become visible. Both effects matter:
     * the read receipt for the sender, and the caller's own read cursor.
     */
    @PostMapping("/{chatId}/messages/{messageId}/read")
    public ResponseEntity<Void> markRead(@AuthenticationPrincipal AuthenticatedUser principal,
                                          @PathVariable UUID chatId, @PathVariable UUID messageId) {
        messageService.markRead(chatId, principal.id(), messageId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Retracts a message the caller sent.
     *
     * <p>A soft delete: the row and its sequence number survive so clients never
     * see a gap, and the content is replaced with a tombstone the UI renders as
     * "This message was deleted". Silently does nothing if the caller is not the
     * sender.
     */
    @DeleteMapping("/{chatId}/messages/{messageId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser principal,
                                        @PathVariable UUID chatId, @PathVariable UUID messageId) {
        messageService.softDelete(chatId, principal.id(), messageId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Leaves a chat, removing it from the caller's list.
     *
     * <p>Used by the Leave action on a conversation. Hides rather than deletes:
     * the messages stay, and reopening the chat with the same person rejoins
     * this conversation with its history rather than starting a new one.
     *
     * <p>After this, sending to the chat returns 403, its history is refused,
     * a STOMP SUBSCRIBE to its topic is rejected, and messages the other party
     * sends are no longer delivered to the caller.
     */
    @DeleteMapping("/{chatId}")
    public ResponseEntity<Void> leaveChat(@AuthenticationPrincipal AuthenticatedUser principal,
                                           @PathVariable UUID chatId) {
        chatService.leaveChat(chatId, principal.id());
        return ResponseEntity.noContent().build();
    }
}
