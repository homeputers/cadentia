CREATE TABLE conversation_sessions (
    id uuid PRIMARY KEY,
    channel varchar(64) NOT NULL,
    state varchar(32) NOT NULL,
    merged_slots_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE conversation_session_revisions (
    id uuid PRIMARY KEY,
    session_id uuid NOT NULL REFERENCES conversation_sessions (id) ON DELETE RESTRICT,
    revision_number integer NOT NULL,
    slot_path varchar(128) NOT NULL,
    prior_value_json jsonb,
    merged_value_json jsonb,
    source_tag varchar(32) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT conversation_session_revisions_source_tag_valid
        CHECK (source_tag IN ('menu', 'free_text', 'default', 'user_edit')),
    CONSTRAINT conversation_session_revisions_unique
        UNIQUE (session_id, revision_number, slot_path)
);

CREATE INDEX conversation_session_revisions_session_created_idx
    ON conversation_session_revisions (session_id, created_at);

COMMENT ON TABLE conversation_sessions IS
    'Session-level normalized request state used by deterministic merge orchestration.';
COMMENT ON TABLE conversation_session_revisions IS
    'Append-only slot merge provenance log with deterministic source tags for ADR-015.';
