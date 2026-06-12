INSERT INTO logical_assets (
    id, stable_identifier, asset_type_code, title, description, owner_actor, owning_ministry,
    default_access_policy_code, lifecycle_status_code, created_by
) VALUES (
    '10000000-0000-4000-8000-000000000001',
    '10000000-0000-4000-8000-000000000101',
    'CHORD_CHART',
    'Fixture Chart',
    'Fixture chord chart metadata only; payload is stored outside the relational database.',
    'fixtures@cadentia.test',
    'Worship Team',
    'WORSHIP_TEAM',
    'AVAILABLE',
    'fixtures@cadentia.test'
);

INSERT INTO asset_versions (
    id, stable_identifier, asset_id, version_number, revision_code, storage_provider_code,
    storage_region, storage_bucket_alias, storage_key, checksum_algorithm, checksum_value,
    mime_type, byte_size, source_uri, provenance_summary, created_by, lifecycle_status_code,
    processing_status_code, access_policy_code
) VALUES (
    '10000000-0000-4000-8000-000000000201',
    '10000000-0000-4000-8000-000000000301',
    '10000000-0000-4000-8000-000000000001',
    1,
    'rev-a',
    'LOCAL_DEV',
    'local',
    'cadentia-fixtures',
    'fixtures/assets/chord-chart-rev-a.pdf',
    'SHA-256',
    'd2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2',
    'application/pdf',
    4096,
    'fixture://asset-domain/chord-chart-rev-a',
    'Fixture provenance for ADR-025 asset domain tests.',
    'fixtures@cadentia.test',
    'AVAILABLE',
    'READY',
    'WORSHIP_TEAM'
);

INSERT INTO asset_version_licenses (
    asset_version_id, license_status_code, license_source, license_reference,
    usage_restrictions, license_holder, effective_at, expires_at, visibility_policy_code
) VALUES (
    '10000000-0000-4000-8000-000000000201',
    'CCLI_COVERED',
    'Fixture CCLI account',
    'CCLI-000000',
    'Use only within fixture worship-team context.',
    'Cadentia Fixtures',
    '2026-01-01T00:00:00Z',
    NULL,
    'WORSHIP_TEAM'
);

INSERT INTO asset_version_lifecycle_events (
    asset_version_id, from_lifecycle_status_code, to_lifecycle_status_code, reason_code, note, changed_by
) VALUES (
    '10000000-0000-4000-8000-000000000201',
    NULL,
    'AVAILABLE',
    'FIXTURE_SEED',
    'Initial fixture asset version.',
    'fixtures@cadentia.test'
);

UPDATE logical_assets
SET current_asset_version_id = '10000000-0000-4000-8000-000000000201'
WHERE id = '10000000-0000-4000-8000-000000000001';
