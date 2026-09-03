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
import com.chatter.chatter.user.dto.ContactRequestDTO;
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

    /**
     * Finds people to start a chat with.
     *
     * <p>Backs the search box in the chat list. Blocked users are filtered out
     * here rather than in the query, keeping the block list a concern of the
     * user module alone.
     */
    @GetMapping("/search")
    public List<UserDTO> search(@AuthenticationPrincipal AuthenticatedUser principal,
                                 @RequestParam("q") String query) {
        Set<UUID> blocked = Set.copyOf(contactService.blockedIds(principal.id()));

        return userService.search(query, principal.id()).stream()
                .filter(user -> !blocked.contains(user.getId()))
                .map(UserDTO::from)
                .toList();
    }

    /**
     * The caller's own profile, display fields and extended fields combined.
     *
     * <p>Used to populate the Profile screen. Two reads because the fields live
     * in two tables — {@code users} for what messages snapshot, {@code
     * user_profiles} for everything else.
     */
    @GetMapping("/me/profile")
    public ProfileDTO myProfile(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ProfileDTO.from(userService.getById(principal.id()), profileService.getProfile(principal.id()));
    }

    /**
     * Saves an edit to the caller's own profile.
     *
     * <p>Used by the Profile form. Scoped to {@code /me} on purpose: a
     * {@code /{userId}/profile} write route would let any signed-in user edit
     * anyone else's details.
     *
     * <p>A {@code PUT} that behaves as a patch — null fields are left alone, so
     * the client may send only what changed.
     */
    @PutMapping("/me/profile")
    public ProfileDTO updateMyProfile(@AuthenticationPrincipal AuthenticatedUser principal,
                                       @Valid @RequestBody UpdateProfileRequest request) {
        User user = profileService.updateProfile(principal.id(), request);
        return ProfileDTO.from(user, profileService.getProfile(principal.id()));
    }

    /**
     * Someone else's profile.
     *
     * <p>Used when viewing a person you are chatting with. Read-only, which is
     * why a userId in the path is acceptable here where it would not be on the
     * write route above.
     */
    @GetMapping("/{userId}/profile")
    public ProfileDTO profileOf(@PathVariable UUID userId) {
        return ProfileDTO.from(userService.getById(userId), profileService.getProfile(userId));
    }

    /**
     * The caller's saved contacts, newest first.
     *
     * <p>Backs the Contacts tab. Blocked contacts are omitted; they are still
     * stored, and reappear if unblocked.
     */
    @GetMapping("/me/contacts")
    public List<ContactDTO> myContacts(@AuthenticationPrincipal AuthenticatedUser principal) {
        return contactService.listContacts(principal.id());
    }

    /**
     * Sends a friend request.
     *
     * <p>Used by "Add friend" beside a search result. This no longer creates a
     * contact outright — the two only become contacts once the recipient
     * accepts, which is what stops a chat opening the instant a request is sent.
     *
     * @return 201 when a request is created, or 200 when it completed a request
     *         that was already coming the other way and the two are now contacts
     * @throws com.chatter.chatter.user.exception.ContactAlreadyExistsException
     *         409, already contacts or already asked
     * @throws com.chatter.chatter.user.exception.ContactBlockedException
     *         403, the recipient has blocked the caller
     */
    @PostMapping("/me/contacts/{userId}")
    public ResponseEntity<Void> sendRequest(@AuthenticationPrincipal AuthenticatedUser principal,
                                             @PathVariable UUID userId) {
        boolean pending = contactService.sendRequest(principal.id(), userId).isPresent();
        return ResponseEntity.status(pending ? 201 : 200).build();
    }

    /**
     * Requests waiting on the caller's decision.
     *
     * <p>Backs the Requests section of the Contacts tab. The REST source of
     * truth: the live relay on {@code /user/queue/contacts} only saves a poll,
     * and drops entirely if the recipient was offline when it arrived.
     */
    @GetMapping("/me/contacts/requests")
    public List<ContactRequestDTO> incomingRequests(@AuthenticationPrincipal AuthenticatedUser principal) {
        return contactService.incomingRequests(principal.id());
    }

    /**
     * Requests the caller has sent and not yet had answered.
     *
     * <p>Lets a search result render "Requested" rather than offering to send a
     * second request that would only be refused.
     */
    @GetMapping("/me/contacts/requests/sent")
    public List<ContactRequestDTO> outgoingRequests(@AuthenticationPrincipal AuthenticatedUser principal) {
        return contactService.outgoingRequests(principal.id());
    }

    /**
     * Accepts a request, making the two of them contacts.
     *
     * <p>Both rows are written in one transaction, so the friendship is mutual
     * the moment it exists and either side can open the chat.
     */
    @PostMapping("/me/contacts/{userId}/accept")
    public ResponseEntity<Void> acceptRequest(@AuthenticationPrincipal AuthenticatedUser principal,
                                               @PathVariable UUID userId) {
        contactService.acceptRequest(principal.id(), userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Declines a request addressed to the caller, or cancels one they sent.
     *
     * <p>One route for both: the row goes either way, and the caller is a party
     * to it in both directions.
     */
    @DeleteMapping("/me/contacts/{userId}/decline")
    public ResponseEntity<Void> declineRequest(@AuthenticationPrincipal AuthenticatedUser principal,
                                                @PathVariable UUID userId) {
        contactService.declineRequest(principal.id(), userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Ends a friendship, from both sides.
     *
     * <p>Distinct from blocking: this deletes both rows, so the person reappears
     * in search and either of them may request again. Blocking keeps them saved
     * and suppressed.
     */
    @DeleteMapping("/me/contacts/{userId}")
    public ResponseEntity<Void> removeContact(@AuthenticationPrincipal AuthenticatedUser principal,
                                               @PathVariable UUID userId) {
        contactService.removeContact(principal.id(), userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Blocks a contact, hiding them from the contact list and from search.
     *
     * <p>Note this does not currently stop them messaging the caller — it is a
     * visibility control, not a delivery one.
     */
    @PostMapping("/me/contacts/{userId}/block")
    public ResponseEntity<Void> blockContact(@AuthenticationPrincipal AuthenticatedUser principal,
                                              @PathVariable UUID userId) {
        contactService.setBlocked(principal.id(), userId, true);
        return ResponseEntity.noContent().build();
    }

    /**
     * Lifts a block, restoring the contact to the list and to search results.
     *
     * <p>Modelled as deleting the block rather than as a second POST, so the
     * pair reads as one resource being set and cleared.
     */
    @DeleteMapping("/me/contacts/{userId}/block")
    public ResponseEntity<Void> unblockContact(@AuthenticationPrincipal AuthenticatedUser principal,
                                                @PathVariable UUID userId) {
        contactService.setBlocked(principal.id(), userId, false);
        return ResponseEntity.noContent().build();
    }

    /**
     * The users the caller has blocked.
     *
     * <p>Lets the client replace the composer with an explanation and an
     * Unblock button, rather than letting the send fail silently.
     *
     * <p>Only the caller's own blocks. Who has blocked <em>them</em> is never
     * disclosed — that is the whole point of blocking being silent.
     */
    @GetMapping("/me/blocked")
    public List<UUID> blockedUsers(@AuthenticationPrincipal AuthenticatedUser principal) {
        return contactService.blockedByMe(principal.id());
    }

    /**
     * Marks a contact as a favourite.
     *
     * <p>A display hint only — favourites sort first. Grants nothing and hides
     * nothing, which is why there is no matching unfavourite route yet: nothing
     * in the UI clears it.
     */
    @PostMapping("/me/contacts/{userId}/favorite")
    public ResponseEntity<Void> favoriteContact(@AuthenticationPrincipal AuthenticatedUser principal,
                                                 @PathVariable UUID userId) {
        contactService.setFavorite(principal.id(), userId, true);
        return ResponseEntity.noContent().build();
    }
}
