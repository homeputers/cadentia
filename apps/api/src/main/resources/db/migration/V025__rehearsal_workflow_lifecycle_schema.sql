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


COMMENT ON COLUMN rehearsal_workflow_readiness_states.code IS
    'Stable lowercase readiness code returned by APIs and used by reports.';
COMMENT ON COLUMN rehearsal_workflow_readiness_states.display_name IS
    'Human-readable readiness label for administrative displays.';
COMMENT ON COLUMN rehearsal_workflow_readiness_states.terminal_state IS
    'Indicates whether the readiness state closes the rehearsal workflow.';
COMMENT ON COLUMN rehearsal_workflow_readiness_states.sort_order IS
    'Deterministic ordering for UI pickers and reports.';
COMMENT ON COLUMN rehearsal_workflow_readiness_states.active IS
    'Whether the readiness state is available for new workflow changes.';
COMMENT ON COLUMN rehearsal_workflow_readiness_states.system_default IS
    'Marks seed values shipped by Cadentia rather than local extensions.';

COMMENT ON COLUMN rehearsal_issue_categories.code IS
    'Stable lowercase issue category code for deterministic readiness reporting.';
COMMENT ON COLUMN rehearsal_issue_categories.display_name IS
    'Human-readable category label for rehearsal issue workflows.';
COMMENT ON COLUMN rehearsal_issue_categories.sort_order IS
    'Deterministic ordering for category pickers and reports.';
COMMENT ON COLUMN rehearsal_issue_categories.active IS
    'Whether the category is available for newly opened issues.';
COMMENT ON COLUMN rehearsal_issue_categories.system_default IS
    'Marks seed values shipped by Cadentia rather than local extensions.';

COMMENT ON COLUMN rehearsal_issue_severities.code IS
    'Stable lowercase severity code used for issue prioritization and reports.';
COMMENT ON COLUMN rehearsal_issue_severities.display_name IS
    'Human-readable severity label for rehearsal issue workflows.';
COMMENT ON COLUMN rehearsal_issue_severities.sort_order IS
    'Deterministic ordering from least to most severe.';
COMMENT ON COLUMN rehearsal_issue_severities.active IS
    'Whether the severity is available for newly opened issues.';
COMMENT ON COLUMN rehearsal_issue_severities.system_default IS
    'Marks seed values shipped by Cadentia rather than local extensions.';

COMMENT ON COLUMN rehearsal_issue_statuses.code IS
    'Stable lowercase issue status code used by APIs and readiness rollups.';
COMMENT ON COLUMN rehearsal_issue_statuses.display_name IS
    'Human-readable status label for rehearsal issue workflows.';
COMMENT ON COLUMN rehearsal_issue_statuses.open_status IS
    'Indicates whether issues in this status should count as unresolved.';
COMMENT ON COLUMN rehearsal_issue_statuses.sort_order IS
    'Deterministic ordering for issue status pickers and reports.';
COMMENT ON COLUMN rehearsal_issue_statuses.active IS
    'Whether the status is available for issue transitions.';
COMMENT ON COLUMN rehearsal_issue_statuses.system_default IS
    'Marks seed values shipped by Cadentia rather than local extensions.';

COMMENT ON COLUMN rehearsal_issue_action_statuses.code IS
    'Stable lowercase action status code used by APIs and owner follow-up reports.';
COMMENT ON COLUMN rehearsal_issue_action_statuses.display_name IS
    'Human-readable status label for issue action workflows.';
COMMENT ON COLUMN rehearsal_issue_action_statuses.open_status IS
    'Indicates whether actions in this status still require follow-up.';
COMMENT ON COLUMN rehearsal_issue_action_statuses.sort_order IS
    'Deterministic ordering for action status pickers and reports.';
COMMENT ON COLUMN rehearsal_issue_action_statuses.active IS
    'Whether the status is available for action transitions.';
COMMENT ON COLUMN rehearsal_issue_action_statuses.system_default IS
    'Marks seed values shipped by Cadentia rather than local extensions.';

COMMENT ON COLUMN rehearsal_target_types.code IS
    'Stable lowercase target type code for notes and structured issues.';
COMMENT ON COLUMN rehearsal_target_types.display_name IS
    'Human-readable target type label for workflow screens.';
COMMENT ON COLUMN rehearsal_target_types.sort_order IS
    'Deterministic ordering for target type pickers and reports.';

COMMENT ON COLUMN rehearsal_sessions.id IS
    'Stable rehearsal session identifier scoped to a service plan.';
COMMENT ON COLUMN rehearsal_sessions.service_plan_id IS
    'Owning service plan for this rehearsal session.';
COMMENT ON COLUMN rehearsal_sessions.session_code IS
    'Service-local stable code such as primary, midweek, or soundcheck.';
COMMENT ON COLUMN rehearsal_sessions.starts_at IS
    'Scheduled rehearsal start timestamp.';
COMMENT ON COLUMN rehearsal_sessions.ends_at IS
    'Scheduled rehearsal end timestamp.';
COMMENT ON COLUMN rehearsal_sessions.location IS
    'Optional rehearsal location label.';
COMMENT ON COLUMN rehearsal_sessions.readiness_state_code IS
    'Current session readiness state using controlled rehearsal workflow codes.';
COMMENT ON COLUMN rehearsal_sessions.archived_at IS
    'Timestamp when the session was archived instead of physically deleted.';
COMMENT ON COLUMN rehearsal_sessions.archived_by IS
    'Actor that archived the session.';
COMMENT ON COLUMN rehearsal_sessions.created_by IS
    'Actor that created the session.';
COMMENT ON COLUMN rehearsal_sessions.updated_by IS
    'Actor that last updated the session.';
COMMENT ON COLUMN rehearsal_sessions.created_at IS
    'Timestamp when the session row was created.';
COMMENT ON COLUMN rehearsal_sessions.updated_at IS
    'Timestamp when the session row was last updated.';

COMMENT ON COLUMN service_rehearsal_workflow_states.service_plan_id IS
    'Service plan whose rehearsal workflow state is summarized by this row.';
COMMENT ON COLUMN service_rehearsal_workflow_states.readiness_state_code IS
    'Current service-level rehearsal readiness state using controlled codes.';
COMMENT ON COLUMN service_rehearsal_workflow_states.rationale IS
    'Optional reason for the latest service-level readiness state.';
COMMENT ON COLUMN service_rehearsal_workflow_states.updated_by IS
    'Actor that last changed the service-level readiness state.';
COMMENT ON COLUMN service_rehearsal_workflow_states.updated_at IS
    'Timestamp when the service-level readiness state was last changed.';

COMMENT ON COLUMN rehearsal_readiness_history.id IS
    'Stable readiness transition history identifier.';
COMMENT ON COLUMN rehearsal_readiness_history.service_plan_id IS
    'Service plan whose rehearsal readiness changed.';
COMMENT ON COLUMN rehearsal_readiness_history.rehearsal_session_id IS
    'Optional rehearsal session associated with the readiness transition.';
COMMENT ON COLUMN rehearsal_readiness_history.previous_state_code IS
    'Prior controlled readiness state, null for the first transition.';
COMMENT ON COLUMN rehearsal_readiness_history.new_state_code IS
    'New controlled readiness state after the transition.';
COMMENT ON COLUMN rehearsal_readiness_history.rationale IS
    'Optional human reason for the readiness transition.';
COMMENT ON COLUMN rehearsal_readiness_history.changed_by IS
    'Actor that recorded the readiness transition.';
COMMENT ON COLUMN rehearsal_readiness_history.changed_at IS
    'Timestamp when the readiness transition was recorded.';

COMMENT ON COLUMN rehearsal_notes.id IS
    'Stable rehearsal note identifier.';
COMMENT ON COLUMN rehearsal_notes.service_plan_id IS
    'Owning service plan for the note.';
COMMENT ON COLUMN rehearsal_notes.rehearsal_session_id IS
    'Optional rehearsal session targeted by or contextualizing the note.';
COMMENT ON COLUMN rehearsal_notes.target_type_code IS
    'Controlled target type describing the related service, session, item, transition, arrangement, role, or assignment.';
COMMENT ON COLUMN rehearsal_notes.service_plan_block_id IS
    'Optional service-plan block or setlist item target scoped to the service.';
COMMENT ON COLUMN rehearsal_notes.setlist_version_item_id IS
    'Optional immutable setlist version item reference when source lineage is available.';
COMMENT ON COLUMN rehearsal_notes.transition_from_block_id IS
    'First adjacent service-plan block when the note targets a transition.';
COMMENT ON COLUMN rehearsal_notes.transition_to_block_id IS
    'Second adjacent service-plan block when the note targets a transition.';
COMMENT ON COLUMN rehearsal_notes.arrangement_id IS
    'Optional canonical arrangement reference used as note context only.';
COMMENT ON COLUMN rehearsal_notes.team_role_code IS
    'Optional musician role target when the note concerns a team role.';
COMMENT ON COLUMN rehearsal_notes.service_team_assignment_id IS
    'Optional service assignment target when ADR-023 data exists.';
COMMENT ON COLUMN rehearsal_notes.rehearsal_team_assignment_id IS
    'Optional rehearsal assignment target when ADR-023 data exists.';
COMMENT ON COLUMN rehearsal_notes.song_assignment_override_id IS
    'Optional song-specific assignment override target when ADR-023 data exists.';
COMMENT ON COLUMN rehearsal_notes.note_body IS
    'Human-authored note text; structured readiness and issue state lives in controlled columns.';
COMMENT ON COLUMN rehearsal_notes.visibility_code IS
    'Controlled privacy classification for displaying the note.';
COMMENT ON COLUMN rehearsal_notes.archived_at IS
    'Timestamp when the note was archived instead of physically deleted.';
COMMENT ON COLUMN rehearsal_notes.created_by IS
    'Actor that created the note.';
COMMENT ON COLUMN rehearsal_notes.updated_by IS
    'Actor that last updated the note.';
COMMENT ON COLUMN rehearsal_notes.created_at IS
    'Timestamp when the note row was created.';
COMMENT ON COLUMN rehearsal_notes.updated_at IS
    'Timestamp when the note row was last updated.';

COMMENT ON COLUMN rehearsal_issues.id IS
    'Stable structured rehearsal issue identifier.';
COMMENT ON COLUMN rehearsal_issues.service_plan_id IS
    'Owning service plan for the issue.';
COMMENT ON COLUMN rehearsal_issues.rehearsal_session_id IS
    'Optional rehearsal session targeted by or contextualizing the issue.';
COMMENT ON COLUMN rehearsal_issues.target_type_code IS
    'Controlled target type describing the related service, session, item, transition, arrangement, role, or assignment.';
COMMENT ON COLUMN rehearsal_issues.service_plan_block_id IS
    'Optional service-plan block or setlist item target scoped to the service.';
COMMENT ON COLUMN rehearsal_issues.setlist_version_item_id IS
    'Optional immutable setlist version item reference when source lineage is available.';
COMMENT ON COLUMN rehearsal_issues.transition_from_block_id IS
    'First adjacent service-plan block when the issue targets a transition.';
COMMENT ON COLUMN rehearsal_issues.transition_to_block_id IS
    'Second adjacent service-plan block when the issue targets a transition.';
COMMENT ON COLUMN rehearsal_issues.arrangement_id IS
    'Optional canonical arrangement reference used as issue context only.';
COMMENT ON COLUMN rehearsal_issues.team_role_code IS
    'Optional musician role target when the issue concerns a team role.';
COMMENT ON COLUMN rehearsal_issues.service_team_assignment_id IS
    'Optional service assignment target when ADR-023 data exists.';
COMMENT ON COLUMN rehearsal_issues.rehearsal_team_assignment_id IS
    'Optional rehearsal assignment target when ADR-023 data exists.';
COMMENT ON COLUMN rehearsal_issues.song_assignment_override_id IS
    'Optional song-specific assignment override target when ADR-023 data exists.';
COMMENT ON COLUMN rehearsal_issues.category_code IS
    'Controlled issue category used for deterministic readiness reporting.';
COMMENT ON COLUMN rehearsal_issues.severity_code IS
    'Controlled issue severity used for prioritization.';
COMMENT ON COLUMN rehearsal_issues.status_code IS
    'Controlled issue status used to determine open versus closed work.';
COMMENT ON COLUMN rehearsal_issues.title IS
    'Short human-readable issue summary.';
COMMENT ON COLUMN rehearsal_issues.detail IS
    'Optional supporting detail; not a replacement for controlled issue state.';
COMMENT ON COLUMN rehearsal_issues.detected_by IS
    'Actor that opened or detected the issue.';
COMMENT ON COLUMN rehearsal_issues.resolved_at IS
    'Timestamp when the issue was resolved, if applicable.';
COMMENT ON COLUMN rehearsal_issues.archived_at IS
    'Timestamp when the issue was archived instead of physically deleted.';
COMMENT ON COLUMN rehearsal_issues.created_at IS
    'Timestamp when the issue row was created.';
COMMENT ON COLUMN rehearsal_issues.updated_at IS
    'Timestamp when the issue row was last updated.';

COMMENT ON COLUMN rehearsal_issue_actions.id IS
    'Stable issue action identifier.';
COMMENT ON COLUMN rehearsal_issue_actions.rehearsal_issue_id IS
    'Structured rehearsal issue this action belongs to.';
COMMENT ON COLUMN rehearsal_issue_actions.service_plan_id IS
    'Owning service plan, duplicated to enforce service-scoped ownership.';
COMMENT ON COLUMN rehearsal_issue_actions.action_status_code IS
    'Controlled action status for owner follow-up.';
COMMENT ON COLUMN rehearsal_issue_actions.action_summary IS
    'Short description of the action required to resolve or defer the issue.';
COMMENT ON COLUMN rehearsal_issue_actions.owner_type IS
    'Controlled owner discriminator: actor, team_role, service_assignment, or unassigned.';
COMMENT ON COLUMN rehearsal_issue_actions.owner_actor IS
    'Optional actor identifier when owner_type is actor.';
COMMENT ON COLUMN rehearsal_issue_actions.owner_team_role_code IS
    'Optional musician role owner when owner_type is team_role.';
COMMENT ON COLUMN rehearsal_issue_actions.owner_service_assignment_id IS
    'Optional service assignment owner when owner_type is service_assignment.';
COMMENT ON COLUMN rehearsal_issue_actions.due_at IS
    'Optional due timestamp for the follow-up action.';
COMMENT ON COLUMN rehearsal_issue_actions.completed_at IS
    'Timestamp when the action was completed, if applicable.';
COMMENT ON COLUMN rehearsal_issue_actions.created_by IS
    'Actor that created the action.';
COMMENT ON COLUMN rehearsal_issue_actions.updated_by IS
    'Actor that last updated the action.';
COMMENT ON COLUMN rehearsal_issue_actions.created_at IS
    'Timestamp when the action row was created.';
COMMENT ON COLUMN rehearsal_issue_actions.updated_at IS
    'Timestamp when the action row was last updated.';

COMMENT ON COLUMN service_arrangement_overrides.id IS
    'Stable service-scoped arrangement override identifier.';
COMMENT ON COLUMN service_arrangement_overrides.service_plan_id IS
    'Owning service plan for the override.';
COMMENT ON COLUMN service_arrangement_overrides.service_plan_block_id IS
    'Optional service-plan block whose effective arrangement values are overridden.';
COMMENT ON COLUMN service_arrangement_overrides.setlist_version_item_id IS
    'Optional immutable setlist item reference for lineage/provenance.';
COMMENT ON COLUMN service_arrangement_overrides.source_arrangement_id IS
    'Canonical arrangement used as the source; this table never mutates it.';
COMMENT ON COLUMN service_arrangement_overrides.source_arrangement_version_ref IS
    'Optional source arrangement/version reference captured for provenance.';
COMMENT ON COLUMN service_arrangement_overrides.effective_key IS
    'Service-specific effective key for this plan or block.';
COMMENT ON COLUMN service_arrangement_overrides.effective_mode IS
    'Service-specific effective mode for this plan or block.';
COMMENT ON COLUMN service_arrangement_overrides.effective_tempo_bpm IS
    'Service-specific effective tempo in beats per minute.';
COMMENT ON COLUMN service_arrangement_overrides.effective_time_signature IS
    'Service-specific effective time signature.';
COMMENT ON COLUMN service_arrangement_overrides.effective_duration_seconds IS
    'Service-specific effective duration in seconds.';
COMMENT ON COLUMN service_arrangement_overrides.effective_energy_level IS
    'Service-specific effective energy level from 1 to 5.';
COMMENT ON COLUMN service_arrangement_overrides.effective_difficulty_level IS
    'Service-specific effective difficulty level from 1 to 5.';
COMMENT ON COLUMN service_arrangement_overrides.effective_notes IS
    'Service-specific arrangement notes for rehearsal or execution.';
COMMENT ON COLUMN service_arrangement_overrides.rationale IS
    'Required reason explaining why this service override exists.';
COMMENT ON COLUMN service_arrangement_overrides.provenance_note IS
    'Required provenance note tying effective values back to the source arrangement/version.';
COMMENT ON COLUMN service_arrangement_overrides.created_by IS
    'Actor that created the override.';
COMMENT ON COLUMN service_arrangement_overrides.updated_by IS
    'Actor that last updated the override.';
COMMENT ON COLUMN service_arrangement_overrides.created_at IS
    'Timestamp when the override row was created.';
COMMENT ON COLUMN service_arrangement_overrides.updated_at IS
    'Timestamp when the override row was last updated.';
COMMENT ON COLUMN service_arrangement_overrides.archived_at IS
    'Timestamp when the override was archived instead of physically deleted.';
