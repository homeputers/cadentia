CREATE TABLE parser_run_history (
    id uuid PRIMARY KEY,
    lyrics_document_id uuid NOT NULL REFERENCES lyrics_documents (id) ON DELETE RESTRICT,
    parser_name varchar(128) NOT NULL,
    parser_version varchar(64) NOT NULL,
    source_content_hash varchar(128) NOT NULL,
    trigger_type varchar(64) NOT NULL,
    actor varchar(128) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    supersedes_run_id uuid REFERENCES parser_run_history (id) ON DELETE RESTRICT,
    superseded_by_run_id uuid REFERENCES parser_run_history (id) ON DELETE RESTRICT,
    parse_status varchar(32) NOT NULL,
    warnings_json jsonb NOT NULL DEFAULT '[]'::jsonb,
    confidence_snapshot_json jsonb NOT NULL DEFAULT '[]'::jsonb,
    CONSTRAINT parser_run_history_parse_status_valid
        CHECK (parse_status IN ('NOT_REQUESTED', 'PARSED', 'FAILED', 'UNSUPPORTED'))
);

CREATE INDEX parser_run_history_lyrics_document_created_idx
    ON parser_run_history (lyrics_document_id, created_at);
