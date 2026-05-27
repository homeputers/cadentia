ALTER TABLE conversation_sessions
    ADD COLUMN expires_at timestamptz,
    ADD COLUMN expired_at timestamptz,
    ADD COLUMN absolute_expires_at timestamptz;

CREATE INDEX conversation_sessions_expires_at_idx
    ON conversation_sessions (expires_at)
    WHERE state <> 'CONFIRMED';

COMMENT ON COLUMN conversation_sessions.expires_at IS
    'Inactivity-based expiration timestamp used by ADR-015 lifecycle enforcement.';
COMMENT ON COLUMN conversation_sessions.absolute_expires_at IS
    'Absolute-lifetime expiration timestamp used by ADR-015 lifecycle enforcement.';
COMMENT ON COLUMN conversation_sessions.expired_at IS
    'Marker timestamp set when session transitions to EXPIRED.';
