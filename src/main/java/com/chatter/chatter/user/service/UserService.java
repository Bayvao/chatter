package com.chatter.chatter.user.service;

import java.util.List;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatter.chatter.user.dto.RegisterRequest;
import com.chatter.chatter.user.exception.EmailAlreadyExistsException;
import com.chatter.chatter.user.exception.UserNotFoundException;
import com.chatter.chatter.user.exception.UsernameAlreadyExistsException;
import com.chatter.chatter.user.model.User;
import com.chatter.chatter.user.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new UsernameAlreadyExistsException(request.username());
        }

        String email = blankToNull(request.email());
        if (email != null && userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }

        return userRepository.save(User.create(request.username(), email, passwordEncoder.encode(request.password())));
    }

    public User getById(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id.toString()));
    }

    public User getByUsername(String username) {
        return userRepository.findByUsername(username).orElseThrow(() -> new UserNotFoundException(username));
    }

    public List<User> search(String term, UUID excludeUserId) {
        if (term == null || term.isBlank()) {
            return List.of();
        }
        return userRepository.search(term.trim(), excludeUserId);
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
