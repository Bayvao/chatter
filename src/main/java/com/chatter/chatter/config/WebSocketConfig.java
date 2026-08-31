package com.chatter.chatter.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Wires the STOMP-over-WebSocket stack: the endpoint clients connect to, the
 * destinations they may use, and the interceptor that authenticates them.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor authChannelInterceptor;
    private final String[] allowedOrigins;

    public WebSocketConfig(StompAuthChannelInterceptor authChannelInterceptor,
                            @Value("${app.cors.allowed-origins}") String[] allowedOrigins) {
        this.authChannelInterceptor = authChannelInterceptor;
        this.allowedOrigins = allowedOrigins;
    }

    /**
     * Publishes {@code /ws} as the connection point, twice.
     *
     * <p>Called once by Spring at startup. The plain endpoint serves modern
     * browsers and the Java test client; the SockJS variant covers clients
     * behind proxies that break raw WebSocket upgrades.
     *
     * <p>Origins are restricted here separately from HTTP CORS — a WebSocket
     * handshake is not subject to the CORS rules in {@code SecurityConfig}.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Plain WebSocket for the test client and modern browsers...
        registry.addEndpoint("/ws").setAllowedOrigins(allowedOrigins);
        // ...plus a SockJS-enabled variant for fallback transports.
        registry.addEndpoint("/ws").setAllowedOrigins(allowedOrigins).withSockJS();
    }

    /**
     * Declares the destination prefixes the application uses.
     *
     * <p>Called once by Spring at startup. {@code /topic} carries chat and
     * presence broadcasts, {@code /queue} the per-user sync replies,
     * {@code /app} routes to {@code @MessageMapping} methods, and {@code /user}
     * is what makes {@code convertAndSendToUser} resolve to one session.
     *
     * <p>The in-memory broker holds subscriptions in this JVM only. It is
     * correct for a single instance; running more than one means replacing it
     * with a relay to RabbitMQ or ActiveMQ, or two users on different instances
     * will not see each other's messages.
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // In-memory broker is right for Phase 1; a relay to RabbitMQ/ActiveMQ
        // replaces it once more than one instance runs.
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    /**
     * Puts {@link StompAuthChannelInterceptor} in front of every inbound frame.
     *
     * <p>Called once by Spring at startup. This registration is the entire
     * reason WebSocket traffic is authenticated at all — the HTTP filter chain
     * never sees these frames.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authChannelInterceptor);
    }
}
