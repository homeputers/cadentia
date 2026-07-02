-- ADR-026 search projection baseline.
-- Canonical catalog tables remain the source of truth; these tables are derived
-- artifacts that may be truncated and rebuilt from catalog, approval,
-- provenance, package, lyrics, tag, and arrangement data.

CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE approved_search_documents (
    document_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    instance_id uuid,
    song_id uuid NOT NULL REFERENCES songs (id) ON DELETE CASCADE,
    arrangement_id uuid REFERENCES arrangements (id) ON DELETE CASCADE,
    document_type varchar(32) NOT NULL DEFAULT 'SONG',
    canonical_title varchar(255) NOT NULL,
    alternate_titles jsonb NOT NULL DEFAULT '[]'::jsonb,
    scripture_references jsonb NOT NULL DEFAULT '[]'::jsonb,
    approved_tags jsonb NOT NULL DEFAULT '[]'::jsonb,
    contributors jsonb NOT NULL DEFAULT '[]'::jsonb,
    musical_key varchar(8),
    key_mode varchar(16),
    tempo_bpm integer,
    meter varchar(16),
    arrangement_label varchar(255),
    lyrics_metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    package_references jsonb NOT NULL DEFAULT '[]'::jsonb,
    source_references jsonb NOT NULL DEFAULT '[]'::jsonb,
    search_text text NOT NULL,
    search_vector tsvector GENERATED ALWAYS AS (to_tsvector('simple', search_text)) STORED,
    projection_version integer NOT NULL DEFAULT 1,
    source_updated_at timestamptz NOT NULL,
    indexed_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT approved_search_documents_type_valid CHECK (document_type IN ('SONG', 'ARRANGEMENT')),
    CONSTRAINT approved_search_documents_title_not_blank CHECK (btrim(canonical_title) <> ''),
    CONSTRAINT approved_search_documents_search_text_not_blank CHECK (btrim(search_text) <> ''),
    CONSTRAINT approved_search_documents_tempo_positive CHECK (tempo_bpm IS NULL OR tempo_bpm > 0)
);

CREATE UNIQUE INDEX approved_search_documents_song_arrangement_instance_unique_idx
    ON approved_search_documents (song_id, COALESCE(arrangement_id, '00000000-0000-0000-0000-000000000000'::uuid), COALESCE(instance_id, '00000000-0000-0000-0000-000000000000'::uuid));
CREATE INDEX approved_search_documents_search_vector_idx ON approved_search_documents USING gin (search_vector);
CREATE INDEX approved_search_documents_search_text_trgm_idx ON approved_search_documents USING gin (search_text gin_trgm_ops);
CREATE INDEX approved_search_documents_title_trgm_idx ON approved_search_documents USING gin (canonical_title gin_trgm_ops);
CREATE INDEX approved_search_documents_instance_idx ON approved_search_documents (instance_id);
CREATE INDEX approved_search_documents_arrangement_idx ON approved_search_documents (arrangement_id);
CREATE INDEX approved_search_documents_key_bpm_idx ON approved_search_documents (musical_key, tempo_bpm);
CREATE INDEX approved_search_documents_scripture_idx ON approved_search_documents USING gin (scripture_references jsonb_path_ops);
CREATE INDEX approved_search_documents_tags_idx ON approved_search_documents USING gin (approved_tags jsonb_path_ops);

CREATE TABLE approved_search_projection_events (
    event_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    source_entity_type varchar(64) NOT NULL,
    source_entity_id uuid NOT NULL,
    projection_action varchar(32) NOT NULL,
    projection_status varchar(32) NOT NULL DEFAULT 'PENDING',
    changed_at timestamptz NOT NULL DEFAULT now(),
    processed_at timestamptz,
    error_message text,
    CONSTRAINT approved_search_projection_events_action_valid CHECK (projection_action IN ('UPSERT', 'DELETE', 'REBUILD')),
    CONSTRAINT approved_search_projection_events_status_valid CHECK (projection_status IN ('PENDING', 'PROCESSED', 'FAILED'))
);

CREATE INDEX approved_search_projection_events_pending_idx
    ON approved_search_projection_events (projection_status, changed_at);

CREATE TABLE admin_review_search_documents (
    document_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    source_entity_type varchar(64) NOT NULL,
    source_entity_id uuid NOT NULL,
    review_state varchar(64) NOT NULL,
    restricted_payload jsonb NOT NULL,
    search_text text NOT NULL,
    search_vector tsvector GENERATED ALWAYS AS (to_tsvector('simple', search_text)) STORED,
    indexed_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT admin_review_search_documents_search_text_not_blank CHECK (btrim(search_text) <> '')
);

CREATE INDEX admin_review_search_documents_search_vector_idx ON admin_review_search_documents USING gin (search_vector);
CREATE INDEX admin_review_search_documents_search_text_trgm_idx ON admin_review_search_documents USING gin (search_text gin_trgm_ops);
CREATE INDEX admin_review_search_documents_entity_idx ON admin_review_search_documents (source_entity_type, source_entity_id);

COMMENT ON TABLE approved_search_documents IS
    'User-facing ADR-026 approved discovery index. Contains only approved, active, visible, license-safe projection data rebuilt from canonical catalog sources.';
COMMENT ON TABLE admin_review_search_documents IS
    'Privileged future admin-review search index. It is intentionally separate from approved_search_documents and must require admin authorization before query or rendering.';
COMMENT ON TABLE approved_search_projection_events IS
    'Traceable ADR-026 queue/audit table for incremental approved search projection updates and rebuild bookkeeping.';
