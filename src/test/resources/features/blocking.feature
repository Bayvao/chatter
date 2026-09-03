Feature: Blocking
  As a signed-in user
  I want blocking someone to stop all contact
  So that they cannot keep reaching me until I lift it

  Background:
    Given "alice" is a registered user
    And "bob" is a registered user

  Scenario: The blocker cannot send in an existing conversation
    Given "alice" and "bob" are contacts
    And "alice" has opened a chat with "bob"
    When "alice" blocks "bob"
    And "alice" sends "still talking" to the chat
    Then the response status should be 403

  Scenario: The blocked user cannot send either
    Given "alice" and "bob" are contacts
    And "alice" has opened a chat with "bob"
    When "alice" blocks "bob"
    And "bob" sends "let me in" to the chat
    Then the response status should be 403

  Scenario: A blocked user cannot open a new chat
    Given "alice" and "bob" are contacts
    And "alice" blocks "bob"
    When "bob" opens a chat with "alice"
    Then the response status should be 403

  Scenario: A blocked user cannot send a friend request
    Given "alice" and "bob" are contacts
    And "alice" blocks "bob"
    And "alice" removes "bob" from contacts
    When "bob" sends a contact request to "alice"
    Then the response status should be 403

  Scenario: You cannot request someone you blocked yourself
    Given "alice" and "bob" are contacts
    And "alice" blocks "bob"
    And "alice" removes "bob" from contacts
    When "alice" sends a contact request to "bob"
    Then the response status should be 403

  Scenario: A block survives removing the contact
    Given "alice" and "bob" are contacts
    And "alice" blocks "bob"
    When "alice" removes "bob" from contacts
    And "bob" sends a contact request to "alice"
    Then the response status should be 403

  Scenario: A stranger can be blocked
    When "alice" blocks "bob"
    Then the response status should be 204

  Scenario: Blocking yourself is rejected
    When "alice" blocks "alice"
    Then the response status should be 400

  Scenario: Unblocking restores messaging
    Given "alice" and "bob" are contacts
    And "alice" has opened a chat with "bob"
    And "alice" blocks "bob"
    When "alice" unblocks "bob"
    And "alice" sends "we are back" to the chat
    Then the response status should be 201

  Scenario: Unblocking restores the contact list
    Given "alice" and "bob" are contacts
    And "alice" blocks "bob"
    And "alice" unblocks "bob"
    Then "alice" should have 1 contact

  Scenario: A blocked user is hidden from both sides of search
    Given "alice" and "bob" are contacts
    When "alice" blocks "bob"
    Then "bob" searches for "alice"
    And the search results should be empty

  Scenario: History stays readable after a block
    Given "alice" and "bob" are contacts
    And "alice" has opened a chat with "bob"
    And "alice" sends "before the block" to the chat
    When "alice" blocks "bob"
    Then the chat history for "bob" should contain "before the block"
