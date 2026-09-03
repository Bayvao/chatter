Feature: User profiles
  As a signed-in user
  I want to maintain a profile
  So that other people know who they are talking to

  Background:
    Given "alice" is a registered user

  Scenario: Reading an empty profile
    When "alice" requests "/api/users/me/profile"
    Then the response status should be 200

  Scenario: Updating profile fields
    When "alice" updates their profile with bio "Building Chatter" and location "Berlin"
    Then the response status should be 200
    And "alice" profile bio should be "Building Chatter"
    And "alice" profile location should be "Berlin"

  Scenario: A partial update leaves other fields untouched
    Given "alice" updates their profile with bio "First bio" and location "Berlin"
    When "alice" updates their profile with bio "Second bio"
    Then "alice" profile bio should be "Second bio"
    And "alice" profile location should be "Berlin"

  Scenario: Updating a display name refreshes the sender snapshot on past messages
    Given "bob" is a registered user
    And "alice" and "bob" are contacts
    And "alice" has opened a chat with "bob"
    And "alice" sends "before the rename" to the chat
    When "alice" updates their display name to "Alice" "Anderson"
    Then the chat history for "bob" should show sender name "Alice Anderson"

  Scenario: Profile updates require authentication
    When an unauthenticated request is made to "/api/users/me/profile"
    Then the response status should be 401
