# ADR-018 Implementation Plan: Service Plan Integration Model

## Objective

Model service plans as first-class artifacts that compose setlist versions with
non-song blocks and service-scoped overrides while preserving catalog integrity.

## Status

- Subtask 1: Complete
- Subtask 2: Complete
- Subtask 3: Complete
- Subtask 4: Complete

## Subtask 1: Define service-plan API contract and block schemas

### Context

**Codebase anchors**
- API service: `apps/api`
- Intent contracts package: `packages/intent-contracts`
- DB migrations: `apps/api/src/main/resources/db/migration`
- Existing tests to extend: `apps/api/src/test/java` and `packages/intent-contracts/test`

ADR-018 requires service plans to represent songs plus non-song moments and to
reference specific setlist versions.

### Prompt

**Implementation starting points**
- Primary codebase: `apps/api` (Java/Spring controllers, services, repositories, migrations).
- Contract package: `packages/intent-contracts` (schemas/tests) when request/response shape is involved.
- Extend nearest tests first (for example in `apps/api/src/test/java/com/cadentia/{intent,reng,api/controller}` and `packages/intent-contracts/test`).
- Reference docs to update: `docs/ARCHITECTURE.md`, relevant ADR, and existing topic docs in `docs/`.

Add OpenAPI resources for service-plan CRUD, block ordering, setlist-version
attachment, publish/finalize operations, and service-specific notes/transposition
fields.

### Acceptance criteria

- Schemas include service date/time, title, theme, scripture, and notes.
- Block types include `praise`, `worship`, `offering`, `altar call`, `communion`, and `special`.
- API supports attaching one or more setlist versions per service plan.
- Publish/finalize responses expose immutable effective sequence snapshots.

### Restrictions

- Do not conflate service plans with setlist lineage entities.
- Do not allow publish endpoints to mutate referenced setlist versions.
- Do not omit block-level provenance back to setlist/version references.

### Subtask 1 Contract Definition (Draft)

- Add `ServicePlans` OpenAPI tag and CRUD endpoints for draft metadata lifecycle:
  - `POST /service-plans`, `GET /service-plans`, `GET/PATCH /service-plans/{servicePlanId}`
- Add service composition endpoints:
  - `POST /service-plans/{servicePlanId}/blocks:order`
  - `POST /service-plans/{servicePlanId}/setlist-attachments`
- Add publish endpoint with immutable effective sequence response contract:
  - `POST /service-plans/{servicePlanId}/publish`
- Define schema families:
  - Core metadata: `CreateServicePlanRequest`, `UpdateServicePlanRequest`, `ServicePlanResponse`
  - Block model: `ServicePlanBlock`, `ServicePlanBlockType`, `ServicePlanBlockProvenance`
  - Attachment/provenance: `AttachSetlistVersionRequest`, `ServicePlanSetlistAttachmentResponse`
  - Publish snapshot: `ServicePlanEffectiveSequenceSnapshot`, `ServicePlanPublishResponse`
- Enforce required ADR-018 fields (`serviceDateTime`, `title`, `theme`, `scripture`, `notes`) and block taxonomy (`praise`, `worship`, `offering`, `altar_call`, `communion`, `special`).

## Subtask 2: Implement domain model, persistence, and override isolation

### Context

Service-specific edits must remain isolated from canonical catalog arrangement
records and from immutable setlist history.

### Prompt

**Implementation starting points**
- Primary codebase: `apps/api` (Java/Spring controllers, services, repositories, migrations).
- Contract package: `packages/intent-contracts` (schemas/tests) when request/response shape is involved.
- Extend nearest tests first (for example in `apps/api/src/test/java/com/cadentia/{intent,reng,api/controller}` and `packages/intent-contracts/test`).
- Reference docs to update: `docs/ARCHITECTURE.md`, relevant ADR, and existing topic docs in `docs/`.

Implement Java services/repositories and DB migrations for service plans, block
composition, plan-to-setlist references, and service-scoped override metadata.

### Acceptance criteria

- Migrations create tables for plans, plan versions/snapshots (if used), blocks, and references.
- Service-specific transposition and notes are stored in plan scope only.
- Finalized service sequence is persisted and retrievable without reconstructing from mutable state.
- Multi-setlist composition preserves source references for each song block.

### Restrictions

- Do not mutate catalog canonical keys when storing service transpositions.
- Do not duplicate setlist items without source linkage metadata.
- Do not allow non-transactional publish that can leave partial finalized state.

## Subtask 3: Add publish workflow controls and synchronization semantics

### Context

Referenced setlist versions may evolve; service plans need explicit behavior for
stale references and post-finalization updates.

### Prompt
Implement publish-lock and revision flow rules, including validation for stale
setlist references, optional rebase/refresh flows, and explicit revision history
for finalized plan changes.

### Acceptance criteria

- Publish validates referenced setlist-version existence and access.
- Post-publish edits require explicit revision/unpublish policy path.
- API clearly signals when a newer setlist version exists but is not linked.
- Documentation defines expected behavior for rehearsal-time last-minute changes.

### Restrictions

- Do not auto-repoint plan references to newer setlist versions silently.
- Do not permit hidden mutations of published snapshots.
- Do not lock workflows without documented emergency override procedures.

## Subtask 4: Instrument observability and operational documentation

### Context

Service planning spans scheduling, publishing, and execution-sensitive
operations that need runtime visibility.

### Prompt

**Implementation starting points**
- Primary codebase: `apps/api` (Java/Spring controllers, services, repositories, migrations).
- Contract package: `packages/intent-contracts` (schemas/tests) when request/response shape is involved.
- Extend nearest tests first (for example in `apps/api/src/test/java/com/cadentia/{intent,reng,api/controller}` and `packages/intent-contracts/test`).
- Reference docs to update: `docs/ARCHITECTURE.md`, relevant ADR, and existing topic docs in `docs/`.

Instrument plan lifecycle metrics and logs, then document dashboards and runbooks
for publish failures, stale references, and cross-team collaboration scenarios.

### Acceptance criteria

- Metrics track draft-to-publish conversion, publish failures, and block reorder activity.
- Audit logs include actor and before/after sequence state for publish/revision actions.
- Alerts identify repeated publish conflicts or stale-reference failures.
- Documentation includes operator guidance for multi-campus fork/share strategies.

### Restrictions

- Do not emit unbounded labels for free-text plan titles.
- Do not rely on ad-hoc logs without structured action codes.
- Do not launch without runbooks for publish incident triage.
