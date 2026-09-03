-- Blocking moves off contacts.is_blocked and into its own table.
--
-- The flag conflated two things with different lifetimes: an address-book
-- entry is mutual and disposable, a ban is one-directional and must outlive
-- everything. Because the flag lived on the contact row, removing a contact
-- silently discarded the block, and a stranger could not be blocked at all
-- since there was no row to set it on.
CREATE TABLE app_user.blocks (
    blocker_id uuid NOT NULL REFERENCES app_user.users (id) ON DELETE CASCADE,
    blocked_id uuid NOT NULL REFERENCES app_user.users (id) ON DELETE CASCADE,
    created_at timestamp with time zone NOT NULL DEFAULT now(),

    PRIMARY KEY (blocker_id, blocked_id)
);

-- The hot query is "is there a block either way between these two", asked on
-- every 1:1 message send. The primary key serves the blocker_id direction;
-- this serves the other.
CREATE INDEX idx_blocks_blocked ON app_user.blocks (blocked_id);

-- Carry existing blocks across before the column goes.
INSERT INTO app_user.blocks (blocker_id, blocked_id)
    SELECT user_id, contact_user_id FROM app_user.contacts WHERE is_blocked = true;

-- Dropped rather than left in place: a column that still exists but now means
-- something subtly different is how the next bug gets written.
ALTER TABLE app_user.contacts DROP COLUMN is_blocked;
