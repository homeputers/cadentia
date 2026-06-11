# ADR-026 Implementation Plan: Search Architecture and Discovery

## Objective

Implement safe, explainable catalog search and discovery so worship leaders can
find approved songs and arrangements by title, alternate title, scripture, tags,
contributors, lyrics metadata, and musical features while preserving Cadentia's
approval gates and the deterministic Recommendation Engine boundary.

## Source ADR

- [ADR-026: Search Architecture and Discovery](../adr/ADR-026-search-architecture-and-discovery.md)

## Status

Overall status: Planned.

- Subtask 1: Planned - search domain model, backend selection, and index
  boundaries.
- Subtask 2: Planned - approved lexical indexes, fuzzy search, autocomplete, and
  rebuild jobs.
- Subtask 3: Planned - visibility, approval, licensing, and tenant-safety gates.
- Subtask 4: Planned - ranking strategy and explanation metadata.
- Subtask 5: Planned - semantic discovery pipeline using approved metadata only.
- Subtask 6: Planned - query API contracts, validation, and client-facing result
  models.
- Subtask 7: Planned - catalog event integration, traceable refresh, and rebuild
  operations.
- Subtask 8: Planned - performance, observability, relevance fixtures, and
  operational documentation.

## Guiding Principles

- Search returns discoverable catalog candidates; it must not construct setlists
  or replace deterministic Recommendation Engine selection.
- Every returned result must pass instance visibility, approval status, active
  status, authorization, and licensing gates before any result details are
  exposed.
- Search indexes must be rebuildable from canonical catalog data and must not
  become an independent source of truth.
- Semantic discovery is optional recall/ranking support and may use only
  approved, visible metadata prepared for indexing; it must not index
  unapproved lyrics or private data.
- Ranking explanations should expose major factors in structured form without
  leaking private, unauthorized, inactive, or unapproved data.
- Operational behavior must be deterministic enough to test, even when semantic
  scores are present as one bounded ranking signal.

## Subtask 1: Define search architecture, backend choice, and index boundaries

### Context

ADR-026 leaves the initial search backend as an open question but requires fuzzy
matching, autocomplete, scripture discovery, scalable ranking, approved-search
indexes, traceable updates, and rebuildability. Cadentia already has canonical
catalog, approval, tag, lyrics, arrangement, contributor, package, and instance
concepts from earlier ADRs. This subtask establishes the search architecture
before query behavior or semantic discovery is implemented.

**Codebase anchors**

- Catalog domain and repositories under `apps/api/src/main/java/com/cadentia/`
- Database migrations under `apps/api/src/main/resources/db/migration/`
- OpenAPI contract under `apps/api/src/main/openapi/`
- Song data infrastructure plan in
  `docs/implementation-plans/ADR-001-song-data-infrastructure-plan.md`
- Approval and doctrinal review plan in
  `docs/implementation-plans/ADR-005-approval-doctrinal-review-plan.md`
- Recommendation read model plan in
  `docs/implementation-plans/ADR-002-recommendation-read-model-plan.md`

### Prompt

Design the search subsystem architecture, choose the initial backend, and define
the index ownership boundaries. Specify whether the first implementation uses
PostgreSQL full-text/trigram capabilities, an embedded search library, or an
external search service, and document the migration path if the backend changes
later. Define canonical-to-index projection models for songs, arrangements,
scripture references, tags, contributors, lyrics metadata, musical features, and
seed-package data. Separate user-facing approved discovery indexes from any
future privileged admin-review indexes.

### Acceptance criteria

- An architecture decision note or design section identifies the initial search
  backend, the reasons for choosing it, operational assumptions, and migration
  seams for a later backend.
- Search projection models cover title, alternate title, scripture references,
  approved tags, contributors, key, BPM, meter, arrangement identifiers,
  arrangement labels, lyrics-derived metadata that is approved for indexing, and
  package/source references where safe to expose.
- Index ownership is clearly defined: canonical catalog tables remain the source
  of truth, search indexes are derived artifacts, and rebuild procedures can
  discard and regenerate all index state.
- Approved user-facing indexes are explicitly separated from any future
  unapproved/admin-review index path through table, schema, index alias,
  collection, or filter-boundary design.
- The design documents expected catalog-size boundaries, target refresh latency,
  target query latency, and any backend-specific limits that require future
  re-evaluation.
- Tests or validation scripts prove the search backend can be initialized in the
  local/test environment without making recommendation results depend on search
  output.

### Restrictions

- Do not let the search backend become the canonical catalog store.
- Do not mix unapproved/admin-review documents into the same user-facing index
  unless a mandatory, centrally enforced approval and authorization filter is
  applied before ranking and response rendering.
- Do not choose a backend that cannot be rebuilt from canonical data or cannot be
  exercised in automated tests.
- Do not introduce search result ordering as recommendation setlist ordering.

## Subtask 2: Build approved lexical indexes, fuzzy matching, and autocomplete

### Context

ADR-026 requires discovery by title, alternate title, scripture, tags,
contributor, key, BPM, and arrangement, along with fuzzy matching and
autocomplete for approved visible catalog data. Lexical search should be the
safe baseline because it is explainable, deterministic, and easy to audit. This
subtask creates the first user-facing search indexes and query logic without
semantic embeddings.

**Codebase anchors**

- Catalog song and arrangement persistence under `apps/api/src/main/java/com/cadentia/`
- Tag taxonomy plan in
  `docs/implementation-plans/ADR-007-tag-taxonomy-plan.md`
- Lyrics storage and parsing plans in
  `docs/implementation-plans/ADR-004-lyrics-storage-format-plan.md` and
  `docs/implementation-plans/ADR-009-lyrics-parsing-musical-analysis-pipeline-plan.md`
- Database migrations under `apps/api/src/main/resources/db/migration/`

### Prompt

Implement the approved lexical search indexes and query services. Index
normalized search tokens for titles, alternate titles, approved scripture
references, tag codes and labels, contributor names, musical keys, BPM ranges,
arrangement metadata, and approved lyrics metadata. Add fuzzy matching and
prefix/autocomplete support for titles, alternate titles, tags, scripture book
names, contributors, and arrangement labels. Normalize punctuation, whitespace,
case, diacritics, and common scripture abbreviations consistently at index and
query time.

### Acceptance criteria

- Approved catalog songs and arrangements are projected into lexical search
  indexes only after they are active, approved, visible to the instance, and safe
  for the user's licensing context.
- Queries can search by title, alternate title, scripture reference, tag,
  contributor, key, BPM range, and arrangement metadata using stable query
  fields and normalized free-text terms.
- Fuzzy title and alternate-title matching handles common typos without matching
  unrelated songs at high rank.
- Autocomplete returns only approved, active, authorized, visible suggestions and
  includes enough typed context for clients to show whether a suggestion is a
  title, tag, scripture reference, contributor, or arrangement.
- Scripture search supports common book abbreviations, chapter-only references,
  verse ranges, and proximity-ready normalized reference data.
- Unit and integration tests cover normalization, fuzzy thresholds,
  autocomplete, scripture parsing, musical filters, empty-result handling, and
  exclusion of unapproved or inactive records.

### Restrictions

- Do not index raw unapproved lyrics or raw private notes into user-facing search
  documents.
- Do not expose inactive, unauthorized, instance-private, or unlicensed records
  through autocomplete suggestions, result counts, typo corrections, or empty
  state hints.
- Do not implement fuzzy matching with an unbounded broad match that overwhelms
  exact title, scripture, or tag matches.
- Do not require the LLM intent agent to parse or rewrite search queries for the
  baseline lexical search path.

## Subtask 3: Enforce approval, visibility, authorization, active-status, and licensing gates

### Context

The central safety requirement in ADR-026 is that semantic search and lexical
search never return unapproved, inactive, unauthorized, or instance-private data.
Filtering after result rendering is too late because counts, snippets,
autocomplete terms, and ranking explanations can leak hidden records. This
subtask creates centralized gates that apply before response rendering and, when
possible, before ranking.

**Codebase anchors**

- Approval and doctrinal review implementation from ADR-005
- Security roles and permissions plan in
  `docs/implementation-plans/ADR-019-security-roles-and-permissions-plan.md`
- Packaged deployment and church customization plan in
  `docs/implementation-plans/ADR-022-packaged-deployment-and-church-customization-model-plan.md`
- Media/licensing-related constraints from
  `docs/implementation-plans/ADR-025-media-and-asset-management-plan.md`

### Prompt

Implement a centralized search eligibility gate used by all search, autocomplete,
semantic recall, result hydration, and explanation paths. The gate must evaluate
instance scope, package visibility, approval state, active status, licensing
permission, role-based authorization, and any catalog governance restrictions
before a candidate can be returned or counted. Ensure index projections either
exclude ineligible documents or carry only the minimum safe fields needed for
pre-ranking filtering, with defense-in-depth checks during result hydration.

### Acceptance criteria

- A single reusable eligibility component or policy service is invoked by lexical
  search, autocomplete, semantic search, result hydration, and explanation
  builders.
- Ineligible documents are removed before client-visible counts, facets,
  suggestions, snippets, and explanations are produced.
- Approval-state transitions, active/inactive changes, licensing changes,
  package visibility changes, and instance-visibility changes update or
  invalidate search documents within the documented refresh target.
- Tests prove that unapproved, inactive, unauthorized, unlicensed, and
  cross-instance records cannot appear through search results, suggestions,
  facets, counts, spelling corrections, semantic neighbors, or ranking-factor
  explanations.
- Administrative search for unapproved content, if present, uses a separate
  policy and separate index or strongly isolated query path with explicit role
  checks and audit logging.
- Error responses and empty-result responses do not reveal that hidden documents
  exist.

### Restrictions

- Do not rely only on UI filtering or client-side hiding for search security.
- Do not expose ranking scores, snippets, matched terms, or aggregate counts for
  records that fail eligibility checks.
- Do not allow semantic embeddings generated from now-ineligible metadata to keep
  a record discoverable after approval or visibility is revoked.
- Do not let package-level seed content bypass instance-local approval and
  licensing rules.

## Subtask 4: Implement explainable ranking and result diagnostics

### Context

ADR-026 requires ranking based on lexical match, curated tag match, scripture
proximity, popularity/familiarity where church policy allows, and semantic
similarity. It also requires explanations that expose major ranking factors
without leaking private or unapproved data. Ranking should make discovery useful
while remaining clearly separate from Recommendation Engine scoring.

**Codebase anchors**

- Recommendation scoring plan in
  `docs/implementation-plans/ADR-010-recommendation-engine-scoring-architecture-plan.md`
- Recommendation explanation plans in
  `docs/implementation-plans/ADR-013-recommendation-explanation-system-plan.md` and
  `docs/implementation-plans/ADR-021-recommendation-engine-explainability-api-plan.md`
- Feedback and familiarity plan in
  `docs/implementation-plans/ADR-017-user-feedback-and-recommendation-tuning-plan.md`
- Congregational familiarity ADR in
  `docs/adr/ADR-034-congregational-familiarity-model.md`

### Prompt

Build the search ranking pipeline and structured ranking-factor metadata. Combine
bounded signals for exact lexical matches, fuzzy lexical matches, curated tag
matches, scripture proximity, contributor matches, musical-feature filter
matches, arrangement matches, popularity/familiarity signals allowed by church
policy, recency or package preference where configured, and semantic similarity
when available. Return machine-readable ranking factors for each result and
optional aggregate diagnostics for debugging authorized support workflows.

### Acceptance criteria

- Ranking weights and tie-breakers are explicit, versioned, configurable where
  appropriate, and covered by deterministic tests.
- Exact title, approved alternate title, scripture, and curated tag matches can
  outrank weaker fuzzy or semantic matches according to documented weights.
- Popularity/familiarity is used only when enabled by church policy and only from
  approved, privacy-safe aggregate data.
- Ranking explanations identify major included factors such as `exactTitleMatch`,
  `fuzzyTitleMatch`, `scriptureProximity`, `curatedTagMatch`,
  `contributorMatch`, `musicalFeatureMatch`, `semanticSimilarity`, and
  `familiaritySignal` without exposing hidden candidate data.
- Support/debug diagnostics are protected by role-based authorization and avoid
  logging sensitive free-text queries or private catalog details.
- Tests cover ranking order, tie-breakers, policy-disabled familiarity,
  explanation redaction, semantic-score absence, and consistency across index
  rebuilds.

### Restrictions

- Do not reuse Recommendation Engine scores as search ranking scores or expose
  search ranking as a deterministic setlist-selection explanation.
- Do not let opaque semantic similarity dominate exact approved catalog matches
  without bounded weighting and visible explanation factors.
- Do not log raw sensitive query text, private notes, or unapproved metadata in
  telemetry labels, ranking explanations, or support diagnostics.
- Do not claim ranking explanations are doctrinal or musical suitability
  recommendations.

## Subtask 5: Add semantic discovery using approved metadata only

### Context

ADR-026 allows semantic discovery but explicitly forbids it from bypassing
approval gates or becoming the Recommendation Engine. Embeddings may improve
recall for theme-like searches, but they add governance requirements around
source metadata, model/version tracking, refresh, deletion, and ranking bounds.
This subtask should be implemented after lexical search and eligibility gates
exist.

**Codebase anchors**

- LLM intent extraction contracts in
  `docs/implementation-plans/ADR-012-llm-intent-extraction-contract-plan.md` and
  `docs/implementation-plans/ADR-014-llm-intent-extraction-contract-plan.md`
- Tag taxonomy and lyrics metadata plans in
  `docs/implementation-plans/ADR-007-tag-taxonomy-plan.md` and
  `docs/implementation-plans/ADR-009-lyrics-parsing-musical-analysis-pipeline-plan.md`
- Search eligibility gate from Subtask 3
- Ranking pipeline from Subtask 4

### Prompt

Implement optional semantic discovery as a recall and ranking supplement. Define
an approved metadata document for embedding generation that includes only safe
approved fields such as title, approved alternate titles, curated tags,
approved scripture references, approved theme metadata, contributors where
public, and approved lyrics-derived summaries or features. Store embedding
model, version, source document hash, generation timestamp, approval state, and
index projection version. Add query embedding support for eligible search
requests and combine semantic candidates with lexical candidates through the
bounded ranking pipeline.

### Acceptance criteria

- Embeddings are generated only from approved, active, visible metadata that is
  explicitly allowed for semantic indexing.
- Embedding records store model/provider identifier, model version, source hash,
  generation timestamp, projection version, owning instance/package scope, and
  approval/visibility eligibility metadata needed for invalidation.
- Approval revocation, active-status changes, visibility changes, licensing
  changes, and source metadata edits invalidate or refresh affected embeddings
  within the documented refresh target.
- Semantic candidates are passed through the same eligibility gate as lexical
  candidates before counts, ranking, explanation, or response hydration.
- Semantic similarity is a bounded ranking factor and can be disabled globally,
  per environment, or per church instance without breaking lexical search.
- Tests cover embedding-source sanitization, model-version tracking, invalidation
  on approval changes, disabled semantic mode, semantic-plus-lexical merge
  behavior, and exclusion of hidden records.

### Restrictions

- Do not embed unapproved lyrics, private notes, rehearsal notes, user feedback
  free text, or unauthorized instance-private metadata into user-facing semantic
  indexes.
- Do not let semantic search select songs for a setlist or satisfy
  Recommendation Engine constraints.
- Do not call external embedding providers without respecting configured data
  residency, privacy, retry, timeout, and failure-mode policies.
- Do not allow stale embeddings for revoked or hidden records to remain
  discoverable.

## Subtask 6: Define and implement search API contracts and client-facing models

### Context

Search clients need stable contracts for full search, autocomplete, filters,
facets, ranking explanations, pagination, and result hydration. ADR-026 requires
support for multiple query dimensions and explainability. The API should also
make it clear that search results are discovery candidates, not generated
setlists.

**Codebase anchors**

- OpenAPI files under `apps/api/src/main/openapi/`
- API controllers under `apps/api/src/main/java/com/cadentia/api/controller/`
- API contract guidance in repository `AGENTS.md`
- Existing generated interfaces/models under the API module

### Prompt

Add versioned OpenAPI contracts and API implementation for catalog search and
autocomplete. Define request schemas for free-text query, structured filters,
scripture filters, tag filters, contributor filters, musical filters, semantic
mode, pagination, sorting, facets, and explanation controls. Define response
schemas for result summaries, result type, matched fields, ranking factors,
facets, pagination cursors, autocomplete suggestions, and safe empty states.
Generate API sources and implement controllers/services that use the lexical,
eligibility, ranking, and semantic components.

### Acceptance criteria

- OpenAPI paths, schemas, parameters, and shared responses are added to the
  split OpenAPI contract using `cadentia-api.yaml`,
  `cadentia-api.paths.yaml`, and `cadentia-api.components.yaml` according to the
  repository guidance.
- Search endpoints support paginated catalog search, autocomplete, optional
  facets, optional ranking explanations, and structured filters for scripture,
  tags, contributors, keys, BPM ranges, arrangements, and semantic mode.
- Response models distinguish song results, arrangement results, scripture/tag
  suggestions, contributor suggestions, and other supported result categories
  without exposing internal entity shapes directly.
- Payload validation rejects invalid filters, unsupported semantic-mode values,
  excessive page sizes, malformed scripture references, invalid BPM ranges, and
  unauthorized explanation/diagnostic requests.
- API tests cover successful searches, autocomplete, filters, pagination,
  facets, explanation flags, validation failures, authorization failures, and
  safe empty responses.
- After OpenAPI changes, `mvn -pl apps/api -DskipTests generate-sources` passes
  and generated interfaces/models are committed if project conventions require
  generated sources in version control.

### Restrictions

- Do not collapse the split OpenAPI contract into a single file.
- Do not expose database entities, embedding vectors, raw internal score arrays,
  private metadata, or unapproved snippets in public responses.
- Do not add API parameters that allow callers to bypass eligibility gates or
  force inclusion of inactive/unapproved records.
- Do not represent search results as recommendation results or setlist items.

## Subtask 7: Integrate catalog events, traceable refresh, rebuild, and rollback operations

### Context

ADR-026 requires index updates traceable to catalog changes and approval events,
and it requires indexes to be rebuildable from canonical catalog data. Catalog
changes can originate from imports, approval review, tag edits, lyrics metadata
updates, arrangement edits, package installation, licensing changes, and
instance-visibility changes. Search must remain safe during partial failures and
rebuilds.

**Codebase anchors**

- Eventing and async processing plan in
  `docs/implementation-plans/ADR-028-eventing-and-async-processing-architecture-plan.md`
- Import and deduplication plans in
  `docs/implementation-plans/ADR-003-song-import-deduplication-plan.md` and
  `docs/implementation-plans/ADR-008-song-acquisition-import-connector-architecture-plan.md`
- Approval workflow plan in
  `docs/implementation-plans/ADR-005-approval-doctrinal-review-plan.md`
- Package governance plan in
  `docs/implementation-plans/ADR-022-packaged-deployment-and-church-customization-model-plan.md`

### Prompt

Implement event-driven and batch-driven index maintenance. Subscribe to catalog,
approval, arrangement, tag, lyrics metadata, import, package, licensing, and
visibility events that affect search projections. Persist trace records for each
indexing action, including source event id, canonical entity id, projection
version, index target, operation, status, timestamps, and error details. Provide
administrative rebuild, backfill, retry, and rollback commands or endpoints that
regenerate indexes from canonical data while maintaining safe search behavior.

### Acceptance criteria

- Index updates are triggered by all canonical changes that affect indexed
  fields, eligibility, ranking, explanations, or embeddings.
- Each index update has an audit/trace record linking it to the source event or
  rebuild job and the canonical entity version used for projection.
- Full rebuild can recreate lexical indexes, autocomplete indexes, ranking
  support data, and semantic embeddings or semantic source queues from canonical
  data without manual edits.
- Failed indexing work is retried with bounded backoff and visible operational
  status, while stale or uncertain documents fail closed when eligibility cannot
  be verified.
- Rebuild and backfill workflows can run instance-by-instance or package-by-
  package to avoid broad outages.
- Tests cover event projection, idempotency, retries, rebuild from scratch,
  deletion/inactivation, approval revocation, visibility changes, and safe
  behavior during partial index outage.

### Restrictions

- Do not require manual index edits to repair normal catalog, approval, or
  visibility changes.
- Do not leave documents searchable when projection fails after an approval
  revocation or visibility removal.
- Do not make indexing jobs mutate canonical catalog approval, lyrics,
  arrangement, or recommendation records.
- Do not emit sensitive indexed content in job names, metrics labels, trace
  summaries, or error messages.

## Subtask 8: Add performance targets, observability, relevance fixtures, and operations documentation

### Context

ADR-026 requires defined scalability boundaries for catalog size, index refresh
latency, and query latency. It also requires explainable relevance and safe
operation at large catalog sizes. Long-term maintainability depends on fixtures,
benchmarks, alerting, runbooks, and regression tests that cover both relevance
and leakage prevention.

**Codebase anchors**

- Observability and telemetry ADR in
  `docs/adr/ADR-029-observability-and-telemetry-strategy.md`
- Caching and performance ADR in
  `docs/adr/ADR-027-caching-and-performance-strategy.md`
- Existing runbooks under `docs/runbooks/`
- Test fixtures under the API module and seed-data documentation in
  `docs/seed-data.md`

### Prompt

Define search service-level targets, add observability, and create relevance and
safety test fixtures. Document expected catalog-size tiers, p50/p95 query
latency, p50/p95 autocomplete latency, index refresh latency after approval or
visibility changes, semantic embedding generation latency, rebuild duration
expectations, and degradation behavior. Add metrics, traces, structured logs,
alerts, fixture datasets, benchmark tests, and an operations runbook for search
index maintenance and troubleshooting.

### Acceptance criteria

- Documentation states initial and target scalability boundaries for catalog
  size, query latency, autocomplete latency, index refresh latency, semantic
  generation latency, and full rebuild duration.
- Metrics cover query latency, autocomplete latency, result counts, zero-result
  rate, eligibility-filtered candidate count, indexing lag, indexing failures,
  rebuild progress, semantic queue lag, embedding failures, and backend health.
- Logs and traces include safe correlation ids, query class, result category,
  index version, and timing data without storing raw sensitive query text or
  private metadata.
- Relevance fixtures cover exact title search, fuzzy title search, scripture
  search, tag search, contributor search, musical filters, autocomplete,
  semantic discovery if enabled, and deterministic ranking regressions.
- Leakage-prevention fixtures verify that unapproved, inactive, unauthorized,
  unlicensed, and cross-instance records remain hidden across results,
  suggestions, facets, counts, explanations, telemetry, and diagnostics.
- A search operations runbook explains rebuild, retry, rollback, semantic mode
  disablement, backend outage handling, relevance regression triage, leakage
  incident response, and post-deploy validation.

### Restrictions

- Do not put raw query text, private catalog notes, unapproved metadata, lyrics
  excerpts, or embedding vectors into telemetry labels or alert dimensions.
- Do not rely only on synthetic happy-path data for relevance and safety tests.
- Do not define latency targets without a repeatable benchmark or smoke-test
  command that can be run in CI or pre-release validation.
- Do not permit search backend outages to degrade into unsafe broad catalog
  disclosure; fail closed or return safe degraded empty responses when gates
  cannot be evaluated.

## Cross-cutting validation checklist

- Search results, suggestions, facets, counts, explanations, semantic
  candidates, and diagnostics all pass the same eligibility gate.
- Search indexes are derived artifacts and can be rebuilt from canonical catalog
  data.
- Recommendation Engine ownership remains unchanged; search never constructs or
  orders a worship setlist.
- Semantic discovery uses approved metadata only and can be disabled without
  breaking lexical search.
- Ranking factors are structured, bounded, testable, and safe to expose to the
  requesting user.
- OpenAPI changes preserve the split contract layout and generated API sources
  remain in sync.
