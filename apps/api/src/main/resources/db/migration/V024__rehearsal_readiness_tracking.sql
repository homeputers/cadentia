CREATE TABLE readiness_statuses (
    code varchar(64) PRIMARY KEY,
    display_name varchar(128) NOT NULL,
    sort_order integer NOT NULL,
    CONSTRAINT readiness_statuses_code_valid CHECK (code IN ('UNKNOWN', 'READY', 'AT_RISK', 'BLOCKED')),
    CONSTRAINT readiness_statuses_display_name_not_blank CHECK (btrim(display_name) <> '')
);

INSERT INTO readiness_statuses (code, display_name, sort_order) VALUES
    ('UNKNOWN', 'Unknown', 0),
    ('READY', 'Ready', 10),
    ('AT_RISK', 'At risk', 20),
    ('BLOCKED', 'Blocked', 30);

CREATE TABLE readiness_notes (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    scope_type varchar(64) NOT NULL,
    scope_id uuid NOT NULL,
    service_plan_id uuid NOT NULL REFERENCES service_plans (id) ON DELETE CASCADE,
    rehearsal_event_id uuid REFERENCES rehearsal_events (id) ON DELETE CASCADE,
    service_assignment_id uuid REFERENCES service_team_assignments (id) ON DELETE CASCADE,
    song_assignment_override_id uuid REFERENCES service_song_assignment_overrides (id) ON DELETE CASCADE,
    service_plan_block_id uuid REFERENCES service_plan_blocks (id) ON DELETE CASCADE,
    arrangement_id uuid REFERENCES arrangements (id) ON DELETE RESTRICT,
    readiness_status_code varchar(64) NOT NULL REFERENCES readiness_statuses (code) ON DELETE RESTRICT,
    objective_blockers jsonb NOT NULL DEFAULT '[]'::jsonb,
    missing_people jsonb NOT NULL DEFAULT '[]'::jsonb,
    unresolved_arrangement_conflicts jsonb NOT NULL DEFAULT '[]'::jsonb,
    rehearsal_response_state varchar(64) NOT NULL DEFAULT 'NOT_REQUESTED',
    human_note text,
    privacy_classification varchar(64) NOT NULL DEFAULT 'TEAM_PRIVATE',
    override_action boolean NOT NULL DEFAULT false,
    updated_by varchar(255) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT readiness_notes_scope_type_valid CHECK (scope_type IN (
        'SERVICE_TEAM', 'REHEARSAL', 'MUSICIAN_ASSIGNMENT', 'SONG_ASSIGNMENT', 'ARRANGEMENT_CONFLICT'
    )),
    CONSTRAINT readiness_notes_privacy_valid CHECK (privacy_classification IN ('PUBLIC', 'TEAM_PRIVATE', 'PASTORAL_PRIVATE')),
    CONSTRAINT readiness_notes_response_state_valid CHECK (rehearsal_response_state IN (
        'NOT_REQUESTED', 'REQUESTED', 'ACKNOWLEDGED', 'ATTENDED', 'ABSENT', 'DECLINED', 'FOLLOW_UP_REQUIRED'
    )),
    CONSTRAINT readiness_notes_updated_by_not_blank CHECK (btrim(updated_by) <> ''),
    CONSTRAINT readiness_notes_json_arrays CHECK (
        jsonb_typeof(objective_blockers) = 'array'
        AND jsonb_typeof(missing_people) = 'array'
        AND jsonb_typeof(unresolved_arrangement_conflicts) = 'array'
    ),
    CONSTRAINT readiness_notes_service_scope_fk CHECK (
        scope_type <> 'SERVICE_TEAM' OR scope_id = service_plan_id
    ),
    CONSTRAINT readiness_notes_rehearsal_scope_fk CHECK (
        scope_type <> 'REHEARSAL' OR (rehearsal_event_id IS NOT NULL AND scope_id = rehearsal_event_id)
    ),
    CONSTRAINT readiness_notes_musician_assignment_scope_fk CHECK (
        scope_type <> 'MUSICIAN_ASSIGNMENT' OR (service_assignment_id IS NOT NULL AND scope_id = service_assignment_id)
    ),
    CONSTRAINT readiness_notes_song_assignment_scope_fk CHECK (
        scope_type <> 'SONG_ASSIGNMENT' OR (song_assignment_override_id IS NOT NULL AND scope_id = song_assignment_override_id)
    ),
    CONSTRAINT readiness_notes_arrangement_conflict_scope_fk CHECK (
        scope_type <> 'ARRANGEMENT_CONFLICT' OR (service_plan_block_id IS NOT NULL AND arrangement_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX readiness_notes_scope_unique_idx ON readiness_notes (scope_type, scope_id);
CREATE INDEX readiness_notes_service_plan_idx ON readiness_notes (service_plan_id, readiness_status_code);
CREATE INDEX readiness_notes_privacy_idx ON readiness_notes (privacy_classification);

CREATE OR REPLACE VIEW v_service_plan_readiness_summary AS
SELECT
    service_plans.id AS service_plan_id,
    CASE
        WHEN COUNT(readiness_notes.id) FILTER (WHERE readiness_notes.readiness_status_code = 'BLOCKED') > 0 THEN 'BLOCKED'
        WHEN COUNT(readiness_notes.id) FILTER (WHERE readiness_notes.readiness_status_code = 'AT_RISK') > 0 THEN 'AT_RISK'
        WHEN COUNT(readiness_notes.id) FILTER (WHERE readiness_notes.readiness_status_code = 'READY') > 0 THEN 'READY'
        ELSE 'UNKNOWN'
    END AS readiness_status_code,
    COALESCE(jsonb_agg(readiness_notes.objective_blockers) FILTER (WHERE readiness_notes.objective_blockers <> '[]'::jsonb), '[]'::jsonb) AS objective_blockers,
    COALESCE(jsonb_agg(readiness_notes.missing_people) FILTER (WHERE readiness_notes.missing_people <> '[]'::jsonb), '[]'::jsonb) AS missing_people,
    COALESCE(jsonb_agg(readiness_notes.unresolved_arrangement_conflicts) FILTER (WHERE readiness_notes.unresolved_arrangement_conflicts <> '[]'::jsonb), '[]'::jsonb) AS unresolved_arrangement_conflicts,
    COUNT(readiness_notes.id) FILTER (WHERE readiness_notes.human_note IS NOT NULL AND btrim(readiness_notes.human_note) <> '') AS private_note_count,
    MAX(readiness_notes.updated_at) AS last_updated_at
FROM service_plans
LEFT JOIN readiness_notes ON readiness_notes.service_plan_id = service_plans.id
GROUP BY service_plans.id;

CREATE OR REPLACE VIEW v_setlist_version_readiness_summary AS
SELECT
    setlist_versions.setlist_id,
    setlist_versions.id AS setlist_version_id,
    service_plan_blocks.service_plan_id,
    CASE
        WHEN COUNT(readiness_notes.id) FILTER (WHERE readiness_notes.readiness_status_code = 'BLOCKED') > 0 THEN 'BLOCKED'
        WHEN COUNT(readiness_notes.id) FILTER (WHERE readiness_notes.readiness_status_code = 'AT_RISK') > 0 THEN 'AT_RISK'
        WHEN COUNT(readiness_notes.id) FILTER (WHERE readiness_notes.readiness_status_code = 'READY') > 0 THEN 'READY'
        ELSE 'UNKNOWN'
    END AS readiness_status_code,
    COALESCE(jsonb_agg(readiness_notes.objective_blockers) FILTER (WHERE readiness_notes.objective_blockers <> '[]'::jsonb), '[]'::jsonb) AS objective_blockers,
    COALESCE(jsonb_agg(readiness_notes.missing_people) FILTER (WHERE readiness_notes.missing_people <> '[]'::jsonb), '[]'::jsonb) AS missing_people,
    COALESCE(jsonb_agg(readiness_notes.unresolved_arrangement_conflicts) FILTER (WHERE readiness_notes.unresolved_arrangement_conflicts <> '[]'::jsonb), '[]'::jsonb) AS unresolved_arrangement_conflicts,
    COUNT(readiness_notes.id) FILTER (WHERE readiness_notes.human_note IS NOT NULL AND btrim(readiness_notes.human_note) <> '') AS private_note_count,
    MAX(readiness_notes.updated_at) AS last_updated_at
FROM setlist_versions
JOIN setlist_version_items ON setlist_version_items.version_id = setlist_versions.id
JOIN v_recommendable_arrangements recommendable
  ON recommendable.arrangement_id = setlist_version_items.catalog_arrangement_id
LEFT JOIN service_plan_blocks
  ON service_plan_blocks.source_setlist_version_id = setlist_versions.id
 AND service_plan_blocks.source_setlist_item_id = setlist_version_items.id
LEFT JOIN readiness_notes
  ON readiness_notes.service_plan_id = service_plan_blocks.service_plan_id
 AND (
      readiness_notes.service_plan_block_id = service_plan_blocks.id
      OR readiness_notes.scope_type IN ('SERVICE_TEAM', 'REHEARSAL', 'MUSICIAN_ASSIGNMENT')
 )
GROUP BY setlist_versions.setlist_id, setlist_versions.id, service_plan_blocks.service_plan_id;

COMMENT ON TABLE readiness_notes IS
    'Operational rehearsal-readiness status and notes. Readiness does not approve songs, arrangements, lyrics, doctrine, or licensing.';
COMMENT ON COLUMN readiness_notes.objective_blockers IS
    'Structured operational blockers safe for readiness summaries, separate from human notes.';
COMMENT ON COLUMN readiness_notes.human_note IS
    'Human readiness note governed by privacy classification and privileged audit; never a catalog approval decision.';
COMMENT ON COLUMN readiness_notes.override_action IS
    'Operational readiness override indicator; never changes catalog approval or recommendation eligibility.';
COMMENT ON VIEW v_service_plan_readiness_summary IS
    'Service-plan readiness summary that aggregates structured blockers and note counts without exposing private note text.';
COMMENT ON VIEW v_setlist_version_readiness_summary IS
    'Setlist readiness summary limited to approved planned arrangements via v_recommendable_arrangements.';
