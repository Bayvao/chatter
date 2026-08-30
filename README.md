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
| `POST` | `/api/chats/with/{userId}` | Open (or reuse) a direct chat |
| `GET` | `/api/chats/{chatId}/messages` | History, newest first |
| `GET` | `/api/chats/{chatId}/messages/since?afterSeq=` | Reconnect sync |
| `POST` | `/api/chats/messages` | Send without a live socket |
| `POST` | `/api/chats/{chatId}/messages/{messageId}/read` | Mark read |
| `DELETE` | `/api/chats/{chatId}/messages/{messageId}` | Soft delete |

WebSocket: connect to `/ws`, send to `/app/chat.send`, subscribe to
`/topic/chats/{chatId}`. The handshake is unauthenticated; the JWT goes on the
STOMP `CONNECT` frame, and `SUBSCRIBE` is checked for chat membership.

## Configuration

| Variable | Default |
|---|---|
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `localhost` / `5432` / `chat_db` |
| `DB_USERNAME` / `DB_PASSWORD` | `chat_user` / `chat_password` |
| `JWT_SECRET` | dev-only placeholder — **override outside local dev** |
| `JWT_EXPIRATION_MS` | `86400000` (24h) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` |

## Next

Phase 2 adds profiles, contacts and search; Phase 3 adds presence, offline
queueing and push. Partitioning, the transactional outbox and Kafka arrive in
Phases 3.5 and 5 — the schema and event shapes here are already aligned with
them.
