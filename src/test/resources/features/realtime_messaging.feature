Feature: Real-time message delivery
  As a user with the app open
  I want messages to arrive without refreshing
  So that a conversation feels live

  Background:
    Given "alice" is a registered user
    And "bob" is a registered user
    And "alice" and "bob" are contacts
    And "alice" has opened a chat with "bob"

  Scenario: A subscriber receives a message pushed over WebSocket
    Given "bob" is subscribed to the chat over WebSocket
    When "alice" sends "Real-time hello" to the chat
    Then "bob" should receive "Real-time hello" over WebSocket

  Scenario: Messages sent over WebSocket are persisted
    Given "alice" is subscribed to the chat over WebSocket
    When "alice" sends "Sent over the socket" over WebSocket
    Then "alice" should receive "Sent over the socket" over WebSocket
    And the chat history for "bob" should contain "Sent over the socket"

  Scenario: Connecting without a token is refused
    When a WebSocket connection is attempted without a token
    Then the WebSocket connection should be refused

  Scenario: Subscribing to someone else's chat is refused
    Given "carol" is a registered user
    When "carol" tries to subscribe to the chat over WebSocket
    Then the WebSocket subscription should be refused
