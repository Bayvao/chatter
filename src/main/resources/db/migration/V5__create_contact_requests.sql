-- Pending friend requests, deliberately NOT a status column on contacts.
--
-- contacts is keyed (user_id, contact_user_id), so alice->bob and bob->alice
-- are two legal rows: two people requesting each other at the same instant
-- would create two pending relationships, each waiting on the other. No
-- constraint on that table can prevent it. A partial unique index would need
-- WHERE status = PENDING, which H2 does not support, splitting the test and
-- production schemas.
--
-- Keying a separate table by the *unordered* pair fixes both: one pending
-- request per pair whichever direction it came from, enforced by a plain
-- UNIQUE that H2 and Postgres both honour.
CREATE TABLE app_user.contact_requests (
    id           uuid NOT NULL PRIMARY KEY,
    requester_id uuid NOT NULL REFERENCES app_user.users (id) ON DELETE CASCADE,
    recipient_id uuid NOT NULL REFERENCES app_user.users (id) ON DELETE CASCADE,

    -- least(a,b) || ':' || greatest(a,b), computed in ContactPair. Stored
    -- rather than generated so both databases agree on the collation used to
    -- order the two ids.
    pair_key     varchar(73) NOT NULL,
    created_at   timestamp with time zone NOT NULL DEFAULT now(),

    CONSTRAINT ux_contact_requests_pair UNIQUE (pair_key)
);

-- The inbox query: pending requests addressed to me, newest first.
CREATE INDEX idx_requests_recipient ON app_user.contact_requests (recipient_id, created_at DESC);
-- The "Requested" state on a search result: what I have outstanding.
CREATE INDEX idx_requests_requester ON app_user.contact_requests (requester_id, created_at DESC);
