-- Core normalized catalog schema for ADR-001.
-- PostgreSQL is the source of truth for curated songs, arrangements, lyrics,
-- tags, provenance, approvals, and import batches. Recommendation read models
-- are intentionally excluded and will be introduced by ADR-002.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE songs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    canonical_title varchar(255) NOT NULL,
    normalized_title varchar(255) NOT NULL,
    primary_language varchar(16) NOT NULL,
    original_artist_display varchar(255),
    composer_credits text,
    ccli_number varchar(64),
    year_written integer,
    song_status varchar(32) NOT NULL DEFAULT 'DRAFT',
    doctrinal_notes text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT songs_canonical_title_not_blank CHECK (btrim(canonical_title) <> ''),
    CONSTRAINT songs_normalized_title_not_blank CHECK (btrim(normalized_title) <> ''),
    CONSTRAINT songs_primary_language_not_blank CHECK (btrim(primary_language) <> ''),
    CONSTRAINT songs_year_written_reasonable CHECK (year_written IS NULL OR year_written BETWEEN 1 AND 9999),
    CONSTRAINT songs_song_status_valid CHECK (song_status IN ('DRAFT', 'IN_REVIEW', 'APPROVED', 'REJECTED', 'ARCHIVED')),
    CONSTRAINT songs_updated_at_not_before_created_at CHECK (updated_at >= created_at),
    CONSTRAINT songs_normalized_title_language_unique UNIQUE (normalized_title, primary_language)
);

CREATE UNIQUE INDEX songs_ccli_number_unique_idx
    ON songs (ccli_number)
    WHERE ccli_number IS NOT NULL;

CREATE INDEX songs_canonical_title_idx ON songs (canonical_title);
CREATE INDEX songs_normalized_title_idx ON songs (normalized_title);
CREATE INDEX songs_song_status_idx ON songs (song_status);

CREATE TABLE arrangements (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    song_id uuid NOT NULL REFERENCES songs (id) ON DELETE RESTRICT,
    name varchar(255) NOT NULL,
    normalized_name varchar(255) NOT NULL,
    source_type varchar(32) NOT NULL,
    language varchar(16) NOT NULL,
    musical_key varchar(8),
    key_mode varchar(16),
    tempo_bpm integer,
    time_signature varchar(16),
    duration_seconds integer,
    energy_level integer,
    difficulty_level integer,
    default_for_song boolean NOT NULL DEFAULT false,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT arrangements_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT arrangements_normalized_name_not_blank CHECK (btrim(normalized_name) <> ''),
    CONSTRAINT arrangements_source_type_valid CHECK (source_type IN ('ORIGINAL', 'LIVE', 'ACOUSTIC', 'STUDIO', 'TRANSLATION', 'CUSTOM', 'UNKNOWN')),
    CONSTRAINT arrangements_language_not_blank CHECK (btrim(language) <> ''),
    CONSTRAINT arrangements_key_mode_valid CHECK (key_mode IS NULL OR key_mode IN ('MAJOR', 'MINOR', 'MODAL', 'UNKNOWN')),
    CONSTRAINT arrangements_tempo_bpm_positive CHECK (tempo_bpm IS NULL OR tempo_bpm > 0),
    CONSTRAINT arrangements_duration_seconds_positive CHECK (duration_seconds IS NULL OR duration_seconds > 0),
    CONSTRAINT arrangements_energy_level_range CHECK (energy_level IS NULL OR energy_level BETWEEN 1 AND 5),
    CONSTRAINT arrangements_difficulty_level_range CHECK (difficulty_level IS NULL OR difficulty_level BETWEEN 1 AND 5),
    CONSTRAINT arrangements_updated_at_not_before_created_at CHECK (updated_at >= created_at),
    CONSTRAINT arrangements_song_name_language_unique UNIQUE (song_id, normalized_name, language)
);

CREATE UNIQUE INDEX arrangements_default_for_song_unique_idx
    ON arrangements (song_id)
    WHERE default_for_song;

CREATE INDEX arrangements_song_id_idx ON arrangements (song_id);
CREATE INDEX arrangements_normalized_name_idx ON arrangements (normalized_name);
CREATE INDEX arrangements_language_idx ON arrangements (language);
CREATE INDEX arrangements_musical_key_idx ON arrangements (musical_key);
CREATE INDEX arrangements_tempo_bpm_idx ON arrangements (tempo_bpm);
CREATE INDEX arrangements_is_active_idx ON arrangements (is_active);

CREATE TABLE lyrics_documents (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    arrangement_id uuid NOT NULL REFERENCES arrangements (id) ON DELETE RESTRICT,
    format varchar(32) NOT NULL,
    content text NOT NULL,
    content_hash varchar(128) NOT NULL,
    version_number integer NOT NULL,
    is_current boolean NOT NULL DEFAULT false,
    contains_chords boolean NOT NULL DEFAULT false,
    contains_sections boolean NOT NULL DEFAULT false,
    source_reference text NOT NULL,
    created_by varchar(255) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT lyrics_documents_format_valid CHECK (format IN ('PLAIN_TEXT', 'CHORDPRO', 'OPENLYRICS', 'MARKDOWN', 'PDF_REFERENCE')),
    CONSTRAINT lyrics_documents_content_not_blank CHECK (btrim(content) <> ''),
    CONSTRAINT lyrics_documents_content_hash_not_blank CHECK (btrim(content_hash) <> ''),
    CONSTRAINT lyrics_documents_version_number_positive CHECK (version_number > 0),
    CONSTRAINT lyrics_documents_source_reference_not_blank CHECK (btrim(source_reference) <> ''),
    CONSTRAINT lyrics_documents_created_by_not_blank CHECK (btrim(created_by) <> ''),
    CONSTRAINT lyrics_documents_arrangement_version_unique UNIQUE (arrangement_id, version_number),
    CONSTRAINT lyrics_documents_arrangement_hash_unique UNIQUE (arrangement_id, content_hash)
);

CREATE UNIQUE INDEX lyrics_documents_current_unique_idx
    ON lyrics_documents (arrangement_id)
    WHERE is_current;

CREATE INDEX lyrics_documents_arrangement_id_idx ON lyrics_documents (arrangement_id);
CREATE INDEX lyrics_documents_content_hash_idx ON lyrics_documents (content_hash);
CREATE INDEX lyrics_documents_format_idx ON lyrics_documents (format);

CREATE TABLE tags (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tag_type varchar(32) NOT NULL,
    name varchar(128) NOT NULL,
    slug varchar(128) NOT NULL,
    description text,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT tags_tag_type_valid CHECK (tag_type IN ('THEME', 'SCRIPTURE', 'LITURGICAL_SEASON', 'SONG_ROLE', 'MOOD', 'STYLE', 'TOPIC')),
    CONSTRAINT tags_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT tags_slug_not_blank CHECK (btrim(slug) <> ''),
    CONSTRAINT tags_updated_at_not_before_created_at CHECK (updated_at >= created_at),
    CONSTRAINT tags_tag_type_slug_unique UNIQUE (tag_type, slug)
);

CREATE INDEX tags_name_idx ON tags (name);
CREATE INDEX tags_slug_idx ON tags (slug);
CREATE INDEX tags_tag_type_idx ON tags (tag_type);
CREATE INDEX tags_is_active_idx ON tags (is_active);

CREATE TABLE song_tags (
    song_id uuid NOT NULL REFERENCES songs (id) ON DELETE CASCADE,
    tag_id uuid NOT NULL REFERENCES tags (id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (song_id, tag_id)
);

CREATE INDEX song_tags_song_id_idx ON song_tags (song_id);
CREATE INDEX song_tags_tag_id_idx ON song_tags (tag_id);

CREATE TABLE arrangement_tags (
    arrangement_id uuid NOT NULL REFERENCES arrangements (id) ON DELETE CASCADE,
    tag_id uuid NOT NULL REFERENCES tags (id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (arrangement_id, tag_id)
);

CREATE INDEX arrangement_tags_arrangement_id_idx ON arrangement_tags (arrangement_id);
CREATE INDEX arrangement_tags_tag_id_idx ON arrangement_tags (tag_id);

CREATE TABLE import_batches (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    source_system varchar(128) NOT NULL,
    initiated_by varchar(255) NOT NULL,
    status varchar(32) NOT NULL DEFAULT 'PENDING',
    summary_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    started_at timestamptz NOT NULL DEFAULT now(),
    completed_at timestamptz,
    CONSTRAINT import_batches_source_system_not_blank CHECK (btrim(source_system) <> ''),
    CONSTRAINT import_batches_initiated_by_not_blank CHECK (btrim(initiated_by) <> ''),
    CONSTRAINT import_batches_status_valid CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    CONSTRAINT import_batches_completed_after_started CHECK (completed_at IS NULL OR completed_at >= started_at)
);

CREATE INDEX import_batches_source_system_idx ON import_batches (source_system);
CREATE INDEX import_batches_status_idx ON import_batches (status);
CREATE INDEX import_batches_started_at_idx ON import_batches (started_at);

CREATE TABLE provenance_records (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    song_id uuid REFERENCES songs (id) ON DELETE RESTRICT,
    arrangement_id uuid REFERENCES arrangements (id) ON DELETE RESTRICT,
    lyrics_document_id uuid REFERENCES lyrics_documents (id) ON DELETE RESTRICT,
    import_batch_id uuid NOT NULL REFERENCES import_batches (id) ON DELETE RESTRICT,
    source_system varchar(128) NOT NULL,
    source_uri text,
    source_label varchar(255) NOT NULL,
    license_type varchar(64) NOT NULL,
    license_notes text,
    import_method varchar(64) NOT NULL,
    confidence_score numeric(5, 4),
    captured_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT provenance_records_one_entity CHECK (num_nonnulls(song_id, arrangement_id, lyrics_document_id) = 1),
    CONSTRAINT provenance_records_source_system_not_blank CHECK (btrim(source_system) <> ''),
    CONSTRAINT provenance_records_source_label_not_blank CHECK (btrim(source_label) <> ''),
    CONSTRAINT provenance_records_license_type_valid CHECK (license_type IN ('PUBLIC_DOMAIN', 'CCLI', 'DIRECT_PERMISSION', 'FAIR_USE_REFERENCE', 'UNKNOWN', 'NOT_APPLICABLE')),
    CONSTRAINT provenance_records_import_method_valid CHECK (import_method IN ('MANUAL_ENTRY', 'CSV_IMPORT', 'API_IMPORT', 'SCRAPER_REVIEWED', 'TEST_FIXTURE')),
    CONSTRAINT provenance_records_confidence_score_range CHECK (confidence_score IS NULL OR confidence_score BETWEEN 0 AND 1)
);

CREATE INDEX provenance_records_song_id_idx ON provenance_records (song_id);
CREATE INDEX provenance_records_arrangement_id_idx ON provenance_records (arrangement_id);
CREATE INDEX provenance_records_lyrics_document_id_idx ON provenance_records (lyrics_document_id);
CREATE INDEX provenance_records_import_batch_id_idx ON provenance_records (import_batch_id);
CREATE INDEX provenance_records_source_system_idx ON provenance_records (source_system);
CREATE INDEX provenance_records_captured_at_idx ON provenance_records (captured_at);

CREATE TABLE approval_records (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    song_id uuid REFERENCES songs (id) ON DELETE RESTRICT,
    arrangement_id uuid REFERENCES arrangements (id) ON DELETE RESTRICT,
    lyrics_document_id uuid REFERENCES lyrics_documents (id) ON DELETE RESTRICT,
    approval_type varchar(64) NOT NULL,
    status varchar(32) NOT NULL,
    reviewer varchar(255) NOT NULL,
    review_notes text,
    reviewed_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT approval_records_one_entity CHECK (num_nonnulls(song_id, arrangement_id, lyrics_document_id) = 1),
    CONSTRAINT approval_records_approval_type_valid CHECK (approval_type IN ('DOCTRINAL', 'EDITORIAL', 'MUSICAL', 'COPYRIGHT', 'CATALOG_INCLUSION')),
    CONSTRAINT approval_records_status_valid CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'NEEDS_CHANGES', 'REVOKED')),
    CONSTRAINT approval_records_reviewer_not_blank CHECK (btrim(reviewer) <> ''),
    CONSTRAINT approval_records_reviewed_not_before_created CHECK (reviewed_at >= created_at)
);

CREATE INDEX approval_records_song_id_idx ON approval_records (song_id);
CREATE INDEX approval_records_arrangement_id_idx ON approval_records (arrangement_id);
CREATE INDEX approval_records_lyrics_document_id_idx ON approval_records (lyrics_document_id);
CREATE INDEX approval_records_approval_type_idx ON approval_records (approval_type);
CREATE INDEX approval_records_status_idx ON approval_records (status);
CREATE INDEX approval_records_reviewed_at_idx ON approval_records (reviewed_at);

CREATE INDEX approval_records_song_type_reviewed_at_idx
    ON approval_records (song_id, approval_type, reviewed_at DESC)
    WHERE song_id IS NOT NULL;

CREATE INDEX approval_records_arrangement_type_reviewed_at_idx
    ON approval_records (arrangement_id, approval_type, reviewed_at DESC)
    WHERE arrangement_id IS NOT NULL;

CREATE INDEX approval_records_lyrics_type_reviewed_at_idx
    ON approval_records (lyrics_document_id, approval_type, reviewed_at DESC)
    WHERE lyrics_document_id IS NOT NULL;
