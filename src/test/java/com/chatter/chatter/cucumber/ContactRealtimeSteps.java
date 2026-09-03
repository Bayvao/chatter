package com.chatter.chatter.cucumber;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;

import static org.assertj.core.api.Assertions.assertThat;

public class ContactRealtimeSteps {

    private static final long TIMEOUT_SECONDS = 5;
    /** Long enough that a relayed event would have landed, short enough to stay quick. */
    private static final long SILENCE_MILLIS = 1500;

    @LocalServerPort
    private int port;

    @Autowired
    private TestContext context;

    @Autowired
    private ObjectMapper objectMapper;

    private final List<StompSession> sessions = new ArrayList<>();
    private final BlockingQueue<Map<String, Object>> events = new LinkedBlockingQueue<>();

    @After
    public void closeSessions() {
        sessions.forEach(session -> {
            if (session.isConnected()) {
                session.disconnect();
            }
        });
        sessions.clear();
        events.clear();
    }

    @Given("{string} is listening for contact events")
    @SuppressWarnings("unchecked")
    public void isListening(String username) throws Exception {
        StompSession session = connect(context.tokenFor(username));

        // Spring rewrites /user/** to this session's own queue, so no
        // subscription check is needed the way chat topics need one.
        session.subscribe("/user/queue/contacts", new StompFrameHandler() {

            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                events.add((Map<String, Object>) payload);
            }
        });
    }

    @Then("{string} should receive a(n) {string} contact event about {string}")
    public void shouldReceiveEvent(String username, String expectedType, String aboutUsername) throws Exception {
        Map<String, Object> event = events.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThat(event).as("no contact event arrived within %ds", TIMEOUT_SECONDS).isNotNull();
        assertThat(event.get("type")).isEqualTo(expectedType);
        assertThat(((Map<?, ?>) event.get("user")).get("username")).isEqualTo(aboutUsername);
    }

    @Then("{string} should receive no contact event")
    public void shouldReceiveNothing(String username) throws Exception {
        // Blocking is deliberately silent: telling someone they were blocked
        // hands a harasser the signal that their target acted.
        assertThat(events.poll(SILENCE_MILLIS, TimeUnit.MILLISECONDS))
                .as("nothing should be relayed to a user who was blocked").isNull();
    }

    private StompSession connect(String token) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        client.setMessageConverter(converter);

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);

        StompSession session = client
                .connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connectHeaders,
                        new StompSessionHandlerAdapter() {
                        })
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        sessions.add(session);
        return session;
    }
}
