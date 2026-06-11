# ADR-024 Rehearsal Workflow Operations

## Readiness state machine

Cadentia readiness is an explicit service-scoped workflow state. It is never inferred
from LLM summaries, note tone, recommendation scores, or the absence of comments.
The application service accepts only these normal transitions:

| From | Allowed next states | Operational meaning |
| --- | --- | --- |
| `draft` | `planned` | Rehearsal workflow exists but setup is not yet scheduled or confirmed. |
| `planned` | `draft`, `rehearsing` | Setup can be corrected before rehearsal starts, or moved into active rehearsal. |
| `rehearsing` | `planned`, `issues_open`, `ready` | Team is actively working the service; unresolved items move it to review. |
| `issues_open` | `rehearsing`, `ready` | Known issues are being reviewed; readiness requires blocker/action closure. |
| `ready` | `rehearsing`, `issues_open`, `completed` | Service is confirmed ready, but can be reopened if rehearsal reality changes. |
| `completed` | none through normal flow | Closure is terminal for the ordinary workflow. |

Emergency corrections from `completed` or other otherwise-invalid transitions require
the administrator-only emergency correction operation, a reason, and an audit record.

## Blocker semantics

An issue blocks readiness when it is still open (`open` or `in_progress`) and either:

- its severity is `blocking`; or
- its category is `blocker`.

Required rehearsal actions block readiness when their action status is `todo` or
`in_progress`. Marking a service `ready` or `completed` is rejected until all open
blocking issues and required rehearsal actions are explicitly resolved, deferred,
cancelled, done, or cancelled as appropriate. Service completion must not silently
resolve blockers.

## Manual overrides

Service-specific arrangement overrides are allowed only as service-scoped workflow
records. They do not mutate canonical arrangements and must be accompanied by an
audited lifecycle event. Normal override creation should include a reference such as
a rehearsal note, incident, or planning ticket.

## Emergency correction workflow

Use emergency correction only for operational data repair, for example an accidental
service closure or a readiness state recorded against the wrong rehearsal session.
The operator must:

1. Confirm the current explicit workflow state and the derived readiness status.
2. Verify that unresolved blockers and open required actions are not being hidden.
3. Execute the administrator-only emergency correction with a concise reason and
   external reference.
4. Review audit history for actor, action code, target type/id, service id,
   rehearsal session id where applicable, timestamp, reason/reference metadata, and
   before/after snapshots.

Do not place sensitive free-text rehearsal notes in telemetry labels, audit action
codes, or compact audit summaries. Store human-authored note bodies only in note
records with the correct visibility classification.

## Reporting and observability operations

### Safe telemetry contract

Rehearsal workflow telemetry uses bounded labels only:

- readiness states: `draft`, `planned`, `rehearsing`, `issues_open`, `ready`, `completed`, `unknown`;
- issue categories/severities/statuses from controlled vocabulary tables;
- action/status labels such as `created`, `updated`, `archived`, `success`, `failed`, `resolved`, `open`, `cancelled`, and `other`;
- count buckets (`0`, `1`, `2_5`, `gt_5`) rather than service-specific titles or names.

Structured logs include the bounded action/status, `correlation_id`, service/rehearsal UUIDs where needed, and controlled state/category values. They must not include service titles, song titles, person names, contact details, lyrics, rehearsal note bodies, pastoral note bodies, issue details, or override free-text notes.

Primary metrics:

| Metric | Interpretation |
| --- | --- |
| `cadentia_rehearsal_readiness_transitions_total` | Readiness state changes by bounded from/to/status/emergency labels. |
| `cadentia_rehearsal_readiness_state_duration` | Time spent in each readiness state. |
| `cadentia_rehearsal_issues_total` | Issue create/status-change volume by controlled category/status labels. |
| `cadentia_rehearsal_blocker_observations_total` | Bucketed observations of open blocker/action counts by readiness state. |
| `cadentia_rehearsal_overrides_total` | Service-arrangement override lifecycle and retention-archive events. |
| `cadentia_rehearsal_readiness_transition_failures_total` | Failed readiness-transition attempts by bounded reason (`invalid_state`, `readiness_gate`, `authorization`, `validation`, `not_found`, `other`). |

### Operational reports

Reporting users (`ADMIN`, `WORSHIP_LEADER`, `TEAM_SCHEDULER`, or `REPORTING_VIEWER`) may use the reporting service to retrieve:

1. services blocked from readiness;
2. open blockers by service;
3. unresolved transition issues;
4. difficult-song issues;
5. overdue owner actions;
6. services with active arrangement overrides;
7. completed-service rehearsal history.

Reports are instance-local and must be served through the policy-backed reporting service. Do not join or export data across church instances. Reports determine blocker status from structured category/severity/status/action fields, not by parsing free-form comments.

## Readiness-blocker incident triage

1. Start with the “services blocked from readiness” report and sort by `service_date_time`.
2. For the impacted service UUID, open “open blockers by service” and “overdue owner actions”. Use the issue/action identifiers and controlled category/severity/status fields to assign work; do not copy note or issue detail text into incident channels.
3. Check `cadentia_rehearsal_readiness_transition_failures_total{reason="readiness_gate"}` for attempted early readiness changes.
4. Correlate structured logs by `correlation_id` and service UUID, then verify the corresponding privileged audit events and readiness history rows.
5. Resolve or defer issues and complete/cancel required actions through normal workflow operations. Re-run the blocked-readiness report before marking the service `ready`.

## Accidental override rollback

1. Locate the service in the “services with active arrangement overrides” report.
2. Verify the override UUID, source arrangement UUID, audit reference, and service UUID. Do not rely on song title or note text.
3. Archive the accidental override through the workflow service (`REHEARSAL_OVERRIDE_ARCHIVED` audit action). Archival restores canonical arrangement rendering for that service block while preserving history.
4. Confirm `cadentia_rehearsal_overrides_total{action="archived",status="archived"}` increments and the active-overrides report no longer lists the service or lists a lower active count.
5. Review the effective rendering for the service to ensure no stale service-only notes or charts are being displayed.

## Audit history review and reconciliation

1. Query completed-service rehearsal history for the relevant completion window.
2. Compare the completed timestamp with `rehearsal_readiness_history` and privileged audit events whose metadata contains the service UUID.
3. Confirm every readiness change, issue resolution, action closure, override creation/update/archive, and emergency correction has a bounded audit action code and before/after snapshot reference.
4. If a readiness transition cannot be explained, treat it as an audit reconciliation incident: freeze further destructive retention/archive jobs for the service, export the audit identifiers, and escalate to an administrator.

## Retention and archive operations

Default retention for completed services:

| Data class | Default | Minimum | Archive behavior |
| --- | ---: | ---: | --- |
| Rehearsal sessions | 400 days | 90 days | Set `archived_at`, `archived_by`, and update metadata. |
| Rehearsal notes | 180 days | 30 days | Set `archived_at`; note bodies leave active reports. |
| Rehearsal issues/actions | 400 days | 180 days | Set issue `archived_at`; action rows remain for issue accountability. |
| Service arrangement overrides | 400 days | 180 days | Set `archived_at`; canonical arrangement rendering resumes. |
| Readiness/audit history | 2555 days | 2555 days | Preserve privileged audit rows and readiness history for accountability. |

Churches may configure longer values through `cadentia.rehearsal.retention.*` settings or environment variables. Values below minimums are rejected. Archive jobs must be run by an administrator, must be correlated with an audit/change ticket, and must never physically delete audit history needed to explain readiness changes or issue resolution.

## Dashboard and alert interpretation

- Rising `readiness_transition_failures_total{reason="readiness_gate"}` usually means teams are trying to mark services ready before blockers/actions are closed; open the blocked-readiness and overdue-action reports.
- Rising `issues_total{action="created"}` near service time suggests rehearsal instability; break down by controlled category rather than reading notes.
- Non-zero override counts after service completion may be expected during retention, but unexpected spikes in `overrides_total{action="created"}` should trigger override review.
- Missing audit events for completed-service history are a reconciliation incident, not a dashboard-only warning.
