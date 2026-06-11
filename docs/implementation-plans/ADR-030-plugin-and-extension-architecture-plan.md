# ADR-030 Implementation Plan: Plugin and Extension Architecture

## Objective

Implement a constrained, versioned, auditable plugin architecture that lets
Cadentia support import connectors, scoring policy contributions, export
formats, church-specific constraints, metadata transforms, and future
integrations without letting extensions bypass approval, licensing, instance
visibility, role authorization, deterministic recommendation guarantees, or core
catalog governance.

## Source ADR

- [ADR-030: Plugin and Extension Architecture](../adr/ADR-030-plugin-and-extension-architecture.md)

## Status

Overall status: Planned.

- Subtask 1: Planned - extension point inventory, trust tiers, and first SPI
  release scope.
- Subtask 2: Planned - plugin registry, package metadata, configuration, and
  lifecycle persistence.
- Subtask 3: Planned - versioned SPI contracts, DTO validation, compatibility,
  and contract fixtures.
- Subtask 4: Planned - instance, environment, role, licensing, and policy
  enforcement around plugin registration and execution.
- Subtask 5: Planned - plugin execution runtime, isolation model, failure
  policies, timeout controls, and deterministic execution safeguards.
- Subtask 6: Planned - import connector and metadata transform extension
  points.
- Subtask 7: Planned - recommendation constraint and scoring contribution
  extension points.
- Subtask 8: Planned - export format and outbound integration extension points.
- Subtask 9: Planned - administrative operations, audit logging,
  observability, certification, and rollout validation.

## Guiding Principles

- Plugins may contribute data, constraints, transformations, scores, exports, or
  integration side effects only through approved extension contracts; core
  Cadentia remains responsible for final eligibility, approval gates, instance
  isolation, deterministic ordering, and audit authority.
- Plugin outputs are untrusted until validated, normalized, policy-filtered, and
  reconciled with canonical Cadentia state.
- Recommendation-path plugins must be deterministic for the same versioned
  inputs, configuration, policy snapshot, and catalog snapshot.
- Plugins must not receive direct database access, raw credentials, unauthorized
  instance data, privileged review notes, or raw copyrighted content unless a
  specific extension contract permits a minimal scoped payload and records the
  reason.
- The first SPI release should favor narrow, testable extension points over a
  broad general-purpose plugin API.
- Failures must be isolated according to explicit policy: fail closed for
  security, approval, licensing, and eligibility checks; degrade gracefully where
  optional plugin output is not required.
- Every privileged, catalog-mutating, or recommendation-influencing plugin
  action must be auditable and observable without leaking sensitive data.

## Subtask 1: Define extension point inventory, trust tiers, and first SPI release scope

### Context

ADR-030 accepts plugin support for import connectors, scoring policy
contributions, export formats, church-specific constraints, metadata transforms,
and future integrations. It leaves open whether plugins run in-process,
out-of-process, or both by trust tier, which certification process is required,
and which extension points are stable enough for the first SPI release. Cadentia
also has existing ADR plans for import connectors, scoring, exports, packaged
church customization, service-plan integrations, eventing, observability,
and security.

**Codebase anchors**

- Source ADR in `docs/adr/ADR-030-plugin-and-extension-architecture.md`
- Import connector plan in
  `docs/implementation-plans/ADR-008-song-acquisition-import-connector-architecture-plan.md`
- Recommendation scoring plan in
  `docs/implementation-plans/ADR-010-recommendation-engine-scoring-architecture-plan.md`
- Service plan integration plan in
  `docs/implementation-plans/ADR-018-service-plan-integration-model-plan.md`
- Packaged deployment and church customization plan in
  `docs/implementation-plans/ADR-022-packaged-deployment-and-church-customization-model-plan.md`
- Eventing plan in
  `docs/implementation-plans/ADR-028-eventing-and-async-processing-architecture-plan.md`
- Observability plan in
  `docs/implementation-plans/ADR-029-observability-and-telemetry-strategy-plan.md`

### Prompt

Create the plugin extension point inventory and first-release scope decision.
List each proposed extension point, the owning core workflow, allowed inputs,
allowed outputs, data sensitivity, determinism requirements, failure behavior,
mutability level, event/audit requirements, expected trust tier, and whether it
is included in SPI v1. Define trust tiers such as core-maintained, partner
certified, church-local, and experimental, and map each tier to execution mode,
review requirements, allowed extension points, operational support expectations,
and rollout controls.

### Acceptance criteria

- Inventory covers import connectors, metadata transforms, scoring policy
  contributions, recommendation constraints, export formats, outbound
  integration hooks, and explicitly deferred future extension points.
- Each extension point identifies the core owner that retains final authority and
  the policy gates that plugin output must pass before use.
- SPI v1 scope is narrow enough to implement and test, and deferred extension
  points include explicit reasons and dependencies.
- Trust tiers are documented with allowed execution modes, certification depth,
  configuration authority, support expectations, and revocation behavior.
- Open questions from ADR-030 are either answered for SPI v1 or recorded as
  deferred decisions that do not block initial implementation.

### Restrictions

- Do not define a generic plugin API that can mutate arbitrary application state
  or query arbitrary data.
- Do not include direct song selection as an extension point; only the
  Recommendation Engine may produce final setlists.
- Do not mark an extension point stable until inputs, outputs, compatibility,
  failure behavior, and policy filters are defined.
- Do not use plugin flexibility as a reason to weaken approval, provenance,
  licensing, role, or instance isolation requirements.

## Subtask 2: Implement plugin registry, package metadata, configuration, and lifecycle persistence

### Context

ADR-030 requires plugin registration, configuration, enablement, disablement,
version tracking, and scoping by church instance, environment, and extension
point. Cadentia needs a canonical registry so administrators and runtime services
can discover which plugin versions are installed, which instances may use them,
which extension points they implement, and which configuration snapshot was used
for a given execution.

**Codebase anchors**

- API application code under `apps/api/src/main/java/com/cadentia/`
- Database migrations under `apps/api/src/main/resources/db/migration/`
- OpenAPI contract under `apps/api/src/main/openapi/`
- Security roles and permissions plan in
  `docs/implementation-plans/ADR-019-security-roles-and-permissions-plan.md`
- Packaged deployment plan in
  `docs/implementation-plans/ADR-022-packaged-deployment-and-church-customization-model-plan.md`

### Prompt

Design and implement the plugin registry and lifecycle model. Add persistence for
plugin package identity, provider, version, supported SPI versions, implemented
extension points, trust tier, signature or checksum metadata, certification
status, installation source, enabled environments, church-instance scopes,
configuration schema, configuration values or secret references, lifecycle
status, deprecation status, and audit metadata. Add service APIs or admin-facing
OpenAPI endpoints for listing plugins, registering approved packages, enabling or
disabling a plugin for an instance and extension point, updating configuration,
viewing version history, and revoking a plugin.

### Acceptance criteria

- Plugin registry records stable plugin ID, package name, provider, semantic
  version, supported SPI versions, extension points, trust tier, certification
  status, checksum/signature metadata, lifecycle status, and timestamps.
- Enablement is scoped by church instance, environment, extension point, plugin
  version, configuration version, and actor authorization.
- Configuration schemas validate required fields, default values, secret
  references, and environment-specific overrides before a plugin can execute.
- Registry operations are protected by role permissions and emit audit records
  for registration, enablement, disablement, configuration changes, version
  upgrades, downgrades, revocation, and deletion.
- Tests cover lifecycle transitions, invalid configuration rejection,
  instance-scoped enablement, disabled-plugin non-execution, and version history.

### Restrictions

- Do not store raw plugin secrets in plain registry records; use the project
  secret-management pattern or references appropriate to the deployment model.
- Do not let a globally installed plugin execute for every church instance by
  default; require explicit scoped enablement or a documented package default.
- Do not allow configuration changes to take effect without a versioned snapshot
  that can be associated with audit and execution records.
- Do not expose registry operations to roles that cannot manage integrations,
  packages, or church-instance settings.

## Subtask 3: Define versioned SPI contracts, DTO validation, compatibility, and contract fixtures

### Context

ADR-030 requires extension SPIs with versioned input/output DTOs and
compatibility rules. Stable contracts are essential because plugin outputs can
influence imports, metadata, recommendation scoring, constraints, exports, and
integrations. DTOs must be validated before plugins receive sensitive inputs and
after plugins return output.

**Codebase anchors**

- OpenAPI contract under `apps/api/src/main/openapi/`
- Recommendation explainability API plan in
  `docs/implementation-plans/ADR-021-recommendation-engine-explainability-api-plan.md`
- Event schema governance plan in
  `docs/implementation-plans/ADR-028-eventing-and-async-processing-architecture-plan.md`
- Import connector plan in
  `docs/implementation-plans/ADR-008-song-acquisition-import-connector-architecture-plan.md`

### Prompt

Create the SPI contract library and compatibility governance. Define versioned
input and output DTOs for each SPI v1 extension point, including metadata,
correlation fields, instance scope, policy snapshot identifiers, catalog snapshot
identifiers, actor or system context where allowed, locale, deterministic seed or
run ID where needed, and safe error output. Add validators that reject malformed,
unsupported, unauthorized, non-deterministic, or policy-unsafe plugin responses.
Document semantic-version compatibility rules, deprecation windows, fixture
examples, and contract tests that plugin implementations and core adapters must
pass.

### Acceptance criteria

- SPI contracts are versioned and documented with required fields, optional
  fields, validation rules, compatibility expectations, and examples.
- Core adapters validate inputs before invocation and validate outputs before
  persisting, displaying, scoring, exporting, or emitting plugin-derived data.
- Compatibility tests cover supported, deprecated, unsupported, missing-field,
  extra-field, invalid-type, and invalid-enum payloads for each SPI v1 contract.
- Contract fixtures include representative import, metadata transform,
  recommendation constraint, scoring contribution, export, success, degraded,
  and failure outputs.
- DTOs include enough snapshot/version metadata to reproduce plugin behavior and
  correlate execution with registry configuration and audit records.

### Restrictions

- Do not pass ORM entities, database handles, mutable internal collections, or
  framework-specific request objects as SPI payloads.
- Do not let plugins return free-form eligibility decisions that bypass core
  approval, licensing, role, or instance filters.
- Do not introduce backward-incompatible DTO changes without a new SPI version
  and migration or deprecation plan.
- Do not rely on prose documentation alone; enforce DTO rules through validation
  and tests.

## Subtask 4: Enforce instance, environment, role, licensing, and policy boundaries around plugins

### Context

ADR-030 requires plugins to be scoped by church instance, environment, and
extension point and prevents plugins from bypassing approval, licensing,
instance visibility, and role gates. This enforcement must happen before plugin
execution, after plugin output, and anywhere plugin-derived data is stored,
queried, or presented.

**Codebase anchors**

- Security roles and permissions plan in
  `docs/implementation-plans/ADR-019-security-roles-and-permissions-plan.md`
- Approval and doctrinal review plan in
  `docs/implementation-plans/ADR-005-approval-doctrinal-review-plan.md`
- Song data infrastructure plan in
  `docs/implementation-plans/ADR-001-song-data-infrastructure-plan.md`
- Packaged deployment plan in
  `docs/implementation-plans/ADR-022-packaged-deployment-and-church-customization-model-plan.md`
- Eventing and async processing plan in
  `docs/implementation-plans/ADR-028-eventing-and-async-processing-architecture-plan.md`

### Prompt

Implement a plugin policy enforcement layer used by all extension points. The
layer should resolve whether a plugin version is allowed to execute for the
current instance, environment, actor, extension point, package, license scope,
and configuration snapshot. It should build minimal input views filtered to the
plugin's authorization scope, enforce output validation and post-processing, and
reject or strip output that references unauthorized songs, arrangements, assets,
people, service plans, review notes, licenses, or instances. Add fail-closed
rules for any security, approval, licensing, or visibility uncertainty.

### Acceptance criteria

- Every plugin invocation checks registry enablement, trust tier, actor or system
  authority, instance scope, environment scope, extension point, version
  compatibility, and license/package constraints before execution.
- Plugin input payloads include only the fields permitted for that extension
  point and trust tier.
- Plugin outputs are filtered through approval status, provenance, licensing,
  instance visibility, role authorization, and canonical catalog existence before
  they affect imports, recommendations, exports, or integrations.
- Tests prove that a plugin cannot expose another instance's data, mark
  unapproved songs recommendable, bypass licensing constraints, read privileged
  review notes, or perform operations when disabled.
- Policy denials produce safe diagnostics, audit records where appropriate, and
  predictable error or degraded responses.

### Restrictions

- Do not trust plugin-declared instance IDs, roles, license flags, approval
  statuses, or song eligibility without checking canonical Cadentia state.
- Do not give plugins broad read access and then depend on plugin behavior to
  self-filter unauthorized data.
- Do not fail open when policy context is missing, stale, or ambiguous.
- Do not expose raw denial details that reveal unauthorized data to the caller or
  plugin.

## Subtask 5: Build plugin execution runtime, isolation model, failure policies, and deterministic safeguards

### Context

ADR-030 requires plugin failures to degrade or fail according to policy without
corrupting core state, and it requires deterministic outputs from plugins used in
recommendation scoring. The runtime must execute enabled plugins with bounded
resources, predictable ordering, timeouts, safe retries, and isolation from core
transactions.

**Codebase anchors**

- Eventing and async processing plan in
  `docs/implementation-plans/ADR-028-eventing-and-async-processing-architecture-plan.md`
- Observability and telemetry plan in
  `docs/implementation-plans/ADR-029-observability-and-telemetry-strategy-plan.md`
- Caching and performance strategy plan in
  `docs/implementation-plans/ADR-027-caching-and-performance-strategy-plan.md`
- Recommendation scoring plan in
  `docs/implementation-plans/ADR-010-recommendation-engine-scoring-architecture-plan.md`

### Prompt

Implement the plugin execution runtime and isolation strategy selected in Subtask
1. Add a stable invocation pipeline that resolves eligible plugins, orders them
deterministically, loads or calls them through the approved adapter, applies
timeouts and resource limits, captures safe errors, returns structured degraded
results, and records execution metadata. Define failure policies per extension
point, including fail closed, skip plugin and continue, retry asynchronously,
mark job failed, or require administrative intervention. Add deterministic
safeguards for recommendation-path plugins, including stable inputs, no wall
clock dependency in scoring output, stable ordering of collections, explicit
random seed handling if needed, and repeatability tests.

### Acceptance criteria

- Runtime executes only registry-enabled plugin versions through approved
  adapters and never inside a core transaction that could be corrupted by plugin
  failure.
- Each extension point has explicit timeout, retry, circuit-breaker or disable,
  fallback, and degraded-response behavior.
- Recommendation-path plugin executions are reproducible for the same plugin
  version, configuration version, input DTO, catalog snapshot, policy snapshot,
  and deterministic seed when present.
- Plugin failures cannot commit partial canonical state, leak unauthorized data,
  mark songs approved, or make unapproved songs recommendable.
- Tests cover timeout, exception, invalid output, duplicate plugin output,
  concurrent execution, deterministic repeatability, and disabled or revoked
  plugin behavior.

### Restrictions

- Do not let plugins execute arbitrary shell commands, open arbitrary network
  connections, or access unrestricted files unless the chosen trust tier and
  execution mode explicitly permits and audits that capability.
- Do not execute unbounded plugin code on synchronous user-request paths without
  timeout and degradation behavior.
- Do not retry non-idempotent plugin operations without an idempotency key and a
  documented side-effect policy.
- Do not use nondeterministic plugin results directly in recommendation scoring.

## Subtask 6: Implement import connector and metadata transform extension points

### Context

ADR-030 allows plugins to contribute candidates and metadata transforms. Import
and transform plugins are useful for partner catalogs and church-local metadata,
but they are also high risk because they can introduce unprovenanceable songs,
incorrect licenses, duplicate records, or unsafe metadata. Core import,
deduplication, provenance, approval, and audit workflows must remain authoritative.

**Codebase anchors**

- Song acquisition and import connector plan in
  `docs/implementation-plans/ADR-008-song-acquisition-import-connector-architecture-plan.md`
- Song import and deduplication plan in
  `docs/implementation-plans/ADR-003-song-import-deduplication-plan.md`
- Lyrics parsing and musical analysis plan in
  `docs/implementation-plans/ADR-009-lyrics-parsing-musical-analysis-pipeline-plan.md`
- Approval and doctrinal review plan in
  `docs/implementation-plans/ADR-005-approval-doctrinal-review-plan.md`
- Tag taxonomy plan in
  `docs/implementation-plans/ADR-007-tag-taxonomy-plan.md`

### Prompt

Implement import connector and metadata transform SPIs. Import connector plugins
should discover or fetch candidate song records, arrangements, metadata,
licensing references, and provenance references using a versioned DTO and return
staged import candidates only. Metadata transform plugins should propose
normalized metadata, tag mappings, field enrichments, or format conversions for
staged records. Route all plugin output through existing deduplication,
provenance, parsing, approval, taxonomy, licensing, and audit workflows before
anything becomes canonical or recommendable.

### Acceptance criteria

- Import connector plugins can create staged import candidates with source
  provenance, external identifiers, licensing references, and safe metadata
  without writing canonical song records directly.
- Metadata transform plugins can propose changes to staged or canonical-eligible
  metadata through reviewed change sets rather than silent direct mutation.
- Plugin-provided songs, lyrics references, arrangements, tags, and metadata pass
  existing deduplication, provenance, parser, taxonomy, licensing, approval, and
  audit gates before recommendation use.
- Failed import or transform plugins produce job status, safe error summaries,
  retry metadata, and audit events when catalog-mutating operations were
  attempted.
- Tests cover duplicate import candidates, missing provenance, unauthorized
  instance data, invalid tag mappings, invalid licensing references, and
  transform output that attempts to approve or recommend content.

### Restrictions

- Do not let import plugins write directly to canonical catalog tables or set
  approval statuses.
- Do not accept plugin-provided lyrics, licenses, provenance, CCLI-like data, or
  external identifiers without source validation appropriate to existing import
  rules.
- Do not allow transform plugins to create uncontrolled taxonomy values without
  taxonomy governance.
- Do not bypass staged import review merely because a plugin is certified.

## Subtask 7: Implement recommendation constraint and scoring contribution extension points

### Context

ADR-030 permits plugins to contribute scoring policy adjustments and
church-specific constraints, but core Cadentia remains responsible for
deterministic tie-breaking and final recommendation eligibility. This subtask is
especially sensitive because plugins must not select songs directly, expose
unapproved songs, or introduce nondeterministic scoring behavior.

**Codebase anchors**

- Recommendation scoring plan in
  `docs/implementation-plans/ADR-010-recommendation-engine-scoring-architecture-plan.md`
- Recommendation read model plan in
  `docs/implementation-plans/ADR-002-recommendation-read-model-plan.md`
- Recommendation explanation plan in
  `docs/implementation-plans/ADR-013-recommendation-explanation-system-plan.md`
- Recommendation explainability API plan in
  `docs/implementation-plans/ADR-021-recommendation-engine-explainability-api-plan.md`
- Energy arc modeling ADR in `docs/adr/ADR-032-energy-arc-modeling.md`
- Musical transition analysis ADR in
  `docs/adr/ADR-031-musical-transition-analysis-engine.md`
- Congregational familiarity ADR in
  `docs/adr/ADR-034-congregational-familiarity-model.md`

### Prompt

Implement recommendation constraint and scoring contribution SPIs. Constraint
plugins should return bounded, explainable filters or soft constraints scoped to
the current request, instance, policy snapshot, and approved candidate set.
Scoring plugins should return bounded score adjustments, reason codes, and
supporting evidence references for candidates already deemed eligible by core
filters. Integrate plugin contributions into the deterministic scoring pipeline
after approval, licensing, instance, role, and read-model eligibility filters and
before core tie-breaking and final ordering.

### Acceptance criteria

- Plugins never receive or return candidates outside the caller's approved,
  licensed, instance-visible, role-authorized candidate set.
- Constraint outputs are typed as hard reject, soft penalty, soft boost, or
  informational signal with documented bounds and conflict resolution behavior.
- Scoring adjustments are bounded, deterministic, versioned, and combined with
  core scores through a documented weighting or policy profile.
- Recommendation explanations include plugin contribution reason codes, plugin
  ID/version, configuration version, and safe evidence summaries where permitted.
- Tests prove repeatable scoring, stable tie-breaking, bounded score impact,
  safe reason-code rendering, exclusion of unapproved songs, and behavior when a
  plugin fails, times out, or returns invalid score output.

### Restrictions

- Do not allow plugins to select final setlists, override core ordering after
  tie-breaking, or directly mark songs as eligible.
- Do not accept negative or positive infinite scores, unbounded weights, random
  ordering, current-time-dependent adjustments, or personalized data outside the
  authorized request scope.
- Do not let plugin reason codes expose hidden review notes, private personnel
  details, unauthorized instance identifiers, or raw copyrighted content.
- Do not let a plugin's failure silently remove all recommendations unless the
  extension point policy explicitly requires fail-closed behavior.

## Subtask 8: Implement export format and outbound integration extension points

### Context

ADR-030 includes export formats and future integrations as plugin candidates.
Export and outbound plugins may produce documents, payloads, or calls to external
systems from setlists, service plans, schedules, assets, or catalog metadata.
They must respect role visibility, licensing, media restrictions, instance
boundaries, service-plan ownership, and async job governance.

**Codebase anchors**

- Eventing and async processing plan in
  `docs/implementation-plans/ADR-028-eventing-and-async-processing-architecture-plan.md`
- Service plan integration plan in
  `docs/implementation-plans/ADR-018-service-plan-integration-model-plan.md`
- Setlist persistence and versioning plan in
  `docs/implementation-plans/ADR-016-setlist-persistence-and-versioning-plan.md`
- Media and asset management plan in
  `docs/implementation-plans/ADR-025-media-and-asset-management-plan.md`
- Rehearsal workflow plan in
  `docs/implementation-plans/ADR-024-rehearsal-and-workflow-lifecycle-plan.md`

### Prompt

Implement export format and outbound integration SPIs. Export plugins should
render approved, role-visible, license-compliant data into configured formats
such as PDF-like documents, planning-system payloads, rehearsal packets, chord
chart bundles, or reporting extracts. Outbound integration plugins should produce
or send versioned payloads through governed integration boundaries with
idempotency keys, correlation IDs, credential references, retry policy, and safe
status reporting. Ensure generated artifacts and external calls are audited and
linked to the source setlist, service plan, asset, or catalog snapshot.

### Acceptance criteria

- Export plugins receive only data authorized for the requesting actor,
  environment, instance, license scope, and export purpose.
- Generated exports include source snapshot identifiers, plugin ID/version,
  configuration version, generation timestamp, actor or system context where
  permitted, and safe provenance metadata.
- Outbound integration plugins use credential references, idempotency keys,
  correlation IDs, retry policies, and event/job status rather than embedding
  raw secrets or uncontrolled side effects.
- Export artifacts and integration attempts are auditable, revocable or
  invalidatable where applicable, and visible through operational status
  screens or APIs.
- Tests cover role-limited exports, license-restricted content omission,
  duplicate outbound requests, retryable and non-retryable failures, disabled
  plugins, and attempts to export unauthorized assets or review notes.

### Restrictions

- Do not allow export plugins to receive raw data solely because the final output
  is expected to hide it; filter inputs first.
- Do not store generated artifacts with broader visibility than the data used to
  create them.
- Do not embed raw credentials in plugin configuration, logs, export artifacts,
  or outbound payload fixtures.
- Do not make outbound calls synchronously without timeout, idempotency, and
  clear user-visible status behavior.

## Subtask 9: Add administrative operations, audit logging, observability, certification, and rollout validation

### Context

ADR-030 requires plugin execution audit for privileged or catalog-mutating
operations and asks what certification process is required for third-party
plugins. Operators need visibility into plugin health, compatibility,
configuration, executions, failures, and revocations. Administrators also need a
safe path to roll out, upgrade, disable, and investigate plugins by instance and
environment.

**Codebase anchors**

- Admin review and governance UI plan in
  `docs/implementation-plans/ADR-011-admin-review-catalog-governance-ui-plan.md`
- Administrative web interface ADR in
  `docs/adr/ADR-036-administrative-web-interface.md`
- Observability plan in
  `docs/implementation-plans/ADR-029-observability-and-telemetry-strategy-plan.md`
- Security roles and permissions plan in
  `docs/implementation-plans/ADR-019-security-roles-and-permissions-plan.md`
- Eventing plan in
  `docs/implementation-plans/ADR-028-eventing-and-async-processing-architecture-plan.md`

### Prompt

Build plugin administrative operations and release validation. Add audit records
for plugin registration, certification changes, enablement, disablement,
configuration changes, invocation, privileged output, catalog-mutating output,
policy denials, failures, retries, circuit-breaker actions, revocation, and
version upgrades. Add logs, metrics, traces, dashboards, and alerts that show
plugin latency, failure rate, timeout rate, invalid-output rate, policy denial
rate, retry/dead-letter counts, and recommendation-path determinism failures.
Define a certification checklist and rollout runbook for first-party,
partner-certified, and church-local plugins.

### Acceptance criteria

- Audit records capture actor or system identity, instance, environment,
  extension point, plugin ID/version, configuration version, input/output schema
  version, policy snapshot, outcome, and safe error summary for privileged or
  catalog-mutating operations.
- Operational telemetry distinguishes plugin runtime failures, policy denials,
  invalid outputs, compatibility errors, timeout degradation, and downstream
  integration failures without logging sensitive payloads.
- Admin operations support listing health by plugin and instance, viewing recent
  executions, disabling or revoking unsafe plugins, testing configuration, and
  identifying affected jobs, imports, exports, or recommendations.
- Certification checklist covers source ownership, dependency review, SPI
  compatibility tests, deterministic tests for recommendation-path plugins,
  security review, data-sensitivity review, licensing review, observability, and
  rollback plan.
- Rollout validation includes local/test fixtures, staging dry run,
  compatibility matrix, seeded failure scenarios, security regression tests,
  deterministic recommendation regression tests, and production enablement gates.

### Restrictions

- Do not log raw lyrics, raw prompts, credentials, private personnel details,
  privileged review notes, unauthorized instance identifiers, or full external
  payloads in plugin telemetry.
- Do not treat successful plugin execution as proof that plugin output passed
  approval, licensing, provenance, or role policy checks.
- Do not allow an unsafe plugin to remain enabled when revocation policy or
  circuit-breaker thresholds require disablement.
- Do not certify third-party plugins without repeatable tests and documented
  operational ownership.
