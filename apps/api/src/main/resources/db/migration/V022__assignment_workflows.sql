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

COMMENT ON COLUMN service_team_assignments.assignment_order IS 'Deterministic display and planning order for service team assignments within a service plan.';
COMMENT ON COLUMN rehearsal_team_assignments.service_assignment_id IS 'Optional service assignment this rehearsal assignment is linked to while preserving separate rehearsal response and readiness status.';

COMMENT ON TABLE team_assignment_history IS 'Append-only operational history for service, rehearsal, and song-level assignment lifecycle changes.';
COMMENT ON COLUMN team_assignment_history.id IS 'Stable assignment history event identifier.';
COMMENT ON COLUMN team_assignment_history.assignment_type IS 'Assignment scope for this history event: service, rehearsal, or song override.';
COMMENT ON COLUMN team_assignment_history.assignment_id IS 'Identifier of the assignment or override row affected by this history event.';
COMMENT ON COLUMN team_assignment_history.service_plan_id IS 'Service plan context for the assignment history event.';
COMMENT ON COLUMN team_assignment_history.rehearsal_event_id IS 'Optional rehearsal event context when the history event applies to a rehearsal assignment.';
COMMENT ON COLUMN team_assignment_history.musician_id IS 'Musician assigned at the time this history event was recorded, when applicable.';
COMMENT ON COLUMN team_assignment_history.role_code IS 'Controlled musician role code captured at the time of the assignment change.';
COMMENT ON COLUMN team_assignment_history.instrument_code IS 'Controlled instrument code captured at the time of the assignment change, when applicable.';
COMMENT ON COLUMN team_assignment_history.vocal_part_code IS 'Controlled vocal part code captured at the time of the assignment change, when applicable.';
COMMENT ON COLUMN team_assignment_history.status_code IS 'Controlled assignment status captured at the time of the assignment change.';
COMMENT ON COLUMN team_assignment_history.assignment_order IS 'Assignment order captured at the time of the service assignment change, when applicable.';
COMMENT ON COLUMN team_assignment_history.substitute_for_assignment_id IS 'Original assignment identifier when this history event records a substitute relationship.';
COMMENT ON COLUMN team_assignment_history.service_assignment_id IS 'Linked service assignment identifier when this history event records a rehearsal assignment.';
COMMENT ON COLUMN team_assignment_history.change_action IS 'Controlled lifecycle action recorded for the assignment change, such as CREATE, UPDATE, REMOVE, REORDER, STATUS, or SUBSTITUTE.';
COMMENT ON COLUMN team_assignment_history.changed_by IS 'Actor identifier that performed the assignment change.';
COMMENT ON COLUMN team_assignment_history.reason_code IS 'Operational reason code supplied for the assignment change, defaulting to unspecified.';
COMMENT ON COLUMN team_assignment_history.reference IS 'Optional external request, ticket, or workflow reference for the assignment change.';
COMMENT ON COLUMN team_assignment_history.changed_at IS 'Timestamp when the assignment history event was recorded.';
