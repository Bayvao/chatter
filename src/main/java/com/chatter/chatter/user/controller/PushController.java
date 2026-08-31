package com.chatter.chatter.user.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.chatter.chatter.user.dto.PushSubscriptionRequest;
import com.chatter.chatter.user.security.AuthenticatedUser;
import com.chatter.chatter.user.service.PushSubscriptionService;

@RestController
@RequestMapping("/api/push")
public class PushController {

    private final PushSubscriptionService subscriptionService;
    private final String vapidPublicKey;
    private final boolean pushEnabled;

    public PushController(PushSubscriptionService subscriptionService,
                           @Value("${app.push.vapid.public-key:}") String vapidPublicKey,
                           @Value("${app.push.vapid.enabled:false}") boolean pushEnabled) {
        this.subscriptionService = subscriptionService;
        this.vapidPublicKey = vapidPublicKey;
        this.pushEnabled = pushEnabled;
    }

    /**
     * The VAPID public key, and whether push is switched on at all.
     *
     * <p>Called first by {@code enablePush()} in the frontend: the browser needs
     * this key to create a subscription, and the {@code enabled} flag lets the
     * client skip the permission prompt entirely when the server has no keys
     * configured.
     *
     * <p>Publishing the key is safe — it is public by definition, being what
     * identifies us to the push service. Only the private half signs.
     */
    @GetMapping("/public-key")
    public Map<String, Object> publicKey() {
        return Map.of("enabled", pushEnabled, "publicKey", vapidPublicKey);
    }

    /**
     * Registers this browser to receive push notifications.
     *
     * <p>Called by {@code enablePush()} once the user grants notification
     * permission. Safe to call on every sign-in: it is idempotent by endpoint.
     *
     * <p>The User-Agent is stored purely so a person can recognise their own
     * devices in a future "manage devices" screen; it is optional and nothing
     * depends on it.
     *
     * @return 201 whether the registration was created or refreshed
     */
    @PostMapping("/subscriptions")
    public ResponseEntity<Void> subscribe(@AuthenticationPrincipal AuthenticatedUser principal,
                                           @Valid @RequestBody PushSubscriptionRequest request,
                                           @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        subscriptionService.subscribe(principal.id(), request, userAgent);
        return ResponseEntity.status(201).build();
    }

    /**
     * Retires this browser's registration.
     *
     * <p>Called by {@code disablePush()} on sign-out, so the next person to use
     * the browser is not notified about the previous user's messages.
     *
     * <p>A DELETE with a body, which is unusual but justified: the endpoint URL
     * that identifies the subscription is far too long to put in a path or
     * query string.
     */
    @DeleteMapping("/subscriptions")
    public ResponseEntity<Void> unsubscribe(@AuthenticationPrincipal AuthenticatedUser principal,
                                             @Valid @RequestBody PushSubscriptionRequest request) {
        subscriptionService.unsubscribe(principal.id(), request.endpoint());
        return ResponseEntity.noContent().build();
    }
}
