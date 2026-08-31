package com.chatter.chatter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Application entry point.
 *
 * <p>{@code @EnableAsync} is what makes {@code @Async} actually dispatch to
 * another thread — without it the annotation is silently inert. {@code
 * WebPushSender.sendMessageNotification} relies on it: it is called from
 * {@code MessageBroadcaster} once per offline recipient, and running those
 * sends inline would put an unreachable push service on the path of every
 * broadcast.
 */
@SpringBootApplication
@EnableAsync
public class ChatterApplication {

    /**
     * Boots the Spring context.
     *
     * @param args standard Spring Boot arguments; any of them may override a
     *        property, e.g. {@code --server.port=8081}
     */
    public static void main(String[] args) {
        SpringApplication.run(ChatterApplication.class, args);
    }

}
