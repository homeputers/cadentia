ALTER TABLE conversation_sessions
    ADD COLUMN slot_sources_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN revision_events_json jsonb NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN confirmed_at timestamptz,
    ADD COLUMN last_channel_update_id varchar(128),
    ADD COLUMN last_recommendation_result_id varchar(128),
    ADD COLUMN correlation_metadata_json jsonb NOT NULL DEFAULT '{}'::jsonb;

CREATE INDEX conversation_sessions_recommendation_result_idx
    ON conversation_sessions (last_recommendation_result_id)
    WHERE last_recommendation_result_id IS NOT NULL;

CREATE INDEX conversation_sessions_channel_update_idx
    ON conversation_sessions (channel, last_channel_update_id)
    WHERE last_channel_update_id IS NOT NULL;

COMMENT ON COLUMN conversation_sessions.slot_sources_json IS
    'Recoverable source stamps for normalized slots. Raw free-text input is intentionally excluded.';
COMMENT ON COLUMN conversation_sessions.revision_events_json IS
    'Recoverable ADR-015 revision event summaries safe for user-facing session recovery.';
COMMENT ON COLUMN conversation_sessions.confirmed_at IS
    'Timestamp set when a normalized request is confirmed for recommendation generation.';
COMMENT ON COLUMN conversation_sessions.last_channel_update_id IS
    'Opaque channel update identifier for cross-table operational correlation; does not include raw user, chat, or message text.';
COMMENT ON COLUMN conversation_sessions.last_recommendation_result_id IS
    'Stable recommendation result identifier returned by the shared recommendation engine.';
COMMENT ON COLUMN conversation_sessions.correlation_metadata_json IS
    'Small sanitized metadata map for request correlation. Must not contain raw Telegram IDs, profile fields, message text, callback payloads, tokens, or secrets.';
