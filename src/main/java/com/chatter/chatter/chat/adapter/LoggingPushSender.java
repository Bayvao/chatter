package com.chatter.chatter.chat.adapter;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.chatter.chatter.chat.port.PushSender;

/**
 * The default {@link PushSender}. Active whenever VAPID keys are not
 * configured, so the application starts and the test suite runs without them.
 */
@Component
@ConditionalOnProperty(name = "app.push.vapid.enabled", havingValue = "false", matchIfMissing = true)
public class LoggingPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingPushSender.class);

    @Override
    public void sendMessageNotification(UUID recipientId, Notification notification) {
        log.debug("Push disabled; would notify {} about chat {}", recipientId, notification.chatId());
    }
}
