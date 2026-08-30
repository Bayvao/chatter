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

    public UserProfile getProfile(UUID userId) {
        return profileRepository.findById(userId).orElse(null);
    }

    /**
     * Null fields are left untouched, so a client can patch a single field.
     *
     * <p>When a display field actually changes, bumps {@code users.version} and
     * publishes {@link UserProfileChanged} — that version is what lets the chat
     * module's denormalised sender snapshot reject stale updates.
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
