Feature: Contact management
  As a signed-in user
  I want a contact list built from accepted requests
  So that I can reach the people I talk to often

  Background:
    Given "alice" is a registered user
    And "bob" is a registered user

  Scenario: An accepted request appears in the contact list
    When "alice" and "bob" are contacts
    Then "alice" should have 1 contact
    And the contacts of "alice" should include "bob"

  Scenario: An accepted contact is mutual
    When "alice" and "bob" are contacts
    Then "bob" should have 1 contact
    And the contacts of "bob" should include "alice"

  Scenario: Removing someone who is not a contact
    When "alice" removes "bob" from contacts
    Then the response status should be 404

  Scenario: A blocked contact is hidden from the contact list
    Given "alice" and "bob" are contacts
    When "alice" blocks "bob"
    Then the response status should be 204
    And "alice" should have 0 contacts

  Scenario: A blocked user is hidden from search results
    Given "alice" and "bob" are contacts
    And "alice" blocks "bob"
    When "alice" searches for "bob"
    Then the search results should be empty

  Scenario: Unblocking restores the contact
    Given "alice" and "bob" are contacts
    And "alice" blocks "bob"
    When "alice" unblocks "bob"
    Then "alice" should have 1 contact

  Scenario: A blocked contact cannot be chatted with
    Given "alice" and "bob" are contacts
    And "alice" blocks "bob"
    When "bob" opens a chat with "alice"
    Then the response status should be 403
