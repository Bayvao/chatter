package com.chatter.chatter.chat.websocket;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import com.chatter.chatter.chat.dto.MessageDTO;
import com.chatter.chatter.chat.dto.SyncRequest;
import com.chatter.chatter.chat.service.MessageService;
import com.chatter.chatter.user.security.AuthenticatedUser;

/**
 * Catch-up on reconnect.
 *
 * <p>The client sends per-chat {@code seq} cursors, not a timestamp. Clocks
 * drift between client and server and two messages can share a millisecond,
 * so a timestamp cannot distinguish "nothing new" from "I missed something".
 * A seq cursor makes the gap explicit: the client holds 412, the counter says
 * 418, exactly six messages are outstanding.
 *
 * <p>There is no separate offline queue to drain — every message is already
 * durable in Postgres, so the cursor answers the question directly.
 */
@Controller
public class SyncController {

    private static final int BATCH_SIZE = 50;

    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;

    public SyncController(MessageService messageService, SimpMessagingTemplate messagingTemplate) {
        this.messageService = messageService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Backfills everything the client missed, across all the chats it names.
     *
     * <p>Invoked for frames addressed to {@code /app/sync.start}. The frontend
     * fires this on every reconnect with its per-chat cursors, so one round trip
     * catches up the whole client.
     *
     * <p>Replies go to the caller's own queues, never a topic — this is one
     * client's backlog, not a broadcast. The completion frame always follows,
     * even when nothing was missed, so the client can tell "caught up" from
     * "still waiting".
     *
     * <p>A null or empty cursor map is valid and simply completes with zero.
     *
     * @throws AccessDeniedException if the STOMP session is unauthenticated
     */
    @MessageMapping("/sync.start")
    public void startSync(@Payload SyncRequest request, Principal principal) {
        AuthenticatedUser user = requirePrincipal(principal);
        Map<UUID, Long> cursors = request.cursors() == null ? Map.of() : request.cursors();

        int total = 0;
        for (Map.Entry<UUID, Long> cursor : cursors.entrySet()) {
            total += sendChat(user, cursor.getKey(), cursor.getValue() == null ? 0 : cursor.getValue());
        }

        messagingTemplate.convertAndSendToUser(user.getName(), "/queue/sync-complete",
                Map.of("messageCount", total));
    }

    /**
     * Sends one chat's missed messages, in batches, and reports how many.
     *
     * <p>Batching at 50 keeps a single frame from carrying a huge backlog, which
     * a client would have to buffer whole before rendering any of it.
     *
     * <p>Membership is enforced inside {@code MessageService.since}, so a cursor
     * naming someone else's chat throws rather than leaking its contents.
     */
    private int sendChat(AuthenticatedUser user, UUID chatId, long afterSeq) {
        // Membership is enforced inside the service; a cursor for someone
        // else's chat throws rather than leaking its contents.
        List<MessageDTO> missed = messageService.since(chatId, user.id(), afterSeq).stream()
                .map(MessageDTO::from)
                .toList();

        for (int start = 0; start < missed.size(); start += BATCH_SIZE) {
            List<MessageDTO> batch = missed.subList(start, Math.min(start + BATCH_SIZE, missed.size()));
            messagingTemplate.convertAndSendToUser(user.getName(), "/queue/sync-batch",
                    Map.of("chatId", chatId, "messages", batch));
        }

        return missed.size();
    }

    /**
     * Unwraps the session's principal, refusing an unauthenticated one.
     *
     * <p>Mirrors the guard in {@code ChatWebSocketController}: the auth
     * interceptor should have made this unreachable, and it is cheap enough to
     * keep as a second line.
     *
     * @throws AccessDeniedException if the session carries no authenticated user
     */
    private AuthenticatedUser requirePrincipal(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        throw new AccessDeniedException("Unauthenticated STOMP session");
    }
}
