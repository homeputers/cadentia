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
    sort_order integer NOT NULL DEFAULT 0,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT tags_tag_type_valid CHECK (tag_type IN ('THEME', 'MOOD', 'OCCASION', 'SCRIPTURE', 'SEASON', 'MUSICAL_STYLE', 'AUDIENCE')),
    CONSTRAINT tags_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT tags_slug_not_blank CHECK (btrim(slug) <> ''),
    CONSTRAINT tags_sort_order_non_negative CHECK (sort_order >= 0),
    CONSTRAINT tags_updated_at_not_before_created_at CHECK (updated_at >= created_at),
    CONSTRAINT tags_tag_type_slug_unique UNIQUE (tag_type, slug),
    CONSTRAINT tags_tag_type_id_unique UNIQUE (tag_type, id)
);

CREATE UNIQUE INDEX tags_tag_type_lower_name_unique_idx ON tags (tag_type, lower(name));
CREATE INDEX tags_name_idx ON tags (name);
CREATE INDEX tags_slug_idx ON tags (slug);
CREATE INDEX tags_tag_type_name_idx ON tags (tag_type, name);
CREATE INDEX tags_tag_type_slug_idx ON tags (tag_type, slug);
CREATE INDEX tags_tag_type_idx ON tags (tag_type);
CREATE INDEX tags_is_active_idx ON tags (is_active);

CREATE TABLE tag_aliases (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tag_type varchar(32) NOT NULL,
    tag_id uuid NOT NULL,
    alias_name varchar(128) NOT NULL,
    alias_slug varchar(128) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT tag_aliases_tag_type_valid CHECK (tag_type IN ('THEME', 'MOOD', 'OCCASION', 'SCRIPTURE', 'SEASON', 'MUSICAL_STYLE', 'AUDIENCE')),
    CONSTRAINT tag_aliases_alias_name_not_blank CHECK (btrim(alias_name) <> ''),
    CONSTRAINT tag_aliases_alias_slug_not_blank CHECK (btrim(alias_slug) <> ''),
    CONSTRAINT tag_aliases_tag_type_tag_id_fkey FOREIGN KEY (tag_type, tag_id)
        REFERENCES tags (tag_type, id) ON DELETE RESTRICT,
    CONSTRAINT tag_aliases_tag_type_alias_slug_unique UNIQUE (tag_type, alias_slug)
);

CREATE UNIQUE INDEX tag_aliases_tag_type_lower_alias_name_unique_idx ON tag_aliases (tag_type, lower(alias_name));
CREATE INDEX tag_aliases_tag_id_idx ON tag_aliases (tag_id);
CREATE INDEX tag_aliases_tag_type_alias_slug_idx ON tag_aliases (tag_type, alias_slug);

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

CREATE TABLE lyrics_document_tags (
    lyrics_document_id uuid NOT NULL REFERENCES lyrics_documents (id) ON DELETE CASCADE,
    tag_id uuid NOT NULL REFERENCES tags (id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (lyrics_document_id, tag_id)
);

CREATE INDEX lyrics_document_tags_lyrics_document_id_idx ON lyrics_document_tags (lyrics_document_id);
CREATE INDEX lyrics_document_tags_tag_id_idx ON lyrics_document_tags (tag_id);

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

COMMENT ON TABLE songs IS 'Canonical song identities curated by Cadentia; recommendation agents must only use persisted songs from this catalog.';
COMMENT ON COLUMN songs.id IS 'Database-generated canonical song identifier.';
COMMENT ON COLUMN songs.canonical_title IS 'Human-readable primary title for the canonical song.';
COMMENT ON COLUMN songs.normalized_title IS 'Search and deduplication title normalized by application import logic.';
COMMENT ON COLUMN songs.primary_language IS 'Primary ISO-like language code for the canonical song text.';
COMMENT ON COLUMN songs.original_artist_display IS 'Display name for the original or best-known artist when known.';
COMMENT ON COLUMN songs.composer_credits IS 'Composer, author, translator, or publishing credits retained as structured text pending richer credit modeling.';
COMMENT ON COLUMN songs.ccli_number IS 'Optional CCLI song number used for deterministic lookup and licensing workflows.';
COMMENT ON COLUMN songs.year_written IS 'Optional year the song was written or first published.';
COMMENT ON COLUMN songs.song_status IS 'Catalog lifecycle state for the canonical song record.';
COMMENT ON COLUMN songs.doctrinal_notes IS 'Review notes about doctrinal concerns or affirmations for the canonical song.';
COMMENT ON COLUMN songs.created_at IS 'Database timestamp when the canonical song was created.';
COMMENT ON COLUMN songs.updated_at IS 'Database timestamp when the canonical song was last updated.';

COMMENT ON TABLE arrangements IS 'Musical arrangements belonging to canonical songs, including key, tempo, and worship-team usability metadata.';
COMMENT ON COLUMN arrangements.id IS 'Database-generated arrangement identifier.';
COMMENT ON COLUMN arrangements.song_id IS 'Canonical song that owns this arrangement.';
COMMENT ON COLUMN arrangements.name IS 'Human-readable arrangement name or version label.';
COMMENT ON COLUMN arrangements.normalized_name IS 'Search and deduplication label normalized by application import logic.';
COMMENT ON COLUMN arrangements.source_type IS 'Controlled source category describing how this arrangement originated.';
COMMENT ON COLUMN arrangements.language IS 'Language code for this arrangement, which may differ from the canonical song language for translations.';
COMMENT ON COLUMN arrangements.musical_key IS 'Preferred notated key center for this arrangement.';
COMMENT ON COLUMN arrangements.key_mode IS 'Controlled mode classification for the arrangement key.';
COMMENT ON COLUMN arrangements.tempo_bpm IS 'Preferred arrangement tempo in beats per minute.';
COMMENT ON COLUMN arrangements.time_signature IS 'Preferred time signature for the arrangement.';
COMMENT ON COLUMN arrangements.duration_seconds IS 'Expected arrangement duration in seconds.';
COMMENT ON COLUMN arrangements.energy_level IS 'Curated musical energy rating from 1 to 5.';
COMMENT ON COLUMN arrangements.difficulty_level IS 'Curated worship-team difficulty rating from 1 to 5.';
COMMENT ON COLUMN arrangements.default_for_song IS 'Marks the single default arrangement for the owning song when one is curated.';
COMMENT ON COLUMN arrangements.is_active IS 'Whether this arrangement is active for catalog workflows.';
COMMENT ON COLUMN arrangements.created_at IS 'Database timestamp when the arrangement was created.';
COMMENT ON COLUMN arrangements.updated_at IS 'Database timestamp when the arrangement was last updated.';

COMMENT ON TABLE lyrics_documents IS 'Versioned lyrics or chord documents traceable to a specific arrangement and source reference.';
COMMENT ON COLUMN lyrics_documents.id IS 'Database-generated lyrics document identifier.';
COMMENT ON COLUMN lyrics_documents.arrangement_id IS 'Arrangement that this lyrics document describes.';
COMMENT ON COLUMN lyrics_documents.format IS 'Controlled lyrics document format.';
COMMENT ON COLUMN lyrics_documents.content IS 'Lyrics or chord document content; full copyrighted content must only be stored with documented licensing.';
COMMENT ON COLUMN lyrics_documents.content_hash IS 'Application-computed content hash for deduplication and integrity checks.';
COMMENT ON COLUMN lyrics_documents.version_number IS 'Monotonic version number within the arrangement.';
COMMENT ON COLUMN lyrics_documents.is_current IS 'Marks the current lyrics document for the arrangement.';
COMMENT ON COLUMN lyrics_documents.contains_chords IS 'Whether the document includes chord symbols.';
COMMENT ON COLUMN lyrics_documents.contains_sections IS 'Whether the document includes structured song sections.';
COMMENT ON COLUMN lyrics_documents.source_reference IS 'Human-readable reference to the source used for this document.';
COMMENT ON COLUMN lyrics_documents.created_by IS 'User, process, or fixture identifier that created this document.';
COMMENT ON COLUMN lyrics_documents.created_at IS 'Database timestamp when the lyrics document was created.';

COMMENT ON TABLE tags IS 'Controlled taxonomy tags used to classify songs and arrangements for deterministic filtering.';
COMMENT ON COLUMN tags.id IS 'Database-generated tag identifier.';
COMMENT ON COLUMN tags.tag_type IS 'Controlled taxonomy category for the tag.';
COMMENT ON COLUMN tags.name IS 'Human-readable tag name.';
COMMENT ON COLUMN tags.slug IS 'Stable normalized tag slug unique within the tag type.';
COMMENT ON COLUMN tags.description IS 'Optional explanation of how the tag should be applied.';
COMMENT ON COLUMN tags.sort_order IS 'Admin-managed display order within a tag type; lower values sort first.';
COMMENT ON COLUMN tags.is_active IS 'Whether this tag is active for catalog workflows.';
COMMENT ON COLUMN tags.created_at IS 'Database timestamp when the tag was created.';
COMMENT ON COLUMN tags.updated_at IS 'Database timestamp when the tag was last updated.';

COMMENT ON TABLE tag_aliases IS 'Controlled alternate names for canonical taxonomy tags; aliases resolve to existing tags and never create production tags automatically.';
COMMENT ON COLUMN tag_aliases.tag_type IS 'Tag type copied from the canonical tag to enforce alias uniqueness within each type.';
COMMENT ON COLUMN tag_aliases.tag_id IS 'Canonical tag represented by this alias.';
COMMENT ON COLUMN tag_aliases.alias_name IS 'Human-readable alternate tag name accepted for lookup or admin review.';
COMMENT ON COLUMN tag_aliases.alias_slug IS 'Stable normalized alias slug unique within the tag type.';
COMMENT ON COLUMN tag_aliases.created_at IS 'Database timestamp when the alias was created.';

COMMENT ON TABLE song_tags IS 'Many-to-many mapping between canonical songs and taxonomy tags.';
COMMENT ON COLUMN song_tags.song_id IS 'Canonical song assigned to the tag.';
COMMENT ON COLUMN song_tags.tag_id IS 'Tag assigned to the canonical song.';
COMMENT ON COLUMN song_tags.created_at IS 'Database timestamp when the song-tag mapping was created.';

COMMENT ON TABLE arrangement_tags IS 'Many-to-many mapping between arrangements and taxonomy tags.';
COMMENT ON COLUMN arrangement_tags.arrangement_id IS 'Arrangement assigned to the tag.';
COMMENT ON COLUMN arrangement_tags.tag_id IS 'Tag assigned to the arrangement.';
COMMENT ON COLUMN arrangement_tags.created_at IS 'Database timestamp when the arrangement-tag mapping was created.';

COMMENT ON TABLE lyrics_document_tags IS 'Many-to-many mapping between lyrics documents and taxonomy tags.';
COMMENT ON COLUMN lyrics_document_tags.lyrics_document_id IS 'Lyrics document assigned to the tag.';
COMMENT ON COLUMN lyrics_document_tags.tag_id IS 'Tag assigned to the lyrics document.';
COMMENT ON COLUMN lyrics_document_tags.created_at IS 'Database timestamp when the lyrics-document-tag mapping was created.';

COMMENT ON TABLE import_batches IS 'Auditable batches for catalog import or fixture loading workflows.';
COMMENT ON COLUMN import_batches.id IS 'Database-generated import batch identifier.';
COMMENT ON COLUMN import_batches.source_system IS 'External system, fixture, or manual source for the batch.';
COMMENT ON COLUMN import_batches.initiated_by IS 'User or process that initiated the import batch.';
COMMENT ON COLUMN import_batches.status IS 'Controlled lifecycle state for the import batch.';
COMMENT ON COLUMN import_batches.summary_json IS 'Structured import summary such as counts, warnings, and non-authoritative diagnostics.';
COMMENT ON COLUMN import_batches.started_at IS 'Database timestamp when the import batch started.';
COMMENT ON COLUMN import_batches.completed_at IS 'Database timestamp when the import batch completed, failed, or was cancelled.';

COMMENT ON TABLE provenance_records IS 'First-class provenance evidence tying songs, arrangements, or lyrics documents to reviewed source material.';
COMMENT ON COLUMN provenance_records.id IS 'Database-generated provenance record identifier.';
COMMENT ON COLUMN provenance_records.song_id IS 'Song described by this provenance record when the record targets a song.';
COMMENT ON COLUMN provenance_records.arrangement_id IS 'Arrangement described by this provenance record when the record targets an arrangement.';
COMMENT ON COLUMN provenance_records.lyrics_document_id IS 'Lyrics document described by this provenance record when the record targets a lyrics document.';
COMMENT ON COLUMN provenance_records.import_batch_id IS 'Import batch that captured this provenance record.';
COMMENT ON COLUMN provenance_records.source_system IS 'System, catalog, publisher, or fixture source where the evidence came from.';
COMMENT ON COLUMN provenance_records.source_uri IS 'Optional URI for the source evidence when available and safe to store.';
COMMENT ON COLUMN provenance_records.source_label IS 'Human-readable source citation or label.';
COMMENT ON COLUMN provenance_records.license_type IS 'Controlled license or permission classification for the source evidence.';
COMMENT ON COLUMN provenance_records.license_notes IS 'Additional licensing, attribution, or review notes.';
COMMENT ON COLUMN provenance_records.import_method IS 'Controlled method used to capture the provenance evidence.';
COMMENT ON COLUMN provenance_records.confidence_score IS 'Optional confidence score from 0 to 1 assigned by deterministic import/review logic.';
COMMENT ON COLUMN provenance_records.captured_at IS 'Database timestamp when the provenance evidence was captured.';

COMMENT ON TABLE approval_records IS 'First-class review decisions for songs, arrangements, or lyrics documents.';
COMMENT ON COLUMN approval_records.id IS 'Database-generated approval record identifier.';
COMMENT ON COLUMN approval_records.song_id IS 'Song reviewed by this approval record when the record targets a song.';
COMMENT ON COLUMN approval_records.arrangement_id IS 'Arrangement reviewed by this approval record when the record targets an arrangement.';
COMMENT ON COLUMN approval_records.lyrics_document_id IS 'Lyrics document reviewed by this approval record when the record targets a lyrics document.';
COMMENT ON COLUMN approval_records.approval_type IS 'Controlled review category such as doctrinal, musical, editorial, copyright, or catalog inclusion.';
COMMENT ON COLUMN approval_records.status IS 'Controlled review outcome or current approval state.';
COMMENT ON COLUMN approval_records.reviewer IS 'Human reviewer, review group, or controlled process responsible for the decision.';
COMMENT ON COLUMN approval_records.review_notes IS 'Review notes explaining the decision, rejection, or required changes.';
COMMENT ON COLUMN approval_records.reviewed_at IS 'Database timestamp for when the review decision was made.';
COMMENT ON COLUMN approval_records.created_at IS 'Database timestamp when the approval record was created.';
