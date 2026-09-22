-- First-class setlist role classification for songs (praise/worship).
-- The stored value is a language-neutral code so recommendation behavior never
-- depends on localized tag names typed by editors. NULL means unclassified; the
-- recommendation engine then falls back to the legacy praise/worship tag slugs.

ALTER TABLE songs
    ADD COLUMN song_role varchar(32),
    ADD CONSTRAINT songs_song_role_valid CHECK (song_role IS NULL OR song_role IN ('PRAISE', 'WORSHIP', 'BOTH'));

COMMENT ON COLUMN songs.song_role IS
    'Deterministic setlist role classification (PRAISE, WORSHIP, BOTH); NULL means unclassified and the recommendation engine falls back to praise/worship tag slugs.';

-- Backfill from the legacy tag-slug convention so existing curated catalogs do
-- not silently regress: a song is BOTH when praise and worship slugs are both
-- assigned at song, arrangement, or current-lyrics-document level.

WITH role_tags AS (
    SELECT song_tags.song_id, lower(tags.slug) AS role_slug
    FROM song_tags
    JOIN tags ON tags.id = song_tags.tag_id
    WHERE tags.is_active
    UNION
    SELECT arrangements.song_id, lower(tags.slug) AS role_slug
    FROM arrangement_tags
    JOIN arrangements ON arrangements.id = arrangement_tags.arrangement_id
    JOIN tags ON tags.id = arrangement_tags.tag_id
    WHERE tags.is_active
    UNION
    SELECT arrangements.song_id, lower(tags.slug) AS role_slug
    FROM lyrics_document_tags
    JOIN lyrics_documents ON lyrics_documents.id = lyrics_document_tags.lyrics_document_id
    JOIN arrangements ON arrangements.id = lyrics_documents.arrangement_id
    JOIN tags ON tags.id = lyrics_document_tags.tag_id
    WHERE lyrics_documents.is_current
      AND tags.is_active
),
classified AS (
    SELECT
        song_id,
        bool_or(role_slug = 'praise') AS has_praise,
        bool_or(role_slug = 'worship') AS has_worship
    FROM role_tags
    WHERE role_slug IN ('praise', 'worship')
    GROUP BY song_id
)
UPDATE songs
SET song_role = CASE
    WHEN classified.has_praise AND classified.has_worship THEN 'BOTH'
    WHEN classified.has_praise THEN 'PRAISE'
    ELSE 'WORSHIP'
END
FROM classified
WHERE classified.song_id = songs.id;

CREATE OR REPLACE VIEW v_recommendable_arrangements AS
SELECT
    arrangements.id AS arrangement_id,
    songs.id AS song_id,
    lyrics_documents.id AS current_lyrics_document_id,
    songs.canonical_title AS title,
    arrangements.language,
    arrangements.musical_key,
    arrangements.key_mode,
    arrangements.tempo_bpm AS bpm,
    arrangements.time_signature,
    arrangements.energy_level AS energy,
    COALESCE(
        array_agg(DISTINCT assigned_tags.slug::text ORDER BY assigned_tags.slug::text)
            FILTER (WHERE assigned_tags.slug IS NOT NULL),
        ARRAY[]::text[]
    ) AS tags,
    song_doctrinal_approval.status AS song_doctrinal_status,
    song_editorial_approval.status AS song_editorial_status,
    song_licensing_approval.status AS song_licensing_status,
    arrangement_musical_approval.status AS arrangement_musical_status,
    arrangement_editorial_approval.status AS arrangement_editorial_status,
    lyrics_doctrinal_approval.status AS lyrics_doctrinal_status,
    lyrics_editorial_approval.status AS lyrics_editorial_status,
    lyrics_licensing_approval.status AS lyrics_licensing_status,
    songs.song_role AS song_role
FROM arrangements
JOIN songs ON songs.id = arrangements.song_id
JOIN lyrics_documents
  ON lyrics_documents.arrangement_id = arrangements.id
 AND lyrics_documents.is_current
JOIN approval_records song_doctrinal_approval
  ON song_doctrinal_approval.song_id = songs.id
 AND song_doctrinal_approval.approval_type = 'DOCTRINAL'
 AND song_doctrinal_approval.status = 'APPROVED'
JOIN approval_records song_editorial_approval
  ON song_editorial_approval.song_id = songs.id
 AND song_editorial_approval.approval_type = 'EDITORIAL'
 AND song_editorial_approval.status = 'APPROVED'
JOIN approval_records song_licensing_approval
  ON song_licensing_approval.song_id = songs.id
 AND song_licensing_approval.approval_type = 'LICENSING'
 AND song_licensing_approval.status = 'APPROVED'
JOIN approval_records arrangement_musical_approval
  ON arrangement_musical_approval.arrangement_id = arrangements.id
 AND arrangement_musical_approval.approval_type = 'MUSICAL'
 AND arrangement_musical_approval.status = 'APPROVED'
JOIN approval_records arrangement_editorial_approval
  ON arrangement_editorial_approval.arrangement_id = arrangements.id
 AND arrangement_editorial_approval.approval_type = 'EDITORIAL'
 AND arrangement_editorial_approval.status = 'APPROVED'
JOIN approval_records lyrics_doctrinal_approval
  ON lyrics_doctrinal_approval.lyrics_document_id = lyrics_documents.id
 AND lyrics_doctrinal_approval.approval_type = 'DOCTRINAL'
 AND lyrics_doctrinal_approval.status = 'APPROVED'
JOIN approval_records lyrics_editorial_approval
  ON lyrics_editorial_approval.lyrics_document_id = lyrics_documents.id
 AND lyrics_editorial_approval.approval_type = 'EDITORIAL'
 AND lyrics_editorial_approval.status = 'APPROVED'
JOIN approval_records lyrics_licensing_approval
  ON lyrics_licensing_approval.lyrics_document_id = lyrics_documents.id
 AND lyrics_licensing_approval.approval_type = 'LICENSING'
 AND lyrics_licensing_approval.status = 'APPROVED'
LEFT JOIN LATERAL (
    SELECT tags.slug
    FROM song_tags
    JOIN tags ON tags.id = song_tags.tag_id
    WHERE song_tags.song_id = songs.id
      AND tags.is_active
    UNION
    SELECT tags.slug
    FROM arrangement_tags
    JOIN tags ON tags.id = arrangement_tags.tag_id
    WHERE arrangement_tags.arrangement_id = arrangements.id
      AND tags.is_active
    UNION
    SELECT tags.slug
    FROM lyrics_document_tags
    JOIN tags ON tags.id = lyrics_document_tags.tag_id
    WHERE lyrics_document_tags.lyrics_document_id = lyrics_documents.id
      AND tags.is_active
) assigned_tags ON true
WHERE arrangements.is_active
  AND songs.song_status <> 'ARCHIVED'
  AND arrangements.musical_key IS NOT NULL
  AND arrangements.key_mode IN ('MAJOR', 'MINOR')
  AND arrangements.tempo_bpm IS NOT NULL
  AND arrangements.time_signature IS NOT NULL
  AND arrangements.energy_level IS NOT NULL
GROUP BY
    arrangements.id,
    songs.id,
    lyrics_documents.id,
    song_doctrinal_approval.status,
    song_editorial_approval.status,
    song_licensing_approval.status,
    arrangement_musical_approval.status,
    arrangement_editorial_approval.status,
    lyrics_doctrinal_approval.status,
    lyrics_editorial_approval.status,
    lyrics_licensing_approval.status;

COMMENT ON COLUMN v_recommendable_arrangements.song_role IS
    'Explicit setlist role classification mirrored from songs.song_role; NULL falls back to praise/worship tag slugs in the scoring engine.';
