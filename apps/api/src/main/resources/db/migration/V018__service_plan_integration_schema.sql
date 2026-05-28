CREATE TABLE service_plans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_date_time TIMESTAMPTZ NOT NULL,
    title TEXT NOT NULL,
    theme TEXT NOT NULL,
    scripture TEXT NOT NULL,
    notes TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'draft' CHECK (status IN ('draft','published','finalized')),
    published_at TIMESTAMPTZ,
    published_by TEXT,
    publish_note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE service_plan_setlist_attachments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_plan_id UUID NOT NULL REFERENCES service_plans(id) ON DELETE CASCADE,
    setlist_id UUID NOT NULL,
    setlist_version_id UUID NOT NULL,
    attachment_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(service_plan_id, setlist_version_id),
    UNIQUE(service_plan_id, attachment_order)
);

CREATE TABLE service_plan_blocks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_plan_id UUID NOT NULL REFERENCES service_plans(id) ON DELETE CASCADE,
    block_type TEXT NOT NULL CHECK (block_type IN ('praise','worship','offering','altar_call','communion','special')),
    position_index INTEGER NOT NULL,
    arrangement_id UUID,
    service_notes TEXT,
    override_key TEXT,
    override_mode TEXT,
    source_setlist_version_id UUID,
    source_setlist_item_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(service_plan_id, position_index)
);

CREATE TABLE service_plan_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_plan_id UUID NOT NULL REFERENCES service_plans(id) ON DELETE CASCADE,
    status TEXT NOT NULL CHECK (status IN ('published','finalized')),
    snapshot_payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE service_plans IS 'Draft/published service-plan metadata that composes setlist versions with non-song blocks.';
COMMENT ON COLUMN service_plans.service_date_time IS 'Scheduled date-time for the worship service.';
COMMENT ON COLUMN service_plans.status IS 'Lifecycle state for service plan publish/finalization workflow.';
COMMENT ON COLUMN service_plans.publish_note IS 'Optional operator note captured at publish time.';

COMMENT ON TABLE service_plan_setlist_attachments IS 'Ordered references to immutable setlist versions attached to a service plan.';
COMMENT ON COLUMN service_plan_setlist_attachments.setlist_id IS 'Parent setlist aggregate identifier referenced by this service plan.';
COMMENT ON COLUMN service_plan_setlist_attachments.setlist_version_id IS 'Immutable setlist version identifier attached to the service plan.';
COMMENT ON COLUMN service_plan_setlist_attachments.attachment_order IS 'Deterministic composition order across multiple attached setlists.';

COMMENT ON TABLE service_plan_blocks IS 'Plan-scoped ordered blocks for song and non-song service moments.';
COMMENT ON COLUMN service_plan_blocks.block_type IS 'Block taxonomy: praise, worship, offering, altar_call, communion, or special.';
COMMENT ON COLUMN service_plan_blocks.override_key IS 'Service-specific key override isolated from canonical arrangement records.';
COMMENT ON COLUMN service_plan_blocks.override_mode IS 'Service-specific mode override isolated from canonical arrangement records.';
COMMENT ON COLUMN service_plan_blocks.source_setlist_version_id IS 'Source setlist version reference for provenance of song-derived blocks.';
COMMENT ON COLUMN service_plan_blocks.source_setlist_item_id IS 'Source setlist item reference for provenance of song-derived blocks.';

COMMENT ON TABLE service_plan_snapshots IS 'Immutable effective-sequence snapshots captured on publish/finalize events.';
COMMENT ON COLUMN service_plan_snapshots.snapshot_payload IS 'Serialized immutable snapshot content used for historical retrieval.';
