-- ADR-002 recommendation read-model performance support.
-- These indexes keep the normalized source tables efficient for the common
-- v_recommendable_arrangements filters without introducing cache invalidation,
-- materialized view refreshes, or bypasses around approval/provenance gating.

CREATE INDEX IF NOT EXISTS arrangements_recommendable_filter_idx
    ON arrangements (language, musical_key, tempo_bpm, energy_level, id)
    WHERE is_active
      AND musical_key IS NOT NULL
      AND key_mode IN ('MAJOR', 'MINOR')
      AND tempo_bpm IS NOT NULL
      AND time_signature IS NOT NULL
      AND energy_level IS NOT NULL;

CREATE INDEX IF NOT EXISTS approval_records_song_approved_gate_idx
    ON approval_records (song_id, approval_type)
    WHERE song_id IS NOT NULL
      AND status = 'APPROVED';

CREATE INDEX IF NOT EXISTS approval_records_arrangement_approved_gate_idx
    ON approval_records (arrangement_id, approval_type)
    WHERE arrangement_id IS NOT NULL
      AND status = 'APPROVED';

CREATE INDEX IF NOT EXISTS approval_records_lyrics_approved_gate_idx
    ON approval_records (lyrics_document_id, approval_type)
    WHERE lyrics_document_id IS NOT NULL
      AND status = 'APPROVED';

CREATE INDEX IF NOT EXISTS tags_active_type_slug_idx
    ON tags (tag_type, slug, id)
    WHERE is_active;

CREATE INDEX IF NOT EXISTS song_tags_tag_song_idx
    ON song_tags (tag_id, song_id);

CREATE INDEX IF NOT EXISTS arrangement_tags_tag_arrangement_idx
    ON arrangement_tags (tag_id, arrangement_id);

CREATE INDEX IF NOT EXISTS lyrics_document_tags_tag_lyrics_document_idx
    ON lyrics_document_tags (tag_id, lyrics_document_id);

COMMENT ON INDEX arrangements_recommendable_filter_idx IS
    'Supports ADR-002 candidate filtering by language, key, BPM, and energy over active arrangements with complete musical metadata.';
COMMENT ON INDEX approval_records_song_approved_gate_idx IS
    'Supports approved-only song-level gates used by v_recommendable_arrangements.';
COMMENT ON INDEX approval_records_arrangement_approved_gate_idx IS
    'Supports approved-only arrangement-level gates used by v_recommendable_arrangements.';
COMMENT ON INDEX approval_records_lyrics_approved_gate_idx IS
    'Supports approved-only current lyrics gates used by v_recommendable_arrangements.';
COMMENT ON INDEX tags_active_type_slug_idx IS
    'Supports deterministic controlled-tag filtering by active tag type and slug.';
COMMENT ON INDEX song_tags_tag_song_idx IS
    'Supports reverse tag-to-song lookups for recommendable arrangement tag filters and reports.';
COMMENT ON INDEX arrangement_tags_tag_arrangement_idx IS
    'Supports reverse tag-to-arrangement lookups for recommendable arrangement tag filters and reports.';
COMMENT ON INDEX lyrics_document_tags_tag_lyrics_document_idx IS
    'Supports reverse tag-to-current-lyrics lookups for recommendable arrangement tag filters and reports.';
