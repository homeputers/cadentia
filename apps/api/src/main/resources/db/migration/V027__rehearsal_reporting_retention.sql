CREATE TABLE rehearsal_retention_policies (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_name varchar(128) NOT NULL UNIQUE,
    completed_sessions_retain_days integer NOT NULL DEFAULT 400,
    notes_retain_days integer NOT NULL DEFAULT 180,
    issues_retain_days integer NOT NULL DEFAULT 400,
    overrides_retain_days integer NOT NULL DEFAULT 400,
    audit_retain_days integer NOT NULL DEFAULT 2555,
    min_completed_sessions_retain_days integer NOT NULL DEFAULT 90,
    min_notes_retain_days integer NOT NULL DEFAULT 30,
    min_issues_retain_days integer NOT NULL DEFAULT 180,
    min_overrides_retain_days integer NOT NULL DEFAULT 180,
    min_audit_retain_days integer NOT NULL DEFAULT 2555,
    archive_behavior varchar(64) NOT NULL DEFAULT 'soft_archive',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT rehearsal_retention_policy_name_not_blank CHECK (btrim(policy_name) <> ''),
    CONSTRAINT rehearsal_retention_policy_archive_behavior CHECK (archive_behavior IN ('soft_archive')),
    CONSTRAINT rehearsal_retention_policy_minimums CHECK (
        completed_sessions_retain_days >= min_completed_sessions_retain_days
        AND notes_retain_days >= min_notes_retain_days
        AND issues_retain_days >= min_issues_retain_days
        AND overrides_retain_days >= min_overrides_retain_days
        AND audit_retain_days >= min_audit_retain_days
    )
);

INSERT INTO rehearsal_retention_policies (policy_name) VALUES ('default');

COMMENT ON TABLE rehearsal_retention_policies IS
    'Church-configurable rehearsal workflow retention limits. The default behavior is soft archive, not physical deletion.';
COMMENT ON COLUMN rehearsal_retention_policies.completed_sessions_retain_days IS
    'Default days to keep completed-service rehearsal sessions visible before archive. Default: 400 days; minimum: 90 days.';
COMMENT ON COLUMN rehearsal_retention_policies.notes_retain_days IS
    'Default days to keep completed-service rehearsal note bodies visible before archive. Default: 180 days; minimum: 30 days.';
COMMENT ON COLUMN rehearsal_retention_policies.issues_retain_days IS
    'Default days to keep completed-service issue records visible before archive. Default: 400 days; minimum: 180 days.';
COMMENT ON COLUMN rehearsal_retention_policies.overrides_retain_days IS
    'Default days to keep completed-service arrangement override records active before archive. Default: 400 days; minimum: 180 days.';
COMMENT ON COLUMN rehearsal_retention_policies.audit_retain_days IS
    'Default days to retain rehearsal audit records for accountability. Default and minimum: 2555 days.';
COMMENT ON COLUMN rehearsal_retention_policies.archive_behavior IS
    'Archival sets archived_at/archived_by fields where available and preserves privileged_action_audit_events.';

COMMENT ON TABLE rehearsal_readiness_history IS
    'Append-only readiness history retained for audit reconciliation after rehearsal workflow closure.';
COMMENT ON TABLE privileged_action_audit_events IS
    'Immutable privileged-operation audit trail. Rehearsal workflow archival must not delete rows needed to explain readiness changes or issue resolution.';
