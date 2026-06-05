ALTER TABLE service_team_assignments
    ADD COLUMN assignment_order integer NOT NULL DEFAULT 0;

ALTER TABLE rehearsal_team_assignments
    ADD COLUMN service_assignment_id uuid REFERENCES service_team_assignments (id) ON DELETE SET NULL;

CREATE TABLE team_assignment_history (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    assignment_type varchar(32) NOT NULL,
    assignment_id uuid NOT NULL,
    service_plan_id uuid NOT NULL,
    rehearsal_event_id uuid,
    musician_id uuid,
    role_code varchar(64),
    instrument_code varchar(64),
    vocal_part_code varchar(64),
    status_code varchar(64),
    assignment_order integer,
    substitute_for_assignment_id uuid,
    service_assignment_id uuid,
    change_action varchar(64) NOT NULL,
    changed_by varchar(255) NOT NULL DEFAULT 'system',
    reason_code varchar(128) NOT NULL DEFAULT 'unspecified',
    reference varchar(255),
    changed_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT team_assignment_history_type_check CHECK (assignment_type IN ('SERVICE', 'REHEARSAL', 'SONG_OVERRIDE')),
    CONSTRAINT team_assignment_history_action_not_blank CHECK (btrim(change_action) <> ''),
    CONSTRAINT team_assignment_history_actor_not_blank CHECK (btrim(changed_by) <> ''),
    CONSTRAINT team_assignment_history_reason_not_blank CHECK (btrim(reason_code) <> '')
);

CREATE INDEX team_assignment_history_assignment_idx
    ON team_assignment_history (assignment_type, assignment_id, changed_at DESC);

CREATE INDEX team_assignment_history_service_idx
    ON team_assignment_history (service_plan_id, changed_at DESC);

CREATE INDEX service_team_assignments_service_order_idx
    ON service_team_assignments (service_plan_id, assignment_order, created_at);

CREATE INDEX rehearsal_team_assignments_service_assignment_idx
    ON rehearsal_team_assignments (service_assignment_id)
    WHERE service_assignment_id IS NOT NULL;
