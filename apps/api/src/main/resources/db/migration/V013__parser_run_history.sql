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

COMMENT ON TABLE parser_run_history IS
    'Append-only parser run lineage for derived lyrics metadata recalculation provenance.';
COMMENT ON COLUMN parser_run_history.id IS 'Unique parser run identifier.';
COMMENT ON COLUMN parser_run_history.lyrics_document_id IS 'Lyrics document this parser run evaluates.';
COMMENT ON COLUMN parser_run_history.parser_name IS 'Deterministic parser identifier used for this run.';
COMMENT ON COLUMN parser_run_history.parser_version IS 'Deterministic parser implementation version used for this run.';
COMMENT ON COLUMN parser_run_history.source_content_hash IS 'Content hash snapshot used by the parser run.';
COMMENT ON COLUMN parser_run_history.trigger_type IS 'Why the run was triggered (for example, recalculation or import).';
COMMENT ON COLUMN parser_run_history.actor IS 'System or user actor that initiated the run.';
COMMENT ON COLUMN parser_run_history.created_at IS 'Timestamp when the parser run record was appended.';
COMMENT ON COLUMN parser_run_history.supersedes_run_id IS 'Immediate prior run superseded by this run.';
COMMENT ON COLUMN parser_run_history.superseded_by_run_id IS 'Immediate subsequent run that superseded this run.';
COMMENT ON COLUMN parser_run_history.parse_status IS 'Parse outcome status for this run.';
COMMENT ON COLUMN parser_run_history.warnings_json IS 'Warning snapshot captured for this run.';
COMMENT ON COLUMN parser_run_history.confidence_snapshot_json IS 'Confidence snapshot captured for this run.';
