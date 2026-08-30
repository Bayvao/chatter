package com.chatter.chatter.cucumber;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static com.chatter.chatter.cucumber.AuthSteps.authHeaders;
import static org.assertj.core.api.Assertions.assertThat;

public class ChatSteps {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private TestContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @When("{string} opens a chat with {string}")
    public void opensChatWith(String username, String otherUsername) throws Exception {
        var response = rest.exchange("/api/chats/with/" + context.userIdFor(otherUsername), HttpMethod.POST,
                new HttpEntity<>(authHeaders(context.tokenFor(username))), String.class);
        context.setLastResponse(response);

        if (response.getStatusCode().is2xxSuccessful()) {
            context.setCurrentChatId(UUID.fromString(objectMapper.readTree(response.getBody()).path("id").asText()));
        }
    }

    @Given("{string} has opened a chat with {string}")
    public void hasOpenedChatWith(String username, String otherUsername) throws Exception {
        opensChatWith(username, otherUsername);
        assertThat(context.getCurrentChatId()).isNotNull();
    }

    @Then("{string} should see {int} chat(s)")
    public void shouldSeeChats(String username, int expectedCount) throws Exception {
        var response = rest.exchange("/api/chats", HttpMethod.GET,
                new HttpEntity<>(authHeaders(context.tokenFor(username))), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(objectMapper.readTree(response.getBody())).hasSize(expectedCount);
    }

    @When("{string} sends {string} to the chat")
    public void sendsMessage(String username, String content) {
        sendMessage(username, content, null);
    }

    @When("{string} sends {string} to the chat with client id {string}")
    public void sendsMessageWithClientId(String username, String content, String clientMsgId) {
        sendMessage(username, content, UUID.fromString(clientMsgId));
    }

    @When("{string} requests the chat history")
    public void requestsChatHistory(String username) {
        context.setLastResponse(fetchHistory(username));
    }

    @Then("the chat history for {string} should contain {string}")
    public void historyShouldContain(String username, String expectedContent) throws Exception {
        JsonNode history = objectMapper.readTree(fetchHistory(username).getBody());

        List<String> contents = history.findValuesAsText("content");
        assertThat(contents).contains(expectedContent);
    }

    @Then("the chat history for {string} should contain {int} message(s)")
    public void historyShouldHaveSize(String username, int expectedCount) throws Exception {
        JsonNode history = objectMapper.readTree(fetchHistory(username).getBody());
        assertThat(history).hasSize(expectedCount);
    }

    @Then("the chat history for {string} should have sequence numbers {int}, {int}, {int}")
    public void historyShouldHaveSequenceNumbers(String username, int first, int second, int third) throws Exception {
        JsonNode history = objectMapper.readTree(fetchHistory(username).getBody());

        // History comes back newest-first; compare against ascending order.
        List<Long> seqs = history.findValues("seq").stream().map(JsonNode::asLong).sorted().toList();
        assertThat(seqs).containsExactly((long) first, (long) second, (long) third);
    }

    @Then("{string} should have {int} unread message(s) in the chat")
    public void shouldHaveUnreadCount(String username, int expectedUnread) throws Exception {
        var response = rest.exchange("/api/chats", HttpMethod.GET,
                new HttpEntity<>(authHeaders(context.tokenFor(username))), String.class);

        JsonNode chats = objectMapper.readTree(response.getBody());
        JsonNode chat = findChat(chats, context.getCurrentChatId());

        assertThat(chat).as("chat %s in %s's list", context.getCurrentChatId(), username).isNotNull();
        assertThat(chat.path("unreadCount").asLong()).isEqualTo(expectedUnread);
    }

    private void sendMessage(String username, String content, UUID clientMsgId) {
        String payload = clientMsgId == null
                ? """
                  {"chatId":"%s","content":"%s"}
                  """.formatted(context.getCurrentChatId(), content)
                : """
                  {"chatId":"%s","content":"%s","clientMsgId":"%s"}
                  """.formatted(context.getCurrentChatId(), content, clientMsgId);

        context.setLastResponse(rest.exchange("/api/chats/messages", HttpMethod.POST,
                new HttpEntity<>(payload, authHeaders(context.tokenFor(username))), String.class));
    }

    private org.springframework.http.ResponseEntity<String> fetchHistory(String username) {
        return rest.exchange("/api/chats/" + context.getCurrentChatId() + "/messages", HttpMethod.GET,
                new HttpEntity<>(authHeaders(context.tokenFor(username))), String.class);
    }

    private JsonNode findChat(JsonNode chats, UUID chatId) {
        for (JsonNode chat : chats) {
            if (chat.path("id").asText().equals(chatId.toString())) {
                return chat;
            }
        }
        return null;
    }
}
