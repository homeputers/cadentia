CREATE OR REPLACE VIEW v_recommendable_arrangement_tags AS
SELECT DISTINCT
    recommendable.arrangement_id,
    assigned_tags.tag_id,
    assigned_tags.tag_type,
    assigned_tags.tag_slug,
    assigned_tags.tag_name,
    assigned_tags.sort_order
FROM v_recommendable_arrangements recommendable
JOIN LATERAL (
    SELECT tags.id AS tag_id,
           tags.tag_type,
           tags.slug AS tag_slug,
           tags.name AS tag_name,
           tags.sort_order
    FROM song_tags
    JOIN tags ON tags.id = song_tags.tag_id
    WHERE song_tags.song_id = recommendable.song_id
      AND tags.is_active
    UNION
    SELECT tags.id AS tag_id,
           tags.tag_type,
           tags.slug AS tag_slug,
           tags.name AS tag_name,
           tags.sort_order
    FROM arrangement_tags
    JOIN tags ON tags.id = arrangement_tags.tag_id
    WHERE arrangement_tags.arrangement_id = recommendable.arrangement_id
      AND tags.is_active
    UNION
    SELECT tags.id AS tag_id,
           tags.tag_type,
           tags.slug AS tag_slug,
           tags.name AS tag_name,
           tags.sort_order
    FROM lyrics_document_tags
    JOIN tags ON tags.id = lyrics_document_tags.tag_id
    WHERE lyrics_document_tags.lyrics_document_id = recommendable.current_lyrics_document_id
      AND tags.is_active
) assigned_tags ON true;

COMMENT ON VIEW v_recommendable_arrangement_tags IS
    'Approved recommendation candidate tags expanded by controlled tag identity for deterministic tag filtering, explanations, and reporting.';
COMMENT ON COLUMN v_recommendable_arrangement_tags.arrangement_id IS
    'Approved recommendable arrangement carrying this controlled tag.';
COMMENT ON COLUMN v_recommendable_arrangement_tags.tag_id IS
    'Canonical controlled tag identifier assigned to the song, arrangement, or current lyrics document.';
COMMENT ON COLUMN v_recommendable_arrangement_tags.tag_type IS
    'Controlled taxonomy category used by recommendation filters and reporting groups.';
COMMENT ON COLUMN v_recommendable_arrangement_tags.tag_slug IS
    'Stable canonical tag slug used for deterministic recommendation filters.';
COMMENT ON COLUMN v_recommendable_arrangement_tags.tag_name IS
    'Human-readable canonical tag name used in recommendation explanations and reports.';
COMMENT ON COLUMN v_recommendable_arrangement_tags.sort_order IS
    'Admin-managed display order for deterministic reporting output.';
