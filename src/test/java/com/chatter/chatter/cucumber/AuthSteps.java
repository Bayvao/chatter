package com.chatter.chatter.cucumber;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.assertj.core.api.Assertions.assertThat;

public class AuthSteps {

    static final String DEFAULT_PASSWORD = "password123";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private TestContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @Given("{string} is a registered user")
    public void userIsRegistered(String username) throws Exception {
        if (context.knowsUser(username)) {
            return;
        }
        register(username, username + "@example.com", DEFAULT_PASSWORD);

        JsonNode body = objectMapper.readTree(context.getLastResponse().getBody());
        context.rememberUser(username,
                UUID.fromString(body.path("user").path("id").asText()),
                body.path("token").asText());
    }

    @When("{string} registers with email {string} and password {string}")
    public void userRegisters(String username, String email, String password) throws Exception {
        register(username, email, password);

        ResponseEntity<String> response = context.getLastResponse();
        if (response.getStatusCode().is2xxSuccessful()) {
            JsonNode body = objectMapper.readTree(response.getBody());
            context.rememberUser(username,
                    UUID.fromString(body.path("user").path("id").asText()),
                    body.path("token").asText());
        }
    }

    @When("{string} logs in with password {string}")
    public void userLogsIn(String username, String password) {
        String payload = """
                {"username":"%s","password":"%s"}
                """.formatted(username, password);

        context.setLastResponse(rest.exchange("/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(payload, jsonHeaders()), String.class));
    }

    @When("an unauthenticated request is made to {string}")
    public void unauthenticatedRequest(String path) {
        context.setLastResponse(rest.getForEntity(path, String.class));
    }

    @When("{string} requests {string}")
    public void authenticatedRequest(String username, String path) {
        context.setLastResponse(rest.exchange(path, HttpMethod.GET,
                new HttpEntity<>(authHeaders(context.tokenFor(username))), String.class));
    }

    @Then("the response status should be {int}")
    public void responseStatusShouldBe(int expected) {
        assertThat(context.getLastResponse().getStatusCode().value()).isEqualTo(expected);
    }

    @Then("the response should contain a token")
    public void responseShouldContainToken() throws Exception {
        JsonNode body = objectMapper.readTree(context.getLastResponse().getBody());
        assertThat(body.path("token").asText()).isNotBlank();
    }

    @Then("the registered username should be {string}")
    public void registeredUsernameShouldBe(String expected) throws Exception {
        JsonNode body = objectMapper.readTree(context.getLastResponse().getBody());
        // Register/login nest the user; /api/auth/me returns it at the root.
        JsonNode user = body.has("user") ? body.path("user") : body;
        assertThat(user.path("username").asText()).isEqualTo(expected);
    }

    private void register(String username, String email, String password) {
        String payload = """
                {"username":"%s","email":"%s","password":"%s"}
                """.formatted(username, email, password);

        context.setLastResponse(rest.exchange("/api/auth/register", HttpMethod.POST,
                new HttpEntity<>(payload, jsonHeaders()), String.class));
    }

    static HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    static HttpHeaders authHeaders(String token) {
        HttpHeaders headers = jsonHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
