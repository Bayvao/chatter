# Future enhancement: push notifications

**Status:** deferred, not implemented.
**Blocked on:** a client that can receive push — a mobile app, or browser
notifications with the tab closed. Neither exists yet.

---

## Why it is deferred

Push notifications exist to reach a user whose app is **closed or backgrounded
on a device**. Chatter is currently a browser app, and the two states a browser
client can be in are both already covered:

| Client state | How the user gets the message today |
|---|---|
| Tab open | WebSocket push (`/topic/chats/{chatId}`) |
| Tab closed | Reconnect sync — everything with `seq > last_read_seq` |

There is no gap for push to fill. Building it now would mean carrying a Firebase
dependency, a credentials file, and a device-token table for a code path nothing
exercises.

### Redis is not a substitute

Worth stating plainly, because it comes up:

- **FCM / APNs** deliver over a connection the **operating system** already
  holds open to Apple's or Google's servers. That is the entire mechanism, and
  only Apple and Google can do it. It is what makes a *closed* app light up.
- **Redis pub/sub** moves data between **our own server instances**. It solves
  "the recipient is connected to instance B but the message arrived on instance
  A" — a real problem, but a different one, and one that only appears when we
  run more than one instance.

No self-hosted component can wake a closed mobile app. If we want that, we
integrate the platform push service; there is no way around it.

---

## What to build, when the time comes

### 1. Pick the transport for the client you actually have

| Client | Service | Notes |
|---|---|---|
| Android / iOS app | **FCM** (FCM relays to APNs for iOS) | Needs a Firebase project + service-account JSON |
| Browser, tab closed | **Web Push** (VAPID + service worker) | No Firebase needed; a W3C standard |
| Desktop (Electron) | OS-native, or Web Push | |

Do not reach for FCM reflexively. If the goal is browser notifications, Web
Push is the smaller dependency — a VAPID key pair and a service worker, no
third-party account.

### 2. Migration: device registry

```sql
CREATE TABLE app_user.user_devices (
    id           uuid PRIMARY KEY,
    user_id      uuid NOT NULL REFERENCES app_user.users (id) ON DELETE CASCADE, -- KEPT
    device_token varchar(512) NOT NULL,
    platform     smallint NOT NULL,     -- 0=web 1=android 2=ios
    app_version  varchar(32),
    registered_at timestamptz NOT NULL DEFAULT now(),
    last_active   timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_user_devices_token ON app_user.user_devices (device_token);
CREATE INDEX idx_user_devices_user ON app_user.user_devices (user_id);
```

The foreign key to `users` is **kept** — same aggregate, same module, never
crosses a service boundary. Contrast `chat.messages.sender_id`, which has none.
See the "no foreign keys across module boundaries" rule in the architecture doc.

The unique index on `device_token` matters: tokens get reassigned by the
platform, and without it one physical device can accumulate rows under several
users and receive someone else's notifications.

### 3. Port + adapter

Follow the existing `SenderDirectory` / `PresenceStore` shape
(`chat/port/`) so nothing outside the adapter knows which provider is in play:

```java
public interface NotificationSender {
    void notifyOfMessage(UUID recipientId, MessageSent event);
}
```

- `LoggingNotificationSender` — the default. Logs and returns.
- `FcmNotificationSender` / `WebPushNotificationSender` — `@ConditionalOnProperty`,
  active only when credentials are configured.

Defaulting to the no-op is deliberate: `docker compose up` must keep working for
a contributor with no Firebase account.

### 4. Hook point

`MessageBroadcaster` (`chat/websocket/MessageBroadcaster.java`) already listens
for `MessageSent` at `AFTER_COMMIT` and will already know, from `PresenceStore`,
whether the recipient is online. Push is one branch:

```java
if (presence.isOnline(recipientId)) {
    messagingTemplate.convertAndSend(...);   // existing path
} else {
    notificationSender.notifyOfMessage(recipientId, event);   // new
}
```

Nothing on the send path changes. That is the payoff for publishing a domain
event from day one.

### 5. Operational details that are easy to miss

- **Token invalidation.** FCM returns `UNREGISTERED` / `INVALID_ARGUMENT` for
  dead tokens. Delete the row on that response, or the table grows without bound
  and every send wastes a call.
- **Failures must not fail the send.** The message is already committed. A push
  failure is logged, never propagated — do not let Google's availability decide
  whether our write succeeds.
- **Fan-out cost.** One message to a 1,000-member group is 1,000 sends. Keep it
  off the request thread; by Phase 5 it belongs behind the outbox and a Kafka
  consumer.
- **Content in the payload.** Fine today. **Not fine after Phase 3.75** — once
  messages are end-to-end encrypted the server holds ciphertext and cannot put
  message text in a notification. It becomes a content-free ping and the client
  decrypts locally. Designing for that now costs nothing: keep the payload to
  ids and a count.
- **Quiet hours / muting.** `chat_participants.muted_until` already exists and
  is currently unused. Check it before sending.

---

## Rough effort

| Piece | Estimate |
|---|---|
| `user_devices` migration + registration endpoint | 3h |
| Port, no-op adapter, wiring, tests | 3h |
| FCM or Web Push adapter + token invalidation | 6h |
| Client registration + permission prompt | 6h |
| **Total** | **~18h** |

The client half is most of it, and none of it is testable without a real device
or a real browser permission grant — budget for manual verification.

---

## References

- `chat/port/SenderDirectory.java` — the port/adapter pattern to copy
- `chat/websocket/MessageBroadcaster.java` — the hook point
- `chat-app-architecture.md` — FK rules; the E2E section on content-free pings
