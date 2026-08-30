CREATE SCHEMA IF NOT EXISTS app_user;

-- The user module keeps its foreign keys: nothing here crosses a module
-- boundary. user_profiles/user_devices (Phase 2) will reference this table
-- with real constraints.
CREATE TABLE app_user.users (
    id          uuid NOT NULL PRIMARY KEY,
    username    varchar(64) NOT NULL UNIQUE,
    email       varchar(320) UNIQUE,
    password    varchar(255) NOT NULL,
    first_name  varchar(80),
    last_name   varchar(80),
    avatar_url  varchar(512),
    status_text varchar(200),
    -- Monotonic counter published in UserProfileChanged events; the chat
    -- module's sender snapshot compares against it to discard stale updates.
    version     bigint NOT NULL DEFAULT 0,
    enabled     boolean NOT NULL DEFAULT true,
    created_at  timestamp with time zone NOT NULL DEFAULT now(),
    last_seen   timestamp with time zone,
    erased_at   timestamp with time zone
);
