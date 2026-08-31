package com.chatter.chatter.cucumber;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.cucumber.java.After;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static com.chatter.chatter.cucumber.AuthSteps.authHeaders;
import static org.assertj.core.api.Assertions.assertThat;

public class SyncSteps {

    private static final long TIMEOUT_SECONDS = 5;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private TestContext context;

    @Autowired
    private ObjectMapper objectMapper;

    private final List<StompSession> sessions = new ArrayList<>();
    private final List<Map<String, Object>> batches = new CopyOnWriteArrayList<>();
    private volatile Integer syncedCount;

    @After
    public void closeSessions() {
        sessions.forEach(session -> {
            if (session.isConnected()) {
                session.disconnect();
            }
        });
        sessions.clear();
    }

    @When("{string} syncs the chat from sequence {int}")
    @SuppressWarnings("unchecked")
    public void syncsFromSequence(String username, int afterSeq) throws Exception {
        StompSession session = connect(context.tokenFor(username));

        session.subscribe("/user/queue/sync-batch", handler(payload -> batches.add((Map<String, Object>) payload)));
        session.subscribe("/user/queue/sync-complete", handler(payload ->
                syncedCount = ((Number) ((Map<String, Object>) payload).get("messageCount")).intValue()));

        session.send("/app/sync.start",
                Map.of("cursors", Map.of(context.getCurrentChatId().toString(), afterSeq)));

        // Wait for the completion frame, which arrives after every batch.
        for (int attempt = 0; attempt < 50 && syncedCount == null; attempt++) {
            Thread.sleep(100);
        }
    }

    @Then("{string} should receive {int} message(s) in the sync batch")
    public void shouldReceiveInSyncBatch(String username, int expected) {
        assertThat(collected()).as("messages delivered by sync").hasSize(expected);
    }

    @Then("the sync batch should contain {string} and {string} in order")
    public void syncBatchInOrder(String first, String second) {
        assertThat(collected().stream().map(node -> node.path("content").asText()).toList())
                .containsExactly(first, second);
    }

    @Then("the newest message in the chat should have status {string}")
    public void newestMessageStatus(String expectedStatus) throws Exception {
        assertThat(newestStatus()).isEqualTo(expectedStatus);
    }

    @Then("the newest message in the chat should eventually have status {string}")
    public void newestMessageEventualStatus(String expectedStatus) throws Exception {
        // The broadcaster marks DELIVERED in its own transaction after commit,
        // so this trails the send response.
        String status = null;
        for (int attempt = 0; attempt < 30; attempt++) {
            status = newestStatus();
            if (expectedStatus.equals(status)) {
                return;
            }
            Thread.sleep(100);
        }
        assertThat(status).isEqualTo(expectedStatus);
    }

    private String newestStatus() throws Exception {
        var response = rest.exchange("/api/chats/" + context.getCurrentChatId() + "/messages", HttpMethod.GET,
                new HttpEntity<>(authHeaders(context.tokenFor("alice"))), String.class);

        JsonNode history = objectMapper.readTree(response.getBody());
        assertThat(history).as("chat history").isNotEmpty();
        return history.get(0).path("status").asText();   // newest first
    }

    private List<JsonNode> collected() {
        return batches.stream()
                .map(batch -> objectMapper.valueToTree(batch.get("messages")))
                .flatMap(node -> {
                    List<JsonNode> messages = new ArrayList<>();
                    ((JsonNode) node).forEach(messages::add);
                    return messages.stream();
                })
                .toList();
    }

    private StompFrameHandler handler(java.util.function.Consumer<Object> consumer) {
        return new StompFrameHandler() {

            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                consumer.accept(payload);
            }
        };
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
