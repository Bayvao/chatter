Feature: Leaving a chat
  As a signed-in user
  I want to remove a conversation
  So that it stops appearing and stops reaching me

  Background:
    Given "alice" is a registered user
    And "bob" is a registered user
    And "alice" and "bob" are contacts
    And "alice" has opened a chat with "bob"

  Scenario: A left chat disappears from your list
    When "alice" leaves the chat
    Then the response status should be 204
    And "alice" should see 0 chats
    And "bob" should see 1 chat

  Scenario: You cannot send to a chat you left
    Given "alice" leaves the chat
    When "alice" sends "still here?" to the chat
    Then the response status should be 403

  Scenario: You cannot read a chat you left
    Given "alice" leaves the chat
    When "alice" requests the chat history
    Then the response status should be 403

  Scenario: Messages sent after you leave do not reach you
    Given "alice" leaves the chat
    When "bob" sends "are you there" to the chat
    Then "alice" should see 0 chats

  Scenario: Reopening rejoins the same chat rather than creating a second
    Given "alice" sends "before leaving" to the chat
    And "alice" leaves the chat
    When "alice" opens a chat with "bob"
    Then "alice" should see 1 chat
    And the chat history for "alice" should contain "before leaving"

  Scenario: Leaving a chat you are not in is refused
    Given "carol" is a registered user
    When "carol" leaves the chat
    Then the response status should be 403
