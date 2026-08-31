package com.chatter.chatter.chat.port;

import java.util.UUID;

/**
 * Delivers a notification to a user who is not currently connected.
 *
 * <p>The payload is deliberately thin — sender name, chat id, a short preview.
 * Once message content is end-to-end encrypted (Phase 3.75) the server holds
 * only ciphertext and this becomes a content-free ping that the client
 * decrypts locally, so nothing here should grow to depend on message text.
 */
public interface PushSender {

    void sendMessageNotification(UUID recipientId, Notification notification);

    record Notification(UUID chatId, String title, String body) {
    }
}
