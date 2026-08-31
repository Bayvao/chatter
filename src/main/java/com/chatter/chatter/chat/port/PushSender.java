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

    /**
     * Notifies a user about a message they will not have seen.
     *
     * <p>Called by {@code MessageBroadcaster} for each recipient that
     * {@code PresenceStore} reports offline.
     *
     * <p>Implementations must not throw and should not block: the message is
     * already committed and delivered to everyone online, so a push failure is
     * not a send failure and an unreachable push service must not stall the
     * broadcast.
     */
    void sendMessageNotification(UUID recipientId, Notification notification);

    /**
     * What the recipient's device is told: which conversation, who from, and a
     * short preview.
     *
     * <p>{@code chatId} is what lets a notification tap open the right
     * conversation.
     */
    record Notification(UUID chatId, String title, String body) {
    }
}
