-- ADR-025 asynchronous asset processing orchestration. Jobs and derived results
-- are version-pinned and contain only storage/result references, never payloads,
-- signed URLs, private notes, lyrics, or object credentials.

CREATE TABLE asset_processing_job_types (
    code varchar(64) PRIMARY KEY,
    display_name varchar(128) NOT NULL,
    sort_order integer NOT NULL,
    CONSTRAINT asset_processing_job_types_code_format CHECK (code = upper(code) AND code ~ '^[A-Z][A-Z0-9_]*$')
);

INSERT INTO asset_processing_job_types (code, display_name, sort_order) VALUES
    ('VIRUS_SCAN', 'Virus scan', 10),
    ('PREVIEW_GENERATION', 'Preview generation', 20),
    ('WAVEFORM_ANALYSIS', 'Waveform analysis', 30),
    ('AUDIO_TRANSCODING', 'Audio transcoding', 40),
    ('METADATA_EXTRACTION', 'Metadata extraction', 50);

CREATE TABLE asset_processing_jobs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_version_id uuid NOT NULL REFERENCES asset_versions (id) ON DELETE RESTRICT,
    job_type_code varchar(64) NOT NULL REFERENCES asset_processing_job_types (code) ON DELETE RESTRICT,
    processor_type varchar(128) NOT NULL,
    processor_version varchar(64) NOT NULL,
    input_checksum varchar(256) NOT NULL,
    status_code varchar(64) NOT NULL,
    attempts integer NOT NULL DEFAULT 0,
    max_attempts integer NOT NULL DEFAULT 3,
    created_at timestamptz NOT NULL DEFAULT now(),
    available_at timestamptz NOT NULL DEFAULT now(),
    started_at timestamptz,
    completed_at timestamptz,
    error_code varchar(128),
    sanitized_error_detail text,
    output_result_id uuid,
    CONSTRAINT asset_processing_jobs_status_check CHECK (status_code IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'DEAD_LETTERED')),
    CONSTRAINT asset_processing_jobs_attempts_check CHECK (attempts >= 0 AND max_attempts > 0),
    CONSTRAINT asset_processing_jobs_processor_type_not_blank CHECK (btrim(processor_type) <> ''),
    CONSTRAINT asset_processing_jobs_processor_version_not_blank CHECK (btrim(processor_version) <> ''),
    CONSTRAINT asset_processing_jobs_input_checksum_not_blank CHECK (btrim(input_checksum) <> ''),
    CONSTRAINT asset_processing_jobs_idempotency UNIQUE (
        asset_version_id, job_type_code, processor_type, processor_version, input_checksum
    )
);

CREATE INDEX asset_processing_jobs_runnable_idx ON asset_processing_jobs (status_code, available_at);
CREATE INDEX asset_processing_jobs_version_idx ON asset_processing_jobs (asset_version_id, created_at);

CREATE TABLE asset_processing_results (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id uuid NOT NULL REFERENCES asset_processing_jobs (id) ON DELETE RESTRICT,
    asset_version_id uuid NOT NULL REFERENCES asset_versions (id) ON DELETE RESTRICT,
    result_type_code varchar(64) NOT NULL REFERENCES asset_processing_job_types (code) ON DELETE RESTRICT,
    processor_type varchar(128) NOT NULL,
    processor_version varchar(64) NOT NULL,
    input_checksum varchar(256) NOT NULL,
    status_code varchar(64) NOT NULL,
    output_storage_key text,
    output_mime_type varchar(255),
    output_byte_size bigint,
    result_metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT asset_processing_results_status_check CHECK (status_code IN ('CLEAN', 'UNSAFE', 'AVAILABLE', 'FAILED')),
    CONSTRAINT asset_processing_results_output_byte_size_check CHECK (output_byte_size IS NULL OR output_byte_size >= 0)
);

ALTER TABLE asset_processing_jobs
    ADD CONSTRAINT asset_processing_jobs_output_result_fk
    FOREIGN KEY (output_result_id) REFERENCES asset_processing_results (id) ON DELETE RESTRICT;

CREATE INDEX asset_processing_results_version_idx ON asset_processing_results (asset_version_id, result_type_code, created_at DESC);

DROP TRIGGER asset_versions_immutable_update ON asset_versions;

CREATE OR REPLACE FUNCTION prevent_asset_version_update()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.lifecycle_status_code IS DISTINCT FROM OLD.lifecycle_status_code
            OR NEW.processing_status_code IS DISTINCT FROM OLD.processing_status_code THEN
        NEW.id := OLD.id;
        NEW.stable_identifier := OLD.stable_identifier;
        NEW.asset_id := OLD.asset_id;
        NEW.version_number := OLD.version_number;
        NEW.revision_code := OLD.revision_code;
        NEW.storage_provider_code := OLD.storage_provider_code;
        NEW.storage_region := OLD.storage_region;
        NEW.storage_bucket_alias := OLD.storage_bucket_alias;
        NEW.storage_key := OLD.storage_key;
        NEW.checksum_algorithm := OLD.checksum_algorithm;
        NEW.checksum_value := OLD.checksum_value;
        NEW.mime_type := OLD.mime_type;
        NEW.byte_size := OLD.byte_size;
        NEW.source_uri := OLD.source_uri;
        NEW.provenance_summary := OLD.provenance_summary;
        NEW.created_by := OLD.created_by;
        NEW.created_at := OLD.created_at;
        NEW.access_policy_code := OLD.access_policy_code;
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'asset_versions rows are immutable except lifecycle and processing state transitions';
END;
$$;

CREATE TRIGGER asset_versions_immutable_update
    BEFORE UPDATE ON asset_versions
    FOR EACH ROW
    EXECUTE FUNCTION prevent_asset_version_update();
