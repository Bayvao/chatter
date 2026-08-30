Feature: User registration and login
  As a new user of Chatter
  I want to create an account and sign in
  So that I can start chatting

  Scenario: Registering a new account
    When "alice" registers with email "alice@example.com" and password "password123"
    Then the response status should be 201
    And the response should contain a token
    And the registered username should be "alice"

  Scenario: Usernames must be unique
    Given "alice" is a registered user
    When "alice" registers with email "other@example.com" and password "password123"
    Then the response status should be 409

  Scenario: Logging in with correct credentials
    Given "alice" is a registered user
    When "alice" logs in with password "password123"
    Then the response status should be 200
    And the response should contain a token

  Scenario: Logging in with the wrong password
    Given "alice" is a registered user
    When "alice" logs in with password "wrong-password"
    Then the response status should be 401

  Scenario: Rejecting a password that is too short
    When "bob" registers with email "bob@example.com" and password "short"
    Then the response status should be 400

  Scenario: Reading your own profile requires a token
    When an unauthenticated request is made to "/api/auth/me"
    Then the response status should be 401

  Scenario: Reading your own profile with a token
    Given "alice" is a registered user
    When "alice" requests "/api/auth/me"
    Then the response status should be 200
    And the registered username should be "alice"
