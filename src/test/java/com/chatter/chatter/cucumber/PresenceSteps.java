package com.chatter.chatter.cucumber;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.cucumber.java.en.Then;

import static com.chatter.chatter.cucumber.AuthSteps.authHeaders;
import static org.assertj.core.api.Assertions.assertThat;

public class PresenceSteps {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private TestContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @Then("{string} should be online")
    public void shouldBeOnline(String username) throws Exception {
        assertThat(awaitOnline(username, true))
                .as("%s should be reported online", username)
                .isTrue();
    }

    @Then("{string} should be offline")
    public void shouldBeOffline(String username) throws Exception {
        assertThat(awaitOnline(username, false))
                .as("%s should be reported offline", username)
                .isFalse();
    }

    @Then("{string} should have a last seen timestamp")
    public void shouldHaveLastSeen(String username) throws Exception {
        assertThat(presenceOf(username).path("lastSeen").isNull()).isFalse();
    }

    /**
     * Presence is updated by a STOMP lifecycle event, which lands slightly
     * after the frame that triggered it, so poll rather than read once.
     */
    private boolean awaitOnline(String username, boolean expected) throws Exception {
        boolean online = false;
        for (int attempt = 0; attempt < 20; attempt++) {
            online = presenceOf(username).path("online").asBoolean();
            if (online == expected) {
                return online;
            }
            Thread.sleep(100);
        }
        return online;
    }

    private JsonNode presenceOf(String username) throws Exception {
        // Any authenticated user may read presence; use the subject's own token.
        var response = rest.exchange("/api/presence/" + context.userIdFor(username), HttpMethod.GET,
                new HttpEntity<>(authHeaders(context.tokenFor(username))), String.class);
        return objectMapper.readTree(response.getBody());
    }
}
