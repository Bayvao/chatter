package com.chatter.chatter.cucumber;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static com.chatter.chatter.cucumber.AuthSteps.authHeaders;
import static org.assertj.core.api.Assertions.assertThat;

public class ProfileSteps {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private TestContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @When("{string} updates their profile with bio {string} and location {string}")
    public void updatesBioAndLocation(String username, String bio, String location) {
        patchProfile(username, """
                {"bio":"%s","location":"%s"}
                """.formatted(bio, location));
    }

    @When("{string} updates their profile with bio {string}")
    public void updatesBio(String username, String bio) {
        patchProfile(username, """
                {"bio":"%s"}
                """.formatted(bio));
    }

    @When("{string} updates their display name to {string} {string}")
    public void updatesDisplayName(String username, String firstName, String lastName) {
        patchProfile(username, """
                {"firstName":"%s","lastName":"%s"}
                """.formatted(firstName, lastName));
    }

    @Then("{string} profile bio should be {string}")
    public void profileBioShouldBe(String username, String expected) throws Exception {
        assertThat(fetchProfile(username).path("bio").asText()).isEqualTo(expected);
    }

    @Then("{string} profile location should be {string}")
    public void profileLocationShouldBe(String username, String expected) throws Exception {
        assertThat(fetchProfile(username).path("location").asText()).isEqualTo(expected);
    }

    @Then("the chat history for {string} should show sender name {string}")
    public void historyShouldShowSenderName(String username, String expectedName) throws Exception {
        var response = rest.exchange("/api/chats/" + context.getCurrentChatId() + "/messages", HttpMethod.GET,
                new HttpEntity<>(authHeaders(context.tokenFor(username))), String.class);

        JsonNode history = objectMapper.readTree(response.getBody());
        assertThat(history).isNotEmpty();
        assertThat(history.findValuesAsText("senderName")).contains(expectedName);
    }

    private void patchProfile(String username, String payload) {
        context.setLastResponse(rest.exchange("/api/users/me/profile", HttpMethod.PUT,
                new HttpEntity<>(payload, authHeaders(context.tokenFor(username))), String.class));
    }

    private JsonNode fetchProfile(String username) throws Exception {
        var response = rest.exchange("/api/users/me/profile", HttpMethod.GET,
                new HttpEntity<>(authHeaders(context.tokenFor(username))), String.class);
        return objectMapper.readTree(response.getBody());
    }
}
