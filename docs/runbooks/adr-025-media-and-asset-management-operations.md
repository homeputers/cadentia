# ADR-025 Media and Asset Management Operations Runbook

## Production defaults and deployment decisions

ADR-025 production rollout uses these explicit defaults unless a deployment
package overrides them through reviewed configuration:

- **Production storage baseline:** S3-compatible object storage with private
  buckets/containers, server-side encryption, bucket versioning, object-lock or
  retention controls where available, and access only through Cadentia service
  credentials. Azure Blob or Google Cloud Storage may be used only after the
  adapter passes the same contract tests and the deployment decision is recorded.
- **Local development storage:** `LOCAL_FILESYSTEM` under a disposable
  `local-development` namespace. Local storage is never production durable
  storage and must not be shared between church instances.
- **Pending upload retention:** pending upload objects expire after 24 hours;
  rejected or failed pending uploads are selected by the cleanup job after 24
  hours unless already quarantined for investigation.
- **Service-completion retention:** assets pinned to service or rehearsal
  history are retained for 7 years after service completion unless the church's
  deployment package sets a stricter legal/licensing window.
- **Instance deletion retention:** instance deletion first revokes access and
  disables new signed URLs immediately, then keeps encrypted object data and
  metadata in a deletion-hold state for 30 days for restore, then purges payloads
  and metadata in the provider lifecycle purge job.
- **Mandatory licensing fields:** every downloadable or streamable asset version
  must store `license_status_code`, `license_source`, `license_reference` when
  the source issues one, `usage_restrictions`, `license_holder`, `effective_at`,
  optional `expires_at`, and `visibility_policy_code`. `NOT_REQUIRED` is allowed
  only for generated previews, local test fixtures, or assets explicitly marked
  non-licensed by an administrator.

## Upload lifecycle

1. Create a pending upload. Cadentia records expected asset type, byte size,
   MIME type, checksum, instance id, actor id, provenance, access policy, and
   licensing metadata.
2. Client uploads bytes to storage using Cadentia-issued instructions. Do not
   paste signed upload URLs into tickets, chat, telemetry, or incident notes.
3. Finalization verifies instance, actor, expiration, storage key, object
   existence, checksum, byte size, MIME type, configured object-size limit, and
   asset type allowance.
4. Finalized versions move from the pending prefix to the available namespace,
   become immutable metadata records, and enqueue virus scan and processing jobs.
5. Rejected, expired, or failed pending uploads remain non-downloadable and are
   eligible for cleanup without deleting historical versions.

## Signed access troubleshooting and incident response

Check these causes before escalating:

- Actor lacks role, assignment context, or instance scope.
- Asset version is not `AVAILABLE`, scan-ready, or license-valid.
- License is expired, revoked, restricted, or missing mandatory metadata.
- Storage object is missing or checksum metadata does not match the asset
  version record.
- Signed URL TTL exceeds package policy or provider clock skew is present.

Never disclose the signed URL itself. Capture only actor id, instance id, asset
id, asset version id, attachment target id, denial reason, request id, and the
provider object ETag/checksum if available.

## Quarantine workflow

- Virus scan failures and unsafe-content findings move the version to
  `QUARANTINED` and block normal download, streaming, previews, and derived
  processing results.
- Operators review scanner code, processor version, checksum, byte size, and
  upload actor without opening the payload unless an approved malware-analysis
  procedure is in place.
- If false-positive resolution is approved, create an audited replacement or
  lifecycle transition through the administrative workflow; do not update
  historical version rows directly.
- If confirmed unsafe, revoke access, retain quarantine evidence for the
  security retention window, notify affected administrators, and schedule purge
  according to incident policy.

## Processing retry and dead-letter triage

- Retry transient scanner, preview, waveform, metadata extraction, and
  transcoding failures with idempotent job ids tied to the asset version id and
  input checksum.
- Dead-letter records must include processor type/version, attempts, error code,
  asset version id, instance id, and request id. They must not include filenames,
  note content, raw lyrics, signed URLs, or storage credentials.
- After retry exhaustion, leave source metadata immutable, mark processing as
  `FAILED` or `REJECTED`, and keep access blocked when the failed job is a safety
  gate.

## Lifecycle cleanup and retention

Cleanup jobs may delete only unreferenced pending-prefix objects, rejected upload
objects outside quarantine, expired generated previews, and objects already past
instance-deletion purge. They must not delete immutable historical versions
pinned by service plans, rehearsal sessions, or audit evidence.

## Backup and restore

Backups must capture relational asset metadata, attachment references,
processing state, lifecycle/audit events, and object-storage manifests with
checksum, byte size, provider alias, and storage key. Restore validation must
compare metadata counts and checksums before re-enabling signed access.

Example restore validation commands:

```bash
mvn -pl apps/api test -Dtest=AssetOperationsRunbookTest
mvn -pl apps/api test -Dtest=AssetUploadServiceTest,AssetAccessServiceTest
```

## Instance deletion handling

1. Mark the instance deletion-hold state and disable new uploads/finalization.
2. Revoke signed access by rotating storage signing credentials or disabling the
   instance signing key alias.
3. Export final metadata/object manifest for the deletion audit package.
4. Keep encrypted metadata and objects for the 30-day deletion hold.
5. Purge object prefixes and metadata through audited deletion jobs after hold
   expiration.

## Licensing expiration review

Run the warning job daily. Warn at 60, 30, 14, and 7 days before `expires_at` and
block normal signed access after expiration unless a new version or updated
license record is approved. Warnings include asset id, version id, holder,
source code/category, expiration date, and owning ministry only; they must not
emit private licensing terms or filenames as telemetry labels.

## Emergency access revocation

- Disable the affected instance signing key alias or rotate provider credentials
  used by the storage adapter.
- Set impacted asset versions to `QUARANTINED`, `REVOKED`, or archived through
  the administrative workflow.
- Invalidate CDN/object-cache entries if configured.
- Confirm new signed access decisions deny requests with machine-readable denial
  reasons.

## Observability contract

Metrics:

- `cadentia_asset_upload_attempts_total{asset_type,instance_tier}`
- `cadentia_asset_upload_failures_total{reason,asset_type}`
- `cadentia_asset_versions_finalized_total{asset_type,processing_profile}`
- `cadentia_asset_signed_access_decisions_total{decision,action}`
- `cadentia_asset_signed_access_denied_total{reason,action}`
- `cadentia_asset_processing_latency_seconds{processor_type,status}`
- `cadentia_asset_processor_failures_total{processor_type,error_code}`
- `cadentia_asset_quarantine_events_total{reason,processor_type}`
- `cadentia_asset_cleanup_deletions_total{object_class,reason}`
- `cadentia_asset_license_expiration_warnings_total{window_days,asset_type}`

Structured log event names:

- `asset.upload.pending_created`
- `asset.upload.finalize_succeeded`
- `asset.upload.finalize_failed`
- `asset.access.signed_decision`
- `asset.processing.job_started`
- `asset.processing.job_failed`
- `asset.processing.dead_lettered`
- `asset.quarantine.entered`
- `asset.cleanup.deleted`
- `asset.license.expiration_warning`
- `asset.restore.manifest_verified`

Audit event names:

- `ASSET_VERSION_FINALIZED`
- `ASSET_SIGNED_ACCESS_GRANTED`
- `ASSET_SIGNED_ACCESS_DENIED`
- `ASSET_VERSION_QUARANTINED`
- `ASSET_PROCESSING_RETRIED`
- `ASSET_PROCESSING_DEAD_LETTERED`
- `ASSET_PENDING_UPLOAD_CLEANED`
- `ASSET_LICENSE_EXPIRATION_WARNED`
- `ASSET_INSTANCE_DELETION_HOLD_STARTED`
- `ASSET_EMERGENCY_ACCESS_REVOKED`

Metric labels, logs, and audit summaries must avoid filenames, licensing terms,
note content, personal information, signed URLs, storage credentials, or raw
payload snippets. Use stable identifiers and low-cardinality enums for incident
response.

## Test fixture strategy

Copyright-safe fixture provenance lives under
`apps/api/src/test/resources/assets/fixtures/` with a README manifest documenting
generation strategy and SHA-256 checksums. Binary fixture payloads are not
committed; tests generate small PDF-like, WAV, and MIDI-like payloads in memory
from deterministic source code. Real worship charts, commercial stems, backing
tracks, rehearsal recordings, or church media are forbidden.
