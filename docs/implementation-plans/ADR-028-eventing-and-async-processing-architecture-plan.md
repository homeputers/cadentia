# ADR-028 Implementation Plan: Eventing and Async Processing Architecture

## Objective

Implement durable, observable, and idempotent eventing and asynchronous
processing for Cadentia workflows that are long-running, failure-prone, or
responsible for derived side effects. The implementation must keep canonical
catalog and planning data as the source of truth while making imports, parsing,
read-model refreshes, search indexing, asset processing, cache invalidation,
notifications, and feedback processing resilient, traceable, and safely
retryable.

## Source ADR

- [ADR-028: Eventing and Async Processing Architecture](../adr/ADR-028-eventing-and-async-processing-architecture.md)

## Status

Overall status: Planned.

- Subtask 1: Planned - event and async-workflow inventory with ownership and
  consistency expectations.
- Subtask 2: Planned - durable event/outbox persistence model and transactional
  publisher boundaries.
- Subtask 3: Planned - event schema contracts, versioning, identifiers, and
  governance rules.
- Subtask 4: Planned - async job model, status API, progress reporting, and
  actor/instance context.
- Subtask 5: Planned - dispatcher, handler framework, idempotency, and
  deduplication controls.
- Subtask 6: Planned - retry, backoff, dead-letter, and administrative recovery
  workflows.
- Subtask 7: Planned - domain event emitters and derived side-effect handlers
  for catalog, imports, assets, search, cache, notifications, service readiness,
  and feedback.
- Subtask 8: Planned - observability, audit correlation, replay/rebuild
  procedures, and rollout validation.

## Guiding Principles

- Canonical relational state remains the source of truth; events and jobs make
  side effects explicit but must not become hidden authority for approval,
  licensing, visibility, or recommendation eligibility.
- Events must be persisted transactionally with the canonical state changes that
  require downstream processing.
- Handlers must be idempotent, deduplicated by stable event identifiers, and safe
  to retry after partial failures.
- Failed work must surface actionable diagnostics through job status, retry
  metadata, dead-letter records, traces, logs, and audit records.
- Asynchronous processing must never bypass approval, provenance, instance
  visibility, role authorization, or deterministic Recommendation Engine
  boundaries.
- Derived stores such as read models, search indexes, cache entries, asset
  derivatives, and notification state must be rebuildable from canonical data and
  event history where applicable.

## Subtask 1: Inventory event sources, async workflows, and consistency expectations

### Context

ADR-028 names imports, parsing, read-model refreshes, asset processing,
notifications, search indexing, cache invalidation, service readiness, instance
configuration changes, and recommendation feedback as asynchronous or
event-driven workflows. Before implementation begins, Cadentia needs a complete
map of which canonical actions emit events, which side effects consume them, who
owns each schema, and what users should see while derived data is eventually
consistent.

**Codebase anchors**

- ADR-028 source decision in
  `docs/adr/ADR-028-eventing-and-async-processing-architecture.md`
- Song import and deduplication plan in
  `docs/implementation-plans/ADR-003-song-import-deduplication-plan.md`
- Lyrics parsing and musical analysis plan in
  `docs/implementation-plans/ADR-009-lyrics-parsing-musical-analysis-pipeline-plan.md`
- Recommendation read model plan in
  `docs/implementation-plans/ADR-002-recommendation-read-model-plan.md`
- Search architecture plan in
  `docs/implementation-plans/ADR-026-search-architecture-and-discovery-plan.md`
- Caching strategy plan in
  `docs/implementation-plans/ADR-027-caching-and-performance-strategy-plan.md`
- Media and asset management plan in
  `docs/implementation-plans/ADR-025-media-and-asset-management-plan.md`
- Service plan integration plan in
  `docs/implementation-plans/ADR-018-service-plan-integration-model-plan.md`
- User feedback and tuning plan in
  `docs/implementation-plans/ADR-017-user-feedback-and-recommendation-tuning-plan.md`

### Prompt

Create an eventing and async workflow inventory that identifies every workflow
that should move out of request-time synchronous execution. For each workflow,
document the source action, canonical aggregate, event names, required payload
summary, producing transaction, expected consumers, derived stores touched,
consistency model, user-visible pending or degraded state, retry safety
considerations, audit requirements, and owning module/team. Include an
implementation priority order that starts with the smallest reliable outbox and
job-status foundation before adding domain-specific handlers.

### Acceptance criteria

- Inventory covers imports, parsing, read-model refreshes, asset processing,
  search indexing, cache invalidation, notifications, catalog approval,
  arrangement changes, instance visibility/configuration changes, service
  readiness, and recommendation feedback.
- Each inventory row identifies one canonical source of truth and one or more
  derived side effects without reversing ownership.
- Consistency expectations are explicit, including whether the UI should show a
  pending state, stale-but-labeled data, degraded results, or a blocking error.
- Workflow priority order distinguishes foundation work from domain handler work
  and names dependencies between ADR implementation plans.
- Inventory is stored in a durable project document or in structured code
  comments/tests that future implementers can reference during schema and handler
  work.

### Restrictions

- Do not implement ad hoc queues before the source actions, event names, and
  consistency expectations are documented.
- Do not mark approval, licensing, visibility, or authorization decisions as
  eventually consistent if they can expose unapproved or unauthorized content.
- Do not let derived stores become the owner of catalog, review, service-plan,
  personnel, or feedback truth.
- Do not use LLM-generated text as an event contract or as a substitute for
  deterministic event names and schema ownership.

## Subtask 2: Implement durable outbox persistence and transactional publishing boundaries

### Context

ADR-028 requires events or outbox records to be persisted transactionally with
canonical state changes. Cadentia should first establish a reliable outbox model
that works with the existing API service, database migrations, local/test
configuration, and future broker integration. This foundation prevents invisible
partial failures where catalog state is committed but read models, search
indexes, caches, or notifications are never updated.

**Codebase anchors**

- API application code under `apps/api/src/main/java/com/cadentia/`
- Application configuration under `apps/api/src/main/resources/`
- Database migrations under `apps/api/src/main/resources/db/migration/`
- OpenAPI aggregate contract under `apps/api/src/main/openapi/`

### Prompt

Design and implement the durable event/outbox persistence layer. Add database
migrations for outbox records, event metadata, payload envelopes, publication
state, timestamps, trace/correlation fields, actor context, instance context,
schema version, retry counters, and error summaries. Add application services
that allow canonical write transactions to append outbox records atomically with
state changes. Provide a publisher/dispatcher boundary that can initially poll
outbox records and later adapt to a broker without changing domain emitters.
Include configuration for polling cadence, batch size, publication lock timeout,
and disabled/local modes.

### Acceptance criteria

- Canonical write operations can append one or more outbox records in the same
  database transaction as the source state change.
- Outbox records include stable event ID, event type, schema version, aggregate
  type, aggregate ID, instance ID when applicable, actor ID or system actor,
  causation ID, correlation ID, trace ID when available, payload, status,
  timestamps, retry counters, and last error summary.
- Dispatcher polling claims records safely across concurrent workers and does not
  publish the same unclaimed record twice under normal operation.
- Outbox publication can run in local/test mode without requiring an external
  broker, while preserving the same event envelope shape.
- Database indexes support common dispatcher queries and operational lookup by
  aggregate, event ID, status, instance, and correlation ID.
- Tests prove that rolling back a canonical transaction also rolls back its
  outbox records.

### Restrictions

- Do not publish events before the canonical transaction commits.
- Do not make in-memory queues the only durable record of work.
- Do not store raw lyrics, privileged review notes, private personnel details, or
  unredacted prompt text in event payloads unless a specific governed event
  contract requires it and redaction/encryption rules are documented.
- Do not couple domain write services directly to a specific external broker API.

## Subtask 3: Define event schema contracts, versioning, identifiers, and governance rules

### Context

ADR-028 identifies several domain events that must coordinate downstream work.
Those events need stable names, schema versions, validation, and compatibility
rules so handlers can evolve safely. Event governance is especially important for
Cadentia because event payloads can influence approved catalog visibility,
recommendation eligibility, derived read models, cache invalidation, search
indexing, and audit traceability.

**Codebase anchors**

- Catalog and approval plans in
  `docs/implementation-plans/ADR-001-song-data-infrastructure-plan.md` and
  `docs/implementation-plans/ADR-005-approval-doctrinal-review-plan.md`
- Arrangement transposition and compatibility plans in
  `docs/implementation-plans/ADR-006-arrangement-transposition-plan.md` and
  `docs/adr/ADR-033-arrangement-compatibility-and-instrumentation-modeling.md`
- Packaged deployment and instance customization plan in
  `docs/implementation-plans/ADR-022-packaged-deployment-and-church-customization-model-plan.md`
- Recommendation explainability and feedback plans in
  `docs/implementation-plans/ADR-021-recommendation-engine-explainability-api-plan.md` and
  `docs/implementation-plans/ADR-017-user-feedback-and-recommendation-tuning-plan.md`

### Prompt

Create the initial event contract registry and validation rules. Define typed
event envelopes and payload schemas for catalog approval changes, arrangement
changes, import lifecycle changes, asset uploads and derivative requests,
instance visibility/configuration changes, service readiness changes,
recommendation feedback capture, read-model refresh requests, search-index
updates, cache invalidation requests, and notification requests. Establish naming
conventions, required metadata, semantic schema versioning, backward-compatible
change rules, deprecation policy, fixture examples, and contract tests that
validate producers and consumers against the registry.

### Acceptance criteria

- Event contracts have stable names, owners, payload schemas, schema versions,
  required metadata, redaction rules, and example fixtures.
- Event IDs are globally unique and stable for a produced event; causation and
  correlation IDs link related actions and downstream side effects.
- Schema validation rejects missing required fields, unknown unsupported schema
  versions, invalid instance/actor context, and payloads that exceed configured
  size or sensitivity limits.
- Contract tests cover all initial event types and verify that producers emit
  valid envelopes and consumers declare supported schema versions.
- Governance documentation explains how to introduce, evolve, deprecate, and
  retire event versions without breaking existing handlers or replay jobs.

### Restrictions

- Do not use free-form maps as the long-term event schema for domain events.
- Do not introduce breaking payload changes without incrementing the schema
  version and providing a migration or compatibility plan.
- Do not include entire aggregate snapshots when consumers only require stable
  identifiers and small change summaries.
- Do not let event payloads authorize actions; handlers must re-check canonical
  state and policy where approval, visibility, licensing, or role boundaries
  matter.

## Subtask 4: Implement async job model, status API, progress reporting, and actor/instance context

### Context

ADR-028 requires job status, progress, error reason, and actor/instance context.
Users and operators need to see whether long-running imports, parsing,
read-model rebuilds, asset derivative generation, search reindexing, cache
warmups, notifications, or replay operations are pending, running, completed,
failed, retrying, or dead-lettered. The job model should be separate from the
outbox event record but correlated with events and traces.

**Codebase anchors**

- API OpenAPI split contract under `apps/api/src/main/openapi/`
- API application code under `apps/api/src/main/java/com/cadentia/`
- Import workflow documentation in `docs/import-workflow.md`
- Media and asset management plan in
  `docs/implementation-plans/ADR-025-media-and-asset-management-plan.md`
- Rehearsal workflow plan in
  `docs/implementation-plans/ADR-024-rehearsal-and-workflow-lifecycle-plan.md`

### Prompt

Implement a first-class async job model and API. Add persistence for job ID,
job type, status, progress counts or percent, source aggregate, actor, instance,
correlation ID, related event IDs, created/started/completed timestamps,
retry/dead-letter state, user-safe status message, internal error details, and
operator diagnostics. Expose read endpoints for job lookup and filtered job
lists, and update existing long-running commands to return a job reference when
processing continues asynchronously. Define status transitions and enforce them
with tests.

### Acceptance criteria

- Long-running workflows can create a job record and return a stable job ID to
  callers without blocking on downstream side effects.
- Job statuses include at least pending, running, succeeded, partially succeeded
  where needed, retry scheduled, failed, cancelled where supported, and
  dead-lettered.
- Status responses include actor/instance context, progress, user-safe messages,
  error reason, retry metadata, related event IDs, and correlation ID.
- OpenAPI changes are made in the split contract files and generated sources are
  verified after API contract changes.
- Authorization rules prevent users from reading job details for another
  instance or privileged operation they are not allowed to see.
- Tests cover valid status transitions, invalid transitions, filtered list
  behavior, and redaction of internal diagnostics from non-admin responses.

### Restrictions

- Do not expose raw stack traces, secrets, private personnel details, privileged
  review notes, raw lyrics, or unredacted external payloads in user-facing job
  messages.
- Do not use job status as the canonical approval, import, review, service-plan,
  or asset state; job status reports processing progress only.
- Do not collapse the split OpenAPI files into one large specification file.
- Do not require clients to poll derived stores directly to infer background job
  state.

## Subtask 5: Build dispatcher, handler framework, idempotency, and deduplication controls

### Context

ADR-028 requires idempotent handlers with stable event IDs and deduplication.
Handlers may fail after partially updating a read model, search index, cache,
asset derivative, notification record, or audit trail. The framework must make
safe retry the default and must prevent duplicate catalog entries,
notification storms, or approval bypass when the same event is delivered more
than once.

**Codebase anchors**

- Recommendation read model plan in
  `docs/implementation-plans/ADR-002-recommendation-read-model-plan.md`
- Song import and deduplication plan in
  `docs/implementation-plans/ADR-003-song-import-deduplication-plan.md`
- Search architecture plan in
  `docs/implementation-plans/ADR-026-search-architecture-and-discovery-plan.md`
- Caching strategy plan in
  `docs/implementation-plans/ADR-027-caching-and-performance-strategy-plan.md`
- Song acquisition and import connector plan in
  `docs/implementation-plans/ADR-008-song-acquisition-import-connector-architecture-plan.md`

### Prompt

Implement the event dispatcher and handler framework. Provide a handler registry
that routes by event type and supported schema version, a processing ledger that
records handler/event outcomes, idempotency keys, concurrency controls,
deduplication checks, handler timeouts, and safe acknowledgement behavior. Add
helper APIs that let handlers update job progress, emit follow-up events, record
audit entries, and load current canonical state before applying derived side
effects. Include test fixtures for duplicate delivery, concurrent processing,
partial failure, and replay.

### Acceptance criteria

- Each handler records completion or failure per event ID, handler name, schema
  version, and idempotency key.
- Duplicate delivery of the same event to the same handler does not duplicate
  catalog entries, asset derivatives, notifications, audit entries, cache
  invalidations, or derived projection rows.
- Handlers can declare whether they are synchronous-within-dispatch,
  long-running, replayable, disabled, or dependent on another handler outcome.
- Handler framework re-checks canonical state before applying side effects that
  depend on approval, licensing, visibility, active status, or authorization.
- Tests cover successful processing, duplicate delivery, concurrent claims,
  partial failure before acknowledgement, retry after failure, and unsupported
  schema versions.

### Restrictions

- Do not let handlers perform non-idempotent inserts without unique constraints,
  ledger checks, or deterministic idempotency keys.
- Do not acknowledge an event as fully processed before required side effects are
  committed or intentionally skipped with an auditable reason.
- Do not let one failing handler permanently block unrelated handlers for the
  same event type unless an explicit dependency is declared.
- Do not call LLMs from handlers to choose songs, alter recommendation order, or
  infer approval decisions.

## Subtask 6: Add retry, backoff, dead-letter, and administrative recovery workflows

### Context

ADR-028 requires retry policies with backoff, retry limits, and dead-letter
queues. Operators need actionable diagnostics and safe recovery tools when an
import connector fails, an asset derivative processor cannot read a file, a
search index is unavailable, a notification provider rejects a request, or a
handler repeatedly violates a schema or business invariant.

**Codebase anchors**

- Administrative web interface ADR in
  `docs/adr/ADR-036-administrative-web-interface.md`
- Security roles and permissions plan in
  `docs/implementation-plans/ADR-019-security-roles-and-permissions-plan.md`
- Song acquisition and import connector plan in
  `docs/implementation-plans/ADR-008-song-acquisition-import-connector-architecture-plan.md`
- Existing runbooks under `docs/runbooks/`

### Prompt

Implement retry and dead-letter mechanics for outbox publication, handler
processing, and async jobs. Define retry classes for transient infrastructure
failures, external provider throttling, validation/business-rule failures, and
permanent unsupported events. Add configurable exponential backoff with jitter,
maximum attempts, next-attempt timestamps, dead-letter records, operator notes,
manual retry, skip-with-reason, requeue, and quarantine flows. Provide admin-only
APIs or service commands for inspecting and recovering failed work, and document
triage procedures in an operations runbook.

### Acceptance criteria

- Retry policy is configurable by event type, handler, job type, and error class
  with sensible defaults.
- Failed work transitions to retry scheduled or dead-lettered with last error,
  attempt count, next attempt time, handler/job context, correlation ID, and
  recommended operator action.
- Dead-lettered events/jobs can be inspected by authorized operators and safely
  retried, skipped, or quarantined with an audit reason.
- Manual retry reuses the original event/job identity or creates a clearly
  linked retry identity without duplicating derived side effects.
- Tests verify backoff scheduling, retry limits, dead-letter transition,
  authorization, audit logging, and idempotent manual retry.
- Runbook documentation explains common failure modes and recovery steps for
  imports, parsing, read-model refreshes, search indexing, cache invalidation,
  asset processing, notifications, and feedback handlers.

### Restrictions

- Do not retry permanent validation or authorization failures indefinitely.
- Do not allow non-admin users to requeue, skip, or mutate dead-lettered work.
- Do not delete failed records as the primary recovery mechanism; preserve
  diagnostics and audit trail subject to retention policy.
- Do not bypass approval, deduplication, provenance, or visibility checks during
  manual recovery.

## Subtask 7: Implement domain event emitters and derived side-effect handlers

### Context

After the foundation exists, ADR-028 requires canonical state changes to emit
events and asynchronous processors to update derived data. This work connects the
framework to Cadentia's specific domains: imports, parsing, catalog approval,
arrangement changes, read models, search indexes, caches, assets, notifications,
service readiness, instance configuration, and recommendation feedback.

**Codebase anchors**

- Song data infrastructure plan in
  `docs/implementation-plans/ADR-001-song-data-infrastructure-plan.md`
- Approval workflow plan in
  `docs/implementation-plans/ADR-005-approval-doctrinal-review-plan.md`
- Import connector plan in
  `docs/implementation-plans/ADR-008-song-acquisition-import-connector-architecture-plan.md`
- Lyrics parsing plan in
  `docs/implementation-plans/ADR-009-lyrics-parsing-musical-analysis-pipeline-plan.md`
- Recommendation scoring and read-model plans in
  `docs/implementation-plans/ADR-010-recommendation-engine-scoring-architecture-plan.md` and
  `docs/implementation-plans/ADR-002-recommendation-read-model-plan.md`
- Search, cache, asset, service-plan, feedback, and notification-related plans in
  `docs/implementation-plans/ADR-026-search-architecture-and-discovery-plan.md`,
  `docs/implementation-plans/ADR-027-caching-and-performance-strategy-plan.md`,
  `docs/implementation-plans/ADR-025-media-and-asset-management-plan.md`,
  `docs/implementation-plans/ADR-018-service-plan-integration-model-plan.md`, and
  `docs/implementation-plans/ADR-017-user-feedback-and-recommendation-tuning-plan.md`

### Prompt

Wire domain emitters and handlers into the eventing foundation in priority
slices. Start with low-risk internal derived stores, then add heavier or external
side effects. Emit events for import lifecycle transitions, parsed lyric/musical
analysis availability, catalog approval changes, arrangement changes, asset
upload and derivative requests, instance visibility/configuration changes,
service readiness changes, recommendation feedback capture, and notification
requests. Implement handlers that refresh recommendation read models, update
search indexes, invalidate or warm caches, process assets, enqueue
notifications, update service-readiness projections, and aggregate feedback
signals while preserving existing approval and deterministic selection rules.

### Acceptance criteria

- Canonical domain services emit the required events transactionally through the
  outbox foundation.
- Derived handlers update read models, search indexes, caches, asset derivatives,
  notification state, service-readiness projections, and feedback aggregates
  without making those stores canonical.
- Import retries cannot create duplicate catalog entries or bypass staged import,
  deduplication, provenance, or approval workflows.
- Approval and visibility changes invalidate or refresh all recommendation,
  search, cache, and notification-derived data that could expose stale
  eligibility.
- Asset and notification handlers are idempotent against repeated event delivery
  and provider retries.
- Integration tests prove end-to-end event flow from at least one source action
  to each major derived side-effect category.

### Restrictions

- Do not move deterministic Recommendation Engine song selection into event
  handlers; handlers may refresh inputs or projections only.
- Do not make derived read models, indexes, caches, or notification records the
  source of approval or visibility truth.
- Do not process external notifications before canonical state and authorization
  checks confirm the recipient and content are allowed.
- Do not batch unrelated instance data in a way that risks cross-instance leakage
  or unclear recovery boundaries.

## Subtask 8: Add observability, audit correlation, replay/rebuild procedures, and rollout validation

### Context

ADR-028 requires events to correlate with observability traces and audit records,
and states that derived data should be rebuildable from canonical sources and
event history where applicable. Operations teams need metrics, logs, traces,
dashboards, retention decisions, replay boundaries, and validation tests before
asynchronous processing becomes the default for user-facing workflows.

**Codebase anchors**

- Observability strategy ADR in
  `docs/adr/ADR-029-observability-and-telemetry-strategy.md`
- Security and audit plan in
  `docs/implementation-plans/ADR-019-security-roles-and-permissions-plan.md`
- Existing runbooks under `docs/runbooks/`
- API application code under `apps/api/src/main/java/com/cadentia/`
- Application configuration under `apps/api/src/main/resources/`

### Prompt

Implement observability and operational readiness for eventing. Add metrics for
outbox depth, publish latency, handler latency, retry counts, dead-letter counts,
job duration, progress, throughput, replay duration, and derived-store lag. Add
structured logs and traces that carry event ID, job ID, aggregate ID, instance
ID, actor ID where safe, correlation ID, causation ID, and handler name. Create
audit entries for high-risk event production, handler decisions, manual retry,
skip, quarantine, replay, and rebuild operations. Define replay and rebuild
commands for supported projections, retention policy for event/outbox/job data,
rollout feature flags, dashboards, alerts, and smoke tests.

### Acceptance criteria

- Metrics, logs, and traces allow an operator to follow a source action through
  event persistence, publication, handler execution, job status, and derived side
  effects.
- Audit records exist for sensitive source actions, administrative recovery, and
  replay/rebuild operations with actor, instance, timestamp, reason, and outcome.
- Replay/rebuild procedures identify which projections can replay from event
  history and which must rebuild from canonical tables.
- Retention policy covers outbox records, event history, handler ledger entries,
  job records, dead-letter records, payload redaction, and audit records.
- Rollout plan includes feature flags or configuration gates, local/test/staging
  validation, backfill strategy, dashboards, alerts, and rollback steps.
- Tests or operational smoke checks validate trace propagation, metric emission,
  redaction, replay idempotency, and derived-store rebuild correctness.

### Restrictions

- Do not log raw lyrics, secrets, credentials, privileged review notes, private
  personnel details, or unredacted external payloads in observability signals.
- Do not offer replay or rebuild operations that can bypass approval,
  deduplication, visibility, licensing, or authorization checks.
- Do not retain sensitive payload data longer than the documented retention and
  privacy policy allows.
- Do not declare rollout complete until operators can see queue depth, handler
  failures, dead-letter volume, and job status health in production-like
  telemetry.
