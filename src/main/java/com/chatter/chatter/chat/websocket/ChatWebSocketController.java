package com.chatter.chatter.chat.websocket;

import java.security.Principal;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import jakarta.validation.Valid;

import com.chatter.chatter.chat.dto.SendMessageRequest;
import com.chatter.chatter.chat.service.MessageService;
import com.chatter.chatter.user.security.AuthenticatedUser;

/**
 * The live send path: STOMP frames from a connected client.
 *
 * <p>The REST twin is {@code ChatController.sendMessage}, for clients without a
 * socket. Both funnel into the same {@code MessageService.send}, so the rules
 * cannot drift between them.
 */
@Controller
public class ChatWebSocketController {

    private final MessageService messageService;

    public ChatWebSocketController(MessageService messageService) {
        this.messageService = messageService;
    }

    /**
     * Handles a message sent over the open socket.
     *
     * <p>Invoked by Spring for frames addressed to {@code /app/chat.send}. The
     * sender comes from the STOMP session established at CONNECT, never from
     * the payload — a client that could name its own sender could post as
     * anyone.
     *
     * <p>Nothing is returned here and there is no {@code @SendTo}: the broadcast
     * happens in {@link MessageBroadcaster} after the transaction commits, so
     * subscribers never see a message that was rolled back. The sender receives
     * it over the same subscription as everyone else, which is why the frontend
     * needs no optimistic copy to reconcile.
     */
    @MessageMapping("/chat.send")
    public void send(@Valid @Payload SendMessageRequest request, Principal principal) {
        AuthenticatedUser sender = requirePrincipal(principal);
        messageService.send(request.chatId(), sender.id(), request.content(), request.clientMsgId());
    }

    /**
     * Unwraps the STOMP session's principal, refusing an unauthenticated one.
     *
     * <p>Belt and braces: {@code StompAuthChannelInterceptor} already rejects a
     * CONNECT without a valid token, so reaching here without a principal would
     * mean that gate had been bypassed. Cheap enough to keep as a second line.
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
