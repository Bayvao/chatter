Feature: Live relay of contact changes
  As a signed-in user with the app open
  I want friend requests to appear without reloading
  So that I can respond to someone while they are still there

  Background:
    Given "alice" is a registered user
    And "bob" is a registered user

  Scenario: An incoming request arrives over the socket
    Given "bob" is listening for contact events
    When "alice" sends a contact request to "bob"
    Then "bob" should receive a "REQUESTED" contact event about "alice"

  Scenario: The sender is told when their request is accepted
    Given "alice" sends a contact request to "bob"
    And "alice" is listening for contact events
    When "bob" accepts the contact request from "alice"
    Then "alice" should receive an "ACCEPTED" contact event about "bob"

  Scenario: Declining is relayed to the sender
    Given "alice" sends a contact request to "bob"
    And "alice" is listening for contact events
    When "bob" declines the contact request from "alice"
    Then "alice" should receive a "DECLINED" contact event about "bob"

  Scenario: Ending a friendship is relayed to the other party
    Given "alice" and "bob" are contacts
    And "bob" is listening for contact events
    When "alice" removes "bob" from contacts
    Then "bob" should receive a "REMOVED" contact event about "alice"

  Scenario: A blocked user is never told they were blocked
    Given "alice" and "bob" are contacts
    And "bob" is listening for contact events
    When "alice" blocks "bob"
    Then "bob" should receive no contact event
