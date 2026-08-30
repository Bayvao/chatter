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

    Sender lookup(UUID userId);

    record Sender(String displayName, String avatarUrl, long version) {
    }
}
