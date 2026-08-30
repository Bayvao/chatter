Feature: Contact management
  As a signed-in user
  I want to keep a contact list
  So that I can reach the people I talk to often

  Background:
    Given "alice" is a registered user
    And "bob" is a registered user

  Scenario: Adding a contact
    When "alice" adds "bob" as a contact
    Then the response status should be 201
    And "alice" should have 1 contact
    And the contacts of "alice" should include "bob"

  Scenario: Contacts are not mutual by default
    When "alice" adds "bob" as a contact
    Then "bob" should have 0 contacts

  Scenario: Adding yourself is rejected
    When "alice" adds "alice" as a contact
    Then the response status should be 400

  Scenario: Adding the same contact twice is rejected
    Given "alice" adds "bob" as a contact
    When "alice" adds "bob" as a contact
    Then the response status should be 409

  Scenario: Removing a contact
    Given "alice" adds "bob" as a contact
    When "alice" removes "bob" from contacts
    Then the response status should be 204
    And "alice" should have 0 contacts

  Scenario: Removing someone who is not a contact
    When "alice" removes "bob" from contacts
    Then the response status should be 404

  Scenario: A blocked contact is hidden from the contact list
    Given "alice" adds "bob" as a contact
    When "alice" blocks "bob"
    Then the response status should be 204
    And "alice" should have 0 contacts

  Scenario: A blocked user is hidden from search results
    Given "alice" adds "bob" as a contact
    And "alice" blocks "bob"
    When "alice" searches for "bob"
    Then the search results should be empty

  Scenario: Unblocking restores the contact
    Given "alice" adds "bob" as a contact
    And "alice" blocks "bob"
    When "alice" unblocks "bob"
    Then "alice" should have 1 contact
