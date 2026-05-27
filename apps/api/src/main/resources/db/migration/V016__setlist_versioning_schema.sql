CREATE TABLE setlists (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    lineage_policy varchar(16) NOT NULL DEFAULT 'LINEAR',
    created_by varchar(255) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT setlists_lineage_policy_valid CHECK (lineage_policy IN ('LINEAR', 'BRANCHED')),
    CONSTRAINT setlists_created_by_not_blank CHECK (btrim(created_by) <> '')
);

CREATE TABLE setlist_versions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    setlist_id uuid NOT NULL REFERENCES setlists (id) ON DELETE CASCADE,
    parent_version_id uuid REFERENCES setlist_versions (id) ON DELETE RESTRICT,
    version_number integer NOT NULL,
    provenance_type varchar(32) NOT NULL,
    request_payload jsonb NOT NULL,
    parsed_intent_payload jsonb NOT NULL,
    explanation_facts jsonb NOT NULL DEFAULT '[]'::jsonb,
    scoring_profile_version varchar(128) NOT NULL,
    engine_version varchar(128) NOT NULL,
    created_by varchar(255) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    commit_summary text,
    CONSTRAINT setlist_versions_version_number_positive CHECK (version_number > 0),
    CONSTRAINT setlist_versions_provenance_type_valid CHECK (provenance_type IN ('GENERATED_BASELINE', 'MANUAL_EDIT')),
    CONSTRAINT setlist_versions_created_by_not_blank CHECK (btrim(created_by) <> ''),
    CONSTRAINT setlist_versions_setlist_version_unique UNIQUE (setlist_id, version_number)
);

CREATE TABLE setlist_version_items (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version_id uuid NOT NULL REFERENCES setlist_versions (id) ON DELETE CASCADE,
    position_index integer NOT NULL,
    catalog_arrangement_id uuid NOT NULL REFERENCES arrangements (id) ON DELETE RESTRICT,
    transposed_key varchar(8),
    transposed_mode varchar(16),
    source_item_id uuid REFERENCES setlist_version_items (id) ON DELETE SET NULL,
    item_provenance varchar(32) NOT NULL,
    notes text,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT setlist_version_items_position_index_non_negative CHECK (position_index >= 0),
    CONSTRAINT setlist_version_items_transposed_mode_valid CHECK (
        transposed_mode IS NULL OR transposed_mode IN ('MAJOR', 'MINOR', 'MODAL', 'UNKNOWN')
    ),
    CONSTRAINT setlist_version_items_item_provenance_valid CHECK (item_provenance IN ('GENERATED', 'MANUAL')),
    CONSTRAINT setlist_version_items_version_position_unique UNIQUE (version_id, position_index)
);

CREATE TABLE setlist_edit_commits (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    setlist_id uuid NOT NULL REFERENCES setlists (id) ON DELETE CASCADE,
    base_version_id uuid NOT NULL REFERENCES setlist_versions (id) ON DELETE RESTRICT,
    created_version_id uuid NOT NULL REFERENCES setlist_versions (id) ON DELETE RESTRICT,
    committed_by varchar(255) NOT NULL,
    committed_at timestamptz NOT NULL DEFAULT now(),
    summary text,
    CONSTRAINT setlist_edit_commits_committed_by_not_blank CHECK (btrim(committed_by) <> ''),
    CONSTRAINT setlist_edit_commits_created_version_unique UNIQUE (created_version_id)
);

CREATE TABLE setlist_edit_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    commit_id uuid NOT NULL REFERENCES setlist_edit_commits (id) ON DELETE CASCADE,
    event_index integer NOT NULL,
    event_type varchar(32) NOT NULL,
    item_id uuid,
    from_position integer,
    to_position integer,
    replacement_arrangement_id uuid REFERENCES arrangements (id) ON DELETE RESTRICT,
    transpose_to_key varchar(8),
    transpose_to_mode varchar(16),
    removed boolean,
    payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT setlist_edit_events_event_index_non_negative CHECK (event_index >= 0),
    CONSTRAINT setlist_edit_events_event_type_valid CHECK (event_type IN ('REORDER', 'REPLACE', 'REMOVE', 'TRANSPOSE')),
    CONSTRAINT setlist_edit_events_transpose_to_mode_valid CHECK (
        transpose_to_mode IS NULL OR transpose_to_mode IN ('MAJOR', 'MINOR', 'MODAL', 'UNKNOWN')
    ),
    CONSTRAINT setlist_edit_events_commit_event_unique UNIQUE (commit_id, event_index)
);

CREATE INDEX setlist_versions_setlist_created_at_idx ON setlist_versions (setlist_id, created_at DESC);
CREATE INDEX setlist_versions_parent_version_id_idx ON setlist_versions (parent_version_id);
CREATE INDEX setlist_version_items_version_id_idx ON setlist_version_items (version_id);
CREATE INDEX setlist_version_items_catalog_arrangement_id_idx ON setlist_version_items (catalog_arrangement_id);
CREATE INDEX setlist_edit_commits_setlist_id_idx ON setlist_edit_commits (setlist_id);
CREATE INDEX setlist_edit_events_commit_id_idx ON setlist_edit_events (commit_id);
