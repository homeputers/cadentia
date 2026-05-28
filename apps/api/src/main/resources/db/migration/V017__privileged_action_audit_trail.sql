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
