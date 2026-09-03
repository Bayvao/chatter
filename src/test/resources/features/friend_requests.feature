Feature: Friend requests
  As a signed-in user
  I want a request to wait until the other person accepts
  So that a conversation only starts once both of us agreed to it

  Background:
    Given "alice" is a registered user
    And "bob" is a registered user

  Scenario: A request stays pending until it is answered
    When "alice" sends a contact request to "bob"
    Then the response status should be 201
    And "bob" should have 1 incoming contact request
    And "alice" should have 0 contacts
    And "bob" should have 0 contacts

  Scenario: A pending request is not enough to open a chat
    Given "alice" sends a contact request to "bob"
    When "alice" opens a chat with "bob"
    Then the response status should be 403

  Scenario: Accepting makes the friendship mutual
    Given "alice" sends a contact request to "bob"
    When "bob" accepts the contact request from "alice"
    Then the response status should be 204
    And "alice" should have 1 contact
    And "bob" should have 1 contact
    And "bob" should have 0 incoming contact requests

  Scenario: A chat can be opened once the request is accepted
    Given "alice" and "bob" are contacts
    When "alice" opens a chat with "bob"
    Then the response status should be 201

  Scenario: Declining removes the request without creating contacts
    Given "alice" sends a contact request to "bob"
    When "bob" declines the contact request from "alice"
    Then the response status should be 204
    And "bob" should have 0 incoming contact requests
    And "alice" should have 0 contacts

  Scenario: The sender can cancel their own request
    Given "alice" sends a contact request to "bob"
    When "alice" declines the contact request from "bob"
    Then the response status should be 204
    And "bob" should have 0 incoming contact requests

  Scenario: Requesting the same person twice is rejected
    Given "alice" sends a contact request to "bob"
    When "alice" sends a contact request to "bob"
    Then the response status should be 409

  Scenario: Requesting yourself is rejected
    When "alice" sends a contact request to "alice"
    Then the response status should be 400

  Scenario: Requesting someone who already accepted is rejected
    Given "alice" and "bob" are contacts
    When "alice" sends a contact request to "bob"
    Then the response status should be 409

  Scenario: A request from someone you blocked is refused
    Given "alice" and "bob" are contacts
    And "bob" blocks "alice"
    And "alice" removes "bob" from contacts
    When "alice" sends a contact request to "bob"
    Then the response status should be 403

  Scenario: Removing a contact does not clear a block held against you
    Given "alice" and "bob" are contacts
    And "bob" blocks "alice"
    When "alice" removes "bob" from contacts
    Then "alice" should have 0 contacts
    And "bob" should have 0 contacts

  Scenario: Accepting a request that was never sent is rejected
    When "bob" accepts the contact request from "alice"
    Then the response status should be 404

  Scenario: The sender sees their outstanding request
    When "alice" sends a contact request to "bob"
    Then "alice" should have 1 outgoing contact request

  Scenario: Removing a contact ends it for both sides
    Given "alice" and "bob" are contacts
    When "alice" removes "bob" from contacts
    Then "alice" should have 0 contacts
    And "bob" should have 0 contacts
