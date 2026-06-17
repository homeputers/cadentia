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

COMMENT ON COLUMN plugin_package_versions.plugin_version_id IS 'Stable identifier for one immutable plugin package version record.';
COMMENT ON COLUMN plugin_package_versions.stable_plugin_id IS 'Canonical plugin identifier that remains stable across package versions.';
COMMENT ON COLUMN plugin_package_versions.package_name IS 'Package name from the plugin distribution metadata.';
COMMENT ON COLUMN plugin_package_versions.provider IS 'Organization or maintainer providing the plugin package.';
COMMENT ON COLUMN plugin_package_versions.semantic_version IS 'Semver package version for deterministic upgrade and downgrade history.';
COMMENT ON COLUMN plugin_package_versions.supported_spi_versions IS 'JSON array of plugin SPI versions supported by this package version.';
COMMENT ON COLUMN plugin_package_versions.extension_points IS 'JSON array of extension points implemented by this package version.';
COMMENT ON COLUMN plugin_package_versions.trust_tier IS 'Trust classification such as CORE, VERIFIED, COMMUNITY, or LOCAL.';
COMMENT ON COLUMN plugin_package_versions.checksum_sha256 IS 'Package checksum metadata used for supply-chain integrity checks.';
COMMENT ON COLUMN plugin_package_versions.signature_ref IS 'Optional reference to a detached signature, transparency log entry, or attestation bundle.';
COMMENT ON COLUMN plugin_package_versions.certification_status IS 'Certification review state for the package version.';
COMMENT ON COLUMN plugin_package_versions.installation_source IS 'Registry, OCI, file, or package source used to install this plugin version.';
COMMENT ON COLUMN plugin_package_versions.lifecycle_status IS 'Registry lifecycle status for the package version.';
COMMENT ON COLUMN plugin_package_versions.deprecation_status IS 'Deprecation status used to steer admins away from unsupported versions.';
COMMENT ON COLUMN plugin_package_versions.configuration_schema IS 'JSON schema-like configuration contract; plaintext secret values are forbidden.';
COMMENT ON COLUMN plugin_package_versions.created_at IS 'Timestamp when the package version was first registered.';
COMMENT ON COLUMN plugin_package_versions.updated_at IS 'Timestamp when lifecycle or deprecation metadata last changed.';
COMMENT ON COLUMN plugin_package_versions.created_by IS 'Actor that registered the package version.';
COMMENT ON COLUMN plugin_package_versions.updated_by IS 'Actor that last changed the package lifecycle metadata.';

COMMENT ON COLUMN plugin_configuration_snapshots.configuration_version_id IS 'Immutable identifier for one plugin configuration snapshot.';
COMMENT ON COLUMN plugin_configuration_snapshots.plugin_version_id IS 'Plugin package version that this configuration snapshot applies to.';
COMMENT ON COLUMN plugin_configuration_snapshots.church_instance_id IS 'Church instance scope for the configuration snapshot.';
COMMENT ON COLUMN plugin_configuration_snapshots.environment IS 'Deployment environment scope for the configuration snapshot.';
COMMENT ON COLUMN plugin_configuration_snapshots.extension_point IS 'Extension point scope for the configuration snapshot.';
COMMENT ON COLUMN plugin_configuration_snapshots.configuration_values IS 'Non-secret JSON configuration values captured for audit and execution replay.';
COMMENT ON COLUMN plugin_configuration_snapshots.secret_refs IS 'Secret-manager references keyed by configuration property; raw secrets are not stored.';
COMMENT ON COLUMN plugin_configuration_snapshots.created_at IS 'Timestamp when this immutable configuration snapshot was created.';
COMMENT ON COLUMN plugin_configuration_snapshots.created_by IS 'Actor that created this configuration snapshot.';

COMMENT ON COLUMN plugin_instance_enablements.enablement_id IS 'Stable identifier for a scoped plugin enablement record.';
COMMENT ON COLUMN plugin_instance_enablements.plugin_version_id IS 'Plugin package version explicitly enabled for this scope.';
COMMENT ON COLUMN plugin_instance_enablements.configuration_version_id IS 'Configuration snapshot used when this plugin executes for the scope.';
COMMENT ON COLUMN plugin_instance_enablements.church_instance_id IS 'Church instance allowed to use the plugin for this scope.';
COMMENT ON COLUMN plugin_instance_enablements.environment IS 'Environment allowed to use the plugin for this scope.';
COMMENT ON COLUMN plugin_instance_enablements.extension_point IS 'Extension point allowed to invoke the plugin for this scope.';
COMMENT ON COLUMN plugin_instance_enablements.lifecycle_status IS 'Scoped enablement lifecycle state.';
COMMENT ON COLUMN plugin_instance_enablements.enabled_at IS 'Timestamp when this scoped enablement was activated.';
COMMENT ON COLUMN plugin_instance_enablements.disabled_at IS 'Timestamp when this scoped enablement was disabled, if applicable.';
COMMENT ON COLUMN plugin_instance_enablements.enabled_by IS 'Actor that enabled the plugin for this scope.';
COMMENT ON COLUMN plugin_instance_enablements.disabled_by IS 'Actor that disabled the plugin for this scope, if applicable.';
