CREATE SCHEMA IF NOT EXISTS chat;

-- Foreign keys exist INSIDE this module and never between modules.
-- created_by / user_id / sender_id are plain uuid columns with no REFERENCES:
-- they point at app_user.users, across a boundary that becomes a network hop.
CREATE TABLE chat.chats (
    id              uuid NOT NULL PRIMARY KEY,
    is_group        boolean NOT NULL DEFAULT false,
    title           varchar(200),
    avatar_url      varchar(512),
    created_by      uuid,
    created_at      timestamp with time zone NOT NULL DEFAULT now(),
    last_message_at timestamp with time zone
);

-- Participants rather than user1_id/user2_id, so group chat needs no second
-- migration later. Also the membership check that stands in for the dropped
-- messages -> users constraint.
CREATE TABLE chat.chat_participants (
    chat_id       uuid NOT NULL,
    user_id       uuid NOT NULL,
    role          smallint NOT NULL DEFAULT 0,
    joined_at     timestamp with time zone NOT NULL DEFAULT now(),
    left_at       timestamp with time zone,
    last_read_seq bigint NOT NULL DEFAULT 0,
    muted_until   timestamp with time zone,

    PRIMARY KEY (chat_id, user_id),
    -- KEPT: same aggregate, same schema, never splits.
    CONSTRAINT fk_participants_chat
        FOREIGN KEY (chat_id) REFERENCES chat.chats (id) ON DELETE CASCADE
);

CREATE INDEX idx_participants_user ON chat.chat_participants (user_id, joined_at DESC);

-- Per-chat monotonic ordinal. Total order without a clock dependency, and it
-- turns offline sync into a range request with detectable gaps.
CREATE TABLE chat.chat_counters (
    chat_id  uuid NOT NULL PRIMARY KEY,
    last_seq bigint NOT NULL DEFAULT 0
);

-- Hash partitioning by chat_id is a Phase 3.5 concern and is deliberately not
-- applied here; the column layout and index set already match the partitioned
-- target so that migration is mechanical.
CREATE TABLE chat.messages (
    id                uuid NOT NULL PRIMARY KEY,
    chat_id           uuid NOT NULL,
    seq               bigint NOT NULL,
    sender_id         uuid NOT NULL,

    client_msg_id     uuid,
    content           text,
    content_type      smallint NOT NULL DEFAULT 0,

    -- Denormalised sender snapshot: the chat module cannot join to
    -- app_user.users, so it keeps a copy guarded by sender_version.
    sender_name       varchar(120),
    sender_avatar_url varchar(512),
    sender_version    bigint NOT NULL DEFAULT 0,

    status            smallint NOT NULL DEFAULT 0,
    created_at        timestamp with time zone NOT NULL DEFAULT now(),
    delivered_at      timestamp with time zone,
    read_at           timestamp with time zone,
    -- Soft delete: removing a row punches a hole in seq and clients re-sync
    -- forever. GDPR erasure redacts content and keeps the row.
    deleted_at        timestamp with time zone
);

CREATE UNIQUE INDEX idx_messages_chat_seq       ON chat.messages (chat_id, seq);
-- Idempotency: a retried send with the same client_msg_id cannot duplicate.
CREATE UNIQUE INDEX idx_messages_client_msg     ON chat.messages (chat_id, client_msg_id);
CREATE INDEX        idx_messages_chat_seq_desc  ON chat.messages (chat_id, seq DESC);
CREATE INDEX        idx_messages_sender         ON chat.messages (sender_id, created_at DESC);
