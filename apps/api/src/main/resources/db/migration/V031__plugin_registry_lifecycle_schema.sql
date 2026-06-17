CREATE TABLE plugin_package_versions (
    plugin_version_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    stable_plugin_id varchar(128) NOT NULL,
    package_name varchar(256) NOT NULL,
    provider varchar(256) NOT NULL,
    semantic_version varchar(64) NOT NULL,
    supported_spi_versions jsonb NOT NULL DEFAULT '[]'::jsonb,
    extension_points jsonb NOT NULL DEFAULT '[]'::jsonb,
    trust_tier varchar(32) NOT NULL,
    checksum_sha256 varchar(128) NOT NULL,
    signature_ref varchar(512),
    certification_status varchar(32) NOT NULL,
    installation_source varchar(512) NOT NULL,
    lifecycle_status varchar(32) NOT NULL,
    deprecation_status varchar(32) NOT NULL DEFAULT 'ACTIVE',
    configuration_schema jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(128) NOT NULL,
    updated_by varchar(128) NOT NULL,
    CONSTRAINT plugin_package_versions_identity_unique UNIQUE (stable_plugin_id, semantic_version),
    CONSTRAINT plugin_package_versions_semver CHECK (semantic_version ~ '^\d+\.\d+\.\d+([-+][0-9A-Za-z.-]+)?$'),
    CONSTRAINT plugin_package_versions_lifecycle CHECK (lifecycle_status IN ('REGISTERED', 'APPROVED', 'ENABLED', 'DISABLED', 'REVOKED', 'DELETED')),
    CONSTRAINT plugin_package_versions_no_plaintext_secrets CHECK (configuration_schema::text !~* '(passwordValue|secretValue|tokenValue|apiKeyValue|credentialValue)')
);

CREATE TABLE plugin_configuration_snapshots (
    configuration_version_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    plugin_version_id uuid NOT NULL REFERENCES plugin_package_versions (plugin_version_id),
    church_instance_id varchar(128) NOT NULL,
    environment varchar(32) NOT NULL,
    extension_point varchar(128) NOT NULL,
    configuration_values jsonb NOT NULL DEFAULT '{}'::jsonb,
    secret_refs jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(128) NOT NULL,
    CONSTRAINT plugin_configuration_snapshots_environment CHECK (environment IN ('DEVELOPMENT', 'STAGING', 'PRODUCTION')),
    CONSTRAINT plugin_configuration_snapshots_no_plaintext_secrets CHECK (
        configuration_values::text !~* '(password|secret|token|authorization|api[_-]?key|private[_-]?key|credential)'
    )
);

CREATE TABLE plugin_instance_enablements (
    enablement_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    plugin_version_id uuid NOT NULL REFERENCES plugin_package_versions (plugin_version_id),
    configuration_version_id uuid NOT NULL REFERENCES plugin_configuration_snapshots (configuration_version_id),
    church_instance_id varchar(128) NOT NULL,
    environment varchar(32) NOT NULL,
    extension_point varchar(128) NOT NULL,
    lifecycle_status varchar(32) NOT NULL,
    enabled_at timestamptz NOT NULL DEFAULT now(),
    disabled_at timestamptz,
    enabled_by varchar(128) NOT NULL,
    disabled_by varchar(128),
    CONSTRAINT plugin_instance_enablements_scope_unique UNIQUE (church_instance_id, environment, extension_point),
    CONSTRAINT plugin_instance_enablements_environment CHECK (environment IN ('DEVELOPMENT', 'STAGING', 'PRODUCTION')),
    CONSTRAINT plugin_instance_enablements_lifecycle CHECK (lifecycle_status IN ('ENABLED', 'DISABLED', 'REVOKED'))
);

CREATE INDEX plugin_package_versions_package_idx
    ON plugin_package_versions (stable_plugin_id, semantic_version);
CREATE INDEX plugin_configuration_snapshots_scope_idx
    ON plugin_configuration_snapshots (church_instance_id, environment, extension_point, created_at DESC);
CREATE INDEX plugin_instance_enablements_scope_idx
    ON plugin_instance_enablements (church_instance_id, environment, extension_point, lifecycle_status);

COMMENT ON TABLE plugin_package_versions IS
    'Canonical ADR-030 plugin package registry with provider, version, SPI, extension point, trust, signature, and lifecycle metadata.';
COMMENT ON TABLE plugin_configuration_snapshots IS
    'Immutable plugin configuration snapshots; secrets are stored only as references.';
COMMENT ON TABLE plugin_instance_enablements IS
    'Explicit church-instance, environment, extension-point plugin enablement records.';
