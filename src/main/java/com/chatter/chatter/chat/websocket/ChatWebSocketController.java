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

@Controller
public class ChatWebSocketController {

    private final MessageService messageService;

    public ChatWebSocketController(MessageService messageService) {
        this.messageService = messageService;
    }

    /**
     * Nothing is returned here and there is no {@code @SendTo}: the broadcast
     * happens in {@link MessageBroadcaster} after the transaction commits, so
     * subscribers never see a message that was rolled back. The sender
     * receives it over the same subscription as everyone else.
     */
    @MessageMapping("/chat.send")
    public void send(@Valid @Payload SendMessageRequest request, Principal principal) {
        AuthenticatedUser sender = requirePrincipal(principal);
        messageService.send(request.chatId(), sender.id(), request.content(), request.clientMsgId());
    }

    private AuthenticatedUser requirePrincipal(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        throw new AccessDeniedException("Unauthenticated STOMP session");
    }
}
