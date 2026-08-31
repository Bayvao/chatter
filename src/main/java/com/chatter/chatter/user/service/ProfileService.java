package com.chatter.chatter.user.service;

import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatter.chatter.user.dto.UpdateProfileRequest;
import com.chatter.chatter.user.event.UserProfileChanged;
import com.chatter.chatter.user.model.User;
import com.chatter.chatter.user.model.UserProfile;
import com.chatter.chatter.user.repository.UserProfileRepository;

/**
 * Profile reads and edits, split across two tables on purpose.
 *
 * <p>{@code users} holds the few fields the chat module snapshots onto every
 * message (display name, avatar); {@code user_profiles} holds everything else
 * (bio, phone, location). Only a change to the first kind needs to ripple
 * outwards, which is why this class distinguishes them.
 */
@Service
@Transactional(readOnly = true)
public class ProfileService {

    private final UserService userService;
    private final UserProfileRepository profileRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ProfileService(UserService userService, UserProfileRepository profileRepository,
                           ApplicationEventPublisher eventPublisher) {
        this.userService = userService;
        this.profileRepository = profileRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * The extended profile for a user, or {@code null} if they have never saved
     * one.
     *
     * <p>Used by {@code UserController} wherever a {@code ProfileDTO} is built.
     * Null rather than empty is fine here because {@code ProfileDTO.from}
     * already treats a missing profile as "all extended fields blank" — most
     * users never fill any of them in.
     */
    public UserProfile getProfile(UUID userId) {
        return profileRepository.findById(userId).orElse(null);
    }

    /**
     * Applies a partial profile edit, creating the extended profile row on first
     * save.
     *
     * <p>Used by {@code UserController.updateProfile}, the only writer. Null
     * fields are left untouched, so a client can patch a single field without
     * having to send the whole profile back.
     *
     * <p>When a display field actually changes, bumps {@code users.version} and
     * publishes {@link UserProfileChanged}. That version is what lets the chat
     * module's denormalised sender snapshot reject stale updates: two edits
     * racing can arrive out of order, and the lower version loses.
     */
    @Transactional
    public User updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = userService.getById(userId);
        boolean displayChanged = applyDisplayFields(user, request);

        UserProfile profile = profileRepository.findById(userId).orElseGet(() -> new UserProfile(userId));
        applyExtendedFields(profile, request);
        profile.touch();
        profileRepository.save(profile);

        if (displayChanged) {
            user.setVersion(user.getVersion() + 1);
            eventPublisher.publishEvent(UserProfileChanged.of(
                    user.getId(), user.getVersion(), user.getDisplayName(), user.getAvatarUrl()));
        }

        return user;
    }

    /**
     * Copies the snapshotted fields onto the user, reporting whether any
     * actually changed.
     *
     * <p>The return value drives the version bump and the event in
     * {@link #updateProfile}, so the comparison has to be by value: saving a
     * profile form unchanged must not republish and rewrite sender snapshots
     * across every message the user has ever sent.
     */
    private boolean applyDisplayFields(User user, UpdateProfileRequest request) {
        boolean changed = false;

        if (request.firstName() != null && !request.firstName().equals(user.getFirstName())) {
            user.setFirstName(request.firstName());
            changed = true;
        }
        if (request.lastName() != null && !request.lastName().equals(user.getLastName())) {
            user.setLastName(request.lastName());
            changed = true;
        }
        if (request.avatarUrl() != null && !request.avatarUrl().equals(user.getAvatarUrl())) {
            user.setAvatarUrl(request.avatarUrl());
            changed = true;
        }
        // Status text is deliberately not part of the change signal: it is not
        // snapshotted onto messages, so republishing for it would rewrite every
        // message the user ever sent for nothing.
        if (request.statusText() != null) {
            user.setStatusText(request.statusText());
        }

        return changed;
    }

    /**
     * Copies the non-snapshotted fields onto the extended profile row.
     *
     * <p>Returns nothing because none of these fields are denormalised anywhere:
     * changing a bio affects only this row, so there is no change signal for
     * {@link #updateProfile} to act on.
     */
    private void applyExtendedFields(UserProfile profile, UpdateProfileRequest request) {
        if (request.phoneNumber() != null) {
            profile.setPhoneNumber(request.phoneNumber());
        }
        if (request.bio() != null) {
            profile.setBio(request.bio());
        }
        if (request.dateOfBirth() != null) {
            profile.setDateOfBirth(request.dateOfBirth());
        }
        if (request.location() != null) {
            profile.setLocation(request.location());
        }
        if (request.website() != null) {
            profile.setWebsite(request.website());
        }
    }
}
