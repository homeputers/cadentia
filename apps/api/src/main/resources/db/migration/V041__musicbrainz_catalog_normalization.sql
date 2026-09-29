CREATE TABLE musicbrainz_entities (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type varchar(32) NOT NULL,
    mbid uuid NOT NULL,
    display_name varchar(500) NOT NULL,
    normalized_key varchar(500) NOT NULL,
    source_uri text NOT NULL,
    metadata_json jsonb NOT NULL,
    fetched_at timestamptz NOT NULL DEFAULT now(),
    response_hash varchar(128) NOT NULL,
    CONSTRAINT musicbrainz_entities_type_valid CHECK (entity_type IN ('ARTIST', 'RELEASE_GROUP', 'RELEASE', 'RECORDING')),
    CONSTRAINT musicbrainz_entities_display_name_not_blank CHECK (btrim(display_name) <> ''),
    CONSTRAINT musicbrainz_entities_normalized_key_not_blank CHECK (btrim(normalized_key) <> ''),
    CONSTRAINT musicbrainz_entities_source_uri_not_blank CHECK (btrim(source_uri) <> ''),
    CONSTRAINT musicbrainz_entities_response_hash_not_blank CHECK (btrim(response_hash) <> ''),
    CONSTRAINT musicbrainz_entities_type_mbid_unique UNIQUE (entity_type, mbid)
);

CREATE INDEX musicbrainz_entities_normalized_key_idx ON musicbrainz_entities (normalized_key);
CREATE INDEX musicbrainz_entities_fetched_at_idx ON musicbrainz_entities (fetched_at);

CREATE TABLE catalog_musicbrainz_proposals (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    song_id uuid NOT NULL REFERENCES songs (id) ON DELETE CASCADE,
    arrangement_id uuid REFERENCES arrangements (id) ON DELETE CASCADE,
    artist_mbid uuid,
    release_group_mbid uuid,
    release_mbid uuid,
    recording_mbid uuid NOT NULL,
    score numeric(5, 4) NOT NULL,
    match_method varchar(64) NOT NULL,
    status varchar(32) NOT NULL DEFAULT 'PROPOSED',
    source_uri text NOT NULL,
    source_payload jsonb NOT NULL,
    created_by varchar(255) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT musicbrainz_proposals_score_range CHECK (score BETWEEN 0 AND 1),
    CONSTRAINT musicbrainz_proposals_match_method_not_blank CHECK (btrim(match_method) <> ''),
    CONSTRAINT musicbrainz_proposals_status_valid CHECK (status IN ('PROPOSED', 'ACCEPTED', 'REJECTED', 'SUPERSEDED')),
    CONSTRAINT musicbrainz_proposals_source_uri_not_blank CHECK (btrim(source_uri) <> ''),
    CONSTRAINT musicbrainz_proposals_created_by_not_blank CHECK (btrim(created_by) <> ''),
    CONSTRAINT musicbrainz_proposals_updated_at_not_before_created_at CHECK (updated_at >= created_at)
);

CREATE INDEX musicbrainz_proposals_song_id_idx ON catalog_musicbrainz_proposals (song_id);
CREATE INDEX musicbrainz_proposals_recording_mbid_idx ON catalog_musicbrainz_proposals (recording_mbid);
CREATE INDEX musicbrainz_proposals_status_idx ON catalog_musicbrainz_proposals (status);

CREATE TABLE catalog_musicbrainz_links (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    song_id uuid NOT NULL REFERENCES songs (id) ON DELETE CASCADE,
    arrangement_id uuid REFERENCES arrangements (id) ON DELETE CASCADE,
    proposal_id uuid NOT NULL REFERENCES catalog_musicbrainz_proposals (id) ON DELETE RESTRICT,
    artist_mbid uuid,
    release_group_mbid uuid,
    release_mbid uuid,
    recording_mbid uuid NOT NULL,
    status varchar(32) NOT NULL DEFAULT 'ACCEPTED',
    score numeric(5, 4) NOT NULL,
    match_method varchar(64) NOT NULL,
    selected_fields jsonb NOT NULL DEFAULT '[]'::jsonb,
    reviewer varchar(255) NOT NULL,
    rationale text NOT NULL,
    accepted_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT musicbrainz_links_reviewer_not_blank CHECK (btrim(reviewer) <> ''),
    CONSTRAINT musicbrainz_links_rationale_not_blank CHECK (btrim(rationale) <> ''),
    CONSTRAINT musicbrainz_links_status_valid CHECK (status IN ('ACCEPTED', 'SUPERSEDED')),
    CONSTRAINT musicbrainz_links_score_range CHECK (score BETWEEN 0 AND 1),
    CONSTRAINT musicbrainz_links_match_method_not_blank CHECK (btrim(match_method) <> ''),
    CONSTRAINT musicbrainz_links_selected_fields_is_array CHECK (jsonb_typeof(selected_fields) = 'array')
);

CREATE INDEX musicbrainz_links_song_id_idx ON catalog_musicbrainz_links (song_id);
CREATE INDEX musicbrainz_links_recording_mbid_idx ON catalog_musicbrainz_links (recording_mbid);
CREATE UNIQUE INDEX musicbrainz_links_active_song_recording_unique
    ON catalog_musicbrainz_links (song_id, COALESCE(arrangement_id, '00000000-0000-0000-0000-000000000000'::uuid), recording_mbid)
    WHERE status = 'ACCEPTED';
