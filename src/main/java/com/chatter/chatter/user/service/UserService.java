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

/**
 * Accounts and account lookup: the one place a {@link User} row is created or
 * fetched. Everything that needs a user goes through here rather than touching
 * {@link UserRepository}, so the uniqueness rules and the not-found behaviour
 * live in a single place.
 */
@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Creates an account, hashing the password on the way in.
     *
     * <p>Used by {@code AuthController.register} — the sign-up endpoint, and
     * the only caller. Username is always unique; e-mail is optional, and only
     * checked for uniqueness when one is actually supplied, so any number of
     * accounts may have no e-mail at all.
     *
     * @throws UsernameAlreadyExistsException if the username is taken
     * @throws EmailAlreadyExistsException if a non-blank e-mail is taken
     */
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

    /**
     * Loads a user by id, or fails loudly.
     *
     * <p>The workhorse lookup: used by {@code AuthController.me},
     * {@code UserController} when rendering a profile, and by
     * {@link ContactService#listContacts} to resolve each contact's details.
     * Throwing rather than returning an {@code Optional} is deliberate — every
     * caller holds an id it believes to be real, so absence is a bug or a
     * deleted account, not a branch worth writing at each call site.
     *
     * @throws UserNotFoundException if no such user exists
     */
    public User getById(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id.toString()));
    }

    /**
     * Loads a user by their unique username.
     *
     * <p>Used by {@code AuthController.login}, which has only the username the
     * caller typed. Note this throws for an unknown username, so the login path
     * must not let that distinction reach the client — an attacker who can tell
     * "no such user" from "wrong password" can enumerate accounts.
     *
     * @throws UserNotFoundException if no such user exists
     */
    public User getByUsername(String username) {
        return userRepository.findByUsername(username).orElseThrow(() -> new UserNotFoundException(username));
    }

    /**
     * Finds people to start a conversation with, by username or display name.
     *
     * <p>Used by {@code UserController.search}, backing the search box in the
     * chat list. {@code excludeUserId} is the caller's own id, kept out of the
     * results because offering to chat with yourself is noise.
     *
     * <p>A blank term returns nothing rather than everyone: an empty search box
     * should show an empty list, not the whole user table.
     */
    public List<User> search(String term, UUID excludeUserId) {
        if (term == null || term.isBlank()) {
            return List.of();
        }
        return userRepository.search(term.trim(), excludeUserId);
    }

    /**
     * Collapses {@code ""} and whitespace to {@code null}.
     *
     * <p>Used when registering, so an e-mail field the client left empty is
     * stored as NULL. That matters for the unique index: Postgres allows many
     * NULLs in a unique column, but only one empty string.
     */
    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
