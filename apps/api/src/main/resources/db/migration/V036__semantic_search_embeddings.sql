-- ADR-026 semantic discovery records. These are derived from approved_search_documents
-- and must not be queried without reapplying the approved search eligibility gate.

CREATE TABLE approved_semantic_embedding_records (
    embedding_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    instance_id uuid,
    song_id uuid NOT NULL REFERENCES songs (id) ON DELETE CASCADE,
    arrangement_id uuid REFERENCES arrangements (id) ON DELETE CASCADE,
    package_visible boolean NOT NULL,
    provider_identifier varchar(128) NOT NULL,
    model_version varchar(128) NOT NULL,
    source_document_hash char(64) NOT NULL,
    generated_at timestamptz NOT NULL DEFAULT now(),
    projection_version integer NOT NULL,
    approved boolean NOT NULL,
    active boolean NOT NULL,
    visible boolean NOT NULL,
    licensed boolean NOT NULL,
    vector_projection jsonb NOT NULL,
    invalidated_at timestamptz,
    invalidation_reason varchar(64),
    CONSTRAINT approved_semantic_embedding_model_not_blank CHECK (btrim(provider_identifier) <> '' AND btrim(model_version) <> ''),
    CONSTRAINT approved_semantic_embedding_hash_valid CHECK (source_document_hash ~ '^[0-9a-f]{64}$')
);

CREATE UNIQUE INDEX approved_semantic_embedding_current_unique_idx
    ON approved_semantic_embedding_records (
        song_id,
        COALESCE(arrangement_id, '00000000-0000-0000-0000-000000000000'::uuid),
        COALESCE(instance_id, '00000000-0000-0000-0000-000000000000'::uuid),
        provider_identifier,
        model_version,
        projection_version
    )
    WHERE invalidated_at IS NULL;
CREATE INDEX approved_semantic_embedding_eligibility_idx
    ON approved_semantic_embedding_records (instance_id, approved, active, visible, licensed, package_visible)
    WHERE invalidated_at IS NULL;
CREATE INDEX approved_semantic_embedding_source_hash_idx
    ON approved_semantic_embedding_records (source_document_hash);

COMMENT ON TABLE approved_semantic_embedding_records IS
    'Derived ADR-026 user-facing semantic discovery embeddings generated only from approved, active, visible, license-safe metadata. Runtime search must still reapply approved_search_documents eligibility before ranking or hydration.';
COMMENT ON COLUMN approved_semantic_embedding_records.provider_identifier IS
    'Embedding provider or local model identifier selected by configured residency/privacy policy.';
COMMENT ON COLUMN approved_semantic_embedding_records.model_version IS
    'Exact embedding model version used to support rebuild and invalidation when model governance changes.';
COMMENT ON COLUMN approved_semantic_embedding_records.source_document_hash IS
    'SHA-256 hash of the sanitized approved metadata document used for embedding generation.';
COMMENT ON COLUMN approved_semantic_embedding_records.vector_projection IS
    'Vector payload or ANN index projection reference; never source text, lyrics, private notes, rehearsal notes, or feedback free text.';
