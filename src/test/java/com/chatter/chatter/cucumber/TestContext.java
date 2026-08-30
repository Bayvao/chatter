package com.chatter.chatter.cucumber;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import io.cucumber.spring.ScenarioScope;

import com.chatter.chatter.chat.dto.MessageDTO;

/**
 * Per-scenario state shared between step definition classes.
 *
 * <p>{@link ScenarioScope} is load-bearing: as a singleton this would keep
 * tokens from earlier scenarios while {@code DatabaseCleanupHook} wipes the
 * users they point at, and every later scenario would authenticate as a
 * user that no longer exists.
 */
@Component
@ScenarioScope
public class TestContext {

    /** username -> issued JWT. */
    private final Map<String, String> tokens = new HashMap<>();
    /** username -> user id. */
    private final Map<String, UUID> userIds = new HashMap<>();

    private ResponseEntity<String> lastResponse;
    private UUID currentChatId;
    private MessageDTO receivedMessage;

    public void rememberUser(String username, UUID id, String token) {
        userIds.put(username, id);
        tokens.put(username, token);
    }

    public String tokenFor(String username) {
        String token = tokens.get(username);
        if (token == null) {
            throw new IllegalStateException("No token for user '" + username + "'; register or log in first");
        }
        return token;
    }

    public UUID userIdFor(String username) {
        UUID id = userIds.get(username);
        if (id == null) {
            throw new IllegalStateException("No id for user '" + username + "'; register first");
        }
        return id;
    }

    public boolean knowsUser(String username) {
        return userIds.containsKey(username);
    }

    public ResponseEntity<String> getLastResponse() {
        return lastResponse;
    }

    public void setLastResponse(ResponseEntity<String> lastResponse) {
        this.lastResponse = lastResponse;
    }

    public UUID getCurrentChatId() {
        return currentChatId;
    }

    public void setCurrentChatId(UUID currentChatId) {
        this.currentChatId = currentChatId;
    }

    public MessageDTO getReceivedMessage() {
        return receivedMessage;
    }

    public void setReceivedMessage(MessageDTO receivedMessage) {
        this.receivedMessage = receivedMessage;
    }
}
