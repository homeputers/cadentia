# ADR-027 Implementation Plan: Caching and Performance Strategy

## Objective

Implement explicit, observable, and safely invalidated caching for Cadentia's
read-heavy recommendation, search, catalog, and governance workflows while
preserving approval, visibility, licensing, authorization, and deterministic
Recommendation Engine correctness guarantees.

## Source ADR

- [ADR-027: Caching and Performance Strategy](../adr/ADR-027-caching-and-performance-strategy.md)

## Status

Overall status: Planned.

- Subtask 1: Planned - cache domain inventory, safety classification, and
  latency targets.
- Subtask 2: Planned - cache key model, version tokens, and policy isolation.
- Subtask 3: Planned - cache provider architecture, bypass mode, and rebuild
  controls.
- Subtask 4: Planned - recommendation candidate-pool caching and eligibility
  invalidation.
- Subtask 5: Planned - search, catalog-read, tag-aggregation, and reference-data
  caching.
- Subtask 6: Planned - event-driven invalidation for approval, visibility,
  licensing, arrangement, and taxonomy changes.
- Subtask 7: Planned - cache observability, audit events, and operational
  runbook.
- Subtask 8: Planned - performance fixtures, stale-data safety tests, and
  rollout validation.

## Guiding Principles

- Cache entries must never weaken Cadentia's approval, instance visibility,
  licensing, active-status, or role/audience authorization gates.
- Recommendation selection remains deterministic and correct when caches are
  empty, disabled, stale, evicted, or rebuilt from source data.
- Eligibility-sensitive caches require conservative invalidation and versioned
  keys; TTL-only invalidation is insufficient for approval or visibility changes.
- Cache keys must be explicit and auditable rather than hidden behind ad hoc
  string concatenation.
- Cached data may accelerate read-heavy workflows, but canonical relational data
  remains the source of truth.
- Cache warmups and rebuilds should be deterministic, repeatable, and safe to
  run in local, test, staging, and production-like environments.

## Subtask 1: Inventory cache domains, classify data sensitivity, and define latency targets

### Context

ADR-027 requires explicit cacheable domains for recommendation candidate pools,
search results, read models, tag aggregations, and low-risk reference data. It
also calls out non-cacheable or short-lived domains for privileged review notes
and highly sensitive personnel details. Before implementation begins, Cadentia
needs a shared map of which workflows may be cached, which workflows must bypass
caches, and which latency thresholds the cache strategy is expected to meet.

**Codebase anchors**

- Recommendation read model plan in
  `docs/implementation-plans/ADR-002-recommendation-read-model-plan.md`
- Approval and doctrinal review plan in
  `docs/implementation-plans/ADR-005-approval-doctrinal-review-plan.md`
- Recommendation scoring plan in
  `docs/implementation-plans/ADR-010-recommendation-engine-scoring-architecture-plan.md`
- Security roles and permissions plan in
  `docs/implementation-plans/ADR-019-security-roles-and-permissions-plan.md`
- Search architecture plan in
  `docs/implementation-plans/ADR-026-search-architecture-and-discovery-plan.md`

### Prompt

Create the cache domain inventory and performance target document or module-level
configuration. Classify each read path as cacheable, short-lived only, or
non-cacheable. Include recommendation candidate pools, recommendation response
support data, search results, autocomplete suggestions, catalog read models, tag
aggregations, low-risk reference data, administrative dashboards, privileged
review notes, audit trails, personnel details, media metadata, and service-plan
summaries. Define initial p50 and p95 latency targets for recommendation
candidate retrieval, full recommendation generation, search, autocomplete,
catalog reads, tag aggregations, and administrative dashboard loads for each
supported catalog-size tier.

### Acceptance criteria

- A documented inventory lists each cache domain, owning workflow, source of
  truth, safety classification, default TTL expectation, invalidation triggers,
  and whether the domain may be warmed or rebuilt deterministically.
- Privileged review notes, sensitive personnel details, audit records, and any
  other high-risk data are marked non-cacheable or short-lived with explicit
  justification.
- Initial p50 and p95 latency targets are defined for recommendation, search,
  autocomplete, catalog reads, tag aggregations, and administrative dashboards.
- Catalog-size tiers are documented so performance tests can load fixtures that
  represent the supported scale.
- The inventory identifies which cache domains are instance-scoped,
  role/audience-scoped, policy-scoped, catalog-version-scoped, and
  request-shape-scoped.

### Restrictions

- Do not classify eligibility-sensitive data as low risk merely because it is
  derived from approved catalog records.
- Do not set latency targets without naming the catalog-size tier and workflow
  shape they apply to.
- Do not cache raw privileged review notes, raw private personnel details, or
  authorization decisions without a documented short TTL and invalidation path.
- Do not let this inventory redefine canonical data ownership; it must describe
  caching behavior only.

## Subtask 2: Implement cache key contracts, version tokens, and policy isolation

### Context

ADR-027 requires cache keys to include instance identifier, authorization or
audience, catalog version, policy version, scoring profile, request shape where
applicable, and approval-visible state. A central key contract prevents accidental
cross-instance leakage, stale policy reuse, and collisions between similar
requests. Version tokens must be easy to advance when catalog, approval,
visibility, licensing, taxonomy, arrangement, or scoring-policy changes occur.

**Codebase anchors**

- Security roles and permissions plan in
  `docs/implementation-plans/ADR-019-security-roles-and-permissions-plan.md`
- Packaged deployment and church customization plan in
  `docs/implementation-plans/ADR-022-packaged-deployment-and-church-customization-model-plan.md`
- Recommendation scoring plan in
  `docs/implementation-plans/ADR-010-recommendation-engine-scoring-architecture-plan.md`
- Search architecture plan in
  `docs/implementation-plans/ADR-026-search-architecture-and-discovery-plan.md`

### Prompt

Build a centralized cache key model used by all cacheable domains. Represent key
parts as typed fields instead of free-form strings, including cache domain,
instance identifier, package or catalog scope, role/audience, approval-visible
state, catalog version, policy version, scoring profile version, request-shape
hash, locale or presentation options when relevant, and schema version. Add
helpers for stable serialization, request normalization, key validation, and safe
logging that redacts sensitive request details while preserving debuggability.
Define how each version token is stored, read, incremented, and included in
cache metrics and invalidation events.

### Acceptance criteria

- All cacheable workflows use a shared typed cache key builder or equivalent
  policy-enforced abstraction.
- Keys include instance identifier, authorization/audience, catalog version,
  policy version, approval-visible state, and domain-specific dimensions such as
  scoring profile and normalized request shape.
- Request-shape hashing is deterministic across equivalent requests and changes
  when filters, scripture focus, theme hints, counts, key policy, tempo policy,
  pagination, or sorting options materially change the result.
- Tests prove that different instances, roles, approval-visible states, catalog
  versions, policy versions, scoring profiles, and request shapes cannot collide
  or reuse each other's cache entries.
- Safe logging exposes key metadata needed for troubleshooting without logging
  raw sensitive input text, privileged notes, or private personnel details.

### Restrictions

- Do not build cache keys through scattered string concatenation.
- Do not omit instance, authorization/audience, catalog version, or policy
  version from eligibility-sensitive domains.
- Do not store raw free-text prompts, privileged notes, personnel details, or
  unredacted request bodies in cache keys, metric labels, or logs.
- Do not rely on TTL alone to separate approval states or policy versions.

## Subtask 3: Establish cache provider architecture, bypass mode, and deterministic rebuild controls

### Context

ADR-027 asks the system to safely bypass or rebuild caches without changing
recommendation correctness. Cadentia needs a cache abstraction that can run
locally and in tests while allowing distributed or external cache providers where
needed. Bypass mode and rebuild operations must be explicit so operators can
recover from suspected stale data without disabling the application.

**Codebase anchors**

- API application code under `apps/api/src/main/java/com/cadentia/`
- Application configuration under `apps/api/src/main/resources/`
- Database migrations under `apps/api/src/main/resources/db/migration/`
- Observability strategy plan in
  `docs/implementation-plans/ADR-029-observability-and-telemetry-strategy-plan.md`
  when available; otherwise align with ADR-029 in `docs/adr/ADR-029-observability-and-telemetry-strategy.md`

### Prompt

Implement the cache provider abstraction, configuration, and operational controls.
Support at least a no-op provider for correctness tests, a local in-process
provider for development, and the selected production-ready provider for shared
cache domains when needed. Add per-domain TTL configuration, maximum entry size
limits, namespace/schema versioning, explicit bypass flags, cache eviction
operations, warmup jobs, and deterministic rebuild commands. Ensure cached data
is serialized with a versioned envelope that can be invalidated safely after
schema or policy changes.

### Acceptance criteria

- Cache providers can be swapped through configuration without changing business
  logic.
- No-op or bypass mode preserves functional correctness for recommendation,
  search, catalog, and governance reads.
- Per-domain TTLs, size limits, provider choice, warmup enablement, and rebuild
  behavior are configurable and documented.
- Warmup and rebuild jobs read from canonical source data and produce the same
  cacheable projections for the same source snapshot.
- Versioned cache envelopes prevent old serialized entries from being used after
  incompatible schema, key, or policy changes.
- Integration tests run successfully with caches disabled and with the local test
  cache provider enabled.

### Restrictions

- Do not make a cache provider the source of truth for catalog, approval,
  licensing, taxonomy, personnel, or recommendation data.
- Do not require external infrastructure for unit tests or local correctness
  checks.
- Do not allow bypass mode to skip authorization, approval, visibility,
  licensing, or active-status checks.
- Do not warm caches from stale search indexes or other derived stores when the
  canonical source data is available.

## Subtask 4: Cache recommendation candidate pools without caching unsafe selection state

### Context

Recommendation workflows are read-heavy and latency-sensitive, but ADR-027
requires recommendation eligibility to be recomputed or invalidated whenever
approval or visibility state changes. The LLM cannot select songs, and caching
must not turn prior recommendation responses into a stale source of eligibility.
Candidate-pool caching should accelerate deterministic Recommendation Engine
inputs while preserving scoring correctness and dataset-reference transparency.

**Codebase anchors**

- Recommendation read model plan in
  `docs/implementation-plans/ADR-002-recommendation-read-model-plan.md`
- Recommendation scoring plan in
  `docs/implementation-plans/ADR-010-recommendation-engine-scoring-architecture-plan.md`
- Recommendation explanation plan in
  `docs/implementation-plans/ADR-013-recommendation-explanation-system-plan.md`
- LLM intent extraction plans in
  `docs/implementation-plans/ADR-012-llm-intent-extraction-contract-plan.md` and
  `docs/implementation-plans/ADR-014-llm-intent-extraction-contract-plan.md`

### Prompt

Add caching for recommendation candidate-pool inputs and reusable read-model
fragments. Cache only eligibility-checked, instance-scoped, approval-visible,
active, licensed, and policy-versioned candidate data needed by the deterministic
Recommendation Engine. Include scoring profile, catalog version, policy version,
request shape, count policy, key policy, tempo policy, and theme/scripture filter
normalization in the cache key where they affect candidates. Keep final setlist
ordering, scoring, and explanation generation deterministic and safe when cache
entries are missing, evicted, bypassed, or rebuilt.

### Acceptance criteria

- Recommendation candidate-pool cache entries include only songs and
  arrangements that are approved, active, visible to the instance, authorized for
  the role/audience, and licensed for the request context.
- Candidate caches are invalidated or made unreachable after approval,
  rejection, activation, deactivation, visibility, licensing, arrangement,
  taxonomy, catalog-version, policy-version, or scoring-profile changes.
- Recommendation generation produces equivalent valid results with the cache
  enabled, disabled, cold, warm, evicted, and rebuilt from source data.
- Cached candidate records retain dataset reference identifiers required for
  recommendation explanations and auditability.
- Tests cover stale approval removal, cross-instance isolation, licensing change
  removal, policy-version changes, scoring-profile changes, and bypass-mode
  correctness.

### Restrictions

- Do not cache indefinite full recommendation responses as the primary strategy.
- Do not cache LLM-selected songs or any state that would allow the LLM intent
  agent to bypass the deterministic Recommendation Engine.
- Do not serve candidate data that has not passed the same eligibility gates used
  by uncached recommendation generation.
- Do not omit dataset references from cached candidate projections.

## Subtask 5: Cache search, catalog-read, tag-aggregation, and low-risk reference workflows

### Context

ADR-027 lists search results, read models, tag aggregations, and low-risk
reference data as cacheable domains. These domains differ in sensitivity:
user-facing search and catalog reads are eligibility-sensitive, while stable
reference vocabularies can usually use longer TTLs. Search and autocomplete
caches also need to align with ADR-026 so hidden records do not leak through
counts, suggestions, snippets, facets, or empty-state hints.

**Codebase anchors**

- Search architecture plan in
  `docs/implementation-plans/ADR-026-search-architecture-and-discovery-plan.md`
- Tag taxonomy plan in
  `docs/implementation-plans/ADR-007-tag-taxonomy-plan.md`
- Catalog governance UI plan in
  `docs/implementation-plans/ADR-011-admin-review-catalog-governance-ui-plan.md`
- Media and asset management plan in
  `docs/implementation-plans/ADR-025-media-and-asset-management-plan.md`

### Prompt

Implement domain-specific caches for approved search results, autocomplete
suggestions, catalog read models, tag aggregations, and low-risk reference data.
Apply shorter TTLs and event-driven invalidation to eligibility-sensitive search,
autocomplete, catalog, and tag views. Use longer TTLs only for low-risk reference
vocabularies that cannot expose restricted catalog or personnel data. Ensure
cached search responses and facets are scoped by instance, role/audience,
approval-visible state, catalog version, policy version, request filters,
pagination, sort order, and localization options where applicable.

### Acceptance criteria

- Search results, autocomplete suggestions, snippets, result counts, facets, and
  empty-state hints never expose inactive, unauthorized, unlicensed,
  cross-instance, private, or unapproved records from cache.
- Catalog read-model and tag-aggregation cache entries are invalidated after
  catalog, approval, visibility, licensing, arrangement, taxonomy, package, or
  instance-scope changes that affect the response.
- Low-risk reference data caches have documented TTLs, rebuild paths, and safety
  rationale.
- Pagination, filters, sorting, locale, role/audience, and instance scope are
  included in keys for cached user-facing read responses.
- Tests cover cache hits and misses for search, autocomplete, catalog reads, tag
  aggregations, reference data, empty results, pagination, and stale-data
  exclusion.

### Restrictions

- Do not cache unapproved search documents or private review material in
  user-facing result caches.
- Do not cache result counts, facets, autocomplete suggestions, or empty-state
  hints without the same eligibility scope as full result rows.
- Do not assign long TTLs to eligibility-sensitive search or catalog responses
  unless event-driven invalidation makes stale entries unreachable immediately.
- Do not let cached search results become Recommendation Engine ordering.

## Subtask 6: Integrate event-driven invalidation across approval, visibility, licensing, arrangement, and taxonomy changes

### Context

ADR-027 explicitly requires invalidating candidate and search caches on approval,
rejection, activation, deactivation, licensing, instance visibility,
arrangement, and taxonomy changes. TTL-only invalidation was rejected because
eligibility changes must take effect immediately. Invalidation must be reliable,
auditable, idempotent, and broad enough to remove derived stale entries when the
exact cache key is unknown.

**Codebase anchors**

- Eventing and async processing plan in
  `docs/implementation-plans/ADR-028-eventing-and-async-processing-architecture-plan.md`
  when available; otherwise align with ADR-028 in
  `docs/adr/ADR-028-eventing-and-async-processing-architecture.md`
- Approval and doctrinal review plan in
  `docs/implementation-plans/ADR-005-approval-doctrinal-review-plan.md`
- Arrangement transposition plan in
  `docs/implementation-plans/ADR-006-arrangement-transposition-plan.md`
- Tag taxonomy plan in
  `docs/implementation-plans/ADR-007-tag-taxonomy-plan.md`
- Security roles and permissions plan in
  `docs/implementation-plans/ADR-019-security-roles-and-permissions-plan.md`

### Prompt

Implement event-driven cache invalidation and version advancement for every
eligibility-affecting change. Publish or consume domain events for approval,
rejection, activation, deactivation, licensing, instance visibility, package
visibility, arrangement updates, taxonomy updates, catalog imports, scoring
policy changes, authorization policy changes, and cache schema changes. Map each
event to affected cache domains, version tokens, and targeted eviction patterns.
Make invalidation handlers idempotent, observable, retryable, and safe when
events arrive out of order.

### Acceptance criteria

- Eligibility-affecting events advance the appropriate catalog, policy,
approval-visible, taxonomy, arrangement, scoring, or authorization version token
  so stale entries are no longer reachable.
- Targeted evictions are performed where practical, and version-token advancement
  provides defense in depth when exact keys cannot be enumerated.
- Invalidation handlers are idempotent and safe for duplicate, delayed, or
  out-of-order events.
- Audit events record the source event, affected cache domains, instance scope,
  version changes, eviction counts when available, result status, and correlation
  identifier.
- Integration tests prove that approval, rejection, activation, deactivation,
  licensing, visibility, arrangement, and taxonomy changes remove stale
  recommendation candidate, search, catalog, and tag cache entries before the
  next user-visible read.

### Restrictions

- Do not rely solely on fixed TTL expiration for approval, visibility,
  licensing, active-status, arrangement, or taxonomy changes.
- Do not silently ignore invalidation failures; failures must be retried,
  surfaced through metrics, and recorded for operational follow-up.
- Do not let invalidation handlers make recommendation choices or mutate
  canonical catalog state beyond version-token and cache-maintenance records.
- Do not expose stale entries during rebuilds or after failed invalidation; prefer
  bypassing or broad invalidation when safety is uncertain.

## Subtask 7: Add cache observability, audit events, and operational documentation

### Context

ADR-027 requires cache hit/miss metrics and invalidation audit events. Operators
need enough visibility to diagnose low hit rates, stale-data concerns, hot keys,
provider failures, latency regressions, and rebuild status. Observability must be
useful without logging raw sensitive request content or leaking private catalog,
review, or personnel details.

**Codebase anchors**

- Observability and telemetry ADR in
  `docs/adr/ADR-029-observability-and-telemetry-strategy.md`
- Existing runbooks under `docs/runbooks/`
- Security roles and permissions plan in
  `docs/implementation-plans/ADR-019-security-roles-and-permissions-plan.md`

### Prompt

Instrument all cache domains with metrics, structured logs, traces, and audit
events. Track hit rate, miss rate, bypass count, eviction count, invalidation
latency, warmup duration, rebuild duration, serialization failures, provider
errors, stale-entry prevention events, entry size, and per-domain latency impact.
Create an operations runbook that explains cache domains, TTLs, bypass mode,
manual invalidation, rebuild procedures, warmup procedures, incident response for
suspected stale eligibility, dashboards, alerts, and safe rollback steps.

### Acceptance criteria

- Metrics are emitted per cache domain, provider, instance scope where safe,
  result type, and operation outcome without unbounded high-cardinality labels.
- Invalidation audit events include enough information to trace why an entry or
  domain was invalidated and which source event caused it.
- Dashboards or documented metric queries show hit/miss trends, latency impact,
  invalidation failures, warmup status, rebuild status, and provider health.
- Alerts or documented alert thresholds cover invalidation failures, provider
  unavailability, serialization failures, extreme miss rates, rebuild failures,
  and latency target breaches.
- The runbook documents normal operations, emergency cache bypass, broad
  invalidation, deterministic rebuilds, warmups, verification steps, and rollback
  behavior.

### Restrictions

- Do not log raw request prompts, privileged review notes, private personnel
  details, raw lyrics, or unredacted authorization context in metrics, logs,
  traces, or audit events.
- Do not use high-cardinality metric labels such as full cache keys, song titles,
  user identifiers, raw query text, or free-text scripture/theme prompts.
- Do not rely on dashboards alone for correctness; stale-data safety must be
  covered by tests and invalidation design.
- Do not make manual invalidation require direct modification of canonical
  catalog tables.

## Subtask 8: Build performance fixtures, stale-data safety tests, and rollout validation

### Context

ADR-027 acceptance criteria require recommendation latency within target
thresholds, immediate removal of stale eligibility, cross-instance safety,
observability, and safe cache bypass or rebuild behavior. These guarantees need
automated coverage and rollout validation across cold-cache, warm-cache,
bypass-mode, invalidation, and rebuild scenarios.

**Codebase anchors**

- API tests under `apps/api/src/test/`
- Database fixtures under `apps/api/src/test/resources/`
- Recommendation, search, approval, licensing, visibility, taxonomy, and
  arrangement plans referenced in earlier subtasks
- Operations runbooks under `docs/runbooks/`

### Prompt

Create automated performance and correctness validation for the caching strategy.
Add representative catalog fixtures for supported catalog-size tiers, including
multiple instances, approval states, licensing states, package visibility states,
arrangement variants, taxonomy updates, and role/audience differences. Measure
cold-cache, warm-cache, bypass-mode, invalidation, rebuild, and provider-failure
scenarios. Gate rollout with tests that prove stale approval, visibility,
licensing, arrangement, and taxonomy data cannot be served from cache.

### Acceptance criteria

- Performance tests or benchmark scripts measure p50 and p95 latency for
  recommendation generation, candidate retrieval, search, autocomplete, catalog
  reads, tag aggregations, and administrative dashboards against the targets
  defined in Subtask 1.
- Correctness tests prove cached responses cannot expose another instance's
  data, unapproved songs, inactive songs, unauthorized data, unlicensed assets or
  arrangements, stale taxonomy labels, or stale arrangement metadata.
- Tests cover cold cache, warm cache, cache disabled, cache provider unavailable,
  explicit bypass, targeted invalidation, broad invalidation, deterministic
  rebuild, and schema-version mismatch scenarios.
- Rollout documentation defines feature flags, default provider configuration,
  staged enablement order, rollback steps, monitoring checks, and acceptance
  thresholds for production readiness.
- CI or documented release checks prevent merging cache changes that break
  correctness without requiring production-only infrastructure.

### Restrictions

- Do not make correctness tests depend on nondeterministic timing or waiting for
  long TTL expiration.
- Do not require production cache infrastructure for normal CI validation.
- Do not use synthetic fixtures that omit instance scope, approval state,
  licensing state, or authorization differences.
- Do not accept performance improvements that depend on serving stale or
  under-scoped cached data.
