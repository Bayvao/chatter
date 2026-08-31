package com.chatter.chatter.chat.websocket;

import java.util.UUID;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.chatter.chatter.chat.dto.MessageDTO;
import com.chatter.chatter.chat.event.MessageSent;
import com.chatter.chatter.chat.model.ChatParticipant;
import com.chatter.chatter.chat.model.Message;
import com.chatter.chatter.chat.port.PresenceStore;
import com.chatter.chatter.chat.port.PushSender;
import com.chatter.chatter.chat.repository.ChatParticipantRepository;
import com.chatter.chatter.chat.repository.MessageRepository;

/**
 * AFTER_COMMIT is the in-process stand-in for the transactional outbox: the
 * push only happens once the row is durable, so "delivered but never saved"
 * cannot occur. In Phase 5 this becomes a {@code @KafkaListener} with the
 * same body, and the send path does not change.
 *
 * <p>Phase 3 adds the per-recipient branch: connected recipients get the
 * WebSocket frame and the message is marked DELIVERED; the rest get a push
 * notification and the message stays SENT until they reconnect and sync.
 */
@Component
public class MessageBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;
    private final MessageRepository messageRepository;
    private final ChatParticipantRepository participantRepository;
    private final PresenceStore presenceStore;
    private final PushSender pushSender;

    public MessageBroadcaster(SimpMessagingTemplate messagingTemplate, MessageRepository messageRepository,
                               ChatParticipantRepository participantRepository, PresenceStore presenceStore,
                               PushSender pushSender) {
        this.messagingTemplate = messagingTemplate;
        this.messageRepository = messageRepository;
        this.participantRepository = participantRepository;
        this.presenceStore = presenceStore;
        this.pushSender = pushSender;
    }

    // REQUIRES_NEW: the publishing transaction has already committed, so this
    // cannot join it, and marking DELIVERED needs a transaction of its own.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageSent(MessageSent event) {
        Message message = messageRepository.findById(event.messageId()).orElse(null);
        if (message == null) {
            return;
        }

        messagingTemplate.convertAndSend("/topic/chats/" + event.chatId(), MessageDTO.from(message));

        boolean anyRecipientOnline = false;

        for (ChatParticipant participant : participantRepository.findByChatIdAndLeftAtIsNull(event.chatId())) {
            UUID recipientId = participant.getUserId();
            if (recipientId.equals(event.senderId())) {
                continue;
            }

            if (presenceStore.isOnline(recipientId)) {
                anyRecipientOnline = true;
            } else {
                pushSender.sendMessageNotification(recipientId,
                        new PushSender.Notification(event.chatId(), event.senderName(), event.content()));
            }
        }

        // DELIVERED means "reached at least one recipient's client". Anyone
        // offline picks it up through sync, which is what advances their own
        // read cursor.
        if (anyRecipientOnline) {
            message.markDelivered();
        }
    }
}
