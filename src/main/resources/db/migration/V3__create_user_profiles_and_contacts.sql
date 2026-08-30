-- Phase 2. Both tables live entirely inside the user module, so unlike
-- chat.messages -> app_user.users these foreign keys are KEPT: neither side
-- will ever cross a service or shard boundary.

CREATE TABLE app_user.user_profiles (
    user_id      uuid PRIMARY KEY REFERENCES app_user.users (id) ON DELETE CASCADE,
    phone_number varchar(32),
    bio          varchar(500),
    date_of_birth date,
    location     varchar(120),
    website      varchar(512),
    created_at   timestamp with time zone NOT NULL DEFAULT now(),
    updated_at   timestamp with time zone NOT NULL DEFAULT now()
);

CREATE TABLE app_user.contacts (
    user_id         uuid NOT NULL REFERENCES app_user.users (id) ON DELETE CASCADE,
    contact_user_id uuid NOT NULL REFERENCES app_user.users (id) ON DELETE CASCADE,
    added_at        timestamp with time zone NOT NULL DEFAULT now(),
    is_blocked      boolean NOT NULL DEFAULT false,
    is_favorite     boolean NOT NULL DEFAULT false,

    -- Composite PK gives the same guarantee as the surrogate id plus a unique
    -- constraint would, with one less column.
    PRIMARY KEY (user_id, contact_user_id)
);

CREATE INDEX idx_contacts_user ON app_user.contacts (user_id, added_at DESC);
