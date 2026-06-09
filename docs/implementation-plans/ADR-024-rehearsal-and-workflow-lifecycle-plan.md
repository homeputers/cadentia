# ADR-024 Implementation Plan: Rehearsal and Workflow Lifecycle

## Objective

Implement service-scoped rehearsal workflow capabilities so Cadentia can track
rehearsal sessions, song and transition issues, owner/action status, readiness
state, and service-specific arrangement overrides without mutating canonical
catalog arrangements or allowing operational notes to affect recommendation
eligibility outside deterministic, approved boundaries.

## Source ADR

- [ADR-024: Rehearsal and Workflow Lifecycle](../adr/ADR-024-rehearsal-and-workflow-lifecycle.md)

## Guiding Principles

- Rehearsal state belongs to a service plan and must not become catalog truth
  unless a separate catalog governance workflow approves a reusable change.
- Readiness is an explicit operational workflow, not a recommendation score or
  free-form note summary.
- Song-specific, transition-specific, arrangement-specific, and role-specific
  rehearsal concerns must be queryable through structured issue state.
- Service-specific arrangement overrides must preserve provenance back to the
  original approved arrangement and be safe to render for charts and planning
  views.
- LLM components must not infer readiness, resolve blockers, select songs, or
  convert rehearsal notes into catalog updates.
- Recommendation diagnostics may display relevant workflow status, but they must
  not treat rehearsal overrides or notes as approved catalog metadata.

## Subtask 1: Define rehearsal workflow domain schema and controlled vocabulary

### Context

ADR-024 requires rehearsal sessions attached to services, explicit readiness
states, note and issue targets, owner/action status, and service-scoped
arrangement overrides. ADR-018 provides service planning context, ADR-016
provides setlist/version lineage, ADR-019 provides audit expectations, and
ADR-023 may provide team role and assignment anchors. The implementation needs
stable identifiers and controlled vocabularies before API or UI workflows can be
built.

**Codebase anchors**

- Service-plan implementation under `apps/api/src/main/java/com/cadentia/serviceplan/`
- Database migrations under `apps/api/src/main/resources/db/migration/`
- Setlist persistence and versioning implementation under
  `apps/api/src/main/java/com/cadentia/reng/setlist/`
- Security and privileged-action audit schema introduced by ADR-019
- Team and musician assignment implementation plan in
  `docs/implementation-plans/ADR-023-team-and-musician-assignment-model-plan.md`

### Prompt

Design and implement the persistence schema, domain types, repositories, and
controlled-vocabulary seeds for service-scoped rehearsal workflows. Model
rehearsal sessions, readiness states, rehearsal notes, issue categories, issue
severity, issue status, owner/action metadata, issue targets, and
service-specific arrangement overrides. Ensure every record is scoped to a
service plan or rehearsal session and can reference the relevant setlist item,
transition, arrangement, team role, or assignment when available.

### Acceptance criteria

- Migrations create normalized tables for rehearsal sessions, workflow state,
  notes, structured issues, issue actions/owners, readiness history, and
  service-scoped arrangement overrides.
- Readiness states include at least `draft`, `planned`, `rehearsing`,
  `issues_open`, `ready`, and `completed`, with stable codes suitable for API
  responses and reports.
- Issue categories can represent unresolved transitions, difficult songs,
  blockers, arrangement concerns, team-role concerns, and general rehearsal
  follow-ups without relying on unstructured comments.
- Notes and issues can target service-level context, a rehearsal session, a
  specific setlist item, an adjacent transition, an arrangement, a team role, or
  a musician assignment when the related ADR-023 data exists.
- Arrangement overrides store service-scoped fields, effective values,
  rationale, provenance to the source arrangement/version, and audit metadata
  without writing to canonical arrangement tables.
- Repository and migration tests verify referential integrity, service scoping,
  cascade/archive behavior, and controlled-vocabulary seed stability.

### Restrictions

- Do not store rehearsal issues as only free-form comments when structured state
  is required for readiness reporting.
- Do not mutate canonical song, arrangement, approval, lyrics, or recommendation
  read-model records from rehearsal workflow migrations or repositories.
- Do not create free-form readiness or issue-status values that deterministic
  services must parse.
- Do not make ADR-023 team assignment tables mandatory if that ADR has not been
  implemented; use nullable references or integration seams where needed.

## Subtask 2: Implement readiness state machine, issue lifecycle, and audit trail

### Context

ADR-024 states that readiness must be visible and derived from explicit workflow
state, and that readiness changes and issue resolution must be audited. Teams
need clear transition rules so a service cannot be marked ready while blocking
issues remain unresolved, and operators need reliable history for rehearsal
progress, owner changes, and action completion.

**Codebase anchors**

- Application services under `apps/api/src/main/java/com/cadentia/`
- Security and permission code introduced by ADR-019
- Audit logging/runbook expectations in
  `docs/runbooks/adr-019-security-observability-and-response.md`
- Service-plan lifecycle implementation from ADR-018

### Prompt

Build the rehearsal workflow application service that governs readiness
transitions, issue creation, issue assignment, issue status changes, blocker
handling, and resolution audit. Encode deterministic state-machine rules for
allowed readiness changes and derive service readiness from explicit state plus
open blocking issues. Persist immutable history entries for readiness changes,
issue ownership changes, issue status changes, and override lifecycle events.

### Acceptance criteria

- Application services expose operations to create/update rehearsal sessions,
  add notes, open issues, assign owners/actions, change issue status, mark
  blockers, resolve/reopen issues, and request readiness transitions.
- Readiness transition validation prevents `ready` when unresolved blocking
  issues or required rehearsal actions remain open.
- Services can move through documented states, including draft/planned setup,
  active rehearsing, issues-open review, ready confirmation, and completed
  closure.
- Audit records include actor, action code, target type/id, service id,
  rehearsal session id when applicable, timestamp, reason/reference metadata,
  and before/after state snapshots or snapshot references.
- Issue lifecycle tests cover creation, ownership assignment, blocked ready
  transition, resolution, reopening, completion, unauthorized changes, and
  deterministic readiness derivation.
- Documentation describes the readiness state machine, blocker semantics,
  permitted manual overrides, and emergency correction workflow.

### Restrictions

- Do not infer readiness from LLM summaries, note sentiment, recommendation
  scores, or absence of comments.
- Do not allow issue deletion to erase audit history; use archival/cancelled
  states where removal from active views is needed.
- Do not let service completion silently resolve open blockers without an
  explicit audited action.
- Do not emit sensitive free-text note content into telemetry labels or compact
  audit summaries.

## Subtask 3: Add rehearsal workflow API contracts and authorization policies

### Context

Planning views, rehearsal tools, and future integrations need stable API
contracts for rehearsal sessions, workflow status, notes, issues, and
service-scoped overrides. ADR-019 requires role-aware access, while ADR-024
requires visibility into progress without exposing private notes or permitting
unapproved catalog mutations.

**Codebase anchors**

- API controllers under `apps/api/src/main/java/com/cadentia/api/controller/`
- API contract/OpenAPI resources, if present, under `packages/intent-contracts/`
  or project OpenAPI locations
- Security role model from ADR-019
- Service-plan endpoints introduced by ADR-018

### Prompt

Define and implement versioned API contracts for rehearsal workflow resources.
Add endpoints to list and manage rehearsal sessions, retrieve service workflow
status, manage notes and issues, transition readiness state, and create/update
service-scoped arrangement overrides. Apply centralized authorization policies
for worship leaders, schedulers, assigned musicians, reviewers, administrators,
and read-only/reporting users. Ensure response payloads redact fields according
to role and expose machine-readable status codes for clients.

### Acceptance criteria

- API schemas define rehearsal session, workflow status, readiness state,
  rehearsal note, issue, issue action/owner, issue history, readiness history,
  and arrangement override DTOs with explicit stable status fields.
- Endpoints support service-scoped create/read/update/archive operations for
  rehearsal sessions, notes, issues, issue ownership, issue resolution, readiness
  transitions, and arrangement overrides.
- Authorization distinguishes at least administrator, worship leader, team
  scheduler, assigned musician, reviewer, and read-only/reporting access.
- Assigned musicians can view rehearsal data relevant to their assignment and
  update allowed action/response fields without receiving broad workflow or
  catalog-governance permissions.
- API tests cover successful workflow operations, denied operations, redaction of
  private notes, blocked readiness transitions, optimistic concurrency/version
  conflicts, and payload validation for invalid status codes.
- API documentation states that rehearsal overrides are service-scoped and do
  not update approved catalog arrangements.

### Restrictions

- Do not expose internal database entities directly as public API responses.
- Do not leak private pastoral notes, sensitive team details, or unauthorized
  musician information through workflow endpoints or error messages.
- Do not allow API clients to submit arbitrary status strings that bypass the
  workflow state machine.
- Do not add endpoints that mutate approved catalog arrangements through the
  rehearsal workflow path.

## Subtask 4: Implement service-specific arrangement override rendering and isolation

### Context

ADR-024 allows rehearsal-specific arrangement modifications but requires
canonical arrangement data and approval gates to remain unchanged. These
overrides must be usable by service planning views, chart rendering, and asset
selection while preserving deterministic source references and making it clear
which values are service-only.

**Codebase anchors**

- Arrangement and transposition code under `apps/api/src/main/java/com/cadentia/catalog/`
- Arrangement transposition plan in
  `docs/implementation-plans/ADR-006-arrangement-transposition-plan.md`
- Service-plan composition and service-specific note/transposition behavior from
  ADR-018
- Media and asset management ADR at `docs/adr/ADR-025-media-and-asset-management.md`

### Prompt

Build the override resolution layer that combines an approved canonical
arrangement with service-scoped rehearsal overrides for service planning and
rehearsal rendering only. Support effective-key, capo/transposition, chart
annotations, section-order notes, transition cues, instrumentation notes, and
other approved service-only override fields. Return both source and effective
values so users and diagnostics can distinguish catalog truth from rehearsal
adaptation.

### Acceptance criteria

- Service/rehearsal views can request an effective arrangement representation
  that includes canonical source values, service override values, override
  provenance, and audit references.
- Override resolution is deterministic and scoped by service id, setlist item id,
  and arrangement id where applicable.
- Chart/rendering APIs can display service-specific key, capo/transposition,
  transition cues, rehearsal annotations, and section-order notes without
  updating canonical arrangement metadata.
- Recommendation read models and approved catalog views remain unchanged when an
  override is created, updated, archived, or rendered.
- Tests cover creating overrides, rendering effective arrangements, archiving
  overrides, preserving source arrangement values, concurrent updates, and
  preventing override leakage into unrelated services.
- Documentation explains how a service-specific override can be promoted only
  through a separate catalog governance workflow.

### Restrictions

- Do not use rehearsal overrides as approved arrangement metadata for candidate
  eligibility, doctrinal approval, or recommendation scoring.
- Do not overwrite canonical key, tempo, meter, lyrics, arrangement sections, or
  approval status when rendering effective service arrangements.
- Do not make rendering depend on free-form note parsing.
- Do not duplicate copyrighted lyrics or chart content unnecessarily when an
  override can reference canonical assets plus service-scoped metadata.

## Subtask 5: Surface rehearsal status in planning views and recommendation diagnostics

### Context

ADR-024 requires workflow status to appear in service planning views and in
recommendation diagnostics where relevant. Users need actionable summaries of
open blockers, difficult songs, transition issues, upcoming rehearsal sessions,
and readiness state. Recommendation diagnostics may reference workflow status as
operational context, but song selection must remain deterministic and catalog
approval-gated.

**Codebase anchors**

- Service-plan API/UI surfaces introduced by ADR-018
- Recommendation explainability API plan at
  `docs/implementation-plans/ADR-021-recommendation-engine-explainability-api-plan.md`
- Recommendation engine code under `apps/api/src/main/java/com/cadentia/reng/`
- Administrative web interface ADR at `docs/adr/ADR-036-administrative-web-interface.md`

### Prompt

Add query services, API responses, and UI-ready DTOs that summarize rehearsal
workflow status for service planning and recommendation diagnostics. Include
readiness state, current rehearsal phase, upcoming/past rehearsal sessions,
blocker counts, unresolved transition issues, difficult-song indicators,
assigned owners/actions, and service-specific override indicators. Wire the
summary into planning views and diagnostic payloads without changing candidate
selection or ordering.

### Acceptance criteria

- Service planning responses include a workflow summary with readiness state,
  blocker counts, open issue counts by category/severity, next rehearsal
  session, overdue actions, and service-specific override indicators.
- Song-level and transition-level planning views can display open rehearsal
  issues and owner/action status for the selected service context.
- Recommendation diagnostics can optionally include safe operational workflow
  status for an existing service plan, clearly separated from deterministic
  scoring and catalog eligibility facts.
- Public or unauthorized diagnostic modes redact private note content and
  restricted personnel details while preserving aggregate status indicators when
  policy allows.
- Tests verify summary aggregation, filtering by service context, diagnostic
  partitioning, redaction, and that enabling diagnostics does not change
  selected songs or order.
- Documentation explains which rehearsal workflow fields are operational context
  and which recommendation fields remain deterministic scoring evidence.

### Restrictions

- Do not let open issues, notes, or readiness state automatically approve,
  disqualify, reorder, or otherwise mutate recommendation results unless a
  future ADR explicitly defines that deterministic policy.
- Do not expose admin-only diagnostics, private review notes, or sensitive team
  data in public planning or diagnostic responses.
- Do not mix service-scoped override facts with approved catalog facts in a way
  that clients cannot distinguish.
- Do not present inferred or LLM-generated pastoral rationale as rehearsal
  status.

## Subtask 6: Add reporting, observability, retention, and operational runbook

### Context

ADR-024 calls for unresolved blockers to be reportable before marking a service
ready and raises an open question about retention for completed rehearsal data.
Operators also need observability for readiness bottlenecks, issue resolution,
and workflow misuse. Reporting and retention must avoid sensitive data leakage
while preserving enough audit history for accountability.

**Codebase anchors**

- Reporting/query code under `apps/api/src/main/java/com/cadentia/`
- Structured logging and metrics conventions from ADR-019 and ADR-029
- Runbooks under `docs/runbooks/`
- Implementation plan index at `docs/implementation-plans/README.md`

### Prompt

Implement rehearsal workflow reports, metrics, structured logs, retention
configuration, and operator documentation. Provide reports for services not
ready, open blockers, unresolved transition issues, difficult songs, overdue
owner actions, override usage, and completed-service rehearsal history. Define
safe telemetry fields, retention defaults, archival behavior, and runbook steps
for readiness incidents, accidental override creation, and audit reconciliation.

### Acceptance criteria

- Reports can list services blocked from readiness, open blockers by service,
  unresolved transition issues, difficult songs, overdue owner actions, and
  services with active arrangement overrides.
- Metrics track readiness state transitions, time spent in each readiness state,
  issue creation/resolution counts, blocker counts, override counts, and failed
  readiness-transition attempts.
- Structured logs use bounded action/status labels and correlation ids without
  embedding free-text rehearsal notes, pastoral notes, contact data, or lyrics.
- Retention configuration defines how long completed rehearsal sessions, notes,
  issues, overrides, and audit records are kept or archived, with documented
  defaults and church-configurable limits where appropriate.
- A runbook documents readiness-blocker triage, accidental service override
  rollback, audit history review, retention/archive operations, and dashboard or
  alert interpretation.
- Tests or verification fixtures cover report queries, retention/archive
  behavior, telemetry label safety, and audit availability after workflow
  closure.

### Restrictions

- Do not use unbounded labels such as service titles, note text, song titles, or
  person names in metrics.
- Do not permanently delete audit history required to explain readiness changes
  or issue resolution unless an approved retention policy explicitly allows it.
- Do not expose completed-service rehearsal history across church instances or
  to unauthorized reporting roles.
- Do not create reports that require parsing free-form comments to determine
  blocker status.

### Subtask 4 implementation note: service-effective arrangement rendering

Service-specific arrangement overrides are resolved only through the rehearsal
workflow rendering layer. The resolver first loads the approved canonical
arrangement and then applies the most specific active override for the requested
`servicePlanId`, `servicePlanBlockId`, `setlistVersionItemId`, and
`sourceArrangementId`. It returns source, override, and effective values for key,
mode, capo/transposition guidance, chart annotations, section-order notes,
transition cues, instrumentation notes, and asset-selection notes so service
planning, rehearsal, and chart views can clearly label catalog truth versus
service-only adaptation.

Override records must never be consumed by recommendation candidate eligibility,
doctrinal approval, catalog approval status, or recommendation scoring. Creating,
updating, archiving, or rendering an override does not write to canonical
`arrangements`, lyrics, approval, or recommendation read-model tables. To promote
a repeated service adaptation into catalog metadata, the team must open a separate
catalog governance change: create or update an arrangement candidate, attach
proper provenance/licensing evidence, run musical and doctrinal review, and allow
the normal approval workflow to publish the new canonical arrangement version.
Until that governance workflow completes, the values remain service-scoped
rehearsal metadata only.

## Subtask 5 Implementation Notes: Planning Workflow Summaries and Diagnostics

Service planning responses now expose an operational rehearsal workflow summary
alongside legacy readiness notes. These fields are **operational context** only:

- Explicit and derived rehearsal readiness state.
- Current rehearsal phase.
- Upcoming and most recent rehearsal sessions.
- Blocking issue counts and open action counts.
- Open issue counts by rehearsal category and severity.
- Song-level and transition-level open rehearsal issue indicators.
- Action owner/status indicators, with restricted personnel identifiers redacted
  outside authorized modes.
- Service-specific arrangement override counts and boolean indicators.

Recommendation responses may include the same summary as
`operationalWorkflowSummary` when an existing service plan context is supplied.
That payload is intentionally outside deterministic scoring evidence. It may
help worship leaders understand rehearsal readiness, blockers, difficult songs,
transition issues, and service-specific overrides, but it must not be interpreted
as catalog eligibility, approval provenance, score impact, or a candidate
ordering reason.

The following recommendation fields remain deterministic recommendation
evidence and are not mutated by rehearsal workflow status:

- Approved catalog candidate eligibility and approval-gate evidence.
- Dataset and provenance references.
- Musical, theme, scripture, energy, transition, team-suitability, and feedback
  score components.
- Hard-filter reasons, deterministic tie-breaks, selected songs, and selected
  ordering.

Public and unauthorized diagnostic modes preserve aggregate status indicators
when allowed, but redact private issue detail, private action summaries,
individual actor identifiers, and service-assignment identifiers. Service-scoped
override indicators are labeled separately from approved catalog facts so clients
can distinguish one-off service execution context from canonical arrangement
metadata.
