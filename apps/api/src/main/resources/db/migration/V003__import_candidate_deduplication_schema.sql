-- Staged import candidate and deduplication review schema for ADR-003.
-- Raw candidates remain outside canonical songs, arrangements, lyrics_documents,
-- and recommendation read models until an explicit reviewed merge operation runs.

CREATE TABLE import_candidates (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    import_batch_id uuid NOT NULL REFERENCES import_batches (id) ON DELETE RESTRICT,
    external_candidate_id varchar(255),
    raw_title varchar(255) NOT NULL,
    normalized_title varchar(255) NOT NULL,
    source_artist_name varchar(255),
    source_artist_metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    ccli_number varchar(64),
    lyrics_hash varchar(128),
    source_payload jsonb NOT NULL,
    status varchar(32) NOT NULL DEFAULT 'STAGED',
    merged_song_id uuid REFERENCES songs (id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT import_candidates_external_candidate_id_not_blank CHECK (
        external_candidate_id IS NULL OR btrim(external_candidate_id) <> ''
    ),
    CONSTRAINT import_candidates_raw_title_not_blank CHECK (btrim(raw_title) <> ''),
    CONSTRAINT import_candidates_normalized_title_not_blank CHECK (btrim(normalized_title) <> ''),
    CONSTRAINT import_candidates_source_artist_name_not_blank CHECK (
        source_artist_name IS NULL OR btrim(source_artist_name) <> ''
    ),
    CONSTRAINT import_candidates_ccli_number_not_blank CHECK (ccli_number IS NULL OR btrim(ccli_number) <> ''),
    CONSTRAINT import_candidates_lyrics_hash_not_blank CHECK (lyrics_hash IS NULL OR btrim(lyrics_hash) <> ''),
    CONSTRAINT import_candidates_status_valid CHECK (
        status IN ('STAGED', 'DEDUPLICATION_REVIEW', 'READY_TO_MERGE', 'MERGED', 'REJECTED', 'FAILED')
    ),
    CONSTRAINT import_candidates_merged_song_requires_merged_status CHECK (
        (status = 'MERGED' AND merged_song_id IS NOT NULL)
        OR (status <> 'MERGED' AND merged_song_id IS NULL)
    ),
    CONSTRAINT import_candidates_updated_at_not_before_created_at CHECK (updated_at >= created_at)
);

CREATE INDEX import_candidates_import_batch_id_idx ON import_candidates (import_batch_id);
CREATE INDEX import_candidates_normalized_title_idx ON import_candidates (normalized_title);
CREATE INDEX import_candidates_status_idx ON import_candidates (status);
CREATE INDEX import_candidates_batch_status_idx ON import_candidates (import_batch_id, status);
CREATE INDEX import_candidates_merged_song_id_idx ON import_candidates (merged_song_id) WHERE merged_song_id IS NOT NULL;

CREATE INDEX import_candidates_ccli_number_idx
    ON import_candidates (ccli_number)
    WHERE ccli_number IS NOT NULL;

CREATE INDEX import_candidates_lyrics_hash_idx
    ON import_candidates (lyrics_hash)
    WHERE lyrics_hash IS NOT NULL;

CREATE TABLE proposed_duplicate_matches (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    import_candidate_id uuid NOT NULL REFERENCES import_candidates (id) ON DELETE CASCADE,
    candidate_song_id uuid NOT NULL REFERENCES songs (id) ON DELETE RESTRICT,
    match_score numeric(5, 4) NOT NULL,
    match_signals jsonb NOT NULL DEFAULT '{}'::jsonb,
    status varchar(32) NOT NULL DEFAULT 'PROPOSED',
    suggested_by varchar(255) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT proposed_duplicate_matches_score_range CHECK (match_score BETWEEN 0 AND 1),
    CONSTRAINT proposed_duplicate_matches_status_valid CHECK (status IN ('PROPOSED', 'REVIEWED', 'SUPERSEDED')),
    CONSTRAINT proposed_duplicate_matches_suggested_by_not_blank CHECK (btrim(suggested_by) <> ''),
    CONSTRAINT proposed_duplicate_matches_updated_at_not_before_created_at CHECK (updated_at >= created_at),
    CONSTRAINT proposed_duplicate_matches_candidate_song_unique UNIQUE (import_candidate_id, candidate_song_id)
);

CREATE INDEX proposed_duplicate_matches_import_candidate_id_idx
    ON proposed_duplicate_matches (import_candidate_id);
CREATE INDEX proposed_duplicate_matches_candidate_song_id_idx ON proposed_duplicate_matches (candidate_song_id);
CREATE INDEX proposed_duplicate_matches_status_idx ON proposed_duplicate_matches (status);
CREATE INDEX proposed_duplicate_matches_score_idx ON proposed_duplicate_matches (match_score DESC);

CREATE TABLE import_candidate_reviews (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    import_candidate_id uuid NOT NULL REFERENCES import_candidates (id) ON DELETE RESTRICT,
    proposed_duplicate_match_id uuid REFERENCES proposed_duplicate_matches (id) ON DELETE RESTRICT,
    decision varchar(32) NOT NULL,
    reviewer varchar(255) NOT NULL,
    review_notes text,
    reviewed_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT import_candidate_reviews_decision_valid CHECK (
        decision IN ('CONFIRM_MATCH', 'REJECT_MATCH', 'CREATE_NEW_SONG', 'REJECT_CANDIDATE', 'NEEDS_MORE_INFO')
    ),
    CONSTRAINT import_candidate_reviews_match_decision_requires_match CHECK (
        (decision IN ('CONFIRM_MATCH', 'REJECT_MATCH') AND proposed_duplicate_match_id IS NOT NULL)
        OR (decision NOT IN ('CONFIRM_MATCH', 'REJECT_MATCH'))
    ),
    CONSTRAINT import_candidate_reviews_reviewer_not_blank CHECK (btrim(reviewer) <> '')
);

CREATE INDEX import_candidate_reviews_import_candidate_id_idx ON import_candidate_reviews (import_candidate_id);
CREATE INDEX import_candidate_reviews_proposed_duplicate_match_id_idx
    ON import_candidate_reviews (proposed_duplicate_match_id)
    WHERE proposed_duplicate_match_id IS NOT NULL;
CREATE INDEX import_candidate_reviews_decision_idx ON import_candidate_reviews (decision);
CREATE INDEX import_candidate_reviews_reviewer_idx ON import_candidate_reviews (reviewer);
CREATE INDEX import_candidate_reviews_reviewed_at_idx ON import_candidate_reviews (reviewed_at DESC);

COMMENT ON TABLE import_candidates IS 'Raw staged song import candidates captured from an import batch; these rows are never recommendable canonical catalog content.';
COMMENT ON COLUMN import_candidates.import_batch_id IS 'Import batch that captured this raw candidate.';
COMMENT ON COLUMN import_candidates.external_candidate_id IS 'Optional source-side row, record, or payload identifier for audit and idempotency.';
COMMENT ON COLUMN import_candidates.raw_title IS 'Title exactly as provided by the source payload.';
COMMENT ON COLUMN import_candidates.normalized_title IS 'Deterministic title normalization used for lookup and duplicate heuristics.';
COMMENT ON COLUMN import_candidates.source_artist_name IS 'Artist display text as provided by the source when available.';
COMMENT ON COLUMN import_candidates.source_artist_metadata IS 'Structured source artist metadata retained for deterministic review and audit.';
COMMENT ON COLUMN import_candidates.ccli_number IS 'Optional source-provided CCLI song number used for exact duplicate lookup.';
COMMENT ON COLUMN import_candidates.lyrics_hash IS 'Optional hash of allowed source text used for deterministic duplicate lookup.';
COMMENT ON COLUMN import_candidates.source_payload IS 'Complete source payload retained for audit; do not discard raw import evidence.';
COMMENT ON COLUMN import_candidates.status IS 'Staged import lifecycle state before reviewed merge into canonical catalog records.';
COMMENT ON COLUMN import_candidates.merged_song_id IS 'Canonical song created or updated by an explicit reviewed merge operation.';

COMMENT ON TABLE proposed_duplicate_matches IS 'Deterministic duplicate suggestions generated from explainable heuristics; reviewer decisions are recorded separately.';
COMMENT ON COLUMN proposed_duplicate_matches.import_candidate_id IS 'Staged candidate that may duplicate a canonical song.';
COMMENT ON COLUMN proposed_duplicate_matches.candidate_song_id IS 'Canonical song suggested as a possible duplicate.';
COMMENT ON COLUMN proposed_duplicate_matches.match_score IS 'Deterministic heuristic score from 0 to 1; never sufficient for automatic merge.';
COMMENT ON COLUMN proposed_duplicate_matches.match_signals IS 'Signal-level explanation such as normalized-title, artist, CCLI, and lyrics-hash evidence.';
COMMENT ON COLUMN proposed_duplicate_matches.status IS 'Operational state of the suggestion independent from human review decisions.';
COMMENT ON COLUMN proposed_duplicate_matches.suggested_by IS 'Deterministic rule set, job, or fixture that produced the suggestion.';

COMMENT ON TABLE import_candidate_reviews IS 'Manual reviewer decisions for staged candidates and proposed duplicate matches.';
COMMENT ON COLUMN import_candidate_reviews.import_candidate_id IS 'Staged import candidate under review.';
COMMENT ON COLUMN import_candidate_reviews.proposed_duplicate_match_id IS 'Specific proposed match under review when the decision confirms or rejects a duplicate.';
COMMENT ON COLUMN import_candidate_reviews.decision IS 'Manual review outcome that gates merge or create-new operations.';
COMMENT ON COLUMN import_candidate_reviews.reviewer IS 'Authorized human reviewer or controlled test process responsible for the decision.';
COMMENT ON COLUMN import_candidate_reviews.review_notes IS 'Optional explanation for the manual review decision.';
