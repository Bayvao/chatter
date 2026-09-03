# Chatter

A chat application built as a **modular monolith designed to be split** — one
Spring Boot process and one database, structured so extracting a service later
is a week of work rather than a quarter.

Phase 1 is implemented: registration and login, one-to-one chat, and real-time
delivery over WebSocket.

## Architecture

Four rules hold the design together, applied from the first migration rather
than retrofitted later:

1. **No foreign keys across module boundaries.** `chat.messages` references
   `app_user.users` by plain `uuid`. FKs inside a module (for example
   `chat_participants → chats`) are kept.
2. **No cross-module repository calls.** The chat module reaches the user
   module through its own `SenderDirectory` port, implemented by an adapter on
   the user side.
3. **Separate schemas in one database** — `app_user` and `chat`.
4. **Domain events from day one.** `MessageSent` is published on the send path
   and consumed in-process; it becomes a Kafka topic later without changing the
   producer.

Message storage settles the decisions that are expensive to reverse:

| Decision | Why |
|---|---|
| UUIDv7 ids, assigned in the service layer | Time-ordered keys keep B-tree inserts local; UUIDv4 scatters them |
| Per-chat `seq` counter | Total order without a clock dependency; offline sync becomes a range request |
| Denormalised sender snapshot + `sender_version` | The chat module cannot join to users; the version guard rejects out-of-order updates |
| Unique `(chat_id, client_msg_id)` | A retried send cannot create a duplicate |
| Soft delete (`deleted_at`) | A hole in `seq` makes clients re-sync forever |

## Running it

```bash
docker compose up --build
# frontend  http://localhost:3000
# backend   http://localhost:8080
```

Register two users in separate browser profiles, search for one from the other,
and send messages — they arrive without a refresh.

### Running the pieces directly

```bash
# Postgres
docker run --rm -p 5432:5432 \
  -e POSTGRES_DB=chat_db -e POSTGRES_USER=chat_user -e POSTGRES_PASSWORD=chat_password \
  postgres:16-alpine

./gradlew bootRun                      # backend on :8080
cd frontend && npm install && npm run dev   # frontend on :3000
```

Flyway owns the schema (`spring.jpa.hibernate.ddl-auto=validate`), so migrations
are the only thing that changes it.

## Tests

```bash
./gradlew test
```

20 Cucumber scenarios plus a context-load test, run against H2 in
PostgreSQL-compatibility mode so the real migrations execute. Coverage includes
registration and login failures, chat creation and reuse, sequence numbering,
idempotent resend, unread counts, membership refusals, and real STOMP delivery
including both WebSocket authorization refusals.

## API

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/auth/register` | Create an account, returns a JWT |
| `POST` | `/api/auth/login` | Sign in, returns a JWT |
| `GET` | `/api/auth/me` | The signed-in user |
| `GET` | `/api/users/search?q=` | Find someone to chat with |
| `GET` | `/api/chats` | Conversations, with unread counts |
| `DELETE` | `/api/chats/{chatId}` | Leave a conversation |
| `POST` | `/api/chats/with/{userId}` | Open (or reuse) a direct chat |
| `GET` | `/api/chats/{chatId}/messages` | History, newest first |
| `GET` | `/api/chats/{chatId}/messages/since?afterSeq=` | Reconnect sync |
| `POST` | `/api/chats/messages` | Send without a live socket |
| `POST` | `/api/chats/{chatId}/messages/{messageId}/read` | Mark read |
| `DELETE` | `/api/chats/{chatId}/messages/{messageId}` | Soft delete |
| `POST` | `/api/users/me/contacts/{userId}` | Send a friend request |
| `GET` | `/api/users/me/contacts/requests` | Incoming requests |
| `GET` | `/api/users/me/contacts/requests/sent` | Requests you have sent |
| `POST` | `/api/users/me/contacts/{userId}/accept` | Accept a request |
| `DELETE` | `/api/users/me/contacts/{userId}/decline` | Decline, or cancel your own |
| `GET` | `/api/users/me/blocked` | Users you have blocked |
| `GET` | `/api/presence/{userId}` | Online flag and last seen |
| `GET` | `/api/presence?userIds=a,b` | The same, in bulk |
| `GET` | `/api/push/public-key` | VAPID public key, and whether push is on |
| `POST` | `/api/push/subscriptions` | Register a browser for Web Push |
| `DELETE` | `/api/push/subscriptions` | Retire one, on sign-out |

WebSocket: connect to `/ws`, send to `/app/chat.send`, subscribe to
`/topic/chats/{chatId}`. The handshake is unauthenticated; the JWT goes on the
STOMP `CONNECT` frame, and `SUBSCRIBE` is checked for chat membership.

| Destination | Purpose |
|---|---|
| `/topic/presence` | Someone connected or disconnected |
| `/app/sync.start` | `{cursors: {chatId: seq}}` — catch up after a drop |
| `/user/queue/sync-batch` | Missed messages, 50 per frame |
| `/user/queue/sync-complete` | `{messageCount}` when the backfill is done |
| `/user/queue/contacts` | Friend requests, accepts, declines and removals |

### Friend requests

A chat cannot be opened with a stranger. `POST /api/chats/with/{userId}` returns
403 unless the two are accepted contacts, which is enforced through the
`RelationshipDirectory` port rather than by the chat module reading contacts
directly.

Pending requests live in their own table keyed by the **unordered pair**, so a
plain `UNIQUE` constraint guarantees one request per pair whichever direction it
came from. Two consequences fall out of that:

- Two people clicking "Add friend" on each other at the same instant become
  contacts, rather than one of them failing arbitrarily — the loser of the
  constraint race sees the reciprocal request and accepts it.
- Clicking twice yourself is a 409, not a duplicate.

Changes relay live on `/user/queue/contacts`, so a request appears without a
reload. Delivery is best effort: a user destination with nobody connected drops,
and the REST lists above remain the source of truth on load. **Blocking is never
relayed to the person blocked** — telling someone they were blocked hands a
harasser the signal that their target acted.

### Blocking

A block bars contact outright, in **both directions**, until it is lifted:
neither party can send in an existing conversation, open a new one, or send a
friend request, and each disappears from the other's search results.

Blocks live in `app_user.blocks`, not as a flag on the contact row. The flag
conflated two things with different lifetimes — an address-book entry is mutual
and disposable, a ban is one-directional and must outlive everything — so
removing a contact silently discarded the block, and a stranger could not be
blocked at all. Now removing a contact leaves the block untouched, and anyone
can be blocked whether or not you ever added them.

Blocking does not delete the friendship or the conversation; unblocking
restores both. **Existing history stays readable** — blocking stops new contact
rather than seizing back what someone was already shown.

The person blocked is never told. `GET /me/blocked` reports only blocks you
hold yourself, and search hides both directions so a blocked user cannot find
their way back to a request button. A send they attempt is refused with a
message on `/user/queue/errors`, which is how their client explains the failure
without disclosing who acted.

### Leaving a chat

`DELETE /api/chats/{chatId}` stamps `left_at` on your participant row. That one
write is the whole behaviour, because every membership query already filters on
it — the chat leaves your list, sends and history return 403, STOMP SUBSCRIBE is
refused, and the broadcaster stops delivering to you.

It hides rather than deletes. Messages and your read cursor survive, so opening
a chat with the same person **rejoins the original conversation** rather than
forking a second one alongside it.

### Presence, delivery status and catch-up

Presence lives in Redis as a TTL key, not as a column on `users`. A server that
dies cannot leave anyone stuck showing "online", because the key expires by
itself. With `PRESENCE_REDIS_ENABLED=false` (the default, and what the tests
use) an in-memory store stands in, which is correct for a single instance.

On send, each recipient is checked: online recipients get the message over the
socket and it is marked `DELIVERED`; offline ones leave it `SENT` and get a push
notification. Nothing is queued anywhere — every message is already durable in
Postgres with a per-chat `seq`, so a reconnecting client asks for
`seq > cursor` and gets exactly what it missed. Cursors rather than timestamps:
clocks drift, and two messages can share a millisecond.

### Web Push

Notifications use Web Push (RFC 8030/8291) with VAPID and a service worker —
no Firebase, no third-party messaging account. The browser nominates its own
push service; we encrypt for the subscription's key and sign with ours.

```bash
npx web-push generate-vapid-keys      # prints a public/private pair

export VAPID_ENABLED=true
export VAPID_PUBLIC_KEY=...           # the client fetches this one
export VAPID_PRIVATE_KEY=...          # keep it out of version control
docker compose up
```

Browsers only permit push over HTTPS, or on `localhost`. Left disabled, the app
works unchanged and `LoggingPushSender` logs what it would have sent.

## Configuration

| Variable | Default |
|---|---|
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `localhost` / `5432` / `chat_db` |
| `DB_USERNAME` / `DB_PASSWORD` | `chat_user` / `chat_password` |
| `JWT_SECRET` | dev-only placeholder — **override outside local dev** |
| `JWT_EXPIRATION_MS` | `86400000` (24h) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` |
| `PRESENCE_REDIS_ENABLED` | `false` — `true` under `docker compose` |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` |
| `VAPID_ENABLED` | `false` — push is off until keys are supplied |
| `VAPID_PUBLIC_KEY` / `VAPID_PRIVATE_KEY` | empty |
| `VAPID_SUBJECT` | `mailto:admin@example.com` |

## Next

Phases 1–3 are in: auth, direct chat, real-time delivery, profiles, contacts
and search, then presence, delivery status, reconnect sync and Web Push, plus
friend requests and leaving a chat.
Partitioning, the transactional outbox and Kafka arrive in Phases 3.5 and 5 —
the schema and event shapes here are already aligned with them.
