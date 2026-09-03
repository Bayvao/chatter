Feature: Presence
  As a user
  I want to see who is online
  So that I know whether to expect a quick reply

  Background:
    Given "alice" is a registered user
    And "bob" is a registered user
    And "alice" and "bob" are contacts

  Scenario: A user is offline before connecting
    Then "bob" should be offline

  Scenario: Connecting over WebSocket marks a user online
    Given "alice" has opened a chat with "bob"
    When "bob" is subscribed to the chat over WebSocket
    Then "bob" should be online

  Scenario: Disconnecting marks a user offline and records last seen
    Given "alice" has opened a chat with "bob"
    And "bob" is subscribed to the chat over WebSocket
    When "bob" disconnects from WebSocket
    Then "bob" should be offline
    And "bob" should have a last seen timestamp
