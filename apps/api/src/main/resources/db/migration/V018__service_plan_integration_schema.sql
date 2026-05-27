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
