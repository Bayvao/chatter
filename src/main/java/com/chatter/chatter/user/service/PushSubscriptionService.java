package com.chatter.chatter.user.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatter.chatter.user.dto.PushSubscriptionRequest;
import com.chatter.chatter.user.model.PushSubscription;
import com.chatter.chatter.user.repository.PushSubscriptionRepository;

/**
 * The registry of browsers that have opted in to Web Push, and the only writer
 * of {@code app_user.push_subscriptions}.
 *
 * <p>A row is an endpoint URL nominated by the browser plus the two keys needed
 * to encrypt for it. {@code WebPushSender} reads these when a message arrives
 * for someone who is offline.
 */
@Service
@Transactional(readOnly = true)
public class PushSubscriptionService {

    private final PushSubscriptionRepository repository;

    public PushSubscriptionService(PushSubscriptionRepository repository) {
        this.repository = repository;
    }

    /**
     * Registers a browser to receive push, or refreshes an existing
     * registration.
     *
     * <p>Used by {@code PushController.subscribe}, called from
     * {@code enablePush()} in the frontend after the user grants notification
     * permission. Safe to call on every sign-in — it is idempotent by endpoint.
     *
     * <p>An endpoint already on file is re-pointed at whoever is registering
     * now, rather than duplicated. Push services reuse endpoints across
     * reinstalls and across users of a shared browser, so a stale row would
     * otherwise deliver one user's messages to another's device.
     */
    @Transactional
    public void subscribe(UUID userId, PushSubscriptionRequest request, String userAgent) {
        repository.findByEndpoint(request.endpoint()).ifPresentOrElse(existing -> {
            existing.setUserId(userId);
            existing.setP256dhKey(request.p256dh());
            existing.setAuthSecret(request.auth());
            existing.setUserAgent(userAgent);
        }, () -> repository.save(new PushSubscription(
                userId, request.endpoint(), request.p256dh(), request.auth(), userAgent)));
    }

    /**
     * Retires a browser's registration.
     *
     * <p>Used by {@code PushController.unsubscribe}, called from
     * {@code disablePush()} on sign-out so the next person to use this browser
     * is not notified about the previous user's messages.
     *
     * <p>Only removes the row if it belongs to the caller: an endpoint is
     * guessable enough that unsubscribing on someone else's behalf should not
     * be possible. Silent when there is nothing to remove, since sign-out must
     * not fail over a subscription that was never created.
     */
    @Transactional
    public void unsubscribe(UUID userId, String endpoint) {
        repository.findByEndpoint(endpoint)
                .filter(subscription -> subscription.getUserId().equals(userId))
                .ifPresent(repository::delete);
    }
}
