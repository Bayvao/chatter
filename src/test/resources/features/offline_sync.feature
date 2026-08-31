Feature: Delivery status and reconnect sync
  As a user who was away
  I want the messages I missed when I come back
  So that a dropped connection does not lose anything

  Background:
    Given "alice" is a registered user
    And "bob" is a registered user
    And "alice" has opened a chat with "bob"

  Scenario: A message to an offline recipient stays SENT
    When "alice" sends "you are away" to the chat
    Then the newest message in the chat should have status "SENT"

  Scenario: A message to a connected recipient is marked DELIVERED
    Given "bob" is subscribed to the chat over WebSocket
    When "alice" sends "you are here" to the chat
    Then the newest message in the chat should eventually have status "DELIVERED"

  Scenario: Reconnecting delivers exactly what was missed
    Given "alice" sends "first" to the chat
    And "alice" sends "second" to the chat
    When "bob" syncs the chat from sequence 0
    Then "bob" should receive 2 messages in the sync batch
    And the sync batch should contain "first" and "second" in order

  Scenario: A cursor part way through only backfills the gap
    Given "alice" sends "first" to the chat
    And "alice" sends "second" to the chat
    And "alice" sends "third" to the chat
    When "bob" syncs the chat from sequence 1
    Then "bob" should receive 2 messages in the sync batch

  Scenario: An up-to-date cursor yields nothing
    Given "alice" sends "only message" to the chat
    When "bob" syncs the chat from sequence 1
    Then "bob" should receive 0 messages in the sync batch

  Scenario: Syncing a chat you are not part of is refused
    Given "carol" is a registered user
    And "alice" sends "private" to the chat
    When "carol" syncs the chat from sequence 0
    Then "carol" should receive 0 messages in the sync batch
