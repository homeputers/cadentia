CREATE TABLE telegram_access_request (
    id uuid PRIMARY KEY,
    channel varchar(32) NOT NULL,
    chat_hash varchar(128) NOT NULL,
    user_hash varchar(128) NOT NULL,
    chat_id varchar(64),
    church_instance_id varchar(128) NOT NULL,
    status varchar(32) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    requested_at timestamptz NOT NULL DEFAULT now(),
    decided_at timestamptz,
    decided_by varchar(256),
    decision_reason text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    audit_metadata jsonb NOT NULL DEFAULT '{}'::jsonb
);

CREATE UNIQUE INDEX telegram_access_request_pending_uniq
    ON telegram_access_request (channel, chat_hash, user_hash, church_instance_id)
    WHERE status = 'PENDING';

CREATE INDEX telegram_access_request_queue_idx
    ON telegram_access_request (church_instance_id, status, requested_at DESC);

COMMENT ON TABLE telegram_access_request IS
    'Self-service Telegram access requests pending administrator decision. Raw Telegram chat IDs are retained only while PENDING so the decision can be pushed to the requester, and are purged (set to NULL) when the request is approved or rejected.';
COMMENT ON COLUMN telegram_access_request.chat_hash IS
    'HMAC-SHA256 pseudonymous chat reference scoped to Telegram channel; retained after decision for audit.';
COMMENT ON COLUMN telegram_access_request.user_hash IS
    'HMAC-SHA256 pseudonymous user reference scoped to Telegram channel; retained after decision for audit.';
COMMENT ON COLUMN telegram_access_request.chat_id IS
    'Raw Telegram chat ID required to deliver the approval/rejection notification. MUST be NULL once status leaves PENDING.';
