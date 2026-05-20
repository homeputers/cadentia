# ADR-011 Implementation Plan: Admin Review and Catalog Governance UI

## Objective

Implement the admin workflows required to review imports, resolve duplicates,
approve catalog eligibility, audit changes, moderate catalog quality, and roll
back mistakes before Phase 2 imported content becomes recommendable.

## Subtask 1: Define admin API and permission boundaries

### Context

ADR-011 requires review, merge, approval, audit, moderation, and rollback actions
that must be restricted to authorized admins and reviewers.

### Prompt

Design admin API endpoints and authorization checks for import queues, candidate
detail, parser evidence, duplicate matches, merge decisions, approval actions,
audit history, moderation flags, and rollback previews.

### Acceptance criteria

- Admin endpoints are separate from public recommendation endpoints.
- Authorization distinguishes viewer, reviewer, approver, and rollback-capable
  roles where supported.
- API contracts expose stable IDs, status transitions, and audit references.
- Tests verify unauthorized users cannot perform review or approval actions.

### Restrictions

- Do not expose admin endpoints without authentication and authorization.
- Do not let UI-only checks replace backend authorization.
- Do not allow admin APIs to bypass provenance or approval constraints.

### Proposed API surface

- `GET /api/admin/import-candidates`: paginated queue endpoint for triage.
  Filters: `status`, `approvalType`, `connectorId`, `batchId`,
  `duplicateConfidenceMin`, `warningSeverity`, `assignee`, `updatedBefore`.
- `GET /api/admin/import-candidates/{candidateId}`: candidate detail including
  normalized metadata, parser evidence summary, duplicate candidates, and linked
  audit references.
- `GET /api/admin/import-candidates/{candidateId}/provenance`: immutable source
  references, connector payload fingerprints, and ingestion lineage.
- `GET /api/admin/import-candidates/{candidateId}/duplicates`: ranked duplicate
  matches and confidence features used for reviewer decision support.
- `POST /api/admin/import-candidates/{candidateId}/merge-decisions`: create a
  merge decision (`CREATE_NEW`, `MERGE_EXISTING`, `REJECT_DUPLICATE`,
  `REJECT_NOT_PERMITTED`, `DEFER`) with explicit reviewer reason.
- `POST /api/admin/import-candidates/{candidateId}/approvals/{approvalType}`:
  submit approval transition (`APPROVE`, `REJECT`, `NEEDS_CHANGES`, `REVOKE`).
- `GET /api/admin/audit-events`: query append-only audit history by entity,
  actor, time range, action type, and import batch.
- `POST /api/admin/moderation-flags`: create moderation flag with scope and
  eligibility impact policy.
- `POST /api/admin/rollback-previews`: dry-run preview returning impacted
  records, eligibility changes, and conflict blockers.
- `POST /api/admin/rollbacks`: execute approved rollback request.

### Permission boundary model

Define permission boundaries in backend policy checks, not in the UI:

- **Viewer** (`catalog.admin.read`)
  - Can read queues, candidate details, parser evidence, provenance, duplicates,
    and audit history.
  - Cannot approve, merge, moderate, or rollback.
- **Reviewer** (`catalog.admin.review`)
  - Inherits Viewer permissions.
  - Can add notes, assign candidates, and create merge decisions that remain
    pending approval where policy requires two-person review.
- **Approver** (`catalog.admin.approve`)
  - Inherits Reviewer permissions.
  - Can apply approval status transitions and finalize publication eligibility
    only when required approval types are satisfied.
- **Rollback Admin** (`catalog.admin.rollback`)
  - Inherits Approver permissions.
  - Can request rollback previews and execute rollback.

### Authorization and state-transition rules

- Enforce endpoint-level authorization in controller/service layers plus
  row-level checks for connector or team scope.
- Every mutating action must validate current state and allowed transition.
  Example: cannot `APPROVE` if parser-blocking severity exists; cannot rollback
  if dependent published versions are locked by newer irreversible changes.
- Merge and approval actions must fail closed when provenance is incomplete or
  required approvals are missing.
- Emit audit events for authorization-denied attempts (without leaking sensitive
  payload fields).
- Use stable IDs (`candidateId`, `songId`, `arrangementId`, `approvalId`,
  `auditEventId`, `rollbackRequestId`) across all responses.

### API contract notes

- Return machine-readable status codes (`PENDING_REVIEW`, `BLOCKED_PARSER`,
  `BLOCKED_PROVENANCE`, `READY_FOR_APPROVAL`, `APPROVED`, `REJECTED`,
  `ROLLED_BACK`) and explicit `allowedActions` for current actor.
- Include `auditRefs` arrays on mutable resource responses so UI can deep-link
  to the exact event trail.
- Use optimistic concurrency (`version` or `etag`) on merge, approval,
  moderation, and rollback execution endpoints.

### Test strategy for this subtask

- Controller authorization tests for each role against each endpoint.
- Service-level transition tests for valid and invalid state changes.
- Integration tests proving public recommendation APIs cannot access admin-only
  resources and admin APIs cannot bypass approval/provenance guards.
- Audit tests verifying successful and denied admin actions both emit traceable
  events.

## Subtask 2: Build import review queue views

### Context

Reviewers need queues for new, blocked, duplicate-suspected, parser-warning, and
ready-for-approval import candidates.

### Prompt

Implement queue queries and UI screens that list import candidates by status,
batch, connector, duplicate state, parser status, provenance status, and review
priority.

### Acceptance criteria

- Review queues support filtering and sorting by status, source, batch, date,
  duplicate confidence, and parser warning severity.
- Queue rows show enough provenance and parser summary to triage safely.
- Blocked candidates are visibly distinct from review-ready candidates.
- Tests cover queue query filtering and access control.

### Restrictions

- Do not show unapproved imported content in user-facing recommendation paths.
- Do not include full copyrighted lyrics in list views where summaries suffice.
- Do not allow bulk approval from queue rows without detailed review workflow.

## Subtask 3: Implement candidate detail and parser evidence review

### Context

Admins must inspect raw source references, normalized candidate data, provenance,
licensing, parser output, warnings, confidence, and duplicate signals.

### Prompt

Create candidate detail views and backing APIs that display source provenance,
normalized metadata, parser evidence, confidence, warnings, duplicate matches,
and audit history in a reviewer-friendly layout.

### Acceptance criteria

- Detail view shows raw source reference, not only normalized fields.
- Parser warnings and confidence are visible before approval.
- Reviewers can add structured notes without mutating parser evidence.
- Tests cover detail retrieval for ready, blocked, duplicate, and parser-warning
  candidates.

### Restrictions

- Do not allow reviewers to edit raw imported payloads in place.
- Do not convert reviewer notes into approved metadata automatically.
- Do not hide low-confidence parser results.

## Subtask 4: Implement merge and duplicate resolution workflow

### Context

ADR-011 requires merge UI for resolving duplicates and deciding whether a staged
candidate becomes a new song, merges into an existing song, or is rejected.

### Prompt

Implement side-by-side duplicate review, field-level merge selection, conflict
warnings, and final merge decision actions. Persist reviewer decisions and create
canonical updates only after successful validation.

### Acceptance criteria

- Reviewers can accept a duplicate match, reject matches, create a new canonical
  song, or merge selected fields into an existing song.
- Field-level provenance is retained for imported values used in canonical data.
- Merge decisions emit audit events and preserve rollback information.
- Tests cover new song, merge into existing, reject candidate, and conflict
  validation failures.

### Restrictions

- Do not auto-merge based on duplicate confidence alone.
- Do not overwrite approved canonical fields without explicit reviewer choice.
- Do not lose previous canonical values needed for rollback.

## Subtask 5: Implement approval and doctrinal review actions

### Context

Only approved catalog records should become recommendable. ADR-011 builds on the
approval workflow and must make required approvals explicit.

### Prompt

Add UI and API actions for doctrinal, licensing, metadata, and musical review
statuses as applicable. Surface missing approvals and enforce valid status
transitions.

### Acceptance criteria

- Approval requirements are visible before recommendability.
- Invalid approval transitions are rejected by backend validation.
- Approved state changes update the recommendation read-model eligibility path
  only through existing approval gates.
- Tests cover approve, reject, needs-changes, revoke, and invalid-transition
  flows.

### Restrictions

- Do not let a single UI checkbox bypass multiple required approval types.
- Do not let imported content become recommendable before all required gates pass.
- Do not allow the LLM to approve or summarize doctrinal correctness as fact.

## Subtask 6: Implement audit history and moderation tools

### Context

Governance requires transparent history, issue flags, ownership, and moderation
actions for catalog quality.

### Prompt

Create audit history displays and moderation tools for flags such as bad source,
licensing concern, incorrect lyrics, metadata conflict, doctrinal concern, and
parser issue. Include assignment and resolution tracking.

### Acceptance criteria

- Audit history shows actor, action, timestamp, before/after references, and
  reason where applicable.
- Moderation flags can be opened, assigned, resolved, or escalated.
- Flagged records can be excluded from recommendation eligibility when policy
  requires it.
- Tests cover flag lifecycle and audit display data.

### Restrictions

- Do not allow audit records to be edited silently.
- Do not delete moderation history when a flag is resolved.
- Do not expose admin moderation notes to normal users unless explicitly designed.

## Subtask 7: Implement rollback previews and rollback execution

### Context

ADR-011 requires rollback behavior for unsafe or mistaken catalog changes.
Rollback must be auditable and must not corrupt related records.

### Prompt

Implement rollback preview and execution for import promotions, merges, approval
changes, and moderation actions. Show impacted songs, arrangements, lyrics,
approvals, read-model eligibility, and audit records before execution.

### Acceptance criteria

- Rollback preview lists all affected records and eligibility impact.
- Rollback execution is transactional and emits a new audit event.
- Rollback cannot erase the fact that the original action occurred.
- Tests cover rollback success, rollback conflicts, and unauthorized rollback.

### Restrictions

- Do not implement destructive rollback that removes audit history.
- Do not rollback across unrelated batches without explicit admin selection.
- Do not ignore downstream read-model refresh requirements.
