-- ADR-025 Subtask 3: explicitly typed asset attachments.
-- Attachments always pin immutable asset_versions.id so service and rehearsal
-- history never floats to logical_assets.current_asset_version_id changes.

CREATE TABLE asset_attachment_target_types (
    code varchar(64) PRIMARY KEY,
    display_name varchar(128) NOT NULL,
    requires_service_context boolean NOT NULL DEFAULT false,
    is_active boolean NOT NULL DEFAULT true,
    sort_order integer NOT NULL,
    CONSTRAINT asset_attachment_target_types_code_valid CHECK (code IN (
        'SONG',
        'ARRANGEMENT',
        'SERVICE',
        'SERVICE_ITEM',
        'REHEARSAL_SESSION',
        'REHEARSAL_ISSUE',
        'REHEARSAL_ISSUE_ACTION',
        'SERVICE_ARRANGEMENT_OVERRIDE'
    )),
    CONSTRAINT asset_attachment_target_types_display_name_not_blank CHECK (btrim(display_name) <> '')
);

INSERT INTO asset_attachment_target_types (code, display_name, requires_service_context, sort_order) VALUES
    ('SONG', 'Song catalog attachment', false, 10),
    ('ARRANGEMENT', 'Arrangement catalog attachment', false, 20),
    ('SERVICE', 'Service attachment', true, 30),
    ('SERVICE_ITEM', 'Service item attachment', true, 40),
    ('REHEARSAL_SESSION', 'Rehearsal session attachment', true, 50),
    ('REHEARSAL_ISSUE', 'Rehearsal issue attachment', true, 60),
    ('REHEARSAL_ISSUE_ACTION', 'Rehearsal issue action attachment', true, 70),
    ('SERVICE_ARRANGEMENT_OVERRIDE', 'Service arrangement override attachment', true, 80);

CREATE TABLE asset_attachment_purpose_codes (
    code varchar(64) PRIMARY KEY,
    display_name varchar(128) NOT NULL,
    is_active boolean NOT NULL DEFAULT true,
    sort_order integer NOT NULL,
    CONSTRAINT asset_attachment_purpose_codes_code_format CHECK (code = upper(code) AND code ~ '^[A-Z][A-Z0-9_]*$'),
    CONSTRAINT asset_attachment_purpose_codes_display_name_not_blank CHECK (btrim(display_name) <> '')
);

INSERT INTO asset_attachment_purpose_codes (code, display_name, sort_order) VALUES
    ('PRIMARY_CHART', 'Primary chart', 10),
    ('REFERENCE', 'Reference material', 20),
    ('REHEARSAL', 'Rehearsal material', 30),
    ('PERFORMANCE', 'Performance material', 40),
    ('EVIDENCE', 'Issue evidence', 50),
    ('FOLLOW_UP', 'Follow-up action material', 60),
    ('LOCAL_OVERRIDE', 'Service-specific override', 70);

CREATE TABLE asset_attachments (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    target_type_code varchar(64) NOT NULL REFERENCES asset_attachment_target_types (code) ON DELETE RESTRICT,
    target_id uuid NOT NULL,
    service_plan_id uuid REFERENCES service_plans (id) ON DELETE CASCADE,
    asset_version_id uuid NOT NULL REFERENCES asset_versions (id) ON DELETE RESTRICT,
    attachment_type_code varchar(64) NOT NULL REFERENCES asset_type_codes (code) ON DELETE RESTRICT,
    display_label varchar(255) NOT NULL,
    sort_order integer NOT NULL,
    purpose_code varchar(64) NOT NULL REFERENCES asset_attachment_purpose_codes (code) ON DELETE RESTRICT,
    required_for_use boolean NOT NULL DEFAULT false,
    effective_from timestamptz,
    effective_until timestamptz,
    visibility_policy_code varchar(64) NOT NULL REFERENCES asset_access_policy_codes (code) ON DELETE RESTRICT,
    archived_at timestamptz,
    archived_by varchar(255),
    archive_reason text,
    created_by varchar(255) NOT NULL,
    updated_by varchar(255) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT asset_attachments_display_label_not_blank CHECK (btrim(display_label) <> ''),
    CONSTRAINT asset_attachments_sort_order_positive CHECK (sort_order > 0),
    CONSTRAINT asset_attachments_effective_range CHECK (effective_until IS NULL OR effective_from IS NULL OR effective_until > effective_from),
    CONSTRAINT asset_attachments_created_by_not_blank CHECK (btrim(created_by) <> ''),
    CONSTRAINT asset_attachments_updated_by_not_blank CHECK (btrim(updated_by) <> ''),
    CONSTRAINT asset_attachments_archived_by_not_blank CHECK (archived_by IS NULL OR btrim(archived_by) <> ''),
    CONSTRAINT asset_attachments_archived_pair CHECK (
        (archived_at IS NULL AND archived_by IS NULL) OR (archived_at IS NOT NULL AND archived_by IS NOT NULL)
    ),
    CONSTRAINT asset_attachments_service_context_required CHECK (
        (target_type_code IN ('SONG', 'ARRANGEMENT') AND service_plan_id IS NULL)
        OR (target_type_code NOT IN ('SONG', 'ARRANGEMENT') AND service_plan_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX asset_attachments_active_position_unique_idx
    ON asset_attachments (target_type_code, target_id, purpose_code, sort_order)
    WHERE archived_at IS NULL;
CREATE INDEX asset_attachments_target_idx ON asset_attachments (target_type_code, target_id, archived_at, sort_order);
CREATE INDEX asset_attachments_service_idx ON asset_attachments (service_plan_id, target_type_code, sort_order);
CREATE INDEX asset_attachments_asset_version_idx ON asset_attachments (asset_version_id);

CREATE TABLE asset_attachment_audit_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    attachment_id uuid NOT NULL REFERENCES asset_attachments (id) ON DELETE CASCADE,
    event_type varchar(64) NOT NULL,
    previous_asset_version_id uuid REFERENCES asset_versions (id) ON DELETE RESTRICT,
    new_asset_version_id uuid REFERENCES asset_versions (id) ON DELETE RESTRICT,
    previous_sort_order integer,
    new_sort_order integer,
    reason text,
    changed_by varchar(255) NOT NULL,
    changed_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT asset_attachment_audit_events_type_valid CHECK (event_type IN ('CREATED', 'REORDERED', 'ARCHIVED', 'REPLACED')),
    CONSTRAINT asset_attachment_audit_events_changed_by_not_blank CHECK (btrim(changed_by) <> '')
);

CREATE INDEX asset_attachment_audit_events_attachment_idx
    ON asset_attachment_audit_events (attachment_id, changed_at DESC);

CREATE OR REPLACE FUNCTION enforce_asset_attachment_integrity()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    version_status varchar(64);
    logical_asset_type varchar(64);
    target_exists boolean;
BEGIN
    SELECT av.lifecycle_status_code, la.asset_type_code
    INTO version_status, logical_asset_type
    FROM asset_versions av
    JOIN logical_assets la ON la.id = av.asset_id
    WHERE av.id = NEW.asset_version_id;

    IF version_status IS NULL THEN
        RAISE EXCEPTION 'asset attachment must reference an existing immutable asset version';
    END IF;

    IF NEW.archived_at IS NULL AND version_status = 'ARCHIVED' THEN
        RAISE EXCEPTION 'active asset attachments cannot reference archived asset versions';
    END IF;

    IF logical_asset_type <> NEW.attachment_type_code THEN
        RAISE EXCEPTION 'attachment_type_code must match the referenced logical asset type';
    END IF;

    CASE NEW.target_type_code
        WHEN 'SONG' THEN
            SELECT EXISTS (SELECT 1 FROM songs WHERE id = NEW.target_id) INTO target_exists;
        WHEN 'ARRANGEMENT' THEN
            SELECT EXISTS (SELECT 1 FROM arrangements WHERE id = NEW.target_id) INTO target_exists;
        WHEN 'SERVICE' THEN
            SELECT EXISTS (SELECT 1 FROM service_plans WHERE id = NEW.target_id AND id = NEW.service_plan_id) INTO target_exists;
        WHEN 'SERVICE_ITEM' THEN
            SELECT EXISTS (SELECT 1 FROM service_plan_blocks WHERE id = NEW.target_id AND service_plan_id = NEW.service_plan_id) INTO target_exists;
        WHEN 'REHEARSAL_SESSION' THEN
            SELECT EXISTS (SELECT 1 FROM rehearsal_sessions WHERE id = NEW.target_id AND service_plan_id = NEW.service_plan_id) INTO target_exists;
        WHEN 'REHEARSAL_ISSUE' THEN
            SELECT EXISTS (SELECT 1 FROM rehearsal_issues WHERE id = NEW.target_id AND service_plan_id = NEW.service_plan_id) INTO target_exists;
        WHEN 'REHEARSAL_ISSUE_ACTION' THEN
            SELECT EXISTS (SELECT 1 FROM rehearsal_issue_actions WHERE id = NEW.target_id AND service_plan_id = NEW.service_plan_id) INTO target_exists;
        WHEN 'SERVICE_ARRANGEMENT_OVERRIDE' THEN
            SELECT EXISTS (SELECT 1 FROM service_arrangement_overrides WHERE id = NEW.target_id AND service_plan_id = NEW.service_plan_id) INTO target_exists;
        ELSE
            target_exists := false;
    END CASE;

    IF NOT target_exists THEN
        RAISE EXCEPTION 'asset attachment target does not exist or does not belong to the service context';
    END IF;

    NEW.updated_at := now();
    RETURN NEW;
END;
$$;

CREATE TRIGGER asset_attachments_integrity_trigger
    BEFORE INSERT OR UPDATE ON asset_attachments
    FOR EACH ROW
    EXECUTE FUNCTION enforce_asset_attachment_integrity();

COMMENT ON TABLE asset_attachments IS
    'Typed links from supported Cadentia entities to immutable asset versions. Service and rehearsal rows pin asset_versions.id and do not follow logical current-version changes.';
COMMENT ON COLUMN asset_attachments.target_type_code IS 'Supported target discriminator; unsupported entity types are rejected by vocabulary and trigger validation.';
COMMENT ON COLUMN asset_attachments.asset_version_id IS 'Immutable asset version selected for this target. Replace by creating an audited replacement, not by following logical current asset pointers.';
COMMENT ON COLUMN asset_attachments.service_plan_id IS 'Required service context for service, service item, rehearsal, issue/action, and service override targets; catalog song/arrangement attachments leave this NULL.';
COMMENT ON COLUMN asset_attachments.purpose_code IS 'Structured purpose used for ordering, duplicate-position checks, and UI grouping.';
COMMENT ON TABLE asset_attachment_audit_events IS
    'Append-only audit trail for attachment creation, reorder, archive, and explicit asset-version replacement events.';
