package com.chatter.chatter.user.controller;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.chatter.chatter.user.dto.ContactDTO;
import com.chatter.chatter.user.dto.ProfileDTO;
import com.chatter.chatter.user.dto.UpdateProfileRequest;
import com.chatter.chatter.user.dto.UserDTO;
import com.chatter.chatter.user.model.User;
import com.chatter.chatter.user.security.AuthenticatedUser;
import com.chatter.chatter.user.service.ContactService;
import com.chatter.chatter.user.service.ProfileService;
import com.chatter.chatter.user.service.UserService;

/**
 * Everything here is scoped to the authenticated caller rather than taking a
 * userId path parameter — a {@code /{userId}/profile} route would let any
 * signed-in user edit anyone else's profile.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final ProfileService profileService;
    private final ContactService contactService;

    public UserController(UserService userService, ProfileService profileService, ContactService contactService) {
        this.userService = userService;
        this.profileService = profileService;
        this.contactService = contactService;
    }

    /** Lets a client find someone to start a chat with. Blocked users are hidden. */
    @GetMapping("/search")
    public List<UserDTO> search(@AuthenticationPrincipal AuthenticatedUser principal,
                                 @RequestParam("q") String query) {
        Set<UUID> blocked = Set.copyOf(contactService.blockedIds(principal.id()));

        return userService.search(query, principal.id()).stream()
                .filter(user -> !blocked.contains(user.getId()))
                .map(UserDTO::from)
                .toList();
    }

    @GetMapping("/me/profile")
    public ProfileDTO myProfile(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ProfileDTO.from(userService.getById(principal.id()), profileService.getProfile(principal.id()));
    }

    @PutMapping("/me/profile")
    public ProfileDTO updateMyProfile(@AuthenticationPrincipal AuthenticatedUser principal,
                                       @Valid @RequestBody UpdateProfileRequest request) {
        User user = profileService.updateProfile(principal.id(), request);
        return ProfileDTO.from(user, profileService.getProfile(principal.id()));
    }

    @GetMapping("/{userId}/profile")
    public ProfileDTO profileOf(@PathVariable UUID userId) {
        return ProfileDTO.from(userService.getById(userId), profileService.getProfile(userId));
    }

    @GetMapping("/me/contacts")
    public List<ContactDTO> myContacts(@AuthenticationPrincipal AuthenticatedUser principal) {
        return contactService.listContacts(principal.id());
    }

    @PostMapping("/me/contacts/{userId}")
    public ResponseEntity<Void> addContact(@AuthenticationPrincipal AuthenticatedUser principal,
                                            @PathVariable UUID userId) {
        contactService.addContact(principal.id(), userId);
        return ResponseEntity.status(201).build();
    }

    @DeleteMapping("/me/contacts/{userId}")
    public ResponseEntity<Void> removeContact(@AuthenticationPrincipal AuthenticatedUser principal,
                                               @PathVariable UUID userId) {
        contactService.removeContact(principal.id(), userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/contacts/{userId}/block")
    public ResponseEntity<Void> blockContact(@AuthenticationPrincipal AuthenticatedUser principal,
                                              @PathVariable UUID userId) {
        contactService.setBlocked(principal.id(), userId, true);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me/contacts/{userId}/block")
    public ResponseEntity<Void> unblockContact(@AuthenticationPrincipal AuthenticatedUser principal,
                                                @PathVariable UUID userId) {
        contactService.setBlocked(principal.id(), userId, false);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/contacts/{userId}/favorite")
    public ResponseEntity<Void> favoriteContact(@AuthenticationPrincipal AuthenticatedUser principal,
                                                 @PathVariable UUID userId) {
        contactService.setFavorite(principal.id(), userId, true);
        return ResponseEntity.noContent().build();
    }
}
