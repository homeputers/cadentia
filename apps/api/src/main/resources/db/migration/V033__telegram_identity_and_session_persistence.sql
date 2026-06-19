CREATE TABLE telegram_account_link (
    id uuid PRIMARY KEY,
    channel varchar(32) NOT NULL,
    chat_hash varchar(128) NOT NULL,
    user_hash varchar(128) NOT NULL,
    church_instance_id varchar(128) NOT NULL,
    actor_id uuid NOT NULL,
    roles text[] NOT NULL DEFAULT '{}',
    status varchar(32) NOT NULL CHECK (status IN ('LINKED', 'UNLINKED', 'REVOKED', 'DISABLED', 'UNAUTHORIZED')),
    link_initiated_at timestamptz NOT NULL DEFAULT now(),
    link_confirmed_at timestamptz,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    audit_metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    UNIQUE (channel, chat_hash, user_hash, church_instance_id)
);

CREATE INDEX telegram_account_link_lookup_idx
    ON telegram_account_link (channel, chat_hash, user_hash, status);

CREATE TABLE telegram_bot_session (
    id uuid PRIMARY KEY,
    channel varchar(32) NOT NULL,
    chat_hash varchar(128) NOT NULL,
    user_hash varchar(128) NOT NULL,
    church_instance_id varchar(128) NOT NULL,
    actor_id uuid NOT NULL,
    state varchar(64) NOT NULL CHECK (state IN ('IDLE', 'NEW_SETLIST_ACTIVE', 'PENDING_CONFIRMATION', 'CANCELLED', 'COMPLETED', 'EXPIRED')),
    pending_confirmation_ref varchar(128),
    last_update_id bigint,
    last_message_id integer,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    inactivity_deadline timestamptz NOT NULL,
    absolute_expiration timestamptz NOT NULL,
    audit_metadata jsonb NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX telegram_bot_session_active_lookup_idx
    ON telegram_bot_session (channel, chat_hash, user_hash, updated_at DESC)
    WHERE state NOT IN ('CANCELLED', 'COMPLETED', 'EXPIRED');

COMMENT ON TABLE telegram_account_link IS
    'Pseudonymous Telegram-to-Cadentia account links. Raw Telegram IDs, profile names, usernames, message text, callback payloads, and bot tokens are intentionally excluded.';
COMMENT ON COLUMN telegram_account_link.chat_hash IS
    'HMAC-SHA256 pseudonymous chat reference scoped to Telegram channel.';
COMMENT ON COLUMN telegram_account_link.user_hash IS
    'HMAC-SHA256 pseudonymous user reference scoped to Telegram channel.';
COMMENT ON TABLE telegram_bot_session IS
    'Durable Telegram conversation state for restart-safe guided flows using only hashed Telegram references and minimal message/update pointers.';
COMMENT ON COLUMN telegram_bot_session.pending_confirmation_ref IS
    'Opaque Cadentia confirmation/correlation reference; must not contain raw Telegram callback payload or message text.';
