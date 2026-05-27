# ADR-016 Setlist Versioning Operations Runbook

## Purpose

Define operational telemetry, retention controls, and incident procedures for
immutable setlist version lineage introduced by ADR-016.

## Scope

Applies to:

- setlist baseline creation from deterministic recommendation output
- setlist edit commits that create immutable child versions
- setlist version retrieval and diff operations
- retention/archival workflows for abandoned draft lineages

## Telemetry Requirements

### Metrics

- `cadentia_setlist_version_created_total`
  - Counter incremented for every persisted version.
  - Labels: `source` (`generated` or `manual_edit`), `result` (`success` or
    `failure`), `lineage_policy` (`linear` or `branched`).
- `cadentia_setlist_version_diff_requests_total`
  - Counter incremented for every diff request.
  - Labels: `result` (`success`, `not_found`, `conflict`, `error`).
- `cadentia_setlist_version_retrieval_latency_seconds`
  - Histogram for end-to-end latency of version load APIs.
  - Labels: `endpoint` (`get_version`, `list_versions`, `get_diff`), `result`.
- `cadentia_setlist_edit_commit_conflict_total`
  - Counter for optimistic concurrency or parent-version mismatch conflicts.
  - Labels: `operation` (`reorder`, `replace`, `remove`, `transpose`).
- `cadentia_setlist_draft_retention_actions_total`
  - Counter for retention lifecycle operations.
  - Labels: `action` (`archive`, `delete`, `restore`), `result`.

### Logging and Audit Events

Emit structured logs/events for each create/edit/retrieve operation:

- required fields: `setlist_id`, `version_id`, `parent_version_id`, `actor_type`,
  `actor_id_hash`, `action`, `result`, `request_id`, `trace_id`, `timestamp`
- edit operations must include `before_version_id` and `after_version_id`
- diff operations must include `from_version_id` and `to_version_id`
- never log free-text notes, lyrics snippets, or personally sensitive payloads

Use hashed/pseudonymous actor identifiers to avoid high-cardinality PII leakage.

## Retention and Archival Policy

### Policy Defaults

- Published versions are immutable and never deleted by background retention.
- Draft lineages can be archived after `90` days of inactivity.
- Archived draft lineages can be hard-deleted after `365` days if no legal or
  audit hold is present.
- Retention jobs must be idempotent and resumable.

### Configuration Contract

Expose retention settings via infrastructure configuration:

- `SETLIST_RETENTION_DRAFT_ARCHIVE_DAYS` (default `90`)
- `SETLIST_RETENTION_DRAFT_DELETE_DAYS` (default `365`)
- `SETLIST_RETENTION_JOB_BATCH_SIZE` (default `500`)
- `SETLIST_RETENTION_ENABLED` (default `false` until operations sign-off)

### Recovery Requirements

Before hard delete:

1. export candidate lineage metadata to durable backup storage
2. write retention action audit records with job execution ID
3. verify restore checkpoint completeness

Restore procedure:

1. locate exported lineage by `setlist_id`
2. rehydrate `setlist`, `version`, and `item` rows in one transaction
3. run lineage integrity check (`parent_version_id` chain continuity)
4. emit `restore` audit event and reconciliation report

## Failure Handling

### Partial Edit Commit Failure

Symptoms:

- version row persisted without matching item rows
- edit event written but immutable snapshot missing

Operator actions:

1. mark affected `setlist_id` as write-locked for remediation
2. replay last valid parent version with original edit request from audit log
3. validate item count/hash against expected post-edit state
4. unlock lineage and annotate incident ticket with replay evidence

### Conflict Retry Guidance

If commit fails due to stale `parent_version_id`:

1. fetch latest head version for the lineage
2. recompute requested change against latest head
3. retry commit with bounded retries (max `3`) and jittered backoff
4. if retries exhausted, return conflict response and require client refresh

### Storage Growth and Capacity Alerts

Trigger alerts when:

- daily version creation exceeds 2x trailing 14-day baseline
- draft backlog older than archive threshold exceeds SLO target
- p95 retrieval latency breaches operational SLO

Response:

1. confirm whether spike aligns with known launch/import events
2. inspect top lineages by version count and diff volume
3. execute archival job dry run and estimate reclaimable storage
4. escalate to platform owner if sustained growth threatens DB headroom

## Operational Checks

- Daily: verify retention job success/failure counts and error budget impact.
- Weekly: review top conflict sources and tune client retry/backoff behavior.
- Monthly: validate restoration drill from archived lineage backup.

## References

- `docs/adr/ADR-016-setlist-persistence-and-versioning.md`
- `docs/implementation-plans/ADR-016-setlist-persistence-and-versioning-plan.md`
- `docs/ARCHITECTURE.md`
