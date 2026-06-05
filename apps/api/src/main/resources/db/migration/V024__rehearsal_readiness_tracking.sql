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

-- ADR-024 service-scoped rehearsal workflow lifecycle schema. These tables model
-- explicit rehearsal workflow state independently from catalog approval and from
-- the legacy operational readiness summary above.
CREATE TABLE rehearsal_workflow_readiness_states (
    code varchar(64) PRIMARY KEY,
    display_name varchar(128) NOT NULL,
    terminal_state boolean NOT NULL DEFAULT false,
    sort_order integer NOT NULL,
    active boolean NOT NULL DEFAULT true,
    system_default boolean NOT NULL DEFAULT true,
    CONSTRAINT rehearsal_workflow_readiness_states_code_valid CHECK (
        code IN ('draft', 'planned', 'rehearsing', 'issues_open', 'ready', 'completed')
    ),
    CONSTRAINT rehearsal_workflow_readiness_states_display_name_not_blank CHECK (btrim(display_name) <> '')
);

INSERT INTO rehearsal_workflow_readiness_states (code, display_name, terminal_state, sort_order) VALUES
    ('draft', 'Draft', false, 10),
    ('planned', 'Planned', false, 20),
    ('rehearsing', 'Rehearsing', false, 30),
    ('issues_open', 'Issues open', false, 40),
    ('ready', 'Ready', false, 50),
    ('completed', 'Completed', true, 60);

CREATE TABLE rehearsal_issue_categories (
    code varchar(64) PRIMARY KEY,
    display_name varchar(128) NOT NULL,
    sort_order integer NOT NULL,
    active boolean NOT NULL DEFAULT true,
    system_default boolean NOT NULL DEFAULT true,
    CONSTRAINT rehearsal_issue_categories_code_valid CHECK (code IN (
        'unresolved_transition', 'difficult_song', 'blocker', 'arrangement_concern',
        'team_role_concern', 'general_follow_up'
    )),
    CONSTRAINT rehearsal_issue_categories_display_name_not_blank CHECK (btrim(display_name) <> '')
);

INSERT INTO rehearsal_issue_categories (code, display_name, sort_order) VALUES
    ('unresolved_transition', 'Unresolved transition', 10),
    ('difficult_song', 'Difficult song', 20),
    ('blocker', 'Blocker', 30),
    ('arrangement_concern', 'Arrangement concern', 40),
    ('team_role_concern', 'Team-role concern', 50),
    ('general_follow_up', 'General rehearsal follow-up', 60);

CREATE TABLE rehearsal_issue_severities (
    code varchar(64) PRIMARY KEY,
    display_name varchar(128) NOT NULL,
    sort_order integer NOT NULL,
    active boolean NOT NULL DEFAULT true,
    system_default boolean NOT NULL DEFAULT true,
    CONSTRAINT rehearsal_issue_severities_code_valid CHECK (code IN ('low', 'medium', 'high', 'blocking')),
    CONSTRAINT rehearsal_issue_severities_display_name_not_blank CHECK (btrim(display_name) <> '')
);

INSERT INTO rehearsal_issue_severities (code, display_name, sort_order) VALUES
    ('low', 'Low', 10),
    ('medium', 'Medium', 20),
    ('high', 'High', 30),
    ('blocking', 'Blocking', 40);

CREATE TABLE rehearsal_issue_statuses (
    code varchar(64) PRIMARY KEY,
    display_name varchar(128) NOT NULL,
    open_status boolean NOT NULL,
    sort_order integer NOT NULL,
    active boolean NOT NULL DEFAULT true,
    system_default boolean NOT NULL DEFAULT true,
    CONSTRAINT rehearsal_issue_statuses_code_valid CHECK (code IN ('open', 'in_progress', 'resolved', 'deferred', 'cancelled')),
    CONSTRAINT rehearsal_issue_statuses_display_name_not_blank CHECK (btrim(display_name) <> '')
);

INSERT INTO rehearsal_issue_statuses (code, display_name, open_status, sort_order) VALUES
    ('open', 'Open', true, 10),
    ('in_progress', 'In progress', true, 20),
    ('resolved', 'Resolved', false, 30),
    ('deferred', 'Deferred', false, 40),
    ('cancelled', 'Cancelled', false, 50);

CREATE TABLE rehearsal_issue_action_statuses (
    code varchar(64) PRIMARY KEY,
    display_name varchar(128) NOT NULL,
    open_status boolean NOT NULL,
    sort_order integer NOT NULL,
    active boolean NOT NULL DEFAULT true,
    system_default boolean NOT NULL DEFAULT true,
    CONSTRAINT rehearsal_issue_action_statuses_code_valid CHECK (code IN ('todo', 'in_progress', 'done', 'cancelled')),
    CONSTRAINT rehearsal_issue_action_statuses_display_name_not_blank CHECK (btrim(display_name) <> '')
);

INSERT INTO rehearsal_issue_action_statuses (code, display_name, open_status, sort_order) VALUES
    ('todo', 'To do', true, 10),
    ('in_progress', 'In progress', true, 20),
    ('done', 'Done', false, 30),
    ('cancelled', 'Cancelled', false, 40);

CREATE TABLE rehearsal_target_types (
    code varchar(64) PRIMARY KEY,
    display_name varchar(128) NOT NULL,
    sort_order integer NOT NULL,
    CONSTRAINT rehearsal_target_types_code_valid CHECK (code IN (
        'service', 'rehearsal_session', 'setlist_item', 'transition', 'arrangement',
        'team_role', 'musician_assignment'
    )),
    CONSTRAINT rehearsal_target_types_display_name_not_blank CHECK (btrim(display_name) <> '')
);

INSERT INTO rehearsal_target_types (code, display_name, sort_order) VALUES
    ('service', 'Service plan', 10),
    ('rehearsal_session', 'Rehearsal session', 20),
    ('setlist_item', 'Setlist item', 30),
    ('transition', 'Adjacent transition', 40),
    ('arrangement', 'Arrangement', 50),
    ('team_role', 'Team role', 60),
    ('musician_assignment', 'Musician assignment', 70);

ALTER TABLE service_song_assignment_overrides
    ADD CONSTRAINT service_song_assignment_overrides_service_id_override_id_unique UNIQUE (service_plan_id, id);

ALTER TABLE rehearsal_team_assignments
    ADD CONSTRAINT rehearsal_team_assignments_service_id_assignment_id_unique UNIQUE (service_plan_id, id);

CREATE TABLE rehearsal_sessions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    service_plan_id uuid NOT NULL REFERENCES service_plans (id) ON DELETE CASCADE,
    session_code varchar(64) NOT NULL,
    starts_at timestamptz NOT NULL,
    ends_at timestamptz NOT NULL,
    location varchar(255),
    readiness_state_code varchar(64) NOT NULL DEFAULT 'draft'
        REFERENCES rehearsal_workflow_readiness_states (code) ON DELETE RESTRICT,
    archived_at timestamptz,
    archived_by varchar(255),
    created_by varchar(255) NOT NULL,
    updated_by varchar(255) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT rehearsal_sessions_time_range CHECK (ends_at > starts_at),
    CONSTRAINT rehearsal_sessions_session_code_not_blank CHECK (btrim(session_code) <> ''),
    CONSTRAINT rehearsal_sessions_location_not_blank CHECK (location IS NULL OR btrim(location) <> ''),
    CONSTRAINT rehearsal_sessions_created_by_not_blank CHECK (btrim(created_by) <> ''),
    CONSTRAINT rehearsal_sessions_updated_by_not_blank CHECK (btrim(updated_by) <> ''),
    CONSTRAINT rehearsal_sessions_archived_by_not_blank CHECK (archived_by IS NULL OR btrim(archived_by) <> ''),
    CONSTRAINT rehearsal_sessions_archived_pair CHECK (
        (archived_at IS NULL AND archived_by IS NULL) OR (archived_at IS NOT NULL AND archived_by IS NOT NULL)
    ),
    CONSTRAINT rehearsal_sessions_service_id_session_id_unique UNIQUE (service_plan_id, id),
    CONSTRAINT rehearsal_sessions_service_code_unique UNIQUE (service_plan_id, session_code)
);

CREATE TABLE service_rehearsal_workflow_states (
    service_plan_id uuid PRIMARY KEY REFERENCES service_plans (id) ON DELETE CASCADE,
    readiness_state_code varchar(64) NOT NULL REFERENCES rehearsal_workflow_readiness_states (code) ON DELETE RESTRICT,
    rationale text,
    updated_by varchar(255) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT service_rehearsal_workflow_states_updated_by_not_blank CHECK (btrim(updated_by) <> '')
);

CREATE TABLE rehearsal_readiness_history (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    service_plan_id uuid NOT NULL REFERENCES service_plans (id) ON DELETE CASCADE,
    rehearsal_session_id uuid,
    previous_state_code varchar(64) REFERENCES rehearsal_workflow_readiness_states (code) ON DELETE RESTRICT,
    new_state_code varchar(64) NOT NULL REFERENCES rehearsal_workflow_readiness_states (code) ON DELETE RESTRICT,
    rationale text,
    changed_by varchar(255) NOT NULL,
    changed_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT rehearsal_readiness_history_session_service_fk
        FOREIGN KEY (service_plan_id, rehearsal_session_id)
        REFERENCES rehearsal_sessions (service_plan_id, id) ON DELETE CASCADE,
    CONSTRAINT rehearsal_readiness_history_changed_by_not_blank CHECK (btrim(changed_by) <> '')
);

CREATE TABLE rehearsal_notes (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    service_plan_id uuid NOT NULL REFERENCES service_plans (id) ON DELETE CASCADE,
    rehearsal_session_id uuid,
    target_type_code varchar(64) NOT NULL REFERENCES rehearsal_target_types (code) ON DELETE RESTRICT,
    service_plan_block_id uuid,
    setlist_version_item_id uuid REFERENCES setlist_version_items (id) ON DELETE SET NULL,
    transition_from_block_id uuid,
    transition_to_block_id uuid,
    arrangement_id uuid REFERENCES arrangements (id) ON DELETE RESTRICT,
    team_role_code varchar(64) REFERENCES musician_roles (code) ON DELETE RESTRICT,
    service_team_assignment_id uuid,
    rehearsal_team_assignment_id uuid,
    song_assignment_override_id uuid,
    note_body text NOT NULL,
    visibility_code varchar(64) NOT NULL DEFAULT 'team_private',
    archived_at timestamptz,
    created_by varchar(255) NOT NULL,
    updated_by varchar(255) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT rehearsal_notes_session_service_fk
        FOREIGN KEY (service_plan_id, rehearsal_session_id)
        REFERENCES rehearsal_sessions (service_plan_id, id) ON DELETE CASCADE,
    CONSTRAINT rehearsal_notes_block_service_fk
        FOREIGN KEY (service_plan_id, service_plan_block_id)
        REFERENCES service_plan_blocks (service_plan_id, id) ON DELETE CASCADE,
    CONSTRAINT rehearsal_notes_transition_from_service_fk
        FOREIGN KEY (service_plan_id, transition_from_block_id)
        REFERENCES service_plan_blocks (service_plan_id, id) ON DELETE CASCADE,
    CONSTRAINT rehearsal_notes_transition_to_service_fk
        FOREIGN KEY (service_plan_id, transition_to_block_id)
        REFERENCES service_plan_blocks (service_plan_id, id) ON DELETE CASCADE,
    CONSTRAINT rehearsal_notes_service_assignment_fk
        FOREIGN KEY (service_plan_id, service_team_assignment_id)
        REFERENCES service_team_assignments (service_plan_id, id) ON DELETE CASCADE,
    CONSTRAINT rehearsal_notes_rehearsal_assignment_fk
        FOREIGN KEY (service_plan_id, rehearsal_team_assignment_id)
        REFERENCES rehearsal_team_assignments (service_plan_id, id) ON DELETE CASCADE,
    CONSTRAINT rehearsal_notes_song_assignment_override_fk
        FOREIGN KEY (service_plan_id, song_assignment_override_id)
        REFERENCES service_song_assignment_overrides (service_plan_id, id) ON DELETE CASCADE,
    CONSTRAINT rehearsal_notes_note_body_not_blank CHECK (btrim(note_body) <> ''),
    CONSTRAINT rehearsal_notes_visibility_valid CHECK (visibility_code IN ('public', 'team_private', 'pastoral_private')),
    CONSTRAINT rehearsal_notes_created_by_not_blank CHECK (btrim(created_by) <> ''),
    CONSTRAINT rehearsal_notes_updated_by_not_blank CHECK (btrim(updated_by) <> ''),
    CONSTRAINT rehearsal_notes_target_required CHECK (
        (target_type_code = 'service' AND rehearsal_session_id IS NULL)
        OR (target_type_code = 'rehearsal_session' AND rehearsal_session_id IS NOT NULL)
        OR (target_type_code = 'setlist_item' AND (service_plan_block_id IS NOT NULL OR setlist_version_item_id IS NOT NULL))
        OR (target_type_code = 'transition' AND transition_from_block_id IS NOT NULL AND transition_to_block_id IS NOT NULL)
        OR (target_type_code = 'arrangement' AND arrangement_id IS NOT NULL)
        OR (target_type_code = 'team_role' AND team_role_code IS NOT NULL)
        OR (target_type_code = 'musician_assignment' AND (
            service_team_assignment_id IS NOT NULL
            OR rehearsal_team_assignment_id IS NOT NULL
            OR song_assignment_override_id IS NOT NULL
        ))
    )
);

CREATE TABLE rehearsal_issues (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    service_plan_id uuid NOT NULL REFERENCES service_plans (id) ON DELETE CASCADE,
    rehearsal_session_id uuid,
    target_type_code varchar(64) NOT NULL REFERENCES rehearsal_target_types (code) ON DELETE RESTRICT,
    service_plan_block_id uuid,
    setlist_version_item_id uuid REFERENCES setlist_version_items (id) ON DELETE SET NULL,
    transition_from_block_id uuid,
    transition_to_block_id uuid,
    arrangement_id uuid REFERENCES arrangements (id) ON DELETE RESTRICT,
    team_role_code varchar(64) REFERENCES musician_roles (code) ON DELETE RESTRICT,
    service_team_assignment_id uuid,
    rehearsal_team_assignment_id uuid,
    song_assignment_override_id uuid,
    category_code varchar(64) NOT NULL REFERENCES rehearsal_issue_categories (code) ON DELETE RESTRICT,
    severity_code varchar(64) NOT NULL REFERENCES rehearsal_issue_severities (code) ON DELETE RESTRICT,
    status_code varchar(64) NOT NULL DEFAULT 'open' REFERENCES rehearsal_issue_statuses (code) ON DELETE RESTRICT,
    title varchar(255) NOT NULL,
    detail text,
    detected_by varchar(255) NOT NULL,
    resolved_at timestamptz,
    archived_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT rehearsal_issues_session_service_fk
        FOREIGN KEY (service_plan_id, rehearsal_session_id)
        REFERENCES rehearsal_sessions (service_plan_id, id) ON DELETE CASCADE,
    CONSTRAINT rehearsal_issues_block_service_fk
        FOREIGN KEY (service_plan_id, service_plan_block_id)
        REFERENCES service_plan_blocks (service_plan_id, id) ON DELETE CASCADE,
    CONSTRAINT rehearsal_issues_transition_from_service_fk
        FOREIGN KEY (service_plan_id, transition_from_block_id)
        REFERENCES service_plan_blocks (service_plan_id, id) ON DELETE CASCADE,
    CONSTRAINT rehearsal_issues_transition_to_service_fk
        FOREIGN KEY (service_plan_id, transition_to_block_id)
        REFERENCES service_plan_blocks (service_plan_id, id) ON DELETE CASCADE,
    CONSTRAINT rehearsal_issues_service_assignment_fk
        FOREIGN KEY (service_plan_id, service_team_assignment_id)
        REFERENCES service_team_assignments (service_plan_id, id) ON DELETE CASCADE,
    CONSTRAINT rehearsal_issues_rehearsal_assignment_fk
        FOREIGN KEY (service_plan_id, rehearsal_team_assignment_id)
        REFERENCES rehearsal_team_assignments (service_plan_id, id) ON DELETE CASCADE,
    CONSTRAINT rehearsal_issues_song_assignment_override_fk
        FOREIGN KEY (service_plan_id, song_assignment_override_id)
        REFERENCES service_song_assignment_overrides (service_plan_id, id) ON DELETE CASCADE,
    CONSTRAINT rehearsal_issues_title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT rehearsal_issues_detected_by_not_blank CHECK (btrim(detected_by) <> ''),
    CONSTRAINT rehearsal_issues_target_required CHECK (
        (target_type_code = 'service' AND rehearsal_session_id IS NULL)
        OR (target_type_code = 'rehearsal_session' AND rehearsal_session_id IS NOT NULL)
        OR (target_type_code = 'setlist_item' AND (service_plan_block_id IS NOT NULL OR setlist_version_item_id IS NOT NULL))
        OR (target_type_code = 'transition' AND transition_from_block_id IS NOT NULL AND transition_to_block_id IS NOT NULL)
        OR (target_type_code = 'arrangement' AND arrangement_id IS NOT NULL)
        OR (target_type_code = 'team_role' AND team_role_code IS NOT NULL)
        OR (target_type_code = 'musician_assignment' AND (
            service_team_assignment_id IS NOT NULL
            OR rehearsal_team_assignment_id IS NOT NULL
            OR song_assignment_override_id IS NOT NULL
        ))
    )
);

ALTER TABLE rehearsal_issues
    ADD CONSTRAINT rehearsal_issues_service_id_issue_id_unique UNIQUE (service_plan_id, id);

CREATE TABLE rehearsal_issue_actions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    rehearsal_issue_id uuid NOT NULL REFERENCES rehearsal_issues (id) ON DELETE CASCADE,
    service_plan_id uuid NOT NULL REFERENCES service_plans (id) ON DELETE CASCADE,
    action_status_code varchar(64) NOT NULL DEFAULT 'todo'
        REFERENCES rehearsal_issue_action_statuses (code) ON DELETE RESTRICT,
    action_summary varchar(255) NOT NULL,
    owner_type varchar(64) NOT NULL,
    owner_actor varchar(255),
    owner_team_role_code varchar(64) REFERENCES musician_roles (code) ON DELETE RESTRICT,
    owner_service_assignment_id uuid,
    due_at timestamptz,
    completed_at timestamptz,
    created_by varchar(255) NOT NULL,
    updated_by varchar(255) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT rehearsal_issue_actions_issue_service_unique UNIQUE (service_plan_id, id),
    CONSTRAINT rehearsal_issue_actions_issue_same_service_fk
        FOREIGN KEY (service_plan_id, rehearsal_issue_id)
        REFERENCES rehearsal_issues (service_plan_id, id) ON DELETE CASCADE,
    CONSTRAINT rehearsal_issue_actions_owner_assignment_fk
        FOREIGN KEY (service_plan_id, owner_service_assignment_id)
        REFERENCES service_team_assignments (service_plan_id, id) ON DELETE RESTRICT,
    CONSTRAINT rehearsal_issue_actions_summary_not_blank CHECK (btrim(action_summary) <> ''),
    CONSTRAINT rehearsal_issue_actions_owner_type_valid CHECK (owner_type IN ('actor', 'team_role', 'service_assignment', 'unassigned')),
    CONSTRAINT rehearsal_issue_actions_owner_required CHECK (
        (owner_type = 'actor' AND owner_actor IS NOT NULL AND btrim(owner_actor) <> '')
        OR (owner_type = 'team_role' AND owner_team_role_code IS NOT NULL)
        OR (owner_type = 'service_assignment' AND owner_service_assignment_id IS NOT NULL)
        OR (owner_type = 'unassigned' AND owner_actor IS NULL AND owner_team_role_code IS NULL AND owner_service_assignment_id IS NULL)
    ),
    CONSTRAINT rehearsal_issue_actions_created_by_not_blank CHECK (btrim(created_by) <> ''),
    CONSTRAINT rehearsal_issue_actions_updated_by_not_blank CHECK (btrim(updated_by) <> '')
);

CREATE TABLE service_arrangement_overrides (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    service_plan_id uuid NOT NULL REFERENCES service_plans (id) ON DELETE CASCADE,
    service_plan_block_id uuid,
    setlist_version_item_id uuid REFERENCES setlist_version_items (id) ON DELETE SET NULL,
    source_arrangement_id uuid NOT NULL REFERENCES arrangements (id) ON DELETE RESTRICT,
    source_arrangement_version_ref varchar(128),
    effective_key varchar(8),
    effective_mode varchar(16),
    effective_tempo_bpm integer,
    effective_time_signature varchar(16),
    effective_duration_seconds integer,
    effective_energy_level integer,
    effective_difficulty_level integer,
    effective_notes text,
    rationale text NOT NULL,
    provenance_note text NOT NULL,
    created_by varchar(255) NOT NULL,
    updated_by varchar(255) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    archived_at timestamptz,
    CONSTRAINT service_arrangement_overrides_block_service_fk
        FOREIGN KEY (service_plan_id, service_plan_block_id)
        REFERENCES service_plan_blocks (service_plan_id, id) ON DELETE CASCADE,
    CONSTRAINT service_arrangement_overrides_mode_valid CHECK (
        effective_mode IS NULL OR effective_mode IN ('MAJOR', 'MINOR', 'MODAL', 'UNKNOWN')
    ),
    CONSTRAINT service_arrangement_overrides_tempo_positive CHECK (effective_tempo_bpm IS NULL OR effective_tempo_bpm > 0),
    CONSTRAINT service_arrangement_overrides_duration_positive CHECK (
        effective_duration_seconds IS NULL OR effective_duration_seconds > 0
    ),
    CONSTRAINT service_arrangement_overrides_energy_range CHECK (
        effective_energy_level IS NULL OR effective_energy_level BETWEEN 1 AND 5
    ),
    CONSTRAINT service_arrangement_overrides_difficulty_range CHECK (
        effective_difficulty_level IS NULL OR effective_difficulty_level BETWEEN 1 AND 5
    ),
    CONSTRAINT service_arrangement_overrides_rationale_not_blank CHECK (btrim(rationale) <> ''),
    CONSTRAINT service_arrangement_overrides_provenance_not_blank CHECK (btrim(provenance_note) <> ''),
    CONSTRAINT service_arrangement_overrides_created_by_not_blank CHECK (btrim(created_by) <> ''),
    CONSTRAINT service_arrangement_overrides_updated_by_not_blank CHECK (btrim(updated_by) <> ''),
    CONSTRAINT service_arrangement_overrides_service_block_arrangement_unique
        UNIQUE (service_plan_id, service_plan_block_id, source_arrangement_id)
);

CREATE INDEX rehearsal_sessions_service_idx ON rehearsal_sessions (service_plan_id, starts_at);
CREATE INDEX rehearsal_notes_service_target_idx ON rehearsal_notes (service_plan_id, target_type_code);
CREATE INDEX rehearsal_issues_service_status_idx ON rehearsal_issues (service_plan_id, status_code, severity_code);
CREATE INDEX rehearsal_issue_actions_service_status_idx ON rehearsal_issue_actions (service_plan_id, action_status_code);
CREATE INDEX rehearsal_readiness_history_service_idx ON rehearsal_readiness_history (service_plan_id, changed_at DESC);
CREATE INDEX service_arrangement_overrides_service_idx ON service_arrangement_overrides (service_plan_id, source_arrangement_id);

COMMENT ON TABLE rehearsal_sessions IS
    'ADR-024 service-scoped rehearsal sessions for workflow lifecycle tracking; does not mutate canonical catalog records.';
COMMENT ON TABLE service_rehearsal_workflow_states IS
    'Current service-level rehearsal readiness state with controlled API/reporting codes.';
COMMENT ON TABLE rehearsal_readiness_history IS
    'Append-only readiness transition history for service plans and optional rehearsal sessions.';
COMMENT ON TABLE rehearsal_notes IS
    'Targeted rehearsal notes with controlled target types and service-scoped references.';
COMMENT ON TABLE rehearsal_issues IS
    'Structured rehearsal issues for deterministic readiness reporting; comments are supplementary detail only.';
COMMENT ON TABLE rehearsal_issue_actions IS
    'Action and owner metadata for resolving structured rehearsal issues.';
COMMENT ON TABLE service_arrangement_overrides IS
    'Service-scoped effective arrangement values and rationale, isolated from canonical arrangement tables.';
