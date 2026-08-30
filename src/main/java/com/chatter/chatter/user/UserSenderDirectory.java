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

    @Override
    @Transactional(readOnly = true)
    public Sender lookup(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId.toString()));
        return new Sender(user.getDisplayName(), user.getAvatarUrl(), user.getVersion());
    }
}
