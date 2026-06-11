# ADR-034 Implementation Plan: Congregational Familiarity Model

## Objective

Implement deterministic congregational familiarity modeling so Cadentia can use
church-instance service history, song and arrangement recency, rolling frequency,
new-song introduction cadence, configurable rotation policies, and versioned
decay formulas to influence Recommendation Engine ranking and explanations
without allowing popularity to bypass approval, licensing, doctrinal, visibility,
or hard musical constraints.

## Source ADR

- [ADR-034: Congregational Familiarity Model](../adr/ADR-034-congregational-familiarity-model.md)

## Status

Overall status: Planned.

- Subtask 1: Planned - familiarity vocabulary, policy contract, formula scope,
  and reason-code taxonomy.
- Subtask 2: Planned - usage history persistence, event capture, provenance,
  and instance-scoped access boundaries.
- Subtask 3: Planned - familiarity snapshot projection, aggregation windows,
  and deterministic formula versioning.
- Subtask 4: Planned - church rotation policy configuration, maximum-repeat
  rules, sparse-history defaults, and governance.
- Subtask 5: Planned - new-song introduction cadence, planned repetition
  windows, and unfamiliar-song caps.
- Subtask 6: Planned - Recommendation Engine integration, filtering precedence,
  scoring, ordering, and tie-breaking.
- Subtask 7: Planned - ADR-021 explanations, reason codes, audit references,
  and privacy-safe observability.
- Subtask 8: Planned - API, OpenAPI, admin, import, and service-history
  operational surfaces.
- Subtask 9: Planned - migrations, backfill, regression fixtures, testing,
  rollout controls, and documentation.

## Guiding Principles

- Familiarity is church-instance scoped and must be computed from authorized
  service history, explicit admin actions, approved arrangement metadata, and
  versioned church policy snapshots.
- Familiarity can affect ranking, penalties, warnings, and explanations, but it
  must never bypass approval, licensing, doctrinal review, active-catalog
  eligibility, instance visibility, LLM guardrails, or hard musical constraints.
- LLM components must not estimate congregational familiarity, infer usage
  history, choose songs because they seem popular, or create authoritative
  familiarity metadata from prompts.
- Identical requests against the same church instance, service-history snapshot,
  catalog snapshot, request policy, and familiarity profile version must produce
  identical scores and explanations.
- Usage-derived popularity must be constrained by rotation policies, overuse
  thresholds, new-song limits, and explicit tie-breaking rules so familiar songs
  do not crowd out healthier set diversity.
- All recommendation-time reads must use approved projections or repository
  boundaries that enforce church-instance isolation and avoid cross-instance
  leakage through results, explanations, metrics, logs, or errors.
- Sparse-history and bootstrap scenarios must be safe, explainable, and
  deterministic rather than filled with global popularity assumptions.

## Subtask 1: Define familiarity vocabulary, policy contract, formula scope, and reason-code taxonomy

### Context

ADR-034 requires Cadentia to track usage frequency, recency, introduction
status, repetition cadence, decay over time, overuse thresholds, and
church-configurable rotation policies. A shared vocabulary and machine-readable
contract must exist before persistence, projections, Recommendation Engine
logic, API surfaces, or explanations are implemented. The model must integrate
with existing scoring, recommendation read-model, explainability, packaged
church customization, and security plans.

**Codebase anchors**

- Source ADR in `docs/adr/ADR-034-congregational-familiarity-model.md`
- Recommendation scoring plan in
  `docs/implementation-plans/ADR-010-recommendation-engine-scoring-architecture-plan.md`
- Recommendation explainability plan in
  `docs/implementation-plans/ADR-021-recommendation-engine-explainability-api-plan.md`
- Recommendation read-model plan in
  `docs/implementation-plans/ADR-002-recommendation-read-model-plan.md`
- Packaged deployment and church customization plan in
  `docs/implementation-plans/ADR-022-packaged-deployment-and-church-customization-model-plan.md`
- Security roles and permissions plan in
  `docs/implementation-plans/ADR-019-security-roles-and-permissions-plan.md`

### Prompt

Create the v1 congregational familiarity domain contract. Define core objects
for usage event, service-history source, song usage aggregate, arrangement usage
aggregate, familiarity snapshot, familiarity score component, decay formula,
rotation policy, repeat rule, seasonal window, introduction plan, planned
repetition window, unfamiliar-song cap, overuse threshold, policy override,
warning, reason code, evidence reference, and audit event. Define controlled
vocabularies for service context, setlist role, usage status, source type,
introduction state, frequency window, penalty type, warning severity, formula
version, and score component names. Establish reason-code families for recent
use, rolling overuse, seasonal overuse, underuse boost, new-song introduction,
planned repetition, unfamiliar-song cap, sparse history, manual override,
missing usage history, formula version, policy fallback, and approval-gate
precedence.

### Acceptance criteria

- A durable specification or code contract defines familiarity inputs,
  aggregates, snapshots, policies, score components, penalties, warnings,
  evidence references, audit events, and result shapes.
- Controlled vocabularies cover song-level usage, arrangement-level usage,
  service context, setlist role, introduction state, frequency windows,
  repetition outcomes, warning severities, and reason codes.
- The contract clearly identifies which values are service-history facts,
  derived aggregates, church-instance policy configuration, packaged defaults,
  request-time policy hints, or recommendation-time computed outputs.
- Formula versions are explicit in contracts and every score component can cite
  the formula version and input snapshot that produced it.
- Reason codes are stable, machine-readable, and compatible with ADR-021
  explanations for positive familiarity signals, soft penalties, hard policy
  exclusions, sparse-history fallbacks, and approval-gate precedence.
- Open questions from ADR-034 about default decay period, multi-campus behavior,
  and manual override permissions are answered for v1 or documented as deferred
  with safe defaults.

### Restrictions

- Do not use global popularity, streaming metrics, CCLI rankings, or LLM guesses
  as congregation familiarity.
- Do not define free-form policy values or reason codes that cannot be
  validated, indexed, tested, audited, or explained deterministically.
- Do not let familiarity metadata become a song-selection authority outside the
  Recommendation Engine.
- Do not make church-specific overrides indistinguishable from packaged defaults
  or derived service-history facts.

## Subtask 2: Implement usage history persistence, event capture, provenance, and instance-scoped access boundaries

### Context

ADR-034 requires tracking song and arrangement usage by church instance,
service, date, context, and setlist role. Usage history is sensitive tenant data
and must not be visible outside authorized church-instance boundaries. Service
plans, setlist persistence, rehearsal/workflow lifecycle, and external
integrations may all create or confirm usage history, so provenance and
deduplication rules are required before derived familiarity scoring can trust the
history.

**Codebase anchors**

- Setlist persistence and versioning plan in
  `docs/implementation-plans/ADR-016-setlist-persistence-and-versioning-plan.md`
- Service plan integration model plan in
  `docs/implementation-plans/ADR-018-service-plan-integration-model-plan.md`
- Rehearsal and workflow lifecycle plan in
  `docs/implementation-plans/ADR-024-rehearsal-and-workflow-lifecycle-plan.md`
- Song acquisition and import connector plan in
  `docs/implementation-plans/ADR-008-song-acquisition-import-connector-architecture-plan.md`
- Security roles and permissions plan in
  `docs/implementation-plans/ADR-019-security-roles-and-permissions-plan.md`

### Prompt

Design and implement the source-of-truth persistence for church-instance usage
history. Capture usage events from finalized setlists, service-plan imports,
completed service lifecycle transitions, admin corrections, and approved
backfills. Store church instance, campus or service grouping where supported,
service identifier, service date, service type, setlist slot, setlist role,
song identifier, arrangement identifier, key or transposition reference when
available, source system, source event id, provenance, capture timestamp,
correction or deletion state, actor, and audit metadata. Add repository or
adapter boundaries that enforce church-instance authorization, deduplicate
idempotent source events, and expose only normalized active usage facts to
familiarity projections.

### Acceptance criteria

- Usage history has a source-of-truth persistence model with stable identifiers,
  church-instance scope, optional campus/service grouping, service date,
  service context, setlist role, song id, arrangement id, source/provenance,
  actor, correction state, and audit fields.
- Event capture is idempotent for repeated imports or workflow callbacks and can
  reconcile corrections, cancellations, deletions, and restored services without
  double-counting usage.
- Repository or adapter boundaries enforce authorization and church-instance
  isolation for all reads and writes, including admin correction paths,
  integration callbacks, audit views, and projection jobs.
- Usage facts can represent both song-level and arrangement-level usage while
  preserving the link to the service/setlist position that produced them.
- Deletion, retention, and correction behavior is documented so derived
  aggregates can rebuild deterministically from the accepted usage history.
- Tests cover cross-instance access denial, idempotent ingestion, correction
  replacement, service cancellation, arrangement-level usage, and role-specific
  usage.

### Restrictions

- Do not infer usage from draft setlists unless a documented lifecycle state
  explicitly marks the usage as planned or completed for the relevant formula.
- Do not expose cross-instance service ids, song usage counts, titles, actor
  names, or correction metadata through errors, logs, metrics, or explanations.
- Do not write recommendation-time scoring logic directly against raw
  integration payloads, draft imports, or unnormalized service-plan data.
- Do not delete historical usage in a way that prevents audit reconstruction;
  use tombstones or correction records when required by audit policy.

## Subtask 3: Build familiarity snapshot projection, aggregation windows, and deterministic formula versioning

### Context

Recommendation-time scoring needs efficient, deterministic familiarity inputs
rather than expensive ad hoc scans of raw usage events. ADR-034 requires recency,
rolling frequency, seasonal usage, overuse thresholds, introduction cadence, and
decay over time. These values must be generated from a consistent history
snapshot and formula version so recommendation results are reproducible and
explainable.

**Codebase anchors**

- Recommendation read-model plan in
  `docs/implementation-plans/ADR-002-recommendation-read-model-plan.md`
- Caching and performance strategy plan in
  `docs/implementation-plans/ADR-027-caching-and-performance-strategy-plan.md`
- Observability and telemetry strategy plan in
  `docs/implementation-plans/ADR-029-observability-and-telemetry-strategy-plan.md`
- Recommendation scoring plan in
  `docs/implementation-plans/ADR-010-recommendation-engine-scoring-architecture-plan.md`

### Prompt

Implement a familiarity projection that converts accepted usage facts into
song-level and arrangement-level familiarity snapshots. Define aggregation
windows for recent use, rolling frequency, seasonal usage, lifetime usage,
first-use date, last-use date, usage by role, usage by service type, and planned
future repetition where applicable. Implement versioned deterministic decay
formulas with documented inputs, default parameters, precision/rounding rules,
cache keys, invalidation triggers, rebuild behavior, and snapshot identifiers.
Provide a read adapter that returns all familiarity inputs required by the
Recommendation Engine for a single church instance and catalog snapshot.

### Acceptance criteria

- A projection or read model provides deterministic song and arrangement
  familiarity snapshots for a specific church instance, service-history
  snapshot, policy profile, formula version, and catalog snapshot.
- Aggregates include at minimum first use, last use, recent-use buckets, rolling
  frequency windows, seasonal usage windows, lifetime usage count, role-specific
  counts, and arrangement-specific counts.
- Decay formulas are versioned, documented, independently testable, and stable
  for identical inputs, including explicit rounding, timezone/date, null, and
  sparse-history behavior.
- Projection rebuilds and incremental updates produce equivalent outputs for the
  same accepted usage event set.
- Cache keys and invalidation account for usage-event changes, correction
  events, policy version changes, formula version changes, catalog snapshot
  changes, and church-instance boundaries.
- Snapshot ids, checksums, or equivalent change markers are available for
  explanations, audit logs, and regression fixtures.

### Restrictions

- Do not compute recommendation-time familiarity by scanning raw event tables if
  a projection/read model is required for performance or determinism.
- Do not let local server timezone, wall-clock time during request handling, or
  unordered collection iteration change scoring outputs.
- Do not combine data from multiple church instances unless an explicit,
  authorized multi-campus policy says so and the snapshot records that policy.
- Do not silently substitute global or package-wide usage when a church instance
  has sparse history.

## Subtask 4: Implement church rotation policy configuration, maximum-repeat rules, sparse-history defaults, and governance

### Context

ADR-034 requires church-configurable rotation policies and maximum-repeat rules.
The defaults must protect healthy rotation, but each church may have different
cadence expectations, seasonal practices, service types, and multi-campus data
sharing needs. Policy changes can alter recommendations and must therefore be
versioned, authorized, audited, and explainable.

**Codebase anchors**

- Packaged deployment and church customization plan in
  `docs/implementation-plans/ADR-022-packaged-deployment-and-church-customization-model-plan.md`
- Security roles and permissions plan in
  `docs/implementation-plans/ADR-019-security-roles-and-permissions-plan.md`
- Admin review and catalog governance UI plan in
  `docs/implementation-plans/ADR-011-admin-review-catalog-governance-ui-plan.md`
- Caching and performance strategy plan in
  `docs/implementation-plans/ADR-027-caching-and-performance-strategy-plan.md`

### Prompt

Create the rotation policy model and governance workflow. Define packaged
defaults, church-instance overrides, optional campus/service-type variants,
effective dates, version identifiers, validation rules, role authorization,
approval or review states where needed, audit entries, and rollback behavior.
Model maximum repeats by song and arrangement across configurable windows,
seasonal caps, minimum rest periods, overuse penalty thresholds, underuse boost
thresholds, sparse-history fallback behavior, multi-campus sharing mode, and
request-time policy override limits. Ensure policy resolution is deterministic
and returns a snapshot reference used by scoring and explanations.

### Acceptance criteria

- Rotation policy configuration supports repeat limits, minimum rest periods,
  rolling frequency thresholds, seasonal thresholds, overuse penalties, underuse
  boosts, sparse-history defaults, service-type variants, and optional
  campus-sharing behavior.
- Policy records are versioned with stable ids, effective lifecycle, validation
  errors, audit entries, actor identity, and rollback or supersession behavior.
- Policy resolution order is documented for packaged defaults,
  church-instance defaults, campus/service-type variants, season-specific
  policies, request-time constraints, and emergency/admin overrides.
- Invalid policy values are rejected before they can affect recommendations,
  including contradictory windows, negative thresholds, unbounded overrides, and
  values outside supported formula ranges.
- Recommendation-time policy reads are authorization-safe and produce a stable
  policy snapshot reference for audit and explanations.
- Sparse-history behavior is explicit, deterministic, and does not substitute
  unapproved or cross-instance popularity.

### Restrictions

- Do not allow policy edits to bypass church admin authorization, audit logging,
  validation, or package-governance boundaries.
- Do not permit arbitrary request-time overrides that disable approval gates,
  hard constraints, tenant isolation, or maximum-repeat safeguards.
- Do not store policy as unstructured JSON without schema validation,
  migration strategy, and generated/tested contract coverage.
- Do not treat campus-shared familiarity as the default unless an explicit
  church policy enables it.

## Subtask 5: Implement new-song introduction cadence, planned repetition windows, and unfamiliar-song caps

### Context

ADR-034 requires support for gradual introduction of new songs, planned
repetition windows, and limits that prevent too many unfamiliar songs in one set.
New-song handling must distinguish between a song that is globally new to the
catalog, newly approved for a church, newly introduced to a congregation, and
currently inside a planned learning cadence. This logic must be deterministic
and explainable.

**Codebase anchors**

- Approval and doctrinal review plan in
  `docs/implementation-plans/ADR-005-approval-doctrinal-review-plan.md`
- Setlist persistence and versioning plan in
  `docs/implementation-plans/ADR-016-setlist-persistence-and-versioning-plan.md`
- Rehearsal and workflow lifecycle plan in
  `docs/implementation-plans/ADR-024-rehearsal-and-workflow-lifecycle-plan.md`
- Recommendation explainability plan in
  `docs/implementation-plans/ADR-021-recommendation-engine-explainability-api-plan.md`

### Prompt

Design and implement the new-song introduction model. Track introduction state
for each church-instance song and arrangement, including not introduced,
introduced, learning, established, retired, and reintroduced states. Define how
introduction state is initialized from approval, first scheduled service, first
completed service, admin planning actions, and backfilled history. Implement
planned repetition windows with target dates or service windows, minimum and
maximum repeats, allowable skips, expiration rules, warning behavior, and
conflict handling with overuse policies. Add unfamiliar-song caps at the setlist,
section, role, and request-policy levels, and ensure the Recommendation Engine
can satisfy or warn on these caps deterministically.

### Acceptance criteria

- Church-instance introduction state is represented separately from global
  catalog approval and can be computed or explicitly planned with audit history.
- Planned repetition windows can be created, updated, satisfied, skipped,
  expired, retired, and explained with stable references and actor/provenance
  data.
- Unfamiliar-song caps can limit new or low-familiarity candidates per whole
  set, praise section, worship section, service moment, or request policy.
- Recommendation logic can apply a planned-repetition boost without violating
  maximum-repeat rules, approval gates, hard musical constraints, or
  unfamiliar-song caps.
- Conflicts between planned repetition and overuse policy are deterministic and
  produce warnings or penalties with machine-readable reason codes.
- Tests cover first introduction, planned second/third repetition, expired
  repetition windows, reintroduction after retirement, sparse history, and caps
  across default 10 praise + 5 worship structures.

### Restrictions

- Do not mark a song as congregation-familiar merely because it is globally
  popular or exists in the catalog.
- Do not let planned repetition force unapproved, unlicensed, hidden,
  out-of-instance, or hard-musical-conflict songs into recommendations.
- Do not allow LLM prompts to create or alter authoritative introduction state.
- Do not silently exceed unfamiliar-song caps; return deterministic warnings or
  no-solution outcomes according to the active policy.

## Subtask 6: Integrate familiarity into Recommendation Engine filtering, scoring, ordering, and tie-breaking

### Context

ADR-034 says familiarity contributes deterministic scoring, penalties, and
explanations while approval gates and hard musical constraints remain
authoritative. Familiarity must fit into existing Recommendation Engine stages
without relying on the LLM for song selection and without destabilizing
transition, energy, compatibility, doctrinal, theme, and musical scoring.

**Codebase anchors**

- Recommendation scoring architecture plan in
  `docs/implementation-plans/ADR-010-recommendation-engine-scoring-architecture-plan.md`
- Recommendation read-model plan in
  `docs/implementation-plans/ADR-002-recommendation-read-model-plan.md`
- Energy arc modeling plan in
  `docs/implementation-plans/ADR-032-energy-arc-modeling-plan.md`
- Musical transition analysis plan in
  `docs/implementation-plans/ADR-031-musical-transition-analysis-engine-plan.md`
- Arrangement compatibility plan in
  `docs/implementation-plans/ADR-033-arrangement-compatibility-and-instrumentation-modeling-plan.md`

### Prompt

Add familiarity to the Recommendation Engine as a deterministic scoring and
policy component. Define the stage order for approval/licensing/visibility/hard
constraints, request filtering, familiarity policy exclusions, familiarity
penalties and boosts, set-level unfamiliar caps, transition/energy/compatibility
balancing, and final tie-breaking. Implement candidate-level and set-level
score components for recency, rolling overuse, seasonal overuse, underuse,
established familiarity, planned repetition, new-song caps, arrangement-specific
usage, and sparse-history fallbacks. Ensure each score component records inputs,
weights, penalties, formula version, snapshot ids, and reason codes for ADR-021
explanations.

### Acceptance criteria

- Familiarity is evaluated after authoritative eligibility gates and before or
  within ranking according to a documented deterministic stage order.
- Approval, licensing, doctrinal review, active-catalog status, visibility,
  arrangement eligibility, hard musical constraints, and tenant isolation always
  take precedence over familiarity boosts or planned repetition.
- Candidate and set-level scoring can penalize recent overuse, rolling overuse,
  seasonal overuse, too many unfamiliar songs, and arrangement repetition while
  allowing configured boosts for underused established songs and planned
  introduction cadence.
- Ordering and tie-breaking are deterministic for identical request, catalog,
  policy, familiarity, and formula snapshots.
- No-solution and degraded-solution scenarios produce structured warnings rather
  than silently ignoring familiarity policy.
- Regression tests cover overused favorites, new-song caps, planned repetition,
  sparse history, arrangement-level repeats, policy variants, and interaction
  with energy/transition/compatibility scoring.

### Restrictions

- Do not let familiarity select songs independently of the Recommendation
  Engine candidate pipeline.
- Do not make familiarity a hard exclusion unless the active rotation policy
  explicitly defines the condition as a hard rule.
- Do not allow a familiarity boost to resurrect a candidate removed by approval,
  licensing, visibility, doctrinal, inactive-catalog, or hard musical filters.
- Do not use nondeterministic tie-breaking, unordered map iteration, random
  sampling without a recorded seed, or current wall-clock values during scoring.

## Subtask 7: Add ADR-021 explanations, reason codes, audit references, and privacy-safe observability

### Context

ADR-034 requires exposing familiarity scores, penalties, and reason codes
through explanations. Leaders need to understand why an overused song was
penalized, why a new song was repeated, or why no more unfamiliar songs were
selected. These details must be actionable but privacy-safe and scoped to the
authorized church instance.

**Codebase anchors**

- Recommendation explainability plan in
  `docs/implementation-plans/ADR-021-recommendation-engine-explainability-api-plan.md`
- Observability and telemetry strategy plan in
  `docs/implementation-plans/ADR-029-observability-and-telemetry-strategy-plan.md`
- Security roles and permissions plan in
  `docs/implementation-plans/ADR-019-security-roles-and-permissions-plan.md`
- Recommendation scoring plan in
  `docs/implementation-plans/ADR-010-recommendation-engine-scoring-architecture-plan.md`

### Prompt

Extend recommendation explanations and telemetry with familiarity details. Add
machine-readable explanation components for familiarity score, recency penalty,
frequency penalty, seasonal penalty, new-song state, planned repetition boost,
unfamiliar cap warning, sparse-history fallback, policy snapshot, formula
version, usage snapshot id, and source evidence references. Define redaction
rules for service history details, cross-instance data, actor information, and
integration source identifiers. Add metrics and logs for projection freshness,
policy usage, formula version, no-solution causes, overuse penalties,
planned-repetition matches, and unfamiliar-cap warnings.

### Acceptance criteria

- Recommendation explanations include familiarity score components, penalties,
  boosts, warnings, reason codes, formula version, policy snapshot id, usage
  snapshot id, and evidence references where authorized.
- Explanation payloads distinguish candidate-level reasons from set-level
  reasons and indicate whether each reason was a hard exclusion, penalty, boost,
  warning, or informational note.
- Privacy rules prevent unauthorized users or tenants from seeing cross-instance
  service history, usage event ids, source-system ids, actor identities, or
  sensitive correction details.
- Observability captures projection freshness, policy resolution, formula
  version distribution, cache behavior, no-solution causes, warning frequencies,
  and performance without high-cardinality tenant leakage.
- Documentation explains familiarity reason codes and gives examples for
  overuse, planned repetition, new-song caps, sparse history, and policy
  fallback.

### Restrictions

- Do not expose raw service history by default in public recommendation
  responses if a summarized score or authorized evidence reference is enough.
- Do not log or emit metrics with unbounded song titles, service names,
  integration source ids, actor names, or cross-instance identifiers.
- Do not represent missing familiarity data as a confident positive or negative
  signal; use explicit sparse-history or missing-data reason codes.
- Do not add prose-only explanations without machine-readable reason codes and
  evidence references.

## Subtask 8: Implement API, OpenAPI, admin, import, and service-history operational surfaces

### Context

Familiarity requires operational surfaces for viewing usage history, correcting
service-history facts, managing rotation policies, planning new-song
introductions, reviewing projections, and exposing recommendation explanation
fields. The project requires API contract changes to update the split OpenAPI
spec first before generated code or implementation changes are made.

**Codebase anchors**

- OpenAPI contract under `apps/api/src/main/openapi/`
- Admin review and catalog governance UI plan in
  `docs/implementation-plans/ADR-011-admin-review-catalog-governance-ui-plan.md`
- Administrative web interface ADR in
  `docs/adr/ADR-036-administrative-web-interface.md`
- Service plan integration model plan in
  `docs/implementation-plans/ADR-018-service-plan-integration-model-plan.md`
- Song acquisition and import connector plan in
  `docs/implementation-plans/ADR-008-song-acquisition-import-connector-architecture-plan.md`

### Prompt

Design and implement the user-facing and integration-facing operational surfaces
for familiarity. Start by updating the split OpenAPI contract in
`apps/api/src/main/openapi/`: add or modify path items in
`cadentia-api.paths.yaml`, reusable schemas/parameters/responses in
`cadentia-api.components.yaml`, and aggregate indexes/tags in
`cadentia-api.yaml`. Then run `mvn -pl apps/api -DskipTests generate-sources`
and implement generated API handlers, services, and tests. Include endpoints or
admin UI flows for usage-history review, correction, projection status, rotation
policy management, introduction-plan management, and recommendation explanation
fields. Define integration ingestion surfaces for service-plan systems only when
required, with idempotency keys and source provenance.

### Acceptance criteria

- Every API addition or response-shape change is specified in the OpenAPI files
  before generated code or handlers are changed, preserving the three-file split
  and expanded YAML style.
- `mvn -pl apps/api -DskipTests generate-sources` succeeds after OpenAPI
  changes, and generated interfaces/models remain in sync with implemented
  handlers.
- API/admin surfaces allow authorized users to inspect summarized usage history,
  correct usage facts, manage rotation policies, manage introduction plans, view
  projection freshness, and see familiarity explanation fields.
- Integration ingestion surfaces are idempotent, provenance-aware,
  authorization-safe, and do not make unapproved raw payloads recommendation
  inputs.
- OpenAPI schemas include stable ids, version fields, snapshot references,
  reason codes, warning severities, audit metadata, pagination/filtering where
  needed, and error responses for authorization, validation, conflicts, and
  stale versions.
- API and UI tests cover authorization, validation, stale policy updates,
  cross-instance isolation, idempotent ingestion, correction workflows, and
  explanation payload compatibility.

### Restrictions

- Do not implement or change API handlers before updating the OpenAPI contract
  first.
- Do not collapse the OpenAPI contract into a single file or use inline
  JSON-style objects where the project prefers expanded YAML style.
- Do not expose raw cross-instance usage history, integration payloads, or audit
  actor details to unauthorized users.
- Do not create admin-only operations that bypass service-layer validation,
  audit logging, version checks, or role authorization.

## Subtask 9: Deliver migrations, backfill, regression fixtures, testing, rollout controls, and documentation

### Context

Familiarity changes recommendation behavior and depends on historical service
data quality. Safe rollout requires migrations, optional backfill from existing
setlists/service plans, deterministic regression fixtures, performance checks,
feature flags, runbooks, and documentation for church admins and operators.

**Codebase anchors**

- Setlist persistence and versioning plan in
  `docs/implementation-plans/ADR-016-setlist-persistence-and-versioning-plan.md`
- Caching and performance strategy plan in
  `docs/implementation-plans/ADR-027-caching-and-performance-strategy-plan.md`
- Observability and telemetry strategy plan in
  `docs/implementation-plans/ADR-029-observability-and-telemetry-strategy-plan.md`
- Security roles and permissions plan in
  `docs/implementation-plans/ADR-019-security-roles-and-permissions-plan.md`
- Project runbooks under `docs/runbooks/`

### Prompt

Create the delivery package for ADR-034. Add database migrations, projection
jobs, backfill tools, fixture data, deterministic regression tests,
performance/load tests, security tests, feature flags, rollout/rollback steps,
operator runbooks, and admin documentation. Backfill existing service/setlist
history where available with provenance and dry-run reporting. Build regression
fixtures that pin expected familiarity scores, policy decisions, warnings,
explanations, and ordered recommendation outcomes across formula versions and
policy profiles.

### Acceptance criteria

- Migrations are reversible or have documented rollback/forward-fix behavior and
  preserve tenant isolation, audit history, and existing recommendation data.
- Backfill tools support dry run, scoped execution by church instance, progress
  reporting, idempotency, correction handling, provenance tagging, and safe
  resume after failure.
- Regression fixtures cover sparse history, normal history, overused songs,
  underused songs, seasonal usage, new-song introductions, planned repetition,
  unfamiliar-song caps, arrangement-level repeats, policy version changes, and
  formula version changes.
- Test coverage includes unit, integration, API contract, projection rebuild,
  authorization, cross-instance isolation, performance, cache invalidation,
  observability, and deterministic ordering checks.
- Rollout controls allow familiarity to run in shadow mode, explanation-only
  mode, limited-instance enablement, full scoring mode, and emergency disable
  without data loss.
- Documentation and runbooks explain policy configuration, usage correction,
  projection rebuilds, backfill operations, common warnings, no-solution
  troubleshooting, rollback, and support escalation.

### Restrictions

- Do not enable familiarity scoring globally before backfill quality,
  projection freshness, regression fixtures, and rollback controls are verified.
- Do not run destructive backfills without dry-run support, scoped execution,
  idempotency, provenance tagging, and audit logging.
- Do not ship formula changes without versioning, fixture updates, and release
  notes describing recommendation behavior impact.
- Do not rely solely on manual QA for deterministic scoring, tenant isolation,
  or policy enforcement.
