-- ADR-025 Subtask 1: normalized media/asset metadata domain.
-- Binary payloads are intentionally excluded; asset_versions only stores durable
-- object-storage metadata, checksums, licensing, lifecycle, and access policy.

CREATE TABLE asset_type_codes (
    code varchar(64) PRIMARY KEY,
    display_name varchar(128) NOT NULL,
    description text,
    media_family varchar(64) NOT NULL,
    is_core_type boolean NOT NULL DEFAULT false,
    is_active boolean NOT NULL DEFAULT true,
    sort_order integer NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT asset_type_codes_code_format CHECK (code = upper(code) AND code ~ '^[A-Z][A-Z0-9_]*$'),
    CONSTRAINT asset_type_codes_display_name_not_blank CHECK (btrim(display_name) <> ''),
    CONSTRAINT asset_type_codes_media_family_valid CHECK (media_family IN ('DOCUMENT', 'AUDIO', 'MIDI', 'VIDEO', 'IMAGE', 'OTHER')),
    CONSTRAINT asset_type_codes_updated_at_not_before_created_at CHECK (updated_at >= created_at)
);

INSERT INTO asset_type_codes (code, display_name, description, media_family, is_core_type, sort_order) VALUES
    ('PDF', 'PDF', 'Portable document asset such as a licensed lead sheet or packet.', 'DOCUMENT', true, 10),
    ('CHORD_CHART', 'Chord chart', 'Chord chart in a supported document or text-derived format.', 'DOCUMENT', true, 20),
    ('STEM', 'Stem', 'Isolated instrument or vocal audio stem.', 'AUDIO', true, 30),
    ('BACKING_TRACK', 'Backing track', 'Full or partial accompaniment track for rehearsal or service use.', 'AUDIO', true, 40),
    ('CLICK_TRACK', 'Click track', 'Metronome or count-off audio track.', 'AUDIO', true, 50),
    ('MIDI_CUE', 'MIDI cue', 'MIDI file or cue sequence for automation and playback control.', 'MIDI', true, 60),
    ('REHEARSAL_RECORDING', 'Rehearsal recording', 'Audio or video captured during rehearsal.', 'AUDIO', true, 70),
    ('PREVIEW', 'Preview', 'Generated preview, excerpt, thumbnail, or waveform-derived media.', 'OTHER', true, 80),
    ('LOCAL_EXTENSION', 'Local extension', 'Instance-local extension type for future asset workflows.', 'OTHER', false, 1000);

CREATE TABLE asset_lifecycle_status_codes (
    code varchar(64) PRIMARY KEY,
    display_name varchar(128) NOT NULL,
    is_referenceable boolean NOT NULL DEFAULT false,
    is_terminal boolean NOT NULL DEFAULT false,
    is_active boolean NOT NULL DEFAULT true,
    sort_order integer NOT NULL,
    CONSTRAINT asset_lifecycle_status_codes_code_format CHECK (code = upper(code) AND code ~ '^[A-Z][A-Z0-9_]*$'),
    CONSTRAINT asset_lifecycle_status_codes_display_name_not_blank CHECK (btrim(display_name) <> '')
);

INSERT INTO asset_lifecycle_status_codes (code, display_name, is_referenceable, is_terminal, sort_order) VALUES
    ('DRAFT', 'Draft', false, false, 10),
    ('AVAILABLE', 'Available', true, false, 20),
    ('ARCHIVED', 'Archived', false, true, 30),
    ('QUARANTINED', 'Quarantined', false, false, 40),
    ('REPLACED', 'Replaced', false, true, 50),
    ('DELETED', 'Deleted metadata tombstone', false, true, 60);

CREATE TABLE asset_processing_status_codes (
    code varchar(64) PRIMARY KEY,
    display_name varchar(128) NOT NULL,
    is_success boolean NOT NULL DEFAULT false,
    is_failure boolean NOT NULL DEFAULT false,
    is_active boolean NOT NULL DEFAULT true,
    sort_order integer NOT NULL,
    CONSTRAINT asset_processing_status_codes_code_format CHECK (code = upper(code) AND code ~ '^[A-Z][A-Z0-9_]*$'),
    CONSTRAINT asset_processing_status_codes_display_name_not_blank CHECK (btrim(display_name) <> '')
);

INSERT INTO asset_processing_status_codes (code, display_name, is_success, is_failure, sort_order) VALUES
    ('NOT_REQUIRED', 'Processing not required', true, false, 10),
    ('PENDING_SCAN', 'Pending virus scan', false, false, 20),
    ('SCANNING', 'Scanning', false, false, 30),
    ('PROCESSING', 'Processing', false, false, 40),
    ('READY', 'Ready', true, false, 50),
    ('FAILED', 'Failed', false, true, 60),
    ('REJECTED', 'Rejected', false, true, 70);

CREATE TABLE asset_license_status_codes (
    code varchar(64) PRIMARY KEY,
    display_name varchar(128) NOT NULL,
    permits_access boolean NOT NULL DEFAULT false,
    requires_expiration boolean NOT NULL DEFAULT false,
    is_active boolean NOT NULL DEFAULT true,
    sort_order integer NOT NULL,
    CONSTRAINT asset_license_status_codes_code_format CHECK (code = upper(code) AND code ~ '^[A-Z][A-Z0-9_]*$'),
    CONSTRAINT asset_license_status_codes_display_name_not_blank CHECK (btrim(display_name) <> '')
);

INSERT INTO asset_license_status_codes (code, display_name, permits_access, requires_expiration, sort_order) VALUES
    ('UNKNOWN', 'Unknown', false, false, 10),
    ('NOT_REQUIRED', 'Not required', true, false, 20),
    ('CCLI_COVERED', 'CCLI covered', true, false, 30),
    ('DIRECT_PERMISSION', 'Direct permission', true, false, 40),
    ('PURCHASED', 'Purchased', true, false, 50),
    ('EXPIRES', 'Expires', true, true, 60),
    ('RESTRICTED', 'Restricted', false, false, 70),
    ('EXPIRED', 'Expired', false, false, 80),
    ('REVOKED', 'Revoked', false, false, 90);

CREATE TABLE asset_access_policy_codes (
    code varchar(64) PRIMARY KEY,
    display_name varchar(128) NOT NULL,
    description text,
    is_sensitive boolean NOT NULL DEFAULT false,
    is_active boolean NOT NULL DEFAULT true,
    sort_order integer NOT NULL,
    CONSTRAINT asset_access_policy_codes_code_format CHECK (code = upper(code) AND code ~ '^[A-Z][A-Z0-9_]*$'),
    CONSTRAINT asset_access_policy_codes_display_name_not_blank CHECK (btrim(display_name) <> '')
);

INSERT INTO asset_access_policy_codes (code, display_name, description, is_sensitive, sort_order) VALUES
    ('PUBLIC_METADATA', 'Public metadata', 'Metadata may be shown broadly; downloads still require lifecycle and licensing checks.', false, 10),
    ('CATALOG_REVIEWERS', 'Catalog reviewers', 'Visible to catalog reviewers and administrators.', true, 20),
    ('WORSHIP_TEAM', 'Worship team', 'Visible to worship leaders, schedulers, and assigned musicians.', true, 30),
    ('SERVICE_PARTICIPANTS', 'Service participants', 'Visible within a service or rehearsal participation context.', true, 40),
    ('ADMINS_ONLY', 'Administrators only', 'Restricted to administrators for sensitive or quarantined media.', true, 50),
    ('LOCAL_POLICY', 'Local policy', 'Instance-local extension point for church-specific access rules.', true, 1000);

CREATE TABLE logical_assets (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    stable_identifier uuid NOT NULL DEFAULT gen_random_uuid(),
    asset_type_code varchar(64) NOT NULL REFERENCES asset_type_codes (code) ON DELETE RESTRICT,
    title varchar(255) NOT NULL,
    description text,
    owner_actor varchar(255) NOT NULL,
    owning_ministry varchar(255),
    default_access_policy_code varchar(64) NOT NULL REFERENCES asset_access_policy_codes (code) ON DELETE RESTRICT,
    lifecycle_status_code varchar(64) NOT NULL REFERENCES asset_lifecycle_status_codes (code) ON DELETE RESTRICT,
    current_asset_version_id uuid,
    created_by varchar(255) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT logical_assets_stable_identifier_unique UNIQUE (stable_identifier),
    CONSTRAINT logical_assets_title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT logical_assets_owner_actor_not_blank CHECK (btrim(owner_actor) <> ''),
    CONSTRAINT logical_assets_created_by_not_blank CHECK (btrim(created_by) <> ''),
    CONSTRAINT logical_assets_updated_at_not_before_created_at CHECK (updated_at >= created_at)
);

CREATE INDEX logical_assets_asset_type_idx ON logical_assets (asset_type_code);
CREATE INDEX logical_assets_lifecycle_status_idx ON logical_assets (lifecycle_status_code);
CREATE INDEX logical_assets_access_policy_idx ON logical_assets (default_access_policy_code);

CREATE TABLE asset_versions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    stable_identifier uuid NOT NULL DEFAULT gen_random_uuid(),
    asset_id uuid NOT NULL REFERENCES logical_assets (id) ON DELETE RESTRICT,
    version_number integer NOT NULL,
    revision_code varchar(64),
    storage_provider_code varchar(64) NOT NULL,
    storage_region varchar(128),
    storage_bucket_alias varchar(128) NOT NULL,
    storage_key text NOT NULL,
    checksum_algorithm varchar(64) NOT NULL,
    checksum_value varchar(256) NOT NULL,
    mime_type varchar(255) NOT NULL,
    byte_size bigint NOT NULL,
    source_uri text,
    provenance_summary text NOT NULL,
    created_by varchar(255) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    lifecycle_status_code varchar(64) NOT NULL REFERENCES asset_lifecycle_status_codes (code) ON DELETE RESTRICT,
    processing_status_code varchar(64) NOT NULL REFERENCES asset_processing_status_codes (code) ON DELETE RESTRICT,
    access_policy_code varchar(64) NOT NULL REFERENCES asset_access_policy_codes (code) ON DELETE RESTRICT,
    CONSTRAINT asset_versions_stable_identifier_unique UNIQUE (stable_identifier),
    CONSTRAINT asset_versions_asset_version_number_unique UNIQUE (asset_id, version_number),
    CONSTRAINT asset_versions_version_number_positive CHECK (version_number > 0),
    CONSTRAINT asset_versions_revision_code_not_blank CHECK (revision_code IS NULL OR btrim(revision_code) <> ''),
    CONSTRAINT asset_versions_storage_provider_not_blank CHECK (btrim(storage_provider_code) <> ''),
    CONSTRAINT asset_versions_storage_bucket_alias_not_blank CHECK (btrim(storage_bucket_alias) <> ''),
    CONSTRAINT asset_versions_storage_key_not_blank CHECK (btrim(storage_key) <> ''),
    CONSTRAINT asset_versions_checksum_algorithm_not_blank CHECK (btrim(checksum_algorithm) <> ''),
    CONSTRAINT asset_versions_checksum_value_not_blank CHECK (btrim(checksum_value) <> ''),
    CONSTRAINT asset_versions_mime_type_not_blank CHECK (btrim(mime_type) <> '' AND position('/' IN mime_type) > 1),
    CONSTRAINT asset_versions_byte_size_positive CHECK (byte_size > 0),
    CONSTRAINT asset_versions_provenance_summary_not_blank CHECK (btrim(provenance_summary) <> ''),
    CONSTRAINT asset_versions_created_by_not_blank CHECK (btrim(created_by) <> '')
);

CREATE INDEX asset_versions_asset_id_idx ON asset_versions (asset_id);
CREATE INDEX asset_versions_lifecycle_status_idx ON asset_versions (lifecycle_status_code);
CREATE INDEX asset_versions_processing_status_idx ON asset_versions (processing_status_code);
CREATE INDEX asset_versions_storage_key_idx ON asset_versions (storage_key);

ALTER TABLE logical_assets
    ADD CONSTRAINT logical_assets_current_version_fk
    FOREIGN KEY (current_asset_version_id) REFERENCES asset_versions (id) ON DELETE RESTRICT DEFERRABLE INITIALLY IMMEDIATE;

CREATE TABLE asset_version_licenses (
    asset_version_id uuid PRIMARY KEY REFERENCES asset_versions (id) ON DELETE RESTRICT,
    license_status_code varchar(64) NOT NULL REFERENCES asset_license_status_codes (code) ON DELETE RESTRICT,
    license_source varchar(255) NOT NULL,
    license_reference text,
    usage_restrictions text,
    license_holder varchar(255),
    effective_at timestamptz,
    expires_at timestamptz,
    visibility_policy_code varchar(64) NOT NULL REFERENCES asset_access_policy_codes (code) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT asset_version_licenses_source_not_blank CHECK (btrim(license_source) <> ''),
    CONSTRAINT asset_version_licenses_expiration_range CHECK (expires_at IS NULL OR effective_at IS NULL OR expires_at >= effective_at),
    CONSTRAINT asset_version_licenses_expiring_status_has_expiration CHECK (license_status_code <> 'EXPIRES' OR expires_at IS NOT NULL)
);

CREATE INDEX asset_version_licenses_status_idx ON asset_version_licenses (license_status_code);
CREATE INDEX asset_version_licenses_expires_at_idx ON asset_version_licenses (expires_at);

CREATE TABLE asset_version_lifecycle_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_version_id uuid NOT NULL REFERENCES asset_versions (id) ON DELETE RESTRICT,
    from_lifecycle_status_code varchar(64) REFERENCES asset_lifecycle_status_codes (code) ON DELETE RESTRICT,
    to_lifecycle_status_code varchar(64) NOT NULL REFERENCES asset_lifecycle_status_codes (code) ON DELETE RESTRICT,
    reason_code varchar(64) NOT NULL,
    note text,
    changed_by varchar(255) NOT NULL,
    changed_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT asset_version_lifecycle_events_reason_not_blank CHECK (btrim(reason_code) <> ''),
    CONSTRAINT asset_version_lifecycle_events_changed_by_not_blank CHECK (btrim(changed_by) <> '')
);

CREATE INDEX asset_version_lifecycle_events_version_idx ON asset_version_lifecycle_events (asset_version_id, changed_at DESC);

CREATE OR REPLACE FUNCTION prevent_asset_version_update()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'asset_versions rows are immutable; create a replacement version or lifecycle event instead';
END;
$$;

CREATE TRIGGER asset_versions_immutable_update
    BEFORE UPDATE ON asset_versions
    FOR EACH ROW
    EXECUTE FUNCTION prevent_asset_version_update();
