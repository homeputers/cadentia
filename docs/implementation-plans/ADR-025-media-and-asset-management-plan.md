# ADR-025 Implementation Plan: Media and Asset Management

## Objective

Implement permission-aware media and asset management so Cadentia can attach,
version, process, authorize, and audit durable binary assets for songs,
arrangements, services, rehearsal sessions, and service-specific overrides
without weakening catalog approval or licensing guardrails.

## Source ADR

- [ADR-025: Media and Asset Management](../adr/ADR-025-media-and-asset-management.md)

## Status

Overall status: Planned.

- Subtask 1: Planned - asset domain schema, controlled vocabularies, and
  immutable version metadata.
- Subtask 2: Planned - object-storage abstraction, upload lifecycle, checksum
  verification, and cleanup policies.
- Subtask 3: Planned - asset attachment model for catalog, service, rehearsal,
  and override references.
- Subtask 4: Planned - permission, licensing, signed access, and audit controls.
- Subtask 5: Planned - metadata and access API contracts.
- Subtask 6: Planned - asynchronous processing pipeline for scanning,
  previews, waveforms, and transcoding.
- Subtask 7: Planned - recommendation, service-planning, and rehearsal
  integration safeguards.
- Subtask 8: Planned - operations runbook, observability, retention, and fixture
  coverage.

## Guiding Principles

- Binary payloads live in durable object/blob storage; Cadentia stores metadata,
  references, checksums, access policy, lifecycle state, and audit history.
- Asset references must be stable, version-aware, and immutable once used by a
  service, rehearsal, or historical recommendation explanation.
- Instance, role, and licensing checks are enforced before metadata disclosure,
  signed URL generation, download, streaming, or processing-result access.
- Asset presence is never a substitute for catalog approval, doctrinal review,
  licensing permission, or deterministic recommendation eligibility.
- Async processors may enrich assets with previews and analysis results, but
  failed or pending processing must not corrupt canonical metadata or expose
  unsafe files.

## Subtask 1: Design the asset domain schema and controlled vocabularies

### Context

ADR-025 requires Cadentia to store asset metadata with stable identifiers,
checksums, MIME type, size, storage key, ownership, version, licensing status,
and access policy while supporting PDFs, chord charts, stems, backing tracks,
click tracks, MIDI cues, rehearsal recordings, and future asset types. The model
must remain instance-scoped and preserve immutable historical references.

**Codebase anchors**

- Database migrations under `apps/api/src/main/resources/db/migration/`
- Domain and repository code under `apps/api/src/main/java/com/cadentia/`
- Existing catalog, arrangement, service-plan, rehearsal, and audit schemas from
  ADR-001, ADR-006, ADR-016, ADR-018, ADR-019, ADR-023, and ADR-024
- Integration fixtures under `apps/api/src/test/resources/db/fixtures/`

### Prompt

Create the relational metadata model and domain vocabulary for assets. Add
migrations, repository models, and seed data for asset records, asset versions,
asset type codes, processing status, storage location metadata, ownership,
checksums, MIME type, byte size, source/provenance, lifecycle state, licensing
status, usage restrictions, expiration dates, and access policy codes. Model
asset versions as immutable records that can be referenced independently from
the mutable logical asset container.

### Acceptance criteria

- Migrations create normalized tables for logical assets, immutable asset
  versions, asset type vocabulary, lifecycle status, processing status,
  licensing metadata, access policy, and audit timestamps.
- Asset type vocabulary includes at least PDF, chord chart, stem, backing track,
  click track, MIDI cue, rehearsal recording, preview, and future/local extension
  support with stable codes and active/inactive status.
- Asset version records include stable identifier, parent asset identifier,
  version number or revision code, storage key, checksum algorithm, checksum
  value, MIME type, byte size, source/provenance, created-by actor, created-at
  timestamp, and immutable lifecycle metadata.
- Database constraints prevent orphaned versions, duplicate active version
  numbers for the same asset, invalid byte sizes, missing checksum metadata, and
  invalid expiration ranges.
- Repository tests cover asset creation, version creation, controlled-vocabulary
  seeding, immutable version behavior, licensing field persistence, and invalid
  constraint failures.

### Restrictions

- Do not store binary payloads in relational database tables.
- Do not represent core asset type, lifecycle, processing, licensing, or access
  policy values as unvalidated arbitrary strings.
- Do not update historical asset version rows in place after they become
  referenceable; create replacement versions or audited lifecycle records.
- Do not introduce shared cross-church asset tables or tenant-row semantics as a
  substitute for isolated deployed instances.

## Subtask 2: Implement object storage abstraction and upload lifecycle

### Context

ADR-025 chooses object storage or equivalent durable blob storage for binary
payloads. Cadentia needs a provider-neutral storage boundary so local
development, self-hosted deployments, and cloud deployments can share the same
asset metadata workflow. Uploads must verify checksum, size, storage key, and
MIME type before an asset version becomes available.

**Codebase anchors**

- API runtime configuration under `apps/api`
- Deployment and package configuration from ADR-022
- External integration boundary guidance from ADR-020
- Existing async/eventing patterns from ADR-028 if already implemented

### Prompt

Build a storage adapter interface and concrete local-development implementation
for asset payloads. Add configuration for provider type, bucket/container,
namespace prefix, signed URL expiry limits, maximum object size, allowed MIME
types by asset type, and quarantine/processing prefixes. Implement the upload
lifecycle for creating pending uploads, issuing upload instructions, finalizing
uploads after server-side verification, rejecting mismatches, and scheduling
cleanup for abandoned or failed uploads.

### Acceptance criteria

- Storage adapter supports object existence checks, metadata reads, checksum or
  digest verification where available, signed upload/download URL generation,
  object copy/move, and delete/quarantine operations.
- Runtime configuration can select local filesystem or object-storage-backed
  providers without changing domain services or API controllers.
- Upload finalization verifies expected storage key, checksum, byte size, MIME
  type, asset type allowance, actor, instance scope, and upload expiration before
  creating an available asset version.
- Failed, abandoned, or rejected uploads remain non-downloadable and are eligible
  for lifecycle cleanup without deleting referenced historical versions.
- Tests cover successful upload finalization, checksum mismatch, MIME type
  rejection, expired pending upload, unauthorized finalization, storage-adapter
  failures, and cleanup candidate selection.
- Documentation explains the local-development storage mode and the production
  storage configuration placeholders.

### Restrictions

- Do not expose raw bucket/container names or storage credentials to normal API
  clients.
- Do not mark an asset version available before Cadentia verifies the uploaded
  object metadata.
- Do not hard-code a single cloud provider in domain services.
- Do not delete immutable historical objects through generic failed-upload
  cleanup jobs.

## Subtask 3: Add asset attachment model for catalog, service, rehearsal, and override references

### Context

ADR-025 requires assets to attach to songs, arrangements, services, rehearsal
sessions, and service-specific overrides. Services must be able to reference the
exact asset version used for rehearsal or performance, while catalog and
service-specific override data must remain separate.

**Codebase anchors**

- Song and catalog schema from ADR-001 through ADR-007
- Arrangement and transposition code under `apps/api/src/main/java/com/cadentia/catalog/`
- Service-plan and setlist versioning implementation from ADR-016 and ADR-018
- Rehearsal workflow implementation from ADR-024

### Prompt

Create a polymorphic or explicitly typed attachment model that links immutable
asset versions to supported Cadentia entities: songs, arrangements, services,
service items, rehearsal sessions, rehearsal issues/actions where appropriate,
and service-specific arrangement overrides. Include attachment type, display
label, sort order, purpose, required/optional flag, effective date range,
visibility hint, and audit metadata. Ensure service and rehearsal records can
pin exact asset versions independent of later catalog asset changes.

### Acceptance criteria

- Attachments can be created, listed, reordered, archived, and audited for songs,
  arrangements, services, service items, rehearsal sessions, and
  service-specific overrides.
- Attachment records point to immutable asset version identifiers, not only to
  mutable logical asset identifiers, when used in service/rehearsal history.
- Service-specific overrides can attach assets without mutating approved catalog
  song or arrangement assets.
- Constraints prevent attachments to unsupported entity types, missing target
  records, archived asset versions where active references are required, and
  duplicate active attachment positions for the same target/purpose.
- Tests cover catalog attachments, service-pinned versions, rehearsal-session
  attachments, override attachments, reorder/archive behavior, and invalid
  target rejection.
- Documentation describes how exact asset versions are selected for service and
  rehearsal history.

### Restrictions

- Do not store attachments as incidental free-form URLs in service notes,
  rehearsal notes, or arrangement comments.
- Do not allow a service-specific override attachment to update canonical
  catalog arrangements.
- Do not make historical service or rehearsal attachment references float to a
  newer asset version unless an explicit audited replacement is requested.
- Do not bypass catalog approval gates merely because an attached asset exists.

## Subtask 4: Enforce permission, licensing, signed access, and audit controls

### Context

ADR-025 requires instance, role, and licensing permissions before download or
streaming. Licensing metadata and usage restrictions must be visible to
authorized users, and access decisions must be auditable. ADR-019 security
policies should be reused so asset access is not implemented with scattered
controller-only checks.

**Codebase anchors**

- Security policy and controller guards under `apps/api/src/main/java/com/cadentia/api/security/`
- Privileged-action audit schema and runbook from ADR-019
- Service, rehearsal, team, and assignment roles from ADR-018, ADR-023, and
  ADR-024
- API controller tests under `apps/api/src/test/java/com/cadentia/api/controller/`

### Prompt

Implement centralized asset authorization policies for metadata reads, private
licensing field reads, upload/finalize operations, attachment management,
processing-result access, signed download URL generation, streaming access, and
archive/delete/quarantine actions. Combine instance isolation, actor role,
service/team assignment context, asset access policy, licensing status,
expiration, and usage restrictions into deterministic access decisions. Emit
audit records for privileged asset actions and denied sensitive access attempts
where required by security policy.

### Acceptance criteria

- Authorization distinguishes at least administrator, worship leader, catalog
  reviewer, team scheduler, assigned musician, rehearsal participant, read-only
  reporter, and unauthenticated/unknown actors.
- Signed download or streaming URLs are generated only after metadata, role,
  instance, lifecycle, and licensing checks pass.
- Licensing metadata includes source, license status, usage restrictions,
  expiration date, and visibility/redaction rules for unauthorized actors.
- Expired, restricted, quarantined, failed-scan, or archived asset versions are
  blocked from normal download/streaming paths with machine-readable denial
  reasons.
- Audit entries include actor, action code, asset id, asset version id,
  attachment target when applicable, service/rehearsal context when applicable,
  decision, timestamp, and reason/reference metadata.
- Security tests cover permitted and denied metadata reads, redacted licensing
  fields, signed URL generation, expired license blocking, service-assignment
  scoped access, cross-instance denial, and audit emission.

### Restrictions

- Do not rely on obscurity of storage keys or signed URL possession as the only
  authorization control.
- Do not log signed URLs, storage credentials, private licensing terms, or
  sensitive filenames in telemetry labels or compact audit summaries.
- Do not allow download/streaming of quarantined, failed-scan, expired-license,
  or archived assets through alternate metadata endpoints.
- Do not duplicate role logic in each controller when a centralized policy can
  be reused.

## Subtask 5: Define and implement media asset API contracts

### Context

Clients need stable APIs to upload assets, manage versions, attach assets to
entities, view licensing metadata, and request access URLs. The OpenAPI contract
is intentionally split across aggregate, paths, and components files, and API
responses must not expose internal storage implementation details.

**Codebase anchors**

- OpenAPI contract under `apps/api/src/main/openapi/`
- API controllers under `apps/api/src/main/java/com/cadentia/api/controller/`
- API DTOs generated from OpenAPI under `apps/api`
- Contract verification command: `mvn -pl apps/api -DskipTests generate-sources`

### Prompt

Add versioned OpenAPI contracts and controller implementations for asset
metadata, upload lifecycle, asset versions, attachment management, licensing
metadata, processing status, and signed access requests. Keep reusable schemas,
parameters, security schemes, and responses in the components file; keep path
operations in the paths file; and expose only DTOs with role-aware redaction and
machine-readable error/denial codes.

### Acceptance criteria

- OpenAPI schemas define asset, asset version, asset type, lifecycle status,
  processing status, licensing metadata, access policy, upload request,
  upload instructions, upload finalization request, attachment, signed access
  request, signed access response, and denial reason DTOs.
- Endpoints support creating pending uploads, finalizing uploads, listing asset
  metadata, listing versions, creating new versions, managing attachments,
  reading licensing metadata, archiving assets/versions/attachments, and
  requesting signed download/streaming access.
- API responses redact storage keys, provider-specific identifiers, private
  licensing terms, and sensitive processing details unless the actor is
  authorized.
- Payload validation rejects unsupported asset types, invalid MIME types,
  mismatched target entity types, invalid lifecycle transitions, oversized
  uploads, expired upload sessions, and arbitrary status strings.
- Controller and contract tests cover success paths, validation failures,
  authorization denials, redaction, optimistic concurrency/version conflicts,
  and machine-readable denial codes.
- OpenAPI generation succeeds after the split contract files are updated.

### Restrictions

- Do not collapse the split OpenAPI contract into a single large file.
- Do not expose internal database entities, storage keys, bucket/container names,
  provider credentials, or unredacted signed URLs in list responses.
- Do not let clients choose arbitrary lifecycle, processing, licensing, or
  access-policy strings that bypass server validation.
- Do not add an endpoint that downloads bytes through the application before the
  signed URL and authorization strategy is reviewed for the target deployment.

## Subtask 6: Build asynchronous asset processing pipeline

### Context

ADR-025 requires asynchronous processing for previews, waveform analysis, virus
scanning, and transcoding. Processing introduces failure modes and must not make
unsafe files available. Processing results should be tied to a specific asset
version and be replaceable or rerunnable without mutating historical metadata.

**Codebase anchors**

- Eventing and async processing architecture from ADR-028, if implemented
- Application services under `apps/api/src/main/java/com/cadentia/`
- Observability and telemetry strategy from ADR-029
- Processing status fields introduced by Subtask 1

### Prompt

Implement an asset processing orchestration layer that schedules jobs when an
asset version is finalized. Add job types for virus scan, preview generation,
waveform analysis, audio transcoding, and metadata extraction, with idempotent
handlers, retry/dead-letter behavior, processing-result persistence, and
state-transition rules. Ensure failed scan or unsafe content moves the version
to a blocked/quarantined state and prevents access until an authorized operator
resolves it.

### Acceptance criteria

- Finalized asset versions enqueue required processing jobs based on asset type,
  MIME type, size, and configured processing profile.
- Processing jobs are idempotent and record job id, asset version id, processor
  type/version, input checksum, status, attempts, timestamps, error code, and
  output asset/result references.
- Virus scanning or safety checks gate normal download/streaming availability
  according to configured policy.
- Preview, waveform, transcoding, and metadata extraction results are associated
  with the source asset version and do not overwrite source binary metadata.
- Retry and dead-letter handling preserve enough information for operators to
  diagnose failures without exposing sensitive payload content.
- Tests cover job scheduling, idempotent handler replays, successful processing,
  failed scan quarantine, retry exhaustion, result persistence, and access
  blocking while processing is pending or failed.

### Restrictions

- Do not run heavyweight media processing synchronously in request/response
  upload finalization paths.
- Do not overwrite original uploaded binaries with derived previews,
  transcodes, or waveform artifacts.
- Do not make unscanned or failed-scan assets downloadable through preview or
  streaming endpoints.
- Do not include raw lyrics, private notes, signed URLs, or object credentials in
  processing job payloads or logs.

## Subtask 7: Integrate assets with recommendation, service-planning, and rehearsal workflows safely

### Context

ADR-025 states that recommendations must never treat asset presence as approval
unless catalog approval gates also pass. At the same time, service plans and
rehearsals need exact asset versions for charts, tracks, cues, and recordings.
Existing deterministic recommendation and explainability logic must remain based
on approved catalog data and explicit constraints.

**Codebase anchors**

- Recommendation Engine code under `apps/api/src/main/java/com/cadentia/recommendation/`
- Recommendation read model and approval gates from ADR-002 and ADR-005
- Setlist persistence/versioning from ADR-016
- Service-plan integration from ADR-018
- Rehearsal workflow from ADR-024
- Recommendation explainability from ADR-013 and ADR-021

### Prompt

Wire asset metadata into service-planning and rehearsal views while keeping
recommendation eligibility independent from asset presence. Add deterministic
helpers for selecting preferred chart/track versions for a service context,
pinning selected versions into setlist/service history, exposing asset-related
warnings in planning/rehearsal diagnostics, and ensuring recommendation
explanations cite catalog approval and dataset references rather than asset
availability alone.

### Acceptance criteria

- Recommendation candidate eligibility continues to require approved catalog
  gates even when assets are present.
- Asset availability can appear only as an explicit deterministic scoring input
  or planning diagnostic when configured, never as a replacement for approval or
  doctrinal review.
- Service-plan and rehearsal views can resolve the exact accessible chart,
  backing track, click track, MIDI cue, stem, or recording version for the actor
  and context.
- Setlist/service history pins selected asset versions used for rehearsal or
  performance so later asset uploads do not rewrite historical context.
- Diagnostics identify missing, inaccessible, expired-license, pending-scan,
  or incompatible asset versions without leaking unauthorized licensing details.
- Tests cover recommendable song with assets, non-approved song with assets,
  approved song without assets, pinned service asset versions, role-specific
  rehearsal asset visibility, and explainability output that preserves approval
  references.

### Restrictions

- Do not allow LLM components to select songs, infer approval, or infer asset
  licensing from filenames, notes, or free-form prompts.
- Do not filter in non-approved catalog songs merely because they have charts,
  tracks, or recordings.
- Do not mutate historical setlists or service plans when a newer asset version
  becomes available.
- Do not expose private asset diagnostics to recommendation audiences that lack
  the corresponding asset permissions.

## Subtask 8: Publish operations runbook, observability, retention, and fixture coverage

### Context

Media workflows need operational guardrails for storage lifecycle policies,
cleanup jobs, scan failures, transcoding failures, signed URL troubleshooting,
licensing expiration, backup/restore, and instance deletion. ADR-025 leaves
provider baseline, retention after deletion/service completion, and mandatory
licensing fields as open questions that should be resolved or explicitly tracked
before production rollout.

**Codebase anchors**

- Operational documentation under `docs/` and `docs/runbooks/`
- Observability strategy from ADR-029
- Deployment/package configuration from ADR-022
- Asset tests and fixtures introduced by earlier subtasks

### Prompt

Create the asset-management operations documentation, fixture strategy, and
observability checks. Document storage provider configuration, local-development
storage, lifecycle cleanup, retention defaults, instance deletion handling,
backup/restore considerations, licensing expiration review, virus-scan failure
triage, processing retry/dead-letter triage, signed URL incident response, and
emergency access revocation. Add metrics, structured log/audit event names, and
fixture assets safe for tests.

### Acceptance criteria

- Runbook documents upload lifecycle, signed access troubleshooting, quarantine
  workflow, processing retry/dead-letter handling, lifecycle cleanup, retention,
  backup/restore, instance deletion, and licensing expiration review.
- Open ADR questions are either resolved in documented defaults or tracked as
  explicit configuration decisions required before production deployment.
- Observability emits metrics for upload attempts/failures, finalized asset
  versions, signed access decisions, denied access reasons, processing latency,
  processor failures, quarantine events, cleanup deletions, and license
  expiration warnings.
- Structured logs and audit events avoid sensitive payloads while preserving
  enough identifiers for incident response.
- Test fixtures include small copyright-safe placeholder PDFs/audio/MIDI-like
  files or generated binary fixtures with documented provenance and checksums.
- Regression checks cover retention cleanup selection, expired-license warnings,
  missing storage objects, backup/restore metadata consistency, and runbook
  command examples where applicable.

### Restrictions

- Do not commit copyrighted worship charts, commercial stems, backing tracks,
  rehearsal recordings, or real church media as fixtures.
- Do not document operational procedures that require plaintext storage
  credentials, signed URL disclosure, or direct database edits for normal
  recovery.
- Do not emit filenames, licensing terms, note content, or personal information
  as high-cardinality telemetry labels.
- Do not leave provider, retention, or mandatory licensing defaults implicit for
  production deployments.

## Subtask 3 attachment model

Cadentia stores media links in `asset_attachments`, a typed attachment table
rather than incidental service notes, rehearsal notes, or arrangement comments.
Each attachment identifies a supported target with `target_type_code` and
`target_id`; service-scoped targets also carry `service_plan_id` so trigger
validation can prove the target belongs to the expected service context.
Supported targets are catalog songs, catalog arrangements, services, service
items, rehearsal sessions, rehearsal issues, rehearsal issue actions, and
service-specific arrangement overrides.

Attachments always reference `asset_versions.id`, not only
`logical_assets.current_asset_version_id`. This means a service plan,
service item, rehearsal session, issue/action, or service arrangement override
pins the exact binary metadata version selected for rehearsal or performance.
If a catalog asset later receives a new current version, historical service and
rehearsal attachments continue to resolve to the original immutable version
until a user performs an explicit, audited replacement workflow. Catalog song
and arrangement attachments use the same version-pinned model for consistency,
while catalog approval gates remain the source of truth for recommendation
eligibility.

The attachment row captures display and planning metadata: attachment type,
display label, sort order, purpose, required/optional flag, effective date
range, visibility policy, archive metadata, and creator/updater audit metadata.
The schema prevents active duplicate positions for the same target and purpose,
rejects unsupported target types, rejects missing or mismatched service-context
target records, rejects active references to archived asset versions, and
verifies that the attachment type matches the referenced logical asset type.
`asset_attachment_audit_events` records create, reorder, archive, and future
explicit replacement events without mutating immutable asset-version rows.

Service-specific arrangement override attachments are attached to
`SERVICE_ARRANGEMENT_OVERRIDE` targets. They do not update canonical
`arrangements` rows or catalog song/arrangement attachments, preserving the
separation between approved catalog data and service-specific rehearsal or
performance decisions.
