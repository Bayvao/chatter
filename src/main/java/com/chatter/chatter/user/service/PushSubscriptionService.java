package com.chatter.chatter.user.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatter.chatter.user.dto.PushSubscriptionRequest;
import com.chatter.chatter.user.model.PushSubscription;
import com.chatter.chatter.user.repository.PushSubscriptionRepository;

@Service
@Transactional(readOnly = true)
public class PushSubscriptionService {

    private final PushSubscriptionRepository repository;

    public PushSubscriptionService(PushSubscriptionRepository repository) {
        this.repository = repository;
    }

    /**
     * Idempotent, and re-points an endpoint at whoever currently owns it.
     * Push services reuse endpoints across reinstalls, so a stale row would
     * otherwise send one user's messages to another's browser.
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

    /** Only removes the row if it belongs to the caller. */
    @Transactional
    public void unsubscribe(UUID userId, String endpoint) {
        repository.findByEndpoint(endpoint)
                .filter(subscription -> subscription.getUserId().equals(userId))
                .ifPresent(repository::delete);
    }
}
