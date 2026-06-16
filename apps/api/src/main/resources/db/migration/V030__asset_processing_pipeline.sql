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

COMMENT ON TABLE asset_processing_job_types IS 'Controlled vocabulary of asynchronous processing job types used by ADR-025 media processing orchestration.';
COMMENT ON COLUMN asset_processing_job_types.code IS 'Stable machine-readable processing job type code, such as VIRUS_SCAN or AUDIO_TRANSCODING.';
COMMENT ON COLUMN asset_processing_job_types.display_name IS 'Human-readable processing job type label for operator tooling.';
COMMENT ON COLUMN asset_processing_job_types.sort_order IS 'Deterministic display order for job type lists.';

COMMENT ON TABLE asset_processing_jobs IS 'Durable, idempotent work queue records for asynchronous processing of a specific immutable asset version.';
COMMENT ON COLUMN asset_processing_jobs.id IS 'Internal unique identifier for this processing job and operator diagnostic reference.';
COMMENT ON COLUMN asset_processing_jobs.asset_version_id IS 'Immutable source asset version that this job reads; jobs never target a mutable logical asset pointer.';
COMMENT ON COLUMN asset_processing_jobs.job_type_code IS 'Type of processing work to perform, such as virus scan, preview generation, waveform analysis, transcoding, or metadata extraction.';
COMMENT ON COLUMN asset_processing_jobs.processor_type IS 'Stable processor implementation identifier used for idempotency, replay, and audit.';
COMMENT ON COLUMN asset_processing_jobs.processor_version IS 'Processor implementation version used so reruns after processor upgrades produce separate auditable jobs.';
COMMENT ON COLUMN asset_processing_jobs.input_checksum IS 'Checksum of the source asset version input; included in idempotency so historical processing remains tied to exact bytes.';
COMMENT ON COLUMN asset_processing_jobs.status_code IS 'Current job execution status: QUEUED, RUNNING, SUCCEEDED, FAILED, or DEAD_LETTERED.';
COMMENT ON COLUMN asset_processing_jobs.attempts IS 'Number of started processing attempts recorded for retry and dead-letter decisions.';
COMMENT ON COLUMN asset_processing_jobs.max_attempts IS 'Maximum attempts allowed before the job is moved to dead-letter status.';
COMMENT ON COLUMN asset_processing_jobs.created_at IS 'Timestamp when the processing job was first enqueued.';
COMMENT ON COLUMN asset_processing_jobs.available_at IS 'Earliest timestamp when the queued job is eligible for execution, including retry backoff scheduling.';
COMMENT ON COLUMN asset_processing_jobs.started_at IS 'Timestamp when the most recent processing attempt started.';
COMMENT ON COLUMN asset_processing_jobs.completed_at IS 'Timestamp when the job reached a terminal successful or dead-letter state.';
COMMENT ON COLUMN asset_processing_jobs.error_code IS 'Sanitized machine-readable failure code for operator diagnosis without payload or credential disclosure.';
COMMENT ON COLUMN asset_processing_jobs.sanitized_error_detail IS 'Optional human-readable diagnostic detail with sensitive URLs, credentials, lyrics, and private notes excluded.';
COMMENT ON COLUMN asset_processing_jobs.output_result_id IS 'Optional pointer to the processing result produced by a successful job.';

COMMENT ON TABLE asset_processing_results IS 'Version-pinned processing outputs and analysis metadata produced by asynchronous jobs without overwriting source binary metadata.';
COMMENT ON COLUMN asset_processing_results.id IS 'Internal unique identifier for this processing result.';
COMMENT ON COLUMN asset_processing_results.job_id IS 'Processing job that produced this result.';
COMMENT ON COLUMN asset_processing_results.asset_version_id IS 'Immutable source asset version for which this result was produced.';
COMMENT ON COLUMN asset_processing_results.result_type_code IS 'Type of processing result, aligned with the originating processing job type.';
COMMENT ON COLUMN asset_processing_results.processor_type IS 'Stable processor implementation identifier that produced the result.';
COMMENT ON COLUMN asset_processing_results.processor_version IS 'Processor implementation version that produced the result.';
COMMENT ON COLUMN asset_processing_results.input_checksum IS 'Checksum of the source asset bytes used to produce this result.';
COMMENT ON COLUMN asset_processing_results.status_code IS 'Result safety/availability status: CLEAN, UNSAFE, AVAILABLE, or FAILED.';
COMMENT ON COLUMN asset_processing_results.output_storage_key IS 'Storage key for derived output artifacts such as previews, transcodes, or waveform documents; never stores signed URLs or credentials.';
COMMENT ON COLUMN asset_processing_results.output_mime_type IS 'MIME type of the derived output artifact when one exists.';
COMMENT ON COLUMN asset_processing_results.output_byte_size IS 'Byte size of the derived output artifact when one exists.';
COMMENT ON COLUMN asset_processing_results.result_metadata IS 'Small JSON metadata produced by processors, excluding raw payloads, lyrics, private notes, signed URLs, and credentials.';
COMMENT ON COLUMN asset_processing_results.created_at IS 'Timestamp when the processing result was recorded.';

COMMENT ON FUNCTION prevent_asset_version_update() IS 'Allows only lifecycle and processing status transitions on asset_versions while preserving immutable source binary metadata.';
COMMENT ON TRIGGER asset_versions_immutable_update ON asset_versions IS 'Prevents mutation of historical asset version metadata except controlled lifecycle and processing state transitions.';
