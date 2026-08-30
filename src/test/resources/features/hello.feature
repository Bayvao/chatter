Feature: Hello endpoint
  As a client of the Chatter API
  I want to check that the service is running
  So that I know it is available

  Scenario: Requesting the root endpoint
    When I send a GET request to "/"
    Then the response status should be 200
    And the response body should be "Chatter is running"
