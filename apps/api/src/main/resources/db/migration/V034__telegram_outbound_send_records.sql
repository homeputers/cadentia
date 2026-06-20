CREATE TABLE telegram_outbound_send_record (
    id UUID PRIMARY KEY,
    idempotency_key TEXT NOT NULL UNIQUE,
    correlation_id TEXT NOT NULL,
    chat_hash TEXT NOT NULL,
    operation TEXT NOT NULL,
    sanitized_preview TEXT NOT NULL,
    status TEXT NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL,
    next_attempt_at TIMESTAMPTZ,
    telegram_message_id TEXT,
    failure_category TEXT,
    sanitized_failure_detail TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT telegram_outbound_send_record_status_valid CHECK (
        status IN ('PENDING', 'SENT', 'RETRY_SCHEDULED', 'DEAD_LETTERED', 'DUPLICATE_SUPPRESSED')
    ),
    CONSTRAINT telegram_outbound_send_record_failure_valid CHECK (
        failure_category IS NULL OR failure_category IN (
            'NETWORK', 'TELEGRAM_5XX', 'RATE_LIMIT', 'CHAT_BLOCKED', 'INVALID_CHAT',
            'MALFORMED_REQUEST', 'UNAUTHORIZED_BOT', 'DISABLED_CHANNEL', 'UNKNOWN'
        )
    ),
    CONSTRAINT telegram_outbound_send_record_no_plaintext_secrets CHECK (
        sanitized_preview !~* '(bot_token|secret|raw prompt|raw lyrics)'
        AND COALESCE(sanitized_failure_detail, '') !~* '(bot_token|secret|raw prompt|raw lyrics)'
    )
);

CREATE INDEX telegram_outbound_send_record_retry_idx
    ON telegram_outbound_send_record (status, next_attempt_at)
    WHERE status = 'RETRY_SCHEDULED';

CREATE INDEX telegram_outbound_send_record_correlation_idx
    ON telegram_outbound_send_record (correlation_id);

CREATE TABLE telegram_outbound_dead_letter (
    id UUID PRIMARY KEY,
    outbound_id UUID NOT NULL REFERENCES telegram_outbound_send_record (id),
    idempotency_key TEXT NOT NULL,
    correlation_id TEXT NOT NULL,
    chat_hash TEXT NOT NULL,
    operation TEXT NOT NULL,
    failure_category TEXT NOT NULL,
    sanitized_failure_detail TEXT NOT NULL,
    sanitized_preview TEXT NOT NULL,
    attempts INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT telegram_outbound_dead_letter_failure_valid CHECK (
        failure_category IN (
            'NETWORK', 'TELEGRAM_5XX', 'RATE_LIMIT', 'CHAT_BLOCKED', 'INVALID_CHAT',
            'MALFORMED_REQUEST', 'UNAUTHORIZED_BOT', 'DISABLED_CHANNEL', 'UNKNOWN'
        )
    ),
    CONSTRAINT telegram_outbound_dead_letter_no_plaintext_secrets CHECK (
        sanitized_preview !~* '(bot_token|secret|raw prompt|raw lyrics)'
        AND sanitized_failure_detail !~* '(bot_token|secret|raw prompt|raw lyrics)'
    )
);

CREATE INDEX telegram_outbound_dead_letter_created_at_idx
    ON telegram_outbound_dead_letter (created_at DESC);

COMMENT ON TABLE telegram_outbound_send_record IS
    'Durable, idempotent Telegram outbound send state. Stores sanitized previews and hashed chat identifiers only.';
COMMENT ON COLUMN telegram_outbound_send_record.idempotency_key IS
    'Deterministic key preventing duplicate user-visible Telegram messages across retries, webhook redelivery, and restarts.';
COMMENT ON COLUMN telegram_outbound_send_record.chat_hash IS
    'HMAC hash of the Telegram chat identifier; raw chat identifiers are not stored here.';
COMMENT ON TABLE telegram_outbound_dead_letter IS
    'Operator-safe inspection metadata for Telegram sends that exhausted retries or failed permanently.';
