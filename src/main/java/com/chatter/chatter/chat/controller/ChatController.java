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

    @GetMapping
    public List<ChatDTO> myChats(@AuthenticationPrincipal AuthenticatedUser principal) {
        return chatService.listChatsFor(principal.id());
    }

    @PostMapping("/with/{userId}")
    public ResponseEntity<ChatDTO> openDirectChat(@AuthenticationPrincipal AuthenticatedUser principal,
                                                    @PathVariable UUID userId) {
        Chat chat = chatService.getOrCreateDirectChat(principal.id(), userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(chatService.toDto(chat, principal.id()));
    }

    @GetMapping("/{chatId}/messages")
    public List<MessageDTO> history(@AuthenticationPrincipal AuthenticatedUser principal,
                                     @PathVariable UUID chatId,
                                     @RequestParam(required = false) Long beforeSeq,
                                     @RequestParam(defaultValue = "50") int limit) {
        int bounded = Math.clamp(limit, 1, MAX_PAGE_SIZE);
        return messageService.history(chatId, principal.id(), beforeSeq, bounded).stream()
                .map(MessageDTO::from)
                .toList();
    }

    /** Reconnect path: "I have through seq N, send me the rest." */
    @GetMapping("/{chatId}/messages/since")
    public List<MessageDTO> since(@AuthenticationPrincipal AuthenticatedUser principal,
                                   @PathVariable UUID chatId,
                                   @RequestParam long afterSeq) {
        return messageService.since(chatId, principal.id(), afterSeq).stream()
                .map(MessageDTO::from)
                .toList();
    }

    /**
     * REST send, mainly for clients without a live socket. The WebSocket path
     * is the primary one; both funnel through the same service and broadcast.
     */
    @PostMapping("/messages")
    public ResponseEntity<MessageDTO> send(@AuthenticationPrincipal AuthenticatedUser principal,
                                            @Valid @RequestBody SendMessageRequest request) {
        Message message = messageService.send(request.chatId(), principal.id(), request.content(),
                request.clientMsgId());
        return ResponseEntity.status(HttpStatus.CREATED).body(MessageDTO.from(message));
    }

    @PostMapping("/{chatId}/messages/{messageId}/read")
    public ResponseEntity<Void> markRead(@AuthenticationPrincipal AuthenticatedUser principal,
                                          @PathVariable UUID chatId, @PathVariable UUID messageId) {
        messageService.markRead(chatId, principal.id(), messageId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{chatId}/messages/{messageId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser principal,
                                        @PathVariable UUID chatId, @PathVariable UUID messageId) {
        messageService.softDelete(chatId, principal.id(), messageId);
        return ResponseEntity.noContent().build();
    }
}
