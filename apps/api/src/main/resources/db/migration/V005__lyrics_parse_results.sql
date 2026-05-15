ALTER TABLE lyrics_documents
    ADD COLUMN parse_status varchar(32) NOT NULL DEFAULT 'NOT_REQUESTED',
    ADD COLUMN parse_error text,
    ADD COLUMN parser_name varchar(128),
    ADD COLUMN parser_version varchar(64),
    ADD COLUMN parsed_at timestamptz,
    ADD COLUMN parsed_sections jsonb,
    ADD COLUMN chord_map jsonb,
    ADD COLUMN structural_markers jsonb,
    ADD CONSTRAINT lyrics_documents_parse_status_valid
        CHECK (parse_status IN ('NOT_REQUESTED', 'PARSED', 'FAILED', 'UNSUPPORTED')),
    ADD CONSTRAINT lyrics_documents_parse_success_payload
        CHECK (
            parse_status <> 'PARSED'
            OR (
                parse_error IS NULL
                AND parser_name IS NOT NULL
                AND parser_version IS NOT NULL
                AND parsed_at IS NOT NULL
                AND parsed_sections IS NOT NULL
                AND chord_map IS NOT NULL
                AND structural_markers IS NOT NULL
            )
        ),
    ADD CONSTRAINT lyrics_documents_parse_failure_error
        CHECK (parse_status NOT IN ('FAILED', 'UNSUPPORTED') OR btrim(parse_error) <> '');

CREATE INDEX lyrics_documents_parse_status_idx ON lyrics_documents (parse_status);
CREATE INDEX lyrics_documents_parsed_sections_gin_idx ON lyrics_documents USING gin (parsed_sections);
CREATE INDEX lyrics_documents_chord_map_gin_idx ON lyrics_documents USING gin (chord_map);
CREATE INDEX lyrics_documents_structural_markers_gin_idx ON lyrics_documents USING gin (structural_markers);

COMMENT ON COLUMN lyrics_documents.parse_status IS
    'Derived lyrics parser status. Raw content remains authoritative regardless of this value.';
COMMENT ON COLUMN lyrics_documents.parse_error IS
    'Non-blocking parser failure or unsupported-parser detail; raw content is retained unchanged.';
COMMENT ON COLUMN lyrics_documents.parser_name IS 'Deterministic parser identifier used for derived structures.';
COMMENT ON COLUMN lyrics_documents.parser_version IS 'Deterministic parser version used for derived structures.';
COMMENT ON COLUMN lyrics_documents.parsed_at IS 'Timestamp when derived parser output was recorded.';
COMMENT ON COLUMN lyrics_documents.parsed_sections IS 'Derived section structure JSON. Does not replace lyrics_documents.content.';
COMMENT ON COLUMN lyrics_documents.chord_map IS 'Derived chord occurrence JSON. Does not replace lyrics_documents.content.';
COMMENT ON COLUMN lyrics_documents.structural_markers IS
    'Derived structural marker JSON. Does not replace lyrics_documents.content.';
