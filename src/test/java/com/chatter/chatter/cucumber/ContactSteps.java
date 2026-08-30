package com.chatter.chatter.cucumber;

import java.util.List;

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

public class ContactSteps {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private TestContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @When("{string} adds {string} as a contact")
    public void addsContact(String username, String contactUsername) {
        context.setLastResponse(rest.exchange("/api/users/me/contacts/" + context.userIdFor(contactUsername),
                HttpMethod.POST, new HttpEntity<>(authHeaders(context.tokenFor(username))), String.class));
    }

    @When("{string} removes {string} from contacts")
    public void removesContact(String username, String contactUsername) {
        context.setLastResponse(rest.exchange("/api/users/me/contacts/" + context.userIdFor(contactUsername),
                HttpMethod.DELETE, new HttpEntity<>(authHeaders(context.tokenFor(username))), String.class));
    }

    @When("{string} blocks {string}")
    public void blocksContact(String username, String contactUsername) {
        context.setLastResponse(rest.exchange(
                "/api/users/me/contacts/" + context.userIdFor(contactUsername) + "/block",
                HttpMethod.POST, new HttpEntity<>(authHeaders(context.tokenFor(username))), String.class));
    }

    @When("{string} unblocks {string}")
    public void unblocksContact(String username, String contactUsername) {
        context.setLastResponse(rest.exchange(
                "/api/users/me/contacts/" + context.userIdFor(contactUsername) + "/block",
                HttpMethod.DELETE, new HttpEntity<>(authHeaders(context.tokenFor(username))), String.class));
    }

    @When("{string} searches for {string}")
    public void searchesFor(String username, String query) {
        context.setLastResponse(rest.exchange("/api/users/search?q=" + query, HttpMethod.GET,
                new HttpEntity<>(authHeaders(context.tokenFor(username))), String.class));
    }

    @Then("the search results should be empty")
    public void searchResultsShouldBeEmpty() throws Exception {
        assertThat(objectMapper.readTree(context.getLastResponse().getBody())).isEmpty();
    }

    @Then("{string} should have {int} contact(s)")
    public void shouldHaveContacts(String username, int expectedCount) throws Exception {
        assertThat(fetchContacts(username)).hasSize(expectedCount);
    }

    @Then("the contacts of {string} should include {string}")
    public void contactsShouldInclude(String username, String contactUsername) throws Exception {
        List<String> usernames = fetchContacts(username).findValues("user").stream()
                .map(user -> user.path("username").asText())
                .toList();
        assertThat(usernames).contains(contactUsername);
    }

    private JsonNode fetchContacts(String username) throws Exception {
        var response = rest.exchange("/api/users/me/contacts", HttpMethod.GET,
                new HttpEntity<>(authHeaders(context.tokenFor(username))), String.class);
        return objectMapper.readTree(response.getBody());
    }
}
