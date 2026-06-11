# ADR-032 Implementation Plan: Energy Arc Modeling

## Objective

Implement deterministic energy arc modeling for Cadentia recommendations so
approved songs and arrangements can be evaluated as an ordered worship flow
against versioned church energy profiles, named target arcs, adjacent-song
movement policies, default praise/worship structure, and machine-readable
explanations without allowing the LLM to label energy or select songs.

## Source ADR

- [ADR-032: Energy Arc Modeling](../adr/ADR-032-energy-arc-modeling.md)

## Status

Overall status: Planned.

- Subtask 1: Planned - energy dimension contract, controlled vocabulary, and
  v1 profile scope.
- Subtask 2: Planned - catalog metadata ownership, governance workflow, and
  snapshot boundaries.
- Subtask 3: Planned - target arc definitions, church-configured variants, and
  versioning model.
- Subtask 4: Planned - request policy resolution and default praise/worship
  structure integration.
- Subtask 5: Planned - adjacent transition and full-set energy scoring engine.
- Subtask 6: Planned - Recommendation Engine integration, deterministic
  ordering, and tie-breaking.
- Subtask 7: Planned - structured explanations, warnings, and metadata
  references.
- Subtask 8: Planned - admin/catalog operations for energy metadata and profile
  lifecycle.
- Subtask 9: Planned - regression fixtures, observability, rollout controls,
  and documentation.

## Guiding Principles

- Energy modeling is deterministic and based only on curated catalog metadata,
  reviewed usage tags, versioned church configuration, and explicit request
  policies.
- The LLM may extract an intent or policy hint when the intent contract allows
  it, but it must never assign energy labels, infer song energy, choose songs,
  or override Recommendation Engine scoring.
- Energy scores inform candidate ordering, warnings, and explanations; they do
  not bypass approval, licensing, instance visibility, doctrinal review,
  recommendation eligibility, or transition-analysis constraints.
- Profiles and arc definitions must be versioned so identical requests against
  the same catalog and policy snapshots produce identical results and auditable
  explanations.
- Missing or low-confidence energy metadata must be visible through structured
  warnings, predictable eligibility/scoring behavior, and review workflows rather
  than silent assumptions.
- Churches may configure preferred arcs, but configured variants must remain
  bounded by controlled dimensions, validated thresholds, and deterministic
  policies.

## Subtask 1: Define the energy dimension contract, controlled vocabulary, and v1 profile scope

### Context

ADR-032 requires controlled modeling of praise intensity, worship
reflectiveness, emotional trajectory, congregation engagement, and service
moment suitability. The ADR also leaves open which dimensions should be
mandatory in the initial catalog schema. Existing recommendation and taxonomy
plans already establish that scoring inputs should be structured, explainable,
and governed through approved metadata rather than free-form LLM judgments.

**Codebase anchors**

- Source ADR in `docs/adr/ADR-032-energy-arc-modeling.md`
- Tag taxonomy plan in
  `docs/implementation-plans/ADR-007-tag-taxonomy-plan.md`
- Recommendation scoring plan in
  `docs/implementation-plans/ADR-010-recommendation-engine-scoring-architecture-plan.md`
- Recommendation explanation plan in
  `docs/implementation-plans/ADR-013-recommendation-explanation-system-plan.md`
- Recommendation explainability API plan in
  `docs/implementation-plans/ADR-021-recommendation-engine-explainability-api-plan.md`

### Prompt

Create the v1 energy dimension contract. Define each canonical dimension,
allowed values, numeric normalization rules, confidence fields, provenance
fields, valid source types, default weight ranges, and whether the field is
mandatory for v1 recommendation eligibility or optional for scoring refinement.
Include praise intensity, worship reflectiveness, emotional trajectory,
congregation engagement, and service moment suitability. Decide how each
dimension maps to existing tag taxonomy concepts, whether it belongs on the song
record, arrangement record, church-instance override, or profile configuration,
and how conflicting values are resolved.

### Acceptance criteria

- The v1 contract lists every required and optional energy field with stable
  names, data types, allowed values, normalization rules, confidence semantics,
  and provenance requirements.
- Mandatory-vs-optional status is explicitly decided for the initial schema,
  including fallback behavior for missing, partial, stale, or low-confidence
  values.
- Controlled values cover the ADR-required dimensions and service moment
  suitability without relying on free-form prose as a machine contract.
- The contract identifies which values are global curated metadata,
  arrangement-specific metadata, church-instance overrides, or profile-level
  preferences.
- Open questions from ADR-032 about initial mandatory dimensions are answered
  for v1 or documented as deferred with non-blocking defaults.

### Restrictions

- Do not use BPM as the only energy proxy or collapse emotional,
  congregational, and service-moment dimensions into tempo.
- Do not permit LLM-generated labels, sentiment scores, or unreviewed prose to
  become authoritative energy metadata.
- Do not define unbounded free-text categories that cannot be validated,
  indexed, tested, or explained deterministically.
- Do not make church-specific subjective overrides indistinguishable from
  globally reviewed catalog metadata.

## Subtask 2: Establish catalog metadata ownership, governance workflow, and snapshot boundaries

### Context

ADR-032 requires use of curated catalog metadata, reviewed usage tags, and
deterministic calculations. Energy metadata is partly subjective, so ownership,
review state, provenance, and auditability must be clear before the
Recommendation Engine uses it. Existing approval, catalog governance, import,
and read-model plans already define approved-only filtering and reviewed
metadata expectations.

**Codebase anchors**

- Song data infrastructure plan in
  `docs/implementation-plans/ADR-001-song-data-infrastructure-plan.md`
- Recommendation read-model plan in
  `docs/implementation-plans/ADR-002-recommendation-read-model-plan.md`
- Song import and deduplication plan in
  `docs/implementation-plans/ADR-003-song-import-deduplication-plan.md`
- Approval and doctrinal review plan in
  `docs/implementation-plans/ADR-005-approval-doctrinal-review-plan.md`
- Admin review and catalog governance UI plan in
  `docs/implementation-plans/ADR-011-admin-review-catalog-governance-ui-plan.md`

### Prompt

Design and implement the energy metadata lifecycle and repository boundary.
Specify who can create, edit, review, approve, supersede, or retire energy
metadata and church overrides. Add persistence or projection fields for energy
metadata, confidence, reviewer, source/provenance, approval state, effective
version, and catalog snapshot identifiers. Ensure the recommendation read model
exposes only approved, instance-eligible energy metadata for scoring and returns
stable snapshot references for explanation and audit purposes.

### Acceptance criteria

- Energy metadata has explicit ownership, review states, reviewer/audit fields,
  provenance, effective dates or versions, and retirement/supersession behavior.
- Recommendation-time reads use a single approved metadata adapter or repository
  boundary that applies approval, visibility, instance, and catalog snapshot
  filters before scoring.
- Unapproved, deleted, private-out-of-scope, wrong-instance, stale-superseded,
  or otherwise ineligible energy metadata is excluded or downgraded according to
  documented policy.
- The read model can return stable metadata references so energy explanations can
  cite song, arrangement, profile, tag, and snapshot identifiers.
- Import and admin workflows identify how imported candidate energy hints are
  staged for review without becoming recommendation inputs until approved.

### Restrictions

- Do not query raw import staging records, raw lyrics, or unapproved tags during
  recommendation-time energy scoring.
- Do not leak cross-instance private energy overrides through IDs, titles,
  warnings, logs, metrics, explanations, or errors.
- Do not allow energy metadata edits to bypass catalog governance, approval
  audit logging, or role authorization.
- Do not duplicate canonical metadata if an existing approved read-model
  projection can be extended with versioned energy fields.

## Subtask 3: Define target arcs, church-configured variants, and profile versioning

### Context

ADR-032 requires support for named arcs such as `rising`,
`rise_then_reflect`, `reflective`, `celebration`, `response`, and
church-configured variants. These arcs must be reproducible and tunable without
custom code. Profile changes also require regression fixtures, so target arcs
and church profiles need stable identifiers and versions.

**Codebase anchors**

- Source ADR in `docs/adr/ADR-032-energy-arc-modeling.md`
- Packaged deployment and church customization plan in
  `docs/implementation-plans/ADR-022-packaged-deployment-and-church-customization-model-plan.md`
- Caching and performance strategy plan in
  `docs/implementation-plans/ADR-027-caching-and-performance-strategy-plan.md`
- Plugin and extension architecture plan in
  `docs/implementation-plans/ADR-030-plugin-and-extension-architecture-plan.md`

### Prompt

Create the target arc and energy profile model. Define built-in arc templates,
allowed church variant customization points, validation rules, thresholds,
weights, position-based target bands, adjacent movement tolerances,
intentional-contrast allowances, service moment mappings, and versioning rules.
Document how profiles are selected by church instance, season, service type,
request policy, and defaults. Include migration rules for profile changes,
cache invalidation behavior, and deterministic profile snapshot references.

### Acceptance criteria

- Built-in profiles exist for `rising`, `rise_then_reflect`, `reflective`,
  `celebration`, and `response`, with normalized target bands across setlist
  positions and praise/worship sections.
- Church-configured variants are bounded by schema validation, allowed value
  ranges, role authorization, and audit logging.
- Each profile version has a stable identifier, effective lifecycle, checksum or
  equivalent change marker, and snapshot reference suitable for deterministic
  scoring and explanation.
- Profile resolution order is documented for church default, service type,
  liturgical or seasonal context, explicit request policy, and system fallback.
- Seasonal and liturgical influence is answered for v1 or deferred with a safe
  default profile resolution policy.

### Restrictions

- Do not allow arbitrary executable code, plugin callbacks, or free-form formula
  strings in church-configured energy profiles.
- Do not permit profile updates to retroactively change persisted explanation
  snapshots without recording the original profile version.
- Do not make a church variant capable of bypassing approval, licensing,
  visibility, doctrinal, or Recommendation Engine eligibility gates.
- Do not create unbounded arc names or values that cannot be validated by API,
  persistence, tests, and admin UI.

## Subtask 4: Integrate request policy resolution and default praise/worship structure

### Context

ADR-032 requires support for the default praise/worship structure, including 10
praise and 5 worship when requested by the default intent contract. Existing
intent extraction and guided flow plans define the LLM boundary: the LLM may
produce structured JSON slots, but backend services enforce selection,
eligibility, and recommendation policy.

**Codebase anchors**

- LLM intent extraction contract plan in
  `docs/implementation-plans/ADR-012-llm-intent-extraction-contract-plan.md`
- Duplicate intent contract governance plan in
  `docs/implementation-plans/ADR-014-llm-intent-extraction-contract-plan.md`
- Guided menu and conversational request flow plan in
  `docs/implementation-plans/ADR-015-guided-menu-and-conversational-request-flow-plan.md`
- Recommendation scoring architecture plan in
  `docs/implementation-plans/ADR-010-recommendation-engine-scoring-architecture-plan.md`

### Prompt

Implement or specify how energy arc policy is resolved from structured request
slots, guided-menu selections, church defaults, service plan context, and system
defaults. Add validation for requested arc names, intentional-contrast policies,
praise/worship counts, service moments, and section boundaries. Ensure the
resolved policy can represent the default 10 praise plus 5 worship structure and
can map target energy bands to actual setlist length, including shorter or
custom counts.

### Acceptance criteria

- Energy policy resolution produces a structured, validated policy object with
  selected arc, profile version, counts, section boundaries, intentional
  contrast settings, and fallback reasons.
- Default behavior supports 10 praise and 5 worship when requested by the
  default contract and maps target bands correctly across all 15 positions.
- Shorter, longer, or custom count requests have deterministic target-band
  interpolation or section mapping rules.
- Invalid, unknown, unsupported, or unauthorized arc policies fail validation or
  fall back according to explicit backend rules with structured warnings.
- Tests prove the LLM cannot assign energy labels, choose songs, or override
  backend profile and eligibility decisions through request text.

### Restrictions

- Do not accept free-text arc descriptions as executable scoring policy.
- Do not let request-time LLM output introduce new arc names, dimensions,
  weights, labels, or service moments that are not in controlled configuration.
- Do not assume every setlist has exactly 15 songs; preserve default behavior
  while supporting validated custom counts.
- Do not merge energy policy resolution with song selection in the LLM layer.

## Subtask 5: Implement adjacent transition and full-set energy scoring engine

### Context

ADR-032 requires evaluation of energy movement across adjacent songs and across
the full setlist. Abrupt energy discontinuities should be prevented, penalized,
or explicitly explained as intentional policy choices. This engine should
complement ADR-031 transition analysis without duplicating musical key, tempo,
or meter responsibilities.

**Codebase anchors**

- Source ADR in `docs/adr/ADR-032-energy-arc-modeling.md`
- Musical transition analysis engine plan in
  `docs/implementation-plans/ADR-031-musical-transition-analysis-engine-plan.md`
- Recommendation scoring architecture plan in
  `docs/implementation-plans/ADR-010-recommendation-engine-scoring-architecture-plan.md`
- Recommendation explanation system plan in
  `docs/implementation-plans/ADR-013-recommendation-explanation-system-plan.md`

### Prompt

Build the deterministic energy scoring engine for an ordered candidate setlist.
Evaluate each song/arrangement against its position target band, section target,
service moment suitability, adjacent energy movement, praise-to-worship
transition quality, emotional trajectory, congregation engagement, and full-set
arc shape. Produce component scores, aggregate score, violations, penalties,
intentional-contrast classifications, warnings, and confidence values. Define
how abrupt movement thresholds interact with profile settings and how missing or
low-confidence metadata affects eligibility versus score.

### Acceptance criteria

- The engine returns stable component and aggregate scores for identical
  request, catalog snapshot, ordered candidates, and profile version.
- Adjacent-song scoring distinguishes smooth movement, allowed contrast,
  penalized discontinuity, hard policy violation, praise-to-worship handoff, and
  missing/low-confidence metadata.
- Full-set scoring evaluates trajectory shape, target-band fit by position,
  section balance, service moment suitability, and congregation engagement
  against the selected profile.
- Abrupt energy changes are avoided when possible, penalized when tolerated, or
  marked with structured intentional-contrast reasons when explicitly allowed.
- Confidence and fallback behavior for missing energy metadata are deterministic
  and covered by unit tests.

### Restrictions

- Do not use randomization, wall-clock time, map iteration order, or mutable
  cache state in scoring or tie-breaking.
- Do not duplicate ADR-031 key, BPM, meter, cadence, or modulation scoring;
  consume those outputs only as separate recommendation features if needed.
- Do not silently assign neutral full-credit values to missing energy metadata.
- Do not make energy scoring the only recommendation score; it remains one input
  among eligibility, thematic, doctrinal, musical, familiarity, transition, and
  policy factors.

## Subtask 6: Integrate energy scoring with the Recommendation Engine and deterministic ordering

### Context

ADR-032 states that energy scoring remains a Recommendation Engine input and
explanation source, not an LLM-generated judgment. Existing scoring architecture
must retain deterministic selection, approved-only filtering, dataset
references, and tie-breaking while adding energy as a first-class but bounded
feature.

**Codebase anchors**

- Recommendation candidate read model plan in
  `docs/implementation-plans/ADR-002-recommendation-read-model-plan.md`
- Recommendation engine scoring architecture plan in
  `docs/implementation-plans/ADR-010-recommendation-engine-scoring-architecture-plan.md`
- Recommendation explanation system plan in
  `docs/implementation-plans/ADR-013-recommendation-explanation-system-plan.md`
- Setlist persistence and versioning plan in
  `docs/implementation-plans/ADR-016-setlist-persistence-and-versioning-plan.md`
- User feedback and recommendation tuning plan in
  `docs/implementation-plans/ADR-017-user-feedback-and-recommendation-tuning-plan.md`

### Prompt

Integrate the energy engine into Recommendation Engine candidate ordering and
setlist assembly. Decide whether energy is applied during candidate expansion,
ordered-list search, post-order reranking, or all three. Define weights,
normalization, hard-vs-soft policy thresholds, deterministic tie-breakers,
interaction with praise/worship counts, transition scoring, familiarity,
feedback tuning, and persisted setlist snapshots. Add safeguards so the engine
can degrade when too few eligible songs contain sufficient energy metadata while
still emitting warnings.

### Acceptance criteria

- Recommendation Engine selection consumes approved energy metadata and resolved
  profile policy through explicit typed inputs rather than LLM prose.
- Energy score contribution is normalized, weighted, and combined with existing
  recommendation factors using deterministic tie-breaking.
- When enough eligible catalog data exists, generated setlists respect energy
  policies; when data is insufficient, the result includes structured warnings
  and documented fallback behavior.
- Persisted recommendation or setlist records include the energy profile version,
  catalog snapshot, score summary, and explanation references required for later
  audit.
- Regression tests demonstrate deterministic ordering for identical requests and
  changed ordering only when catalog/profile/policy snapshots change.

### Restrictions

- Do not let energy scoring bypass approval, doctrinal, licensing, visibility,
  role, or count constraints.
- Do not let user feedback directly mutate approved energy labels without review;
  feedback may inform separate tuning workflows only.
- Do not select songs solely because they satisfy an arc if they fail other
  required recommendation constraints.
- Do not introduce nondeterministic search heuristics without stable seeds,
  sorted inputs, and reproducible tie-breakers.

## Subtask 7: Emit structured explanations, warnings, and metadata references

### Context

ADR-032 requires machine-readable energy explanations and warnings. Existing
explainability ADRs require structured reason codes, evidence references, and
audience-aware output. Energy explanations must show why a setlist follows or
breaks an arc without exposing unauthorized metadata or relying on prose-only
justification.

**Codebase anchors**

- Recommendation explanation system plan in
  `docs/implementation-plans/ADR-013-recommendation-explanation-system-plan.md`
- Recommendation Engine explainability API plan in
  `docs/implementation-plans/ADR-021-recommendation-engine-explainability-api-plan.md`
- Security roles and permissions plan in
  `docs/implementation-plans/ADR-019-security-roles-and-permissions-plan.md`
- Observability and telemetry strategy plan in
  `docs/implementation-plans/ADR-029-observability-and-telemetry-strategy-plan.md`

### Prompt

Define and implement the energy explanation payload. Include reason-code
registry entries, component score fields, target-vs-actual energy facts,
adjacent movement details, full-arc summary, service moment evidence,
intentional-contrast markers, warnings, confidence, and metadata references.
Map reason codes to user-facing copy through existing explanation mechanisms
while preserving a stable machine-readable contract for API clients and tests.
Apply authorization and audience-mode filtering to hide sensitive or
cross-instance metadata.

### Acceptance criteria

- Energy explanations use stable reason codes and structured fields for target
  arc fit, adjacent movement, abrupt discontinuity, intentional contrast,
  service moment suitability, missing metadata, low confidence, and fallback
  policy.
- Explanation payloads cite approved metadata references such as song,
  arrangement, tag/profile version, catalog snapshot, and score component IDs.
- Audience-specific rendering can show concise worship-leader summaries without
  removing machine-readable evidence needed for audit and tests.
- Unauthorized, private, review-only, or cross-instance details are filtered
  from explanations, logs, metrics, and errors.
- Contract fixtures cover smooth arc, intentional contrast, abrupt penalty,
  insufficient metadata, custom church profile, and default 10 praise/5 worship
  scenarios.

### Restrictions

- Do not make prose explanations the only representation of energy decisions.
- Do not expose unapproved reviewer notes, private church overrides, or hidden
  candidate metadata through reason details.
- Do not invent metadata references that cannot be resolved to approved catalog,
  profile, or snapshot records.
- Do not allow reason-code wording changes to break API clients; keep codes
  stable and map display text separately.

## Subtask 8: Build admin and catalog operations for energy metadata and profile lifecycle

### Context

Energy metadata is partly subjective and requires governance. Churches need
profile variants without custom code, while catalog reviewers need workflows to
review and maintain metadata quality. Existing admin review, security, packaged
deployment, and observability plans provide patterns for role-gated operations,
audit logs, and lifecycle management.

**Codebase anchors**

- Admin review and catalog governance UI plan in
  `docs/implementation-plans/ADR-011-admin-review-catalog-governance-ui-plan.md`
- Security roles and permissions plan in
  `docs/implementation-plans/ADR-019-security-roles-and-permissions-plan.md`
- Packaged deployment and church customization plan in
  `docs/implementation-plans/ADR-022-packaged-deployment-and-church-customization-model-plan.md`
- Observability and telemetry strategy plan in
  `docs/implementation-plans/ADR-029-observability-and-telemetry-strategy-plan.md`
- Administrative web interface ADR in
  `docs/adr/ADR-036-administrative-web-interface.md`

### Prompt

Add admin workflows for reviewing energy metadata, resolving low-confidence or
missing values, managing church energy profile variants, previewing arc effects,
approving changes, publishing versions, and rolling back profile or metadata
updates. Define role permissions, validation errors, audit events, batch review
flows, bulk import staging behavior, and operational dashboards for metadata
coverage. Include reviewer guidance that explains the controlled dimensions and
how they should be applied consistently.

### Acceptance criteria

- Authorized users can create, edit, review, approve, supersede, retire, and
  audit energy metadata and profile versions through documented workflows.
- Admin validation prevents invalid dimensions, out-of-range thresholds,
  unapproved arc names, missing required provenance, and unauthorized
  cross-instance changes.
- Review screens or operational reports identify songs/arrangements missing
  mandatory energy data, low-confidence metadata, stale profiles, and profile
  changes that would materially affect recommendations.
- Publishing or rolling back metadata/profile changes emits audit events and
  invalidates affected read-model projections or caches deterministically.
- Reviewer documentation and fixtures support consistent classification across
  praise intensity, reflectiveness, trajectory, engagement, and service moment
  suitability.

### Restrictions

- Do not provide unrestricted bulk-edit operations without validation,
  preview/diff, authorization, and audit trails.
- Do not let church admins change global catalog metadata unless their role and
  workflow explicitly allow it.
- Do not allow admin previews to reveal private songs, hidden arrangements, or
  cross-instance profile data.
- Do not make profile publication depend on manual database edits or custom code
  deployment.

## Subtask 9: Add regression fixtures, observability, rollout controls, and documentation

### Context

ADR-032 notes that profile changes require regression fixtures and that overly
strict arcs can reduce eligible recommendations. Energy modeling also affects
recommendation quality, warnings, and user trust, so rollout must include
fixtures, metrics, runbooks, and migration guidance. Existing observability,
eventing, caching, and setlist persistence plans provide operational patterns.

**Codebase anchors**

- Observability and telemetry strategy plan in
  `docs/implementation-plans/ADR-029-observability-and-telemetry-strategy-plan.md`
- Eventing and async processing architecture plan in
  `docs/implementation-plans/ADR-028-eventing-and-async-processing-architecture-plan.md`
- Caching and performance strategy plan in
  `docs/implementation-plans/ADR-027-caching-and-performance-strategy-plan.md`
- Setlist persistence and versioning plan in
  `docs/implementation-plans/ADR-016-setlist-persistence-and-versioning-plan.md`

### Prompt

Create the verification and rollout package for energy arc modeling. Add seed
fixtures and regression scenarios for all built-in arcs, default 10 praise/5
worship requests, custom church profiles, insufficient metadata, abrupt
transitions, intentional contrast, and profile version changes. Define metrics,
logs, traces, audit events, alerts, migration steps, feature flags, backfill
jobs, rollback procedures, and documentation for operators and implementers.

### Acceptance criteria

- Automated tests cover dimension validation, metadata eligibility, profile
  resolution, adjacent scoring, full-arc scoring, Recommendation Engine
  integration, explanation payloads, authorization filtering, and deterministic
  tie-breaking.
- Golden fixtures prove deterministic output for the same request, catalog
  snapshot, ordered candidates, and profile version, and clearly show expected
  differences after profile changes.
- Observability includes metrics for metadata coverage, low-confidence usage,
  energy policy violations, abrupt discontinuity penalties, fallback frequency,
  profile version adoption, and recommendation latency impact.
- Rollout documentation covers feature flags, data backfill, cache/read-model
  rebuild, profile publishing, rollback, alert triage, and known v1 limitations.
- Migration or backfill jobs never make unreviewed imported hints active without
  catalog governance approval.

### Restrictions

- Do not rely only on snapshot tests that hide the reason a score changed; keep
  component-level assertions for scoring and explanations.
- Do not emit metrics or logs containing private song details, reviewer notes,
  raw lyrics, or cross-instance identifiers.
- Do not roll out hard energy eligibility enforcement before metadata coverage
  thresholds and fallback behavior are measured.
- Do not leave profile/version changes without regression fixtures that explain
  the expected recommendation impact.
