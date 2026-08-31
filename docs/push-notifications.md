# Push notifications

**Status:** Web Push **implemented in Phase 3**. Mobile push (FCM / APNs)
remains deferred — there is no mobile app to receive it.

This file started life as a plan for deferred work. Phase 3 built the browser
half of it, so what follows is a record of what shipped, and what is still open.

---

## What shipped

Web Push (RFC 8030/8291), with VAPID and a service worker. **No Firebase**, no
third-party messaging account, no credentials file: the browser nominates its
own push service, we encrypt the payload for the subscription's key and sign
the request with ours.

| Piece | Where |
|---|---|
| Port | `chat/port/PushSender.java` |
| Real adapter | `chat/adapter/WebPushSender.java` (`app.push.vapid.enabled=true`) |
| Default no-op | `chat/adapter/LoggingPushSender.java` |
| Subscription store | `app_user.push_subscriptions` (`V4__create_push_subscriptions.sql`) |
| Registration API | `user/controller/PushController.java` |
| Hook point | `chat/websocket/MessageBroadcaster.java` |
| Service worker | `frontend/public/sw.js` |
| Browser subscribe flow | `frontend/src/services/push.js` |

The hook point landed as predicted — `MessageBroadcaster` was already listening
for `MessageSent` at `AFTER_COMMIT`, so push was one branch on the presence
check, with nothing on the send path changed. That is the payoff for publishing
a domain event from day one.

The table is `push_subscriptions` rather than the `user_devices` sketched below,
because a Web Push subscription is not a device token: it is an endpoint URL
plus two client-held keys (`p256dh`, `auth`). The **unique index on `endpoint`
was kept**, for exactly the reason the original plan gave for `device_token` —
push services reassign endpoints, and without it one browser accumulates rows
under several users and receives someone else's messages.

Two operational rules from the plan are implemented: dead subscriptions are
deleted on a `404`/`410`, and a push failure is logged but never propagated —
the message is already committed, so an unavailable push service must not
decide whether our write succeeded.

**Not covered by tests.** `WebPushSender` needs a live browser subscription and
the service worker needs HTTPS, so neither runs in CI; `LoggingPushSender` is
the tested default. Verify manually against a real browser.

---

## What is still open

### Mobile push (FCM / APNs)

Still blocked on the same thing: there is no mobile app. When one exists, the
transport table below still applies, and the `PushSender` port means the
addition is a second adapter rather than a change to the send path.

#### Redis is not a substitute

Worth restating, because it comes up, and because Phase 3 introduced Redis for
presence — which is a different job entirely:

- **FCM / APNs** deliver over a connection the **operating system** already
  holds open to Apple's or Google's servers. That is the entire mechanism, and
  only Apple and Google can do it. It is what makes a *closed* app light up.
- **Redis pub/sub** moves data between **our own server instances**. It solves
  "the recipient is connected to instance B but the message arrived on instance
  A" — a real problem, but a different one, and one that only appears when we
  run more than one instance.

No self-hosted component can wake a closed mobile app. If we want that, we
integrate the platform push service; there is no way around it.

#### Transport, per client

| Client | Service | Notes |
|---|---|---|
| Browser, tab closed | **Web Push** (VAPID + service worker) | ✅ shipped in Phase 3 |
| Android / iOS app | **FCM** (FCM relays to APNs for iOS) | Needs a Firebase project + service-account JSON |
| Desktop (Electron) | OS-native, or Web Push | |

Do not reach for FCM reflexively. For browsers, Web Push was the smaller
dependency — a VAPID key pair and a service worker — which is why Phase 3 took
it.

### Muting

`chat_participants.muted_until` exists and is **still unused**. It should be
checked before sending a notification. This is the smallest open item.

### Fan-out cost

One message to a 1,000-member group is 1,000 sends. `WebPushSender` is `@Async`
so it is already off the request thread, but by Phase 5 it belongs behind the
outbox and a Kafka consumer rather than a thread pool.

### Payload content after Phase 3.75

The payload currently carries `{chatId, title, body}`, where `body` is the
message text, truncated. **That stops being viable once messages are
end-to-end encrypted** — the server will hold ciphertext and cannot put message
text in a notification. It becomes a content-free ping and the client decrypts
locally. The shape is already close: dropping `body` and adding a count is the
whole change.

---

## References

- `chat/port/PushSender.java`, `chat/port/PresenceStore.java` — the ports
- `chat/websocket/MessageBroadcaster.java` — the hook point
- `chat-app-architecture.md` — FK rules; the E2E section on content-free pings
- README, "Web Push" — generating a VAPID keypair and enabling it locally
