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
     * The browser needs the VAPID public key to create a subscription. It is
     * public by definition — it is what identifies us to the push service.
     */
    @GetMapping("/public-key")
    public Map<String, Object> publicKey() {
        return Map.of("enabled", pushEnabled, "publicKey", vapidPublicKey);
    }

    @PostMapping("/subscriptions")
    public ResponseEntity<Void> subscribe(@AuthenticationPrincipal AuthenticatedUser principal,
                                           @Valid @RequestBody PushSubscriptionRequest request,
                                           @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        subscriptionService.subscribe(principal.id(), request, userAgent);
        return ResponseEntity.status(201).build();
    }

    @DeleteMapping("/subscriptions")
    public ResponseEntity<Void> unsubscribe(@AuthenticationPrincipal AuthenticatedUser principal,
                                             @Valid @RequestBody PushSubscriptionRequest request) {
        subscriptionService.unsubscribe(principal.id(), request.endpoint());
        return ResponseEntity.noContent().build();
    }
}
