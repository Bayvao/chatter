package com.chatter.chatter;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A liveness endpoint at the root path.
 *
 * <p>Left unauthenticated in {@code SecurityConfig} so a load balancer or
 * {@code docker compose} healthcheck can reach it without credentials.
 */
@RestController
public class HelloController {

    /**
     * Answers that the application is up and serving requests.
     *
     * <p>Reaching this proves the context started and the web layer is
     * listening. It deliberately says nothing about the database — a real
     * readiness probe wants {@code /actuator/health} instead.
     */
    @GetMapping("/")
    public String hello() {
        return "Chatter is running";
    }

}
