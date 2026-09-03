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

    /**
     * Whether a block stands between two users, in either direction.
     *
     * <p>Called by {@code MessageService.send} for every 1:1 message. Blocking
     * silences an existing conversation both ways — the blocked party must not
     * be able to keep talking, and the blocker should not be able to either
     * while the bar stands.
     *
     * <p>Separate from {@link #areConnected} because it is the narrower and
     * cheaper question, and because the answers differ: un-friending someone
     * leaves an existing conversation usable, blocking them does not.
     */
    boolean isBlockedEitherWay(UUID userA, UUID userB);
}
