package com.chatter.chatter.chat.port;

import java.util.UUID;

/**
 * What the chat module needs to know about a sender, expressed as its own
 * contract. The user module supplies an adapter; chat never touches
 * {@code UserRepository} directly (rule 2: no cross-module repository calls).
 *
 * <p>When the modules split, this interface becomes the seam: the adapter is
 * replaced by a cached projection fed from user events, and no chat code
 * changes.
 */
public interface SenderDirectory {

    /**
     * Resolves a user's display details.
     *
     * <p>Called by {@code MessageService.send} to snapshot the sender onto a new
     * message, and by {@code ChatService} to name the other party in a 1:1 chat
     * and to check a user exists before opening one.
     *
     * <p>Implementations throw rather than return null for an unknown user;
     * callers rely on that to reject a chat with someone who does not exist.
     */
    Sender lookup(UUID userId);

    /**
     * The display fields the chat module copies onto messages.
     *
     * <p>{@code version} travels with them so
     * {@code SenderSnapshotProjection} can reject an update that arrives out of
     * order.
     */
    record Sender(String displayName, String avatarUrl, long version) {
    }
}
