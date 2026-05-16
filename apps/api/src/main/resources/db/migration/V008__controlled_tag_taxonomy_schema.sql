-- ADR-007 controlled taxonomy schema lifecycle additions.
-- Existing migrations remain immutable; this migration evolves the ADR-001 tag
-- schema to the ADR-007 controlled vocabulary contract.

ALTER TABLE tags
    DROP CONSTRAINT tags_tag_type_valid;

ALTER TABLE tags
    ADD COLUMN sort_order integer NOT NULL DEFAULT 0;

ALTER TABLE tags
    ADD CONSTRAINT tags_tag_type_valid
        CHECK (tag_type IN ('THEME', 'MOOD', 'OCCASION', 'SCRIPTURE', 'SEASON', 'MUSICAL_STYLE', 'AUDIENCE')),
    ADD CONSTRAINT tags_sort_order_non_negative
        CHECK (sort_order >= 0),
    ADD CONSTRAINT tags_tag_type_id_unique
        UNIQUE (tag_type, id);

CREATE UNIQUE INDEX tags_tag_type_lower_name_unique_idx ON tags (tag_type, lower(name));
CREATE INDEX tags_tag_type_name_idx ON tags (tag_type, name);
CREATE INDEX tags_tag_type_slug_idx ON tags (tag_type, slug);

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

CREATE TABLE lyrics_document_tags (
    lyrics_document_id uuid NOT NULL REFERENCES lyrics_documents (id) ON DELETE CASCADE,
    tag_id uuid NOT NULL REFERENCES tags (id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (lyrics_document_id, tag_id)
);

CREATE INDEX lyrics_document_tags_lyrics_document_id_idx ON lyrics_document_tags (lyrics_document_id);
CREATE INDEX lyrics_document_tags_tag_id_idx ON lyrics_document_tags (tag_id);

COMMENT ON COLUMN tags.sort_order IS 'Admin-managed display order within a tag type; lower values sort first.';
COMMENT ON TABLE tag_aliases IS 'Controlled alternate names for canonical taxonomy tags; aliases resolve to existing tags and never create production tags automatically.';
COMMENT ON COLUMN tag_aliases.tag_type IS 'Tag type copied from the canonical tag to enforce alias uniqueness within each type.';
COMMENT ON COLUMN tag_aliases.tag_id IS 'Canonical tag represented by this alias.';
COMMENT ON COLUMN tag_aliases.alias_name IS 'Human-readable alternate tag name accepted for lookup or admin review.';
COMMENT ON COLUMN tag_aliases.alias_slug IS 'Stable normalized alias slug unique within the tag type.';
COMMENT ON COLUMN tag_aliases.created_at IS 'Database timestamp when the alias was created.';
COMMENT ON TABLE lyrics_document_tags IS 'Many-to-many mapping between lyrics documents and taxonomy tags.';
COMMENT ON COLUMN lyrics_document_tags.lyrics_document_id IS 'Lyrics document assigned to the tag.';
COMMENT ON COLUMN lyrics_document_tags.tag_id IS 'Tag assigned to the lyrics document.';
COMMENT ON COLUMN lyrics_document_tags.created_at IS 'Database timestamp when the lyrics-document-tag mapping was created.';

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

COMMENT ON COLUMN v_recommendable_arrangements.tags IS
    'Deterministically ordered active tag slugs assigned to the song, arrangement, or current lyrics document for transparent candidate filtering.';
