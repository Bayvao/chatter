package com.chatter.chatter.chat.port;

import java.util.UUID;

/**
 * Whether two users are connected closely enough to open a direct chat.
 *
 * <p>The chat module needs this to refuse a conversation between strangers, but
 * friendship is user-module data and rule 2 forbids reaching into
 * {@code ContactRepository}. So it is a port, exactly like
 * {@link SenderDirectory}: the user module supplies the adapter, and when the
 * modules split this becomes a network call with no chat code changed.
 */
public interface RelationshipDirectory {

    /**
     * Whether the two users have an accepted, unblocked friendship.
     *
     * <p>Called by {@code ChatService.getOrCreateDirectChat} before creating a
     * 1:1 conversation. Symmetric: the order of the arguments does not matter.
     *
     * <p>Group chats deliberately do not consult this — being added to a group
     * is not a friendship.
     */
    boolean areConnected(UUID userA, UUID userB);
}
