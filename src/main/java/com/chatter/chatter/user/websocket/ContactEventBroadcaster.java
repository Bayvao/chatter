package com.chatter.chatter.user.websocket;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.chatter.chatter.user.dto.ContactEventDTO;
import com.chatter.chatter.user.dto.UserDTO;
import com.chatter.chatter.user.event.ContactChanged;
import com.chatter.chatter.user.model.User;
import com.chatter.chatter.user.service.UserService;

/**
 * Relays relationship changes to the person affected, live.
 *
 * <p>Modelled on {@code MessageBroadcaster}: AFTER_COMMIT so a request that
 * rolled back is never announced, and REQUIRES_NEW because the publishing
 * transaction is already gone by the time this runs.
 *
 * <p>Sends to the user destination rather than a topic — a relationship change
 * concerns one person, and Spring scopes {@code /user/queue/**} to the session
 * principal, so no subscription check is needed the way chat topics need one.
 *
 * <p>Delivery is best effort. A user destination with nobody connected simply
 * drops, which is why {@code GET /me/contacts/requests} remains the source of
 * truth on load; this only saves a poll.
 */
@Component
public class ContactEventBroadcaster {

    /** Clients subscribe here; Spring prefixes it with /user for the session. */
    public static final String DESTINATION = "/queue/contacts";

    private final SimpMessagingTemplate messagingTemplate;
    private final UserService userService;

    public ContactEventBroadcaster(SimpMessagingTemplate messagingTemplate, UserService userService) {
        this.messagingTemplate = messagingTemplate;
        this.userService = userService;
    }

    /**
     * Delivers one committed relationship change to its audience.
     *
     * <p>Invoked by Spring for each {@link ContactChanged} published by
     * {@code ContactService}. The event names its own audience, because who
     * hears about a change is asymmetric — a block reaches only the blocker.
     *
     * <p>Addressed by username: user destinations resolve against
     * {@code Principal.getName()}, which {@code AuthenticatedUser} returns the
     * username from.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onContactChanged(ContactChanged event) {
        User audience = userService.getById(event.audienceId());
        // The counterpart is whoever the audience is not, so one payload shape
        // works for the sender and the recipient of the same change.
        User other = userService.getById(
                event.audienceId().equals(event.actorId()) ? event.subjectId() : event.actorId());

        messagingTemplate.convertAndSendToUser(audience.getUsername(), DESTINATION,
                new ContactEventDTO(event.type().name(), UserDTO.from(other)));
    }
}
