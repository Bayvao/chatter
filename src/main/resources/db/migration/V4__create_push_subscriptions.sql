-- Web Push (RFC 8030) subscriptions. One row per browser/device that has
-- granted notification permission. FK to users is KEPT: same module, same
-- aggregate, never crosses a boundary.
CREATE TABLE app_user.push_subscriptions (
    id         uuid NOT NULL PRIMARY KEY,
    user_id    uuid NOT NULL REFERENCES app_user.users (id) ON DELETE CASCADE,

    -- The push service URL the browser handed us (Mozilla, Google, Apple...).
    endpoint   varchar(1024) NOT NULL,
    -- Client public key and auth secret used to encrypt the payload so the
    -- push service relays ciphertext it cannot read.
    p256dh_key varchar(255) NOT NULL,
    auth_secret varchar(255) NOT NULL,

    user_agent varchar(255),
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    last_used_at timestamp with time zone
);

-- A push service can reassign an endpoint. Without this, one browser could
-- accumulate rows under several users and receive someone else's messages.
CREATE UNIQUE INDEX ux_push_subscriptions_endpoint ON app_user.push_subscriptions (endpoint);
CREATE INDEX idx_push_subscriptions_user ON app_user.push_subscriptions (user_id);
