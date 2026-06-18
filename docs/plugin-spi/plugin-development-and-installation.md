# Plugin Development and Installation Guide

This guide turns the ADR-030 plugin architecture into an operator and developer
workflow. It is intentionally conservative: plugins extend Cadentia through
versioned SPIs, but core Cadentia remains responsible for approval, provenance,
licensing, authorization, deterministic recommendation behavior, audit, and
revocation.

## Audience and prerequisites

| Audience | What this guide supports | Required background |
| --- | --- | --- |
| Plugin developers | Build and package a plugin that implements an allowed SPI v1 extension point. | ADR-030 extension boundaries, SPI v1 DTO contracts, safe error handling, and test fixtures. |
| Partner certification reviewers | Verify third-party packages before production enablement. | Certification checklist, dependency/SBOM review, security review, licensing review, and rollout gates. |
| Church-local administrators | Install, configure, test, disable, or remove an instance-local plugin. | Instance/environment scoping, secret-reference configuration, operational ownership, and safe-mode rollback. |
| Platform operators | Roll out, monitor, revoke, and investigate plugin versions. | Audit taxonomy, telemetry dashboards, alerts, circuit breakers, and affected-object analysis. |

Before developing or installing a plugin, read:

- [ADR-030: Plugin and Extension Architecture](../adr/ADR-030-plugin-and-extension-architecture.md)
- [ADR-030 SPI v1 Contracts and Compatibility Governance](ADR-030-spi-v1-contracts.md)
- [ADR-030 implementation plan](../implementation-plans/ADR-030-plugin-and-extension-architecture-plan.md)

## Development boundaries

Plugins must follow these boundaries regardless of trust tier:

- Implement only declared extension points. SPI v1 supports import connectors,
  metadata transforms, export renderers, outbound publish hooks, and static
  package customization manifests.
- Never select final worship setlists. Recommendation-path plugins may only
  provide bounded declarative contributions when that SPI is enabled and tested;
  the Recommendation Engine retains final authority.
- Never assume plugin execution success means output is approved. Core policy
  still evaluates approval, licensing, provenance, role authorization, instance
  isolation, schema validity, and deterministic replay.
- Never request direct database access, raw credentials, framework request
  objects, mutable ORM entities, unauthorized instance data, privileged review
  notes, or raw copyrighted content outside an explicitly permitted SPI payload.
- Return deterministic output for the same input envelope, plugin version,
  configuration version, policy snapshot, and catalog snapshot when the
  extension point is deterministic or recommendation-influencing.
- Use safe error codes and summaries. Do not include stack traces, raw lyrics,
  raw prompts, credentials, private personnel details, privileged review notes,
  unauthorized instance identifiers, SQL, internal hostnames, or full external
  payloads in errors, logs, metrics, traces, fixtures, or generated artifacts.

## Package layout

A plugin package should be reproducible and self-describing. The exact archive
format may vary by deployment, but the registry must be able to validate this
logical layout:

```text
plugin-package/
  plugin.yaml
  sbom.json
  checksums.txt
  LICENSES/
  docs/
    README.md
    OPERATIONS.md
    ROLLBACK.md
  fixtures/
    import-connector/
    metadata-transform/
    export-renderer/
    outbound-publish-hook/
  bin/ or image-reference.txt
```

Required files:

- `plugin.yaml`: package manifest, extension points, SPI versions, trust tier,
  provider identity, configuration schema, permissions, data classes,
  deterministic behavior declaration, observability declaration, and rollback
  target.
- `sbom.json`: software bill of materials for dependency and license review.
- `checksums.txt`: cryptographic hashes for package artifacts and fixtures.
- `LICENSES/`: third-party and plugin license notices.
- `docs/README.md`: developer and administrator summary.
- `docs/OPERATIONS.md`: owner, support path, alerts, dashboards, known failure
  modes, and escalation contacts.
- `docs/ROLLBACK.md`: disablement, rollback, revocation, credential cleanup, and
  data cleanup instructions.
- `fixtures/`: repeatable contract inputs and expected sanitized outputs for
  every declared extension point and supported SPI version.

## Manifest requirements

The manifest is the installation contract between the plugin and Cadentia.
Use expanded YAML style so registry diffs and reviews remain readable.

```yaml
pluginId: com.example.planning-center-export
packageName: planning-center-export
provider:
  name: Example Integrations LLC
  ownershipContact: plugins@example.invalid
  supportContact: support@example.invalid
version: 1.2.3
trustTier: partner_certified
supportedSpiVersions:
  - 1.0.0
extensionPoints:
  - OUTBOUND_PUBLISH_HOOK
  - EXPORT_RENDERER
execution:
  mode: out_of_process
  timeoutMs: 5000
  retryPolicy: bounded_exponential_backoff
  idempotencyRequired: true
dataSensitivity:
  receives:
    - service_plan_metadata
    - display_safe_song_metadata
  emits:
    - external_delivery_reference
  forbidden:
    - raw_lyrics
    - raw_prompts
    - credentials
    - privileged_review_notes
configurationSchemaVersion: 1.0.0
configurationSchemaRef: schemas/configuration.schema.json
permissions:
  secrets:
    - planning-center-api-token
  outboundHosts:
    - api.planningcenter.example
observability:
  metrics: true
  traces: true
  safeLogs: true
  dashboards:
    - plugin-fleet-overview
    - outbound-publish-health
certification:
  checklistVersion: adr-030-subtask-9-v1
  sbomRef: sbom.json
  licenseReviewRequired: true
  deterministicReplayRequired: false
rollback:
  previousCompatibleVersion: 1.2.2
  safeModeBehavior: skip_plugin
```

Registry validation must reject manifests that omit provider ownership,
supported SPI versions, extension points, execution mode, data-sensitivity
classification, configuration schema, observability declaration, or rollback
instructions.

## Development workflow

1. **Select the extension point**
   - Confirm the target extension point is allowed for the plugin trust tier and
     environment.
   - Confirm the plugin does not need generic data access or direct catalog
     mutation. If it does, the plugin is not eligible for SPI v1.
2. **Define configuration**
   - Model configuration with a versioned schema.
   - Store secrets as references only; never store raw credentials in plugin
     config, fixtures, logs, generated artifacts, or package documentation.
3. **Implement the SPI adapter**
   - Accept only the DTO envelope for the declared SPI version.
   - Validate required fields and reject unsupported schema versions.
   - Return bounded output DTOs and safe error summaries.
4. **Add contract fixtures**
   - Include success, degraded, failure, invalid input, invalid output,
     policy-denied, timeout, and retry/dead-letter examples where relevant.
   - Include deterministic replay fixtures for recommendation-path plugins or
     future recommendation-path prototypes.
5. **Add operational telemetry**
   - Emit metrics and traces through the runtime boundary, not by leaking
     payloads directly from plugin code.
   - Use the standard failure classes: runtime failure, policy denial, invalid
     output, compatibility error, timeout degradation, downstream integration
     failure, retry exhausted, and circuit-breaker action.
6. **Run local validation**
   - Validate package manifest and configuration schema.
   - Run SPI contract tests against all fixtures.
   - Run security and redaction checks against logs, errors, traces, artifacts,
     and fixture outputs.
7. **Prepare certification evidence**
   - Attach SBOM, dependency scan, license review, security review, data
     sensitivity map, fixture results, observability mapping, operational owner,
     and rollback plan.

## Installation workflow

Installation is scoped by package version, instance, environment, extension
point, and configuration version. Administrators should install plugins through
the governed admin API or administrative interface rather than by placing code on
a runtime host manually.

1. **Register package version**
   - Upload or reference the signed package.
   - Verify checksum/signature, manifest, SBOM, provider ownership, extension
     points, and supported SPI versions.
   - Registry state starts as `registered`, `uncertified`, or `quarantined`
     depending on validation outcome.
2. **Complete certification or local attestation**
   - Core-maintained and partner-certified plugins require the full evidence
     checklist.
   - Church-local plugins require local owner attestation, passing contract
     fixtures, safe-mode instructions, and acknowledgement of support limits.
3. **Create configuration draft**
   - Select instance, environment, extension point, and package version.
   - Provide non-secret values and secret references.
   - Validate configuration schema, data sensitivity, role scope, outbound host
     allowlist, and policy snapshot.
4. **Run configuration test**
   - Execute non-mutating dry-run fixtures or approved staging data.
   - Verify that plugin execution, policy validation, output validation,
     telemetry, audit records, and dashboards behave as expected.
5. **Enable in non-production**
   - Enable the plugin for a test or staging environment first.
   - Run seeded failure scenarios and rollback tests before production.
6. **Enable production canary**
   - Enable by environment, instance group, extension point, and plugin version.
   - Freeze rollout on policy-denial spikes, invalid-output spikes, timeout
     degradation, deterministic replay failures, revocation feed matches, or
     circuit-breaker threshold breaches.
7. **Monitor and promote**
   - Watch plugin fleet, instance health, extension point SLO, rollout, security,
     and determinism dashboards for the full observation window.
   - Promote only when audit, telemetry, support ownership, rollback, and
     production gate evidence remain valid.

## Upgrade workflow

- Register and certify the new package version before changing any production
  enablement.
- Verify the compatibility matrix for the current Cadentia release, SPI version,
  extension point, execution mode, trust tier, instance policy, and environment.
- Run configuration migration as a draft and preserve the prior active
  configuration version.
- Canary the upgrade, compare latency/failure/policy-denial/invalid-output
  rates against the previous version, and retain the rollback target until the
  observation window completes.
- Audit `plugin.version.upgraded` and any related `plugin.configuration.changed`
  events with previous version, new version, migration result, and rollback
  target.

## Disable, revoke, and remove

Use disablement for operational or instance-specific problems. Use revocation
for security, certification, policy, provenance, licensing, or platform-wide
safety problems.

| Action | When to use | Required behavior |
| --- | --- | --- |
| Disable | Instance-specific defect, failed canary, configuration error, optional plugin degradation, or planned maintenance. | Stop new executions in the scoped instance/environment/extension point, preserve history, record reason, and identify affected jobs/imports/exports/recommendations. |
| Roll back | New version or configuration causes regression but previous version remains valid. | Reactivate prior package/config version, keep audit lineage, and rerun affected dry-run checks. |
| Revoke | Package is unsafe, compromised, uncertified, expired, legally blocked, policy-blocked, or fails mandatory circuit-breaker disablement. | Force disable matching enablements, block credential access, drain workers, deny queued executions, alert operators, and produce affected-surface report. |
| Remove | Plugin is no longer needed and has no active enablements or retained artifacts requiring runtime support. | Keep audit/certification history, retain records per policy, remove deployable package from active runtime pools, and document cleanup. |

An unsafe plugin must not remain enabled when revocation policy or
circuit-breaker thresholds require disablement.

## Operational investigation

When investigating plugin behavior, administrators and support engineers should
use correlation IDs, execution IDs, audit event IDs, safe output digests, and
affected-object graphs instead of raw payloads.

Minimum investigation checklist:

- Confirm the plugin ID/version, trust tier, instance, environment, extension
  point, configuration version, input/output schema versions, policy snapshot,
  catalog snapshot where applicable, and execution mode.
- Distinguish runtime failure, policy denial, invalid output, compatibility
  error, timeout degradation, downstream integration failure, retry exhaustion,
  and circuit-breaker action.
- Check whether output was accepted, rejected, redacted, staged for review, or
  denied by approval/provenance/licensing/role policy.
- Identify affected imports, staged candidates, exports, outbound deliveries,
  jobs, recommendations, service plans, and generated artifacts.
- Disable or revoke before deeper analysis when safety policy requires it.
- Preserve safe audit summaries and incident notes without copying sensitive
  payloads into tickets or chat.

## Developer release checklist

A plugin package is not ready for registration until all items are complete:

- [ ] Manifest declares package identity, provider ownership, semantic version,
      supported SPI versions, extension points, trust tier, execution mode,
      configuration schema, permissions, data classes, observability, and
      rollback behavior.
- [ ] Package has SBOM, checksums/signature metadata, license notices, operations
      documentation, rollback documentation, and fixture coverage.
- [ ] SPI contract tests pass for every declared extension point and SPI version.
- [ ] Security tests cover authorization, instance isolation, secret-reference
      handling, payload size/type limits, outbound allowlists, idempotency, and
      policy denials.
- [ ] Redaction tests prove logs, metrics, traces, errors, fixtures, artifacts,
      and audit summaries do not contain forbidden sensitive payloads.
- [ ] Deterministic replay tests pass for any recommendation-path behavior.
- [ ] Observability mapping identifies metrics, dashboards, alerts, safe error
      codes, and escalation owners.
- [ ] Rollback and revocation procedures are tested and documented.
