package com.chatter.chatter.user;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.chatter.chatter.chat.port.SenderDirectory;
import com.chatter.chatter.user.exception.UserNotFoundException;
import com.chatter.chatter.user.model.User;
import com.chatter.chatter.user.repository.UserRepository;

/**
 * User-side adapter for the chat module's {@link SenderDirectory} port. The
 * dependency points inward from user to chat, so chat stays unaware of the
 * user module.
 */
@Component
public class UserSenderDirectory implements SenderDirectory {

    private final UserRepository userRepository;

    public UserSenderDirectory(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Called by {@code MessageService.send} to snapshot the sender's name
     * onto a new message, and by {@code ChatService} to name the other party in
     * a 1:1 conversation. This is the only route from chat code to a user row.
     *
     * <p>The version travels with the name so the snapshot can reject a stale
     * profile update that arrives out of order.
     *
     * @throws UserNotFoundException if no such user exists — which is also how
     *         {@code getOrCreateDirectChat} rejects a chat with a nonexistent
     *         person
     */
    @Override
    @Transactional(readOnly = true)
    public Sender lookup(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId.toString()));
        return new Sender(user.getDisplayName(), user.getAvatarUrl(), user.getVersion());
    }
}
