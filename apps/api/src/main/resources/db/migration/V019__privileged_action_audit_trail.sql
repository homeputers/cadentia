CREATE TABLE privileged_action_audit_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    actor varchar(128) NOT NULL,
    actor_roles jsonb NOT NULL DEFAULT '[]'::jsonb,
    action varchar(128) NOT NULL,
    target_type varchar(128) NOT NULL,
    target_id uuid,
    request_id varchar(128),
    before_state_ref varchar(512),
    after_state_ref varchar(512),
    before_state_hash varchar(128),
    after_state_hash varchar(128),
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    occurred_at timestamptz NOT NULL DEFAULT now(),
    recorded_at timestamptz NOT NULL DEFAULT now(),
    retention_until timestamptz NOT NULL DEFAULT (now() + interval '400 days'),
    CONSTRAINT privileged_action_audit_events_actor_not_blank CHECK (btrim(actor) <> ''),
    CONSTRAINT privileged_action_audit_events_action_not_blank CHECK (btrim(action) <> ''),
    CONSTRAINT privileged_action_audit_events_target_type_not_blank CHECK (btrim(target_type) <> ''),
    CONSTRAINT privileged_action_audit_events_no_secrets CHECK (
        metadata::text !~* '(password|secret|token|authorization|api[_-]?key|private[_-]?key|credential)'
    )
);

CREATE INDEX privileged_action_audit_events_actor_occurred_idx
    ON privileged_action_audit_events (actor, occurred_at DESC);
CREATE INDEX privileged_action_audit_events_action_occurred_idx
    ON privileged_action_audit_events (action, occurred_at DESC);
CREATE INDEX privileged_action_audit_events_target_occurred_idx
    ON privileged_action_audit_events (target_type, target_id, occurred_at DESC);
CREATE INDEX privileged_action_audit_events_retention_idx
    ON privileged_action_audit_events (retention_until);

COMMENT ON TABLE privileged_action_audit_events IS
    'Immutable privileged-operation audit trail for approvals, role assignments, policy overrides, and merges.';
COMMENT ON COLUMN privileged_action_audit_events.id IS
    'Stable audit event identifier.';
COMMENT ON COLUMN privileged_action_audit_events.actor IS
    'Authenticated principal responsible for the privileged action.';
COMMENT ON COLUMN privileged_action_audit_events.actor_roles IS
    'Role snapshot for the actor at the moment of action execution.';
COMMENT ON COLUMN privileged_action_audit_events.action IS
    'Controlled action code for the privileged operation.';
COMMENT ON COLUMN privileged_action_audit_events.target_type IS
    'Controlled target entity type.';
COMMENT ON COLUMN privileged_action_audit_events.target_id IS
    'Identifier of the target entity when one exists.';
COMMENT ON COLUMN privileged_action_audit_events.request_id IS
    'Optional request correlation identifier.';
COMMENT ON COLUMN privileged_action_audit_events.before_state_ref IS
    'Reference pointer to before-state snapshot material.';
COMMENT ON COLUMN privileged_action_audit_events.after_state_ref IS
    'Reference pointer to after-state snapshot material.';
COMMENT ON COLUMN privileged_action_audit_events.before_state_hash IS
    'Integrity hash of the before-state snapshot.';
COMMENT ON COLUMN privileged_action_audit_events.after_state_hash IS
    'Integrity hash of the after-state snapshot.';
COMMENT ON COLUMN privileged_action_audit_events.metadata IS
    'Structured, non-secret audit metadata for evidentiary context.';
COMMENT ON COLUMN privileged_action_audit_events.occurred_at IS
    'Timestamp when the privileged action occurred.';
COMMENT ON COLUMN privileged_action_audit_events.recorded_at IS
    'Timestamp when the audit row was persisted.';
COMMENT ON COLUMN privileged_action_audit_events.retention_until IS
    'Minimum retention horizon for governance and incident response.';

CREATE OR REPLACE VIEW v_privileged_action_audit_history AS
SELECT
    id,
    actor,
    actor_roles,
    action,
    target_type,
    target_id,
    request_id,
    before_state_ref,
    after_state_ref,
    before_state_hash,
    after_state_hash,
    metadata,
    occurred_at,
    recorded_at,
    retention_until
FROM privileged_action_audit_events;

COMMENT ON VIEW v_privileged_action_audit_history IS
    'Search-oriented audit history projection for actor/action/time-window queries.';
