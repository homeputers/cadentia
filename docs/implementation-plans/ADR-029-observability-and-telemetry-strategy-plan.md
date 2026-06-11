# ADR-029 Implementation Plan: Observability and Telemetry Strategy

## Objective

Implement privacy-aware observability for Cadentia's recommendation, import,
approval, intent extraction, background job, asset, cache, search, and packaged
deployment workflows so operators can diagnose failures through correlated logs,
metrics, traces, and audit records without exposing sensitive church-instance,
licensed, copyrighted, personnel, prompt, credential, or privileged review data.

## Source ADR

- [ADR-029: Observability and Telemetry Strategy](../adr/ADR-029-observability-and-telemetry-strategy.md)

## Status

Overall status: Planned.

- Subtask 1: Planned - telemetry inventory, audience model, sensitivity
  classification, and backend decision record.
- Subtask 2: Planned - correlation context propagation and structured logging
  foundation.
- Subtask 3: Planned - metrics taxonomy, label governance, and SLO baseline.
- Subtask 4: Planned - distributed tracing instrumentation and span taxonomy.
- Subtask 5: Planned - recommendation diagnostics and safe scoring summaries.
- Subtask 6: Planned - import, parser, read-model, search-index, asset, and
  background-job telemetry.
- Subtask 7: Planned - approval audit trails and privileged action correlation.
- Subtask 8: Planned - LLM intent prompt diagnostics and boundary evidence.
- Subtask 9: Planned - redaction, retention, access control, and leakage tests.
- Subtask 10: Planned - dashboards, alerts, runbooks, and release validation.

## Guiding Principles

- Observability must improve deterministic diagnosis without changing
  recommendation selection, approval eligibility, authorization, or catalog
  governance behavior.
- Logs, metrics, traces, and audit records serve different purposes and must not
  be collapsed into one undifferentiated event stream.
- Telemetry must be safe by default: no raw lyrics excerpts, copyrighted payloads,
  credentials, privileged review notes, private personnel details, unredacted
  prompt text, or inappropriate cross-instance identifiers in operational sinks.
- Correlation identifiers should connect request, job, event, trace, audit, and
  service-plan activity while respecting instance isolation and audience limits.
- Metric labels must be low-cardinality, stable, and explicitly governed.
- Every diagnostic summary should identify versions, snapshots, phase outcomes,
  and exclusion reasons without revealing data that the viewer is not permitted
  to see.
- Audit trails must be durable, integrity-oriented, and queryable for privileged
  actions; operational telemetry may have different retention and sampling rules.

## Subtask 1: Inventory telemetry domains, audiences, sensitivity classes, and backend target

### Context

ADR-029 covers recommendation execution, imports, approval workflows, LLM intent
extraction, background jobs, packaged deployments, and instance authorization.
Before instrumentation is added, Cadentia needs a shared telemetry inventory that
defines what each domain should emit, who may view it, which data is sensitive,
and which observability backend is the initial target. This inventory should also
resolve the ADR open question about the first production telemetry backend or
record a deliberate temporary local/test target.

**Codebase anchors**

- Architecture overview in `docs/ARCHITECTURE.md`
- Eventing plan in
  `docs/implementation-plans/ADR-028-eventing-and-async-processing-architecture-plan.md`
- Caching plan in
  `docs/implementation-plans/ADR-027-caching-and-performance-strategy-plan.md`
- Security roles and permissions plan in
  `docs/implementation-plans/ADR-019-security-roles-and-permissions-plan.md`
- Packaged deployment plan in
  `docs/implementation-plans/ADR-022-packaged-deployment-and-church-customization-model-plan.md`

### Prompt

Create the telemetry domain inventory and backend decision artifact. List each
workflow that must emit observability data, including recommendations, scoring,
transition analysis, explanation generation, imports, lyrics parsing, approvals,
read-model refreshes, search indexing, cache operations, asset processing,
background jobs, external integrations, service-plan operations, packaged
instance provisioning, authorization decisions, and LLM intent extraction.
Classify each field or payload category by sensitivity, allowed sinks, retention
expectations, sampling rules, allowed viewers, and redaction requirements. Select
or document the initial metrics, logs, traces, and audit storage targets for
local, test, staging, and production-like deployments.

### Acceptance criteria

- A durable project document or configuration registry lists telemetry domains,
  owning components, emitted signal types, expected event names or metric
  families, viewer audiences, sensitivity class, retention expectations,
  sampling policy, and allowed sinks.
- The initial observability backend target is named for logs, metrics, traces,
  and audit records, or a documented interim decision explains how local/test
  emitters map to the eventual backend interface.
- Sensitive categories are explicitly identified, including raw lyrics,
  copyrighted payloads, unapproved catalog content, privileged review notes,
  prompt text, credentials, private personnel data, tenant identifiers, and
  cross-instance correlation data.
- The inventory distinguishes operator-visible, developer-visible,
  church-administrator-visible, and security/audit-only telemetry.
- The inventory documents which ADR-029 open questions remain deferred and what
  implementation subtasks are blocked by each deferred decision.

### Restrictions

- Do not start broad instrumentation before defining allowed sinks and redaction
  requirements for sensitive data.
- Do not assume one retention policy fits logs, metrics, traces, and audit
  records.
- Do not expose cross-instance identifiers to church administrators unless an
  explicit audience rule permits it.
- Do not select a backend in code without documenting local/test behavior and
  operational ownership.

## Subtask 2: Implement correlation context propagation and structured logging foundation

### Context

ADR-029 requires structured logs with instance identifier, actor where permitted,
request ID, correlation ID, service plan ID, and event/job ID. Cadentia also has
asynchronous eventing, packaged deployments, and background jobs, so context must
flow across HTTP requests, service calls, outbox events, scheduled work, import
jobs, and response rendering. A shared logging foundation prevents each workflow
from inventing incompatible field names or accidentally logging sensitive data.

**Codebase anchors**

- API application code under `apps/api/src/main/java/com/cadentia/`
- API configuration under `apps/api/src/main/resources/`
- OpenAPI contract under `apps/api/src/main/openapi/`
- Eventing implementation plan in
  `docs/implementation-plans/ADR-028-eventing-and-async-processing-architecture-plan.md`

### Prompt

Build the structured logging and correlation context foundation. Add middleware,
filters, interceptors, or equivalent framework hooks that create or accept a
request ID, correlation ID, trace ID, instance identifier, actor context,
service-plan ID, event ID, job ID, and causation ID where applicable. Propagate
this context through service layers, asynchronous event envelopes, background job
executors, import pipelines, and outbound integration boundaries. Standardize
log field names, severity usage, error summaries, and safe diagnostic payload
helpers. Add tests for context propagation and for omission or hashing of fields
that are not permitted in a given sink.

### Acceptance criteria

- Incoming requests receive stable request and correlation identifiers when the
  caller does not provide safe identifiers, and accepted caller-provided
  identifiers are validated before use.
- Structured logs use one shared field naming contract for service name,
  environment, instance ID or safe instance token, actor ID or system actor,
  request ID, correlation ID, trace ID, service plan ID, event ID, job ID,
  causation ID, operation name, outcome, latency, and sanitized error summary.
- Correlation context flows from HTTP requests into domain services, outbox or
  event records, background jobs, import jobs, and response rendering without
  relying on global mutable state that breaks concurrent requests.
- Logs never include raw lyrics, raw prompts, credentials, privileged review
  notes, private personnel details, unredacted request bodies, or unauthorized
  cross-instance identifiers.
- Tests prove correlation fields are present on representative request, event,
  job, and error paths and are cleared or isolated between concurrent requests.

### Restrictions

- Do not concatenate unstructured messages as the only diagnostic record for
  important workflows.
- Do not log full request bodies, lyrics excerpts, prompt text, review notes, or
  stack traces containing secrets by default.
- Do not let actor or instance identifiers become high-cardinality public metric
  labels; keep them in governed logs or audit records only where permitted.
- Do not rely on thread-local context unless asynchronous propagation and cleanup
  are tested.

## Subtask 3: Define metrics taxonomy, label governance, and SLO baseline

### Context

ADR-029 requires metrics for latency, throughput, errors, queue depth, cache
behavior, approval workflow, and recommendation quality signals. Metrics must be
useful for operational debugging and SLO monitoring, but high-cardinality labels
can increase cost and leak data. Cadentia needs a governed taxonomy before broad
metrics are added.

**Codebase anchors**

- Recommendation scoring plan in
  `docs/implementation-plans/ADR-010-recommendation-engine-scoring-architecture-plan.md`
- Recommendation explainability plan in
  `docs/implementation-plans/ADR-021-recommendation-engine-explainability-api-plan.md`
- Caching plan in
  `docs/implementation-plans/ADR-027-caching-and-performance-strategy-plan.md`
- Approval plan in
  `docs/implementation-plans/ADR-005-approval-doctrinal-review-plan.md`
- Rehearsal workflow plan in
  `docs/implementation-plans/ADR-024-rehearsal-and-workflow-lifecycle-plan.md`

### Prompt

Create the metrics taxonomy and implement the initial metrics foundation. Define
metric names, types, descriptions, units, allowed labels, cardinality budgets,
exemplar or trace-link rules, and ownership for recommendation latency,
recommendation phase duration, candidate counts, exclusion counts, scoring
profile usage, explanation generation, import throughput, parser failures,
read-model refresh lag, search-index refresh lag, asset processing, queue depth,
cache hits and misses, approval workflow transitions, authorization denials,
external integration calls, background job retries, and recommendation quality
signals. Establish initial SLO and alert candidates for critical user-facing and
operational workflows.

### Acceptance criteria

- A metrics registry or documentation artifact defines each metric name, signal
  type, unit, allowed labels, disallowed labels, owner, source component, and
  expected dashboard or alert consumer.
- Initial instrumentation emits latency, throughput, error, queue depth, cache,
  approval workflow, recommendation phase, and quality-signal metrics through a
  shared metrics facade or backend abstraction.
- Labels avoid raw song titles, lyrics, prompt text, actor IDs, instance IDs,
  service-plan names, review notes, request IDs, correlation IDs, and other
  high-cardinality or sensitive values.
- SLO candidates are documented for recommendation generation, imports,
  approval queue processing, read-model/index refresh lag, background job
  failures, and LLM intent extraction boundary failures.
- Tests or static checks validate that registered metrics use only approved
  labels and units.

### Restrictions

- Do not add ad hoc metric names directly in domain code without registering
  them in the taxonomy.
- Do not place user input, song titles, scripture text, prompt text, actor IDs,
  request IDs, correlation IDs, or instance identifiers in metric labels.
- Do not make metrics the source of truth for audit, billing, licensing, or
  approval state.
- Do not create release-blocking SLOs until baseline data and ownership are
  documented.

## Subtask 4: Instrument distributed tracing and span taxonomy

### Context

Recommendation execution and asynchronous workflows cross validation, filtering,
scoring, transition analysis, explanation generation, response rendering,
eventing, cache reads, search index updates, and background processing. ADR-029
requires traceability end-to-end, so Cadentia needs a span taxonomy that mirrors
these phases without leaking unsafe payloads.

**Codebase anchors**

- Recommendation scoring plan in
  `docs/implementation-plans/ADR-010-recommendation-engine-scoring-architecture-plan.md`
- Transition analysis ADR in
  `docs/adr/ADR-031-musical-transition-analysis-engine.md`
- Eventing plan in
  `docs/implementation-plans/ADR-028-eventing-and-async-processing-architecture-plan.md`
- Search plan in
  `docs/implementation-plans/ADR-026-search-architecture-and-discovery-plan.md`

### Prompt

Define and implement the distributed tracing foundation and span taxonomy.
Instrument request entry, authorization, validation, recommendation phases,
cache access, database access boundaries, outbox publishing, event handlers,
background jobs, imports, search indexing, asset processing, external connector
calls, LLM intent extraction, and response rendering. Add safe span attributes
for operation names, component names, profile versions, catalog snapshot IDs or
safe tokens, phase names, outcome codes, exclusion code groups, retry counts,
queue names, and latency. Ensure traces can be sampled or disabled by
configuration while preserving required audit behavior.

### Acceptance criteria

- A documented span naming convention covers HTTP operations, recommendation
  phases, import phases, event/outbox processing, job execution, external calls,
  cache operations, search indexing, asset processing, LLM intent extraction,
  and response rendering.
- Trace context propagates across synchronous service calls, asynchronous event
  handling, background jobs, and outbound integration boundaries where safe.
- Representative traces connect request validation, candidate filtering, scoring,
  transition analysis, tie-breaking, explanation generation, and response
  rendering for recommendation execution.
- Span attributes are limited to approved low-cardinality and sanitized values;
  raw lyrics, prompts, credentials, notes, request bodies, and private personnel
  details are excluded.
- Tests or integration fixtures verify that tracing can be enabled, sampled, and
  disabled without changing business outcomes or audit persistence.

### Restrictions

- Do not use span names or attributes that contain raw user input, song titles,
  lyrics excerpts, prompt text, review notes, credentials, or actor identifiers.
- Do not make trace sampling responsible for preserving legally or operationally
  required audit records.
- Do not change deterministic recommendation ordering or filtering to make spans
  easier to emit.
- Do not couple domain services directly to a vendor-specific tracing API if a
  framework abstraction is available.

## Subtask 5: Add recommendation diagnostics and safe scoring summaries

### Context

ADR-029 specifically requires recommendation tracing across request validation,
candidate filtering, scoring, transition analysis, tie-breaking, explanation
generation, and response rendering. It also requires scoring diagnostics with
profile version, catalog snapshot, candidate counts, exclusion codes, and latency
by phase. These diagnostics must help operators explain deterministic behavior
without letting the LLM select songs or exposing unapproved catalog content.

**Codebase anchors**

- Recommendation read model plan in
  `docs/implementation-plans/ADR-002-recommendation-read-model-plan.md`
- Recommendation scoring plan in
  `docs/implementation-plans/ADR-010-recommendation-engine-scoring-architecture-plan.md`
- Recommendation explanation plan in
  `docs/implementation-plans/ADR-013-recommendation-explanation-system-plan.md`
- Recommendation explainability API plan in
  `docs/implementation-plans/ADR-021-recommendation-engine-explainability-api-plan.md`
- Energy arc ADR in `docs/adr/ADR-032-energy-arc-modeling.md`

### Prompt

Implement recommendation diagnostics emitted through logs, metrics, traces, and
explainability-safe records. Capture validation outcome, request shape hash,
profile version, policy version, catalog snapshot token, candidate pool counts,
approval-visible counts, exclusion code counts, scoring component summaries,
transition analysis summary, energy arc summary, tie-break decision metadata,
explanation generation outcome, response rendering latency, and final result
count. Provide operator-facing debug views or query helpers that correlate these
signals by request ID and correlation ID while respecting viewer permissions.

### Acceptance criteria

- Recommendation execution emits phase-level logs, metrics, and spans for
  validation, candidate filtering, scoring, transition analysis, tie-breaking,
  explanation generation, and response rendering.
- Scoring diagnostics include scoring profile version, policy version, catalog
  snapshot token, candidate counts, approved/eligible counts, exclusion code
  groups, phase latencies, and sanitized score distribution summaries.
- Diagnostics identify why candidates were excluded using stable exclusion codes
  rather than raw private notes, unapproved song details, or free-form LLM text.
- Operator-facing diagnostic lookup can correlate recommendation telemetry with
  audit-relevant request context without exposing content beyond the viewer's
  authorized audience.
- Tests cover successful recommendation, empty candidate pool, authorization
  denial, invalid request, cache hit, cache miss, transition failure, and
  explanation-generation failure paths.

### Restrictions

- Do not let the LLM select songs or provide scoring rationale that overrides
  deterministic Recommendation Engine diagnostics.
- Do not log raw scripture prompt text, lyrics, unapproved song metadata,
  privileged notes, private instance names, or full result payloads in
  operational telemetry.
- Do not use unstable free-form exclusion messages as the primary diagnostic
  contract; use stable codes with documented meanings.
- Do not expose diagnostics for songs or arrangements the viewer is not
  authorized to access.

## Subtask 6: Add import, parser, read-model, search-index, asset, and background-job telemetry

### Context

ADR-029 requires import monitoring, parser diagnostics, read model and index
refresh metrics, asset processing metrics, and background job visibility. These
workflows are often asynchronous and may be retried, so they need consistent job,
event, causation, and correlation metadata to diagnose stale reads, failed media
processing, or catalog governance delays.

**Codebase anchors**

- Import connector plan in
  `docs/implementation-plans/ADR-008-song-acquisition-import-connector-architecture-plan.md`
- Lyrics parsing plan in
  `docs/implementation-plans/ADR-009-lyrics-parsing-musical-analysis-pipeline-plan.md`
- Recommendation read model plan in
  `docs/implementation-plans/ADR-002-recommendation-read-model-plan.md`
- Search plan in
  `docs/implementation-plans/ADR-026-search-architecture-and-discovery-plan.md`
- Media and asset management plan in
  `docs/implementation-plans/ADR-025-media-and-asset-management-plan.md`
- Eventing plan in
  `docs/implementation-plans/ADR-028-eventing-and-async-processing-architecture-plan.md`

### Prompt

Instrument import, parsing, read-model refresh, search-index refresh, asset
processing, and background-job workflows. Emit structured lifecycle events,
metrics, and spans for job accepted, job started, phase completed, retry
scheduled, dead-lettered, partial success, completed, and failed states. Capture
safe diagnostics for connector type, source category, parser rule version,
validation error codes, duplicate detection outcome, asset derivative type,
queue depth, lag, retry count, payload size bucket, and refresh staleness.
Correlate jobs back to the request, event, actor, instance, and service plan that
caused the work where permitted.

### Acceptance criteria

- Import and parser telemetry reports connector category, parser version,
  validation outcome, duplicate-detection outcome, rejected-record counts,
  accepted-record counts, phase latency, retry count, and sanitized error codes.
- Read-model and search-index telemetry reports refresh requests, refresh lag,
  processed aggregate counts, stale-read windows, handler failures, and final
  refresh outcome.
- Asset processing telemetry reports upload acceptance, derivative generation,
  malware or validation outcome if applicable, storage operation result, retry
  count, and sanitized failure reason.
- Background-job telemetry consistently records job ID, event ID, causation ID,
  correlation ID, queue name, status, attempt number, latency, and terminal
  outcome.
- Tests or fixtures cover successful, retry, partial failure, dead-letter, and
  redaction behavior for representative asynchronous workflows.

### Restrictions

- Do not log imported raw lyrics, copyrighted source payloads, credentials,
  connector tokens, full file paths that reveal private tenant details, or
  privileged review notes.
- Do not mark asynchronous work complete before durable state and telemetry agree
  on the terminal outcome.
- Do not use queue depth or lag metrics with instance IDs, actor IDs, song names,
  or request IDs as labels.
- Do not hide partial failures behind a generic success metric.

## Subtask 7: Implement approval audit trails and privileged action correlation

### Context

ADR-029 requires approval audit trails and privileged action correlation. Audit
records are not just operational logs: they must explain who or what changed
approval, visibility, licensing, review, role, instance, or privileged workflow
state. They also need integrity, retention, and access rules that differ from
sampled traces or short-lived logs.

**Codebase anchors**

- Approval and doctrinal review plan in
  `docs/implementation-plans/ADR-005-approval-doctrinal-review-plan.md`
- Security roles and permissions plan in
  `docs/implementation-plans/ADR-019-security-roles-and-permissions-plan.md`
- Import connector architecture plan in
  `docs/implementation-plans/ADR-008-song-acquisition-import-connector-architecture-plan.md`
- Song import and deduplication plan in
  `docs/implementation-plans/ADR-003-song-import-deduplication-plan.md`
- Packaged deployment plan in
  `docs/implementation-plans/ADR-022-packaged-deployment-and-church-customization-model-plan.md`

### Prompt

Design and implement durable audit records for privileged actions and connect
them to logs, metrics, traces, and event records through correlation metadata.
Cover approval state changes, doctrinal review decisions, catalog visibility
changes, licensing-sensitive actions, role or permission changes, instance
configuration changes, service-plan administrative actions, integration
credential changes, packaged deployment operations, audit exports, and security
relevant authorization denials. Define audit schemas, immutable fields, actor and
system actor representation, before/after summaries, reason codes, retention,
access control, export behavior, and tamper-evidence strategy.

### Acceptance criteria

- Privileged actions produce durable audit records with action type, aggregate
  type, aggregate ID or safe token, instance context, actor or system actor,
  timestamp, request ID, correlation ID, trace ID when available, causation ID,
  before/after safe summary, reason code, outcome, and sanitized error summary.
- Audit records can be queried by aggregate, actor, instance, action type,
  outcome, time range, and correlation ID according to role-based permissions.
- Operational logs and traces include enough correlation metadata to find the
  related audit record without duplicating privileged notes or sensitive payloads.
- Tests prove that approval, role, visibility, integration credential, and
  instance configuration changes cannot commit without their required audit
  records.
- Retention, export, and access policies are documented separately from ordinary
  logs, metrics, and traces.

### Restrictions

- Do not treat sampled traces or ordinary logs as the authoritative audit trail.
- Do not store raw privileged review notes, secrets, credentials, or raw lyrics
  in audit records unless a governed field explicitly permits encrypted storage
  with access controls.
- Do not allow users to edit or delete audit records through ordinary domain
  update paths.
- Do not expose audit records across church instances or audiences without an
  explicit administrative permission model.

## Subtask 8: Implement LLM intent prompt diagnostics and boundary evidence

### Context

ADR-029 requires prompt diagnostics for LLM intent extraction without logging
unsafe or unnecessary raw sensitive payloads. Cadentia's guardrails state that
the LLM may parse intent and slots but must not select songs, and all LLM output
must pass schema validation. Telemetry should prove that boundary without storing
raw prompts or generated prose that could leak private content.

**Codebase anchors**

- LLM intent extraction plan in
  `docs/implementation-plans/ADR-014-llm-intent-extraction-contract-plan.md`
- LLM intent extraction ADR in `docs/adr/ADR-012-llm-intent-extraction-contract.md`
- Guided conversational flow plan in
  `docs/implementation-plans/ADR-015-guided-menu-and-conversational-request-flow-plan.md`
- Recommendation scoring plan in
  `docs/implementation-plans/ADR-010-recommendation-engine-scoring-architecture-plan.md`

### Prompt

Add privacy-preserving diagnostics for LLM intent extraction. Capture prompt
template version, model identifier or provider class where allowed, schema
version, input shape categories, request shape hash, token count buckets,
validation result, refusal or repair outcome, boundary violation code, latency,
retry count, timeout outcome, and handoff result to deterministic backend
services. Prove through telemetry and tests that LLM output is schema-validated,
that prose is rejected when JSON is required, and that song selection is never
accepted from the LLM path.

### Acceptance criteria

- Intent extraction telemetry records prompt template version, schema version,
  model/provider category, sanitized input category, token count bucket,
  validation result, boundary violation code if any, retry count, latency, and
  handoff outcome.
- Raw prompts, raw scripture text, private notes, free-form pastoral context,
  credentials, raw model responses, and generated prose are not written to logs,
  metric labels, traces, or ordinary diagnostics.
- Schema validation failures, malformed JSON, unexpected prose, and attempted
  song-selection output emit stable diagnostic codes and safe summaries.
- Tests cover valid extraction, malformed JSON, prose output, attempted song
  selection, timeout, provider failure, and redaction behavior.
- Documentation explains how operators can verify that the LLM stayed within the
  intent-extraction boundary using correlation IDs and diagnostic codes.

### Restrictions

- Do not store raw LLM prompts or raw model completions in operational telemetry
  by default.
- Do not let telemetry capture or replay user input in a way that bypasses the
  intent-extraction data minimization policy.
- Do not allow LLM diagnostics to become an alternate song recommendation or
  ranking source.
- Do not include actor IDs, instance IDs, request IDs, prompt hashes, or free
  text as metric labels.

## Subtask 9: Enforce redaction, retention, access control, and leakage tests

### Context

ADR-029 warns that telemetry must not leak unapproved content, private
church-instance data, secrets, or copyrighted payloads. Redaction cannot rely on
developers remembering individual rules in every log statement. Cadentia needs
central sanitization utilities, retention policies, access controls, and tests
that fail when sensitive data appears in inappropriate sinks.

**Codebase anchors**

- Security roles and permissions plan in
  `docs/implementation-plans/ADR-019-security-roles-and-permissions-plan.md`
- Approval plan in
  `docs/implementation-plans/ADR-005-approval-doctrinal-review-plan.md`
- Lyrics storage plan in
  `docs/implementation-plans/ADR-004-lyrics-storage-format-plan.md`
- Media and asset management plan in
  `docs/implementation-plans/ADR-025-media-and-asset-management-plan.md`

### Prompt

Implement centralized telemetry redaction and policy enforcement. Provide safe
value wrappers, sanitizers, denylisted field detectors, allowlisted structured
payload builders, sink-specific redaction rules, retention configuration, and
role-aware access controls for telemetry search or export paths. Add automated
leakage tests that seed representative sensitive values and assert they do not
appear in logs, metric labels, spans, ordinary diagnostic records, exports, or
error responses. Include static or lint-like checks where feasible for dangerous
logging patterns.

### Acceptance criteria

- Shared sanitization helpers or safe telemetry value types are used by logging,
  metrics, tracing, audit correlation, recommendation diagnostics, import
  diagnostics, and LLM diagnostics.
- Sink-specific policies define what may be stored in logs, metric labels, span
  attributes, audit records, diagnostic tables, dashboards, alerts, and exports.
- Automated leakage tests cover raw lyrics, unapproved song titles, review notes,
  credentials, private personnel details, raw prompts, model completions,
  cross-instance identifiers, connector tokens, and service-plan private data.
- Retention and access-control policies are documented for logs, metrics,
  traces, audit records, diagnostic records, and exported telemetry.
- Dangerous logging or telemetry patterns are either blocked by tests/static
  checks or documented as manual review requirements with codeowner ownership.

### Restrictions

- Do not depend solely on code review to prevent sensitive telemetry leaks.
- Do not use reversible hashing or deterministic tokens for secrets unless the
  threat model and rotation plan are documented.
- Do not give church administrators access to cross-instance operational data or
  internal security telemetry by default.
- Do not allow telemetry exports to bypass the same redaction and authorization
  policies used by interactive views.

## Subtask 10: Build dashboards, alerts, runbooks, and release validation

### Context

ADR-029 acceptance criteria require failures to be diagnosable through correlated
logs, metrics, traces, and audit records, and metrics to support operational
debugging and SLO monitoring. Instrumentation is only useful if operators have
dashboards, alert rules, and runbooks that explain how to use it safely.

**Codebase anchors**

- Existing runbooks under `docs/runbooks/`
- Architecture overview in `docs/ARCHITECTURE.md`
- Caching and eventing plans in
  `docs/implementation-plans/ADR-027-caching-and-performance-strategy-plan.md`
  and
  `docs/implementation-plans/ADR-028-eventing-and-async-processing-architecture-plan.md`

### Prompt

Create operational dashboards, alert candidates, runbooks, and release
validation checks for the observability strategy. Include dashboards for
recommendation health, phase latency, candidate exclusions, LLM intent boundary
health, import and parser health, queue depth and lag, read-model and search
staleness, cache behavior, approval workflow throughput, asset processing,
external integrations, authorization denials, audit activity, and packaged
instance operations. Write runbooks that explain how to diagnose common failures
using correlation IDs across logs, metrics, traces, and audit records. Add a
release checklist or automated validation suite that proves required telemetry is
present and safe before rollout.

### Acceptance criteria

- Dashboards or dashboard definitions exist for recommendation execution, LLM
  intent extraction, imports, parsing, read-model/index refresh, cache behavior,
  approval workflow, background jobs, asset processing, authorization denials,
  audit activity, and packaged deployment operations.
- Alert candidates define signal, threshold or anomaly rule, severity, owner,
  user impact, routing expectation, and immediate runbook link.
- Runbooks show how to start from a request ID, correlation ID, trace ID, event
  ID, job ID, service plan ID, or audit record and find related telemetry safely.
- Release validation checks verify that required logs, metrics, traces, audit
  records, redaction behavior, and dashboard data sources are present in local or
  staging-like environments.
- Documentation explains what telemetry may be shown to church administrators
  versus internal operators and security/audit roles.

### Restrictions

- Do not create alerts without an owner, severity, investigation path, and
  expected user impact.
- Do not expose dashboards containing cross-instance, personnel, credential,
  prompt, raw lyrics, or privileged review data to unauthorized audiences.
- Do not make dashboard availability a dependency for core recommendation or
  approval correctness.
- Do not mark the ADR complete until release validation covers both observability
  presence and sensitive-data absence.
