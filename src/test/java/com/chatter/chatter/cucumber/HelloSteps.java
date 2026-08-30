package com.chatter.chatter.cucumber;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

public class HelloSteps {

    @Autowired
    private TestRestTemplate restTemplate;

    private ResponseEntity<String> response;

    @When("I send a GET request to {string}")
    public void iSendAGetRequestTo(String path) {
        response = restTemplate.getForEntity(path, String.class);
    }

    @Then("the response status should be {int}")
    public void theResponseStatusShouldBe(int expectedStatus) {
        assertThat(response.getStatusCode().value()).isEqualTo(expectedStatus);
    }

    @And("the response body should be {string}")
    public void theResponseBodyShouldBe(String expectedBody) {
        assertThat(response.getBody()).isEqualTo(expectedBody);
    }
}
