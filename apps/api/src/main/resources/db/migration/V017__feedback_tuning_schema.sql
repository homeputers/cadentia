CREATE TABLE feedback_rule_configurations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    version_label VARCHAR(64) NOT NULL UNIQUE,
    rules_json JSONB NOT NULL,
    migration_notes TEXT,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(128) NOT NULL
);
COMMENT ON TABLE feedback_rule_configurations IS 'Versioned deterministic feedback tuning rules referenced by recommendation scoring profiles.';
COMMENT ON COLUMN feedback_rule_configurations.version_label IS 'Human-readable immutable rule version label.';
COMMENT ON COLUMN feedback_rule_configurations.rules_json IS 'Deterministic scoring-weight configuration payload.';
COMMENT ON COLUMN feedback_rule_configurations.migration_notes IS 'Operator-facing notes describing rule migration rationale.';
COMMENT ON COLUMN feedback_rule_configurations.active IS 'Marks the currently active feedback rule configuration.';

CREATE TABLE recommendation_feedback_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    setlist_id UUID NOT NULL,
    setlist_version_id UUID,
    arrangement_id UUID NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    scope_layer VARCHAR(32) NOT NULL,
    scope_id UUID NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    replacement_reason VARCHAR(64),
    replaced_with_arrangement_id UUID,
    familiarity_score INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT feedback_outcome_chk CHECK (outcome IN ('accepted', 'rejected', 'skipped', 'favorited')),
    CONSTRAINT feedback_scope_layer_chk CHECK (scope_layer IN ('personal', 'team', 'policy')),
    CONSTRAINT feedback_familiarity_chk CHECK (familiarity_score IS NULL OR (familiarity_score >= 0 AND familiarity_score <= 100))
);
COMMENT ON TABLE recommendation_feedback_events IS 'Append-only recommendation feedback events independent from canonical catalog metadata.';
COMMENT ON COLUMN recommendation_feedback_events.outcome IS 'Explicit feedback outcome used for deterministic rank influence.';
COMMENT ON COLUMN recommendation_feedback_events.scope_layer IS 'Preference scope layer: personal, team, or policy.';
COMMENT ON COLUMN recommendation_feedback_events.scope_id IS 'Stable identifier for the selected scope layer.';
COMMENT ON COLUMN recommendation_feedback_events.replacement_reason IS 'Controlled taxonomy code describing replacement rationale.';
COMMENT ON COLUMN recommendation_feedback_events.familiarity_score IS 'Optional 0-100 familiarity signal supplied by user.';

CREATE INDEX idx_feedback_events_scope_created
    ON recommendation_feedback_events (scope_layer, scope_id, created_at DESC);
CREATE INDEX idx_feedback_events_arrangement
    ON recommendation_feedback_events (arrangement_id, created_at DESC);

CREATE TABLE recommendation_feedback_scope_aggregates (
    scope_layer VARCHAR(32) NOT NULL,
    scope_id UUID NOT NULL,
    accepted_count INTEGER NOT NULL DEFAULT 0,
    rejected_count INTEGER NOT NULL DEFAULT 0,
    skipped_count INTEGER NOT NULL DEFAULT 0,
    favorited_count INTEGER NOT NULL DEFAULT 0,
    replacement_reason_counts JSONB NOT NULL DEFAULT '{}'::jsonb,
    last_feedback_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (scope_layer, scope_id)
);
COMMENT ON TABLE recommendation_feedback_scope_aggregates IS 'Deterministic aggregate counters by feedback scope for scoring lookups.';
COMMENT ON COLUMN recommendation_feedback_scope_aggregates.replacement_reason_counts IS 'JSON object of replacement reason taxonomy codes to counts.';
COMMENT ON COLUMN recommendation_feedback_scope_aggregates.last_feedback_at IS 'Timestamp of newest ingested feedback event for the scope.';
