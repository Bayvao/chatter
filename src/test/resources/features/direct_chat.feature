Feature: One-to-one chat
  As a signed-in user
  I want to open a chat with another user and exchange messages
  So that we can talk to each other

  Background:
    Given "alice" is a registered user
    And "bob" is a registered user
    And "alice" and "bob" are contacts

  Scenario: Opening a chat with another user
    When "alice" opens a chat with "bob"
    Then the response status should be 201
    And "alice" should see 1 chat

  Scenario: Opening the same chat twice reuses it
    When "alice" opens a chat with "bob"
    And "alice" opens a chat with "bob"
    Then "alice" should see 1 chat

  Scenario: A chat opened by one user is visible to the other
    When "alice" opens a chat with "bob"
    Then "bob" should see 1 chat

  Scenario: Sending a message and reading it back
    Given "alice" has opened a chat with "bob"
    When "alice" sends "Hello Bob" to the chat
    Then the response status should be 201
    And the chat history for "bob" should contain "Hello Bob"

  Scenario: Messages are numbered sequentially within a chat
    Given "alice" has opened a chat with "bob"
    When "alice" sends "first" to the chat
    And "bob" sends "second" to the chat
    And "alice" sends "third" to the chat
    Then the chat history for "alice" should have sequence numbers 1, 2, 3

  Scenario: A resent message with the same client id is stored once
    Given "alice" has opened a chat with "bob"
    When "alice" sends "only once" to the chat with client id "11111111-1111-1111-1111-111111111111"
    And "alice" sends "only once" to the chat with client id "11111111-1111-1111-1111-111111111111"
    Then the chat history for "alice" should contain 1 message

  Scenario: An outsider cannot read a chat they are not part of
    Given "carol" is a registered user
    And "alice" has opened a chat with "bob"
    When "carol" requests the chat history
    Then the response status should be 403

  Scenario: An outsider cannot post to a chat they are not part of
    Given "carol" is a registered user
    And "alice" has opened a chat with "bob"
    When "carol" sends "let me in" to the chat
    Then the response status should be 403

  Scenario: Unread counts track what the reader has seen
    Given "alice" has opened a chat with "bob"
    When "alice" sends "unread one" to the chat
    And "alice" sends "unread two" to the chat
    Then "bob" should have 2 unread messages in the chat
    And "alice" should have 0 unread messages in the chat
