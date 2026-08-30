package com.chatter.chatter.cucumber;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
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
import io.cucumber.java.en.When;

import com.chatter.chatter.chat.dto.MessageDTO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

public class WebSocketSteps {

    private static final long TIMEOUT_SECONDS = 5;

    @LocalServerPort
    private int port;

    @Autowired
    private TestContext context;

    @Autowired
    private ObjectMapper objectMapper;

    private final List<StompSession> openSessions = new ArrayList<>();
    private final BlockingQueue<MessageDTO> received = new LinkedBlockingQueue<>();
    private Throwable connectionFailure;
    private Throwable subscriptionFailure;

    @After
    public void closeSessions() {
        openSessions.forEach(session -> {
            if (session.isConnected()) {
                session.disconnect();
            }
        });
        openSessions.clear();
    }

    @Given("{string} is subscribed to the chat over WebSocket")
    public void isSubscribed(String username) throws Exception {
        StompSession session = connect(context.tokenFor(username));
        subscribeToCurrentChat(session);
    }

    @When("{string} sends {string} over WebSocket")
    public void sendsOverWebSocket(String username, String content) throws Exception {
        StompSession session = openSessions.isEmpty() ? connect(context.tokenFor(username)) : openSessions.get(0);

        session.send("/app/chat.send", new SendPayload(context.getCurrentChatId(), content, null));
    }

    @Then("{string} should receive {string} over WebSocket")
    public void shouldReceive(String username, String expectedContent) throws Exception {
        MessageDTO message = received.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThat(message).as("no message arrived over WebSocket within %ds", TIMEOUT_SECONDS).isNotNull();
        assertThat(message.content()).isEqualTo(expectedContent);
        context.setReceivedMessage(message);
    }

    @When("a WebSocket connection is attempted without a token")
    public void connectWithoutToken() {
        connectionFailure = catchThrowable(() -> connect(null));
    }

    @Then("the WebSocket connection should be refused")
    public void connectionShouldBeRefused() {
        assertThat(connectionFailure).isNotNull();
    }

    @When("{string} tries to subscribe to the chat over WebSocket")
    public void triesToSubscribe(String username) throws Exception {
        StompSession session = connect(context.tokenFor(username));

        // The broker rejects the SUBSCRIBE asynchronously by closing the
        // session, so assert on that rather than on a thrown exception.
        subscriptionFailure = catchThrowable(() -> subscribeToCurrentChat(session));
        if (subscriptionFailure == null) {
            for (int attempt = 0; attempt < 20 && session.isConnected(); attempt++) {
                Thread.sleep(100);
            }
            assertThat(session.isConnected()).as("session should have been closed after a refused SUBSCRIBE").isFalse();
        }
    }

    @Then("the WebSocket subscription should be refused")
    public void subscriptionShouldBeRefused() {
        assertThat(received).as("no message should have been delivered to an unauthorized subscriber").isEmpty();
    }

    private StompSession connect(String token) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        client.setMessageConverter(converter);

        StompHeaders connectHeaders = new StompHeaders();
        if (token != null) {
            connectHeaders.add("Authorization", "Bearer " + token);
        }

        try {
            StompSession session = client
                    .connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connectHeaders,
                            new StompSessionHandlerAdapter() {
                            })
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            openSessions.add(session);
            return session;
        } catch (ExecutionException e) {
            throw e.getCause() instanceof Exception cause ? cause : e;
        }
    }

    private void subscribeToCurrentChat(StompSession session) {
        session.subscribe("/topic/chats/" + context.getCurrentChatId(), new StompFrameHandler() {

            @Override
            public Type getPayloadType(StompHeaders headers) {
                return MessageDTO.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((MessageDTO) payload);
            }
        });
    }

    /** Mirrors SendMessageRequest without depending on its validation annotations. */
    private record SendPayload(UUID chatId, String content, UUID clientMsgId) {
    }
}
