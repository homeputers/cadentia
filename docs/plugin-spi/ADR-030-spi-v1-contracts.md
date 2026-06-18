# ADR-030 SPI v1 Contracts and Compatibility Governance

SPI v1 uses semantic version `1.0.0` and immutable JSON DTO envelopes. Core adapters must validate every input before plugin invocation and every output before persisting, displaying, scoring, exporting, or emitting plugin-derived data.

For developer packaging, installation, upgrade, certification, and operational workflows, see [Plugin Development and Installation Guide](plugin-development-and-installation.md).

## Common envelope

Required fields: `spiVersion`, `extensionPoint`, `executionId`, `correlationId`, `churchInstanceId`, `environment`, `pluginVersionId`, `configurationVersionId`, `registrySnapshotId`, `policySnapshotId`, and `locale`. Recommendation-path contracts also require `catalogSnapshotId`, `deterministicSeed`, and `runId` for replay. Actor context is allowed only when the workflow has already authorized the actor; system context is allowed for batch or event-driven runs.

No ORM entities, database handles, mutable internal collections, framework request objects, secrets, or unredacted privileged records are valid SPI payload fields.

## Extension point DTOs

| Extension point | Input DTO | Output DTO | Validation rules |
| --- | --- | --- | --- |
| `IMPORT_CONNECTOR` | Source pointer, legal mode, scoped secret references, locale, instance/environment, registry/config/policy snapshots. | `SUCCESS`, `DEGRADED`, or `FAILURE`; staged source documents with source ID, content hash, provenance reference, license claim, warnings, and safe errors. | No approved catalog mutation; provenance and license claims required; raw secrets forbidden; output remains staged. |
| `METADATA_TRANSFORM` | Staged candidate snapshot ID, controlled vocabulary version, locale, policy snapshot, source provenance summary. | Proposed metadata map, confidence, review notes, warnings/errors. | Suggestion-only; cannot approve songs or overwrite canonical facts; confidence must be reviewable. |
| `RECOMMENDATION_CONSTRAINT` | Validated request snapshot, allowed constraint vocabulary, approved candidate IDs, catalog and policy snapshots, deterministic seed/run ID. | Declarative constraints with stable code, type, bounded weight, explanation label, and safe errors. | No free-form eligibility decisions, direct final ordering, unapproved song IDs, network/time/random dependencies, or weights outside `[-1.0, 1.0]`. |
| `SCORING_CONTRIBUTION` | Scoring profile version, allowed component codes, approved candidate IDs, catalog/policy snapshots, deterministic seed/run ID. | Bounded score component adjustments with candidate ID, component code, delta, and reason code. | Deltas must be within `[-0.2, 0.2]`; plugin cannot bypass hard filters or deterministic tie-breaking. |
| `EXPORT_RENDERER` | Immutable service-plan/setlist snapshot, requested format, display-safe metadata, locale, and export options. | Artifact reference or structured export document metadata, MIME type, filename, checksum, warnings, and safe errors. | Role/license redaction must happen before invocation; content type, checksum, and size policies are enforced before delivery. |
| `OUTBOUND_PUBLISH_HOOK` | Redacted event schema ID, correlation ID, idempotency key, allowed field set, endpoint configuration by secret reference. | Delivery result, external reference, reconciliation status, retry-safe errors. | Event schema and field allowlists enforced; idempotency key required; no core mutation from hook output. |

## Compatibility rules

- Patch and minor releases may add optional fields only when validators tolerate absence and core adapters ignore unknown future fields by explicit version-gated migration. SPI v1 validators reject unexpected fields for `1.0.0` fixtures so plugin implementations cannot depend on accidental payload shape.
- Removing fields, changing types, changing enum meanings, widening authority, or changing deterministic replay requirements requires a new major SPI version and migration plan.
- Deprecated SPI versions receive at least one minor-release deprecation window and continue to run only while registry policy marks them `DEPRECATED`; unsupported versions fail closed before invocation.
- Safe errors must use non-sensitive code/message pairs and retry metadata. They must never contain credentials, raw lyrics beyond the licensed payload already authorized for the workflow, stack traces, SQL, or internal hostnames.

## Fixtures and contract tests

Representative fixtures live under `apps/api/src/test/resources/plugin-spi/v1/`. They cover import, metadata transform, recommendation constraint, scoring contribution, export, outbound success/degraded/failure outputs, plus missing-field, extra-field, invalid-enum, unsupported-version, policy-unsafe, and out-of-range responses. `SpiContractValidatorTest` is the executable compatibility suite that plugin implementations and core adapters must pass before enablement.
