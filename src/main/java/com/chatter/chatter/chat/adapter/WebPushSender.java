package com.chatter.chatter.chat.adapter;

import java.security.Security;
import java.time.Instant;
import java.util.UUID;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import nl.martijndwars.webpush.PushService;

import com.chatter.chatter.chat.port.PushSender;
import com.chatter.chatter.user.model.PushSubscription;
import com.chatter.chatter.user.repository.PushSubscriptionRepository;

/**
 * Web Push (RFC 8030/8291): the payload is encrypted for the subscription's
 * own key and posted to whatever push service the browser nominated, signed
 * with our VAPID key so the service can attribute it to us. No third-party
 * messaging account is involved.
 *
 * <p>Active only when {@code app.push.vapid.enabled} is true and keys are
 * supplied; otherwise {@link LoggingPushSender} takes over.
 */
@Component
@ConditionalOnProperty(name = "app.push.vapid.enabled", havingValue = "true")
public class WebPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(WebPushSender.class);

    /** Push services reject anything much larger once encrypted. */
    private static final int MAX_BODY_CHARS = 120;

    private final PushSubscriptionRepository subscriptions;
    private final ObjectMapper objectMapper;
    private final String publicKey;
    private final String privateKey;
    private final String subject;

    private PushService pushService;

    public WebPushSender(PushSubscriptionRepository subscriptions, ObjectMapper objectMapper,
                          @Value("${app.push.vapid.public-key}") String publicKey,
                          @Value("${app.push.vapid.private-key}") String privateKey,
                          @Value("${app.push.vapid.subject}") String subject) {
        this.subscriptions = subscriptions;
        this.objectMapper = objectMapper;
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.subject = subject;
    }

    @PostConstruct
    void init() throws Exception {
        // web-push needs a BouncyCastle provider registered for the ECDH and
        // HKDF work that payload encryption depends on.
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        pushService = new PushService(publicKey, privateKey, subject);
    }

    /**
     * Runs off the caller's thread: the message is already committed, and a
     * slow or unavailable push service must not hold up delivery to everyone
     * else. REQUIRES_NEW because the publishing transaction is long gone.
     */
    @Async
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendMessageNotification(UUID recipientId, PushSender.Notification notification) {
        for (PushSubscription subscription : subscriptions.findByUserId(recipientId)) {
            deliver(subscription, notification);
        }
    }

    private void deliver(PushSubscription subscription, PushSender.Notification notification) {
        try {
            byte[] payload = objectMapper.writeValueAsBytes(new Payload(
                    notification.chatId().toString(),
                    notification.title(),
                    truncate(notification.body())));

            // Fully qualified: PushSender.Notification is also in scope here.
            var response = pushService.send(new nl.martijndwars.webpush.Notification(
                    subscription.getEndpoint(),
                    subscription.getP256dhKey(),
                    subscription.getAuthSecret(),
                    payload));

            int status = response.getStatusLine().getStatusCode();
            if (status == 404 || status == 410) {
                // The subscription is permanently dead. Drop it, or the table
                // grows without bound and every send wastes a request.
                log.info("Removing expired push subscription {}", subscription.getId());
                subscriptions.delete(subscription);
            } else if (status >= 400) {
                log.warn("Push service returned {} for subscription {}", status, subscription.getId());
            } else {
                subscription.setLastUsedAt(Instant.now());
            }
        } catch (Exception e) {
            // Never propagate: the message is already saved and delivered to
            // anyone online. A push failure is not a send failure.
            log.warn("Failed to send push notification to subscription {}", subscription.getId(), e);
        }
    }

    private String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= MAX_BODY_CHARS ? body : body.substring(0, MAX_BODY_CHARS) + "…";
    }

    private record Payload(String chatId, String title, String body) {
    }
}
