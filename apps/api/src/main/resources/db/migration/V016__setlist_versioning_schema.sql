CREATE TABLE setlists (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    lineage_policy varchar(16) NOT NULL DEFAULT 'LINEAR',
    created_by varchar(255) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT setlists_lineage_policy_valid CHECK (lineage_policy IN ('LINEAR', 'BRANCHED')),
    CONSTRAINT setlists_created_by_not_blank CHECK (btrim(created_by) <> '')
);
COMMENT ON TABLE setlists IS 'Logical setlist lineage root that groups immutable versions under one planning workflow.';
COMMENT ON COLUMN setlists.id IS 'Stable lineage identifier shared by all immutable setlist versions.';
COMMENT ON COLUMN setlists.lineage_policy IS 'Lineage strategy used by the workflow: LINEAR or BRANCHED parent progression.';
COMMENT ON COLUMN setlists.created_by IS 'Actor identifier that created the lineage root.';
COMMENT ON COLUMN setlists.created_at IS 'Timestamp when the lineage root was first persisted.';

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
COMMENT ON TABLE setlist_versions IS 'Immutable setlist snapshots containing deterministic generation context or manual edit outcomes.';
COMMENT ON COLUMN setlist_versions.id IS 'Immutable snapshot identifier for one setlist version.';
COMMENT ON COLUMN setlist_versions.setlist_id IS 'Owning lineage root for this immutable snapshot.';
COMMENT ON COLUMN setlist_versions.parent_version_id IS 'Optional parent snapshot reference used for linear or branched version graphs.';
COMMENT ON COLUMN setlist_versions.version_number IS 'Human-ordered version number unique within a setlist lineage.';
COMMENT ON COLUMN setlist_versions.provenance_type IS 'Snapshot provenance marker: GENERATED_BASELINE or MANUAL_EDIT.';
COMMENT ON COLUMN setlist_versions.request_payload IS 'Original generate request JSON preserved for reproducibility.';
COMMENT ON COLUMN setlist_versions.parsed_intent_payload IS 'Validated parsed intent JSON used by recommendation orchestration.';
COMMENT ON COLUMN setlist_versions.explanation_facts IS 'Deterministic explanation facts captured at snapshot creation.';
COMMENT ON COLUMN setlist_versions.scoring_profile_version IS 'Scoring profile identifier used to generate or evaluate the snapshot.';
COMMENT ON COLUMN setlist_versions.engine_version IS 'Recommendation engine build/version identifier for deterministic replay.';
COMMENT ON COLUMN setlist_versions.created_by IS 'Actor identifier that created this immutable snapshot.';
COMMENT ON COLUMN setlist_versions.created_at IS 'Timestamp when this immutable snapshot was persisted.';
COMMENT ON COLUMN setlist_versions.commit_summary IS 'Optional human-readable summary for the edit commit creating this version.';

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
COMMENT ON TABLE setlist_version_items IS 'Version-scoped ordered items for immutable snapshots, each referencing a catalog arrangement.';
COMMENT ON COLUMN setlist_version_items.id IS 'Stable item identifier within an immutable snapshot.';
COMMENT ON COLUMN setlist_version_items.version_id IS 'Owning immutable snapshot for the version-scoped item.';
COMMENT ON COLUMN setlist_version_items.position_index IS 'Zero-based order index for deterministic arrangement sequencing.';
COMMENT ON COLUMN setlist_version_items.catalog_arrangement_id IS 'Catalog arrangement selected for this slot; never a free-text song reference.';
COMMENT ON COLUMN setlist_version_items.transposed_key IS 'Service-specific transposed key override captured without mutating catalog canonical keys.';
COMMENT ON COLUMN setlist_version_items.transposed_mode IS 'Service-specific transposed mode override captured without mutating catalog canonical modes.';
COMMENT ON COLUMN setlist_version_items.source_item_id IS 'Optional prior-version item linkage used to trace item lineage across edits.';
COMMENT ON COLUMN setlist_version_items.item_provenance IS 'Item-level provenance marker: GENERATED or MANUAL.';
COMMENT ON COLUMN setlist_version_items.notes IS 'Optional operator notes for this version-scoped item.';
COMMENT ON COLUMN setlist_version_items.created_at IS 'Timestamp when the version-scoped item was persisted.';

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
COMMENT ON TABLE setlist_edit_commits IS 'Auditable edit commit metadata linking a base version to the newly created immutable version.';
COMMENT ON COLUMN setlist_edit_commits.id IS 'Edit commit identifier used to group granular edit events.';
COMMENT ON COLUMN setlist_edit_commits.setlist_id IS 'Lineage root that owns the edit commit.';
COMMENT ON COLUMN setlist_edit_commits.base_version_id IS 'Version snapshot used as edit input.';
COMMENT ON COLUMN setlist_edit_commits.created_version_id IS 'New immutable snapshot produced by applying this commit.';
COMMENT ON COLUMN setlist_edit_commits.committed_by IS 'Actor identifier that committed the edit operation.';
COMMENT ON COLUMN setlist_edit_commits.committed_at IS 'Timestamp when the edit commit was persisted.';
COMMENT ON COLUMN setlist_edit_commits.summary IS 'Optional human-readable summary of the grouped edit transaction.';

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
COMMENT ON TABLE setlist_edit_events IS 'Granular auditable edit operations (reorder/replace/remove/transpose) within a grouped edit commit.';
COMMENT ON COLUMN setlist_edit_events.id IS 'Edit event identifier.';
COMMENT ON COLUMN setlist_edit_events.commit_id IS 'Owning edit commit that groups this event with adjacent operations.';
COMMENT ON COLUMN setlist_edit_events.event_index IS 'Zero-based order index of this event within its edit commit.';
COMMENT ON COLUMN setlist_edit_events.event_type IS 'Operation type: REORDER, REPLACE, REMOVE, or TRANSPOSE.';
COMMENT ON COLUMN setlist_edit_events.item_id IS 'Target setlist item identifier when event acts on an existing item.';
COMMENT ON COLUMN setlist_edit_events.from_position IS 'Original item position before this event, when applicable.';
COMMENT ON COLUMN setlist_edit_events.to_position IS 'Destination item position after this event, when applicable.';
COMMENT ON COLUMN setlist_edit_events.replacement_arrangement_id IS 'Replacement catalog arrangement for REPLACE operations.';
COMMENT ON COLUMN setlist_edit_events.transpose_to_key IS 'Requested destination key for TRANSPOSE operations.';
COMMENT ON COLUMN setlist_edit_events.transpose_to_mode IS 'Requested destination mode for TRANSPOSE operations.';
COMMENT ON COLUMN setlist_edit_events.removed IS 'Explicit removal flag for REMOVE operations.';
COMMENT ON COLUMN setlist_edit_events.payload IS 'Structured event details for audit and diff reconstruction.';

CREATE INDEX setlist_versions_setlist_created_at_idx ON setlist_versions (setlist_id, created_at DESC);
CREATE INDEX setlist_versions_parent_version_id_idx ON setlist_versions (parent_version_id);
CREATE INDEX setlist_version_items_version_id_idx ON setlist_version_items (version_id);
CREATE INDEX setlist_version_items_catalog_arrangement_id_idx ON setlist_version_items (catalog_arrangement_id);
CREATE INDEX setlist_edit_commits_setlist_id_idx ON setlist_edit_commits (setlist_id);
CREATE INDEX setlist_edit_events_commit_id_idx ON setlist_edit_events (commit_id);
