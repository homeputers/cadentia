CREATE OR REPLACE VIEW v_recommendable_arrangements AS
SELECT
    arrangements.id AS arrangement_id,
    songs.id AS song_id,
    lyrics_documents.id AS current_lyrics_document_id,
    songs.canonical_title AS title,
    arrangements.language,
    arrangements.musical_key,
    arrangements.tempo_bpm AS bpm,
    arrangements.time_signature,
    arrangements.energy_level AS energy,
    COALESCE(
        array_agg(DISTINCT tags.slug ORDER BY tags.slug) FILTER (WHERE tags.id IS NOT NULL),
        ARRAY[]::text[]
    ) AS tags,
    song_doctrinal_approval.status AS song_doctrinal_status,
    song_editorial_approval.status AS song_editorial_status,
    song_licensing_approval.status AS song_licensing_status,
    arrangement_musical_approval.status AS arrangement_musical_status,
    arrangement_editorial_approval.status AS arrangement_editorial_status,
    lyrics_doctrinal_approval.status AS lyrics_doctrinal_status,
    lyrics_editorial_approval.status AS lyrics_editorial_status,
    lyrics_licensing_approval.status AS lyrics_licensing_status
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
LEFT JOIN arrangement_tags ON arrangement_tags.arrangement_id = arrangements.id
LEFT JOIN tags
  ON tags.id = arrangement_tags.tag_id
 AND tags.is_active
WHERE arrangements.is_active
  AND songs.song_status <> 'ARCHIVED'
  AND arrangements.musical_key IS NOT NULL
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

COMMENT ON VIEW v_recommendable_arrangements IS
    'Approval-gated recommendation candidate read model. Rows exist only when all required song, arrangement, and current lyrics approvals are approved.';
COMMENT ON COLUMN v_recommendable_arrangements.tags IS
    'Deterministically ordered active arrangement tag slugs for transparent candidate filtering.';
COMMENT ON COLUMN v_recommendable_arrangements.song_doctrinal_status IS
    'Song-level doctrinal approval status exposed for explainability; private review notes are intentionally excluded.';
COMMENT ON COLUMN v_recommendable_arrangements.song_editorial_status IS
    'Song-level editorial approval status exposed for explainability; private review notes are intentionally excluded.';
COMMENT ON COLUMN v_recommendable_arrangements.song_licensing_status IS
    'Song-level licensing approval status exposed for explainability; private review notes are intentionally excluded.';
COMMENT ON COLUMN v_recommendable_arrangements.arrangement_musical_status IS
    'Arrangement-level musical approval status exposed for explainability; private review notes are intentionally excluded.';
COMMENT ON COLUMN v_recommendable_arrangements.arrangement_editorial_status IS
    'Arrangement-level editorial approval status exposed for explainability; private review notes are intentionally excluded.';
COMMENT ON COLUMN v_recommendable_arrangements.lyrics_doctrinal_status IS
    'Current lyrics document doctrinal approval status exposed for explainability; private review notes are intentionally excluded.';
COMMENT ON COLUMN v_recommendable_arrangements.lyrics_editorial_status IS
    'Current lyrics document editorial approval status exposed for explainability; private review notes are intentionally excluded.';
COMMENT ON COLUMN v_recommendable_arrangements.lyrics_licensing_status IS
    'Current lyrics document licensing approval status exposed for explainability; private review notes are intentionally excluded.';
