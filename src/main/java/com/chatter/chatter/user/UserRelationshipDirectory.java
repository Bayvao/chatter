package com.chatter.chatter.user;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.chatter.chatter.chat.port.RelationshipDirectory;
import com.chatter.chatter.user.service.ContactService;

/**
 * User-side adapter for the chat module's {@link RelationshipDirectory} port,
 * beside {@link UserSenderDirectory}. The dependency points inward from user to
 * chat, so chat stays unaware of contacts entirely.
 */
@Component
public class UserRelationshipDirectory implements RelationshipDirectory {

    private final ContactService contactService;

    public UserRelationshipDirectory(ContactService contactService) {
        this.contactService = contactService;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Called by {@code ChatService.getOrCreateDirectChat} on every attempt to
     * open a 1:1 chat — the check whose absence made a friend request look
     * instantly accepted.
     */
    @Override
    @Transactional(readOnly = true)
    public boolean areConnected(UUID userA, UUID userB) {
        return contactService.areConnected(userA, userB);
    }

    /**
     * {@inheritDoc}
     *
     * <p>On the send path, so it is one indexed query rather than the pair of
     * contact reads {@link #areConnected} needs.
     */
    @Override
    @Transactional(readOnly = true)
    public boolean isBlockedEitherWay(UUID userA, UUID userB) {
        return contactService.isBlockedEitherWay(userA, userB);
    }
}
