# ADR-033 Implementation Plan: Arrangement Compatibility and Instrumentation Modeling

## Objective

Implement deterministic arrangement compatibility and instrumentation modeling so
Cadentia can evaluate whether an approved arrangement is practical for a
specific worship team, service format, vocal configuration, instrumentation
inventory, and church-instance override policy. The implementation must keep
approval gates authoritative, maintain human-reviewed compatibility metadata,
support deterministic filtering and scoring, and expose structured ADR-021
explanations without allowing the LLM to infer suitability or select songs.

## Source ADR

- [ADR-033: Arrangement Compatibility and Instrumentation Modeling](../adr/ADR-033-arrangement-compatibility-and-instrumentation-modeling.md)

## Status

Overall status: Planned.

- Subtask 1: Planned - compatibility vocabulary, profile contract, and reason-code taxonomy.
- Subtask 2: Planned - persistence model, ownership, provenance, approval, and snapshot boundaries.
- Subtask 3: Planned - instrumentation slots, substitutions, coverage rules, and unsupported configurations.
- Subtask 4: Planned - vocal, choir, congregation-accessibility, and lead-range modeling.
- Subtask 5: Planned - complexity, rehearsal burden, rhythmic difficulty, and technical dependency modeling.
- Subtask 6: Planned - ADR-023 team capability integration and request policy resolution.
- Subtask 7: Planned - church-specific overrides, packaged defaults, governance, and audit behavior.
- Subtask 8: Planned - deterministic filtering, scoring, ordering, and Recommendation Engine integration.
- Subtask 9: Planned - ADR-021 explanations, API/read-model exposure, and admin/search query surfaces.
- Subtask 10: Planned - migration, fixtures, testing, observability, rollout controls, and documentation.

## Guiding Principles

- Compatibility answers whether an already-approved arrangement can be served by
  a supplied team or service context; it must never replace doctrinal, musical,
  licensing, lyrics, provenance, active-catalog, or administrative approval
  gates.
- LLM components must not infer instrumentation, suitability, complexity,
  choir-support, vocal-range, or substitution metadata from lyrics, titles,
  notes, charts, media, or musician profiles.
- Recommendation-time compatibility decisions must use only curated,
  human-reviewed, approved, church-instance-authorized metadata and deterministic
  policies.
- Church-instance overrides must be explicit overlays on global catalog defaults
  and must never mutate package-level or global compatibility metadata.
- Filtering, scoring, warnings, and explanations must be reproducible for the
  same arrangement snapshot, team capability snapshot, request policy, and
  church-instance configuration.
- Compatibility conflicts must be explainable with machine-readable reason codes,
  evidence references, snapshot identifiers, and privacy-safe audit records.
- Missing optional metadata should degrade predictably through documented
  warnings and scoring behavior; missing required metadata should block
  compatibility only when the active policy requires it.

## Subtask 1: Define compatibility vocabulary, profile contract, and reason-code taxonomy

### Context

ADR-033 requires structured compatibility metadata for required and optional
instruments, acoustic/electric suitability, choir support, vocal configuration,
complexity, rehearsal burden, track dependency, and minimum team capability. A
shared vocabulary is needed before persistence, Recommendation Engine logic,
admin UI, or explanations can be implemented. This vocabulary must align with
ADR-023 team capability data, ADR-010 scoring, ADR-021 explanation reason codes,
ADR-022 packaged church customization, and ADR-030 extension boundaries.

**Codebase anchors**

- Source ADR in `docs/adr/ADR-033-arrangement-compatibility-and-instrumentation-modeling.md`
- Team and musician assignment plan in
  `docs/implementation-plans/ADR-023-team-and-musician-assignment-model-plan.md`
- Recommendation scoring plan in
  `docs/implementation-plans/ADR-010-recommendation-engine-scoring-architecture-plan.md`
- Recommendation explainability plan in
  `docs/implementation-plans/ADR-021-recommendation-engine-explainability-api-plan.md`
- Packaged deployment and church customization plan in
  `docs/implementation-plans/ADR-022-packaged-deployment-and-church-customization-model-plan.md`

### Prompt

Create the v1 arrangement compatibility domain contract. Define the core objects
for arrangement suitability profile, suitability slot, instrument requirement,
substitution rule, unsupported configuration, vocal requirement, choir support,
complexity metric, technical dependency, team capability reference, church
compatibility override, evidence reference, compatibility warning, and
compatibility result. Define controlled vocabularies for team formats,
instrument families, service roles, vocal parts, skill floors, coverage rules,
suitability flags, warning severities, hard-filter outcomes, and score component
names. Establish reason-code families for missing required instruments,
substitution matches, unsupported configurations, vocal conflicts, choir
mismatch, lead-range mismatch, complexity overload, track dependency, missing
metadata, low-confidence metadata, override application, and approval-gate
failures.

### Acceptance criteria

- A durable specification or code contract defines compatibility profiles,
  slots, requirements, substitutions, unsupported configurations, vocal and
  choir fields, complexity fields, technical dependencies, warnings, evidence,
  reason codes, and result shapes.
- Controlled vocabularies cover acoustic, electric/full-band, keys-led,
  choir-supported, stripped-down, tracks-assisted, and extensible service-format
  suitability flags without relying on unbounded free text for machine logic.
- Reason-code families are suitable for ADR-021 explanations and cover positive
  matches, hard conflicts, soft warnings, missing metadata, low-confidence
  metadata, and church override behavior.
- The contract identifies which fields are global package defaults,
  arrangement-specific curated metadata, church-instance overrides,
  team-capability references, or request-time policies.
- Open questions from ADR-033 about minimum required metadata, substitution
  scoring, and choir-default ownership are answered for v1 or documented as
  deferred with safe non-blocking defaults.

### Restrictions

- Do not use free-form arrangement notes, chart text, lyric text, or media
  analysis as the primary machine contract for compatibility decisions.
- Do not define compatibility categories that cannot be validated, indexed,
  tested, audited, or explained deterministically.
- Do not let the compatibility model select songs or bypass the Recommendation
  Engine.
- Do not make church-specific subjective overrides indistinguishable from
  globally reviewed catalog defaults.

## Subtask 2: Establish persistence model, ownership, provenance, approval, and snapshot boundaries

### Context

ADR-033 requires compatibility data to be queryable, explainable, auditable, and
approval-gated. Compatibility metadata is operationally sensitive because a
recommendation may be valid doctrinally but impractical for the scheduled team.
Cadentia therefore needs clear source-of-truth records, review states,
provenance, versioning, read-model exposure, and snapshot references before the
Recommendation Engine consumes compatibility data.

**Codebase anchors**

- Song data infrastructure plan in
  `docs/implementation-plans/ADR-001-song-data-infrastructure-plan.md`
- Recommendation read-model plan in
  `docs/implementation-plans/ADR-002-recommendation-read-model-plan.md`
- Approval and doctrinal review plan in
  `docs/implementation-plans/ADR-005-approval-doctrinal-review-plan.md`
- Admin review and catalog governance UI plan in
  `docs/implementation-plans/ADR-011-admin-review-catalog-governance-ui-plan.md`
- Security roles and permissions plan in
  `docs/implementation-plans/ADR-019-security-roles-and-permissions-plan.md`

### Prompt

Design and implement the source-of-truth persistence and approved read-model
projection for arrangement compatibility metadata. Specify tables or documents
for suitability profiles, profile versions, slots, substitutions, unsupported
configurations, vocal/choir requirements, complexity ratings, technical
dependencies, metadata provenance, review state, reviewer identity, audit
entries, effective lifecycle, supersession, and retirement. Add repository or
adapter boundaries that expose only approved, active, instance-authorized,
recommendation-eligible compatibility snapshots. Define how imported candidate
compatibility hints or admin drafts are staged for review without becoming
recommendation inputs.

### Acceptance criteria

- Compatibility metadata has explicit ownership, provenance, review states,
  reviewer/audit fields, effective versions, supersession behavior, retirement
  behavior, and stable snapshot identifiers.
- Recommendation-time reads use a single approved compatibility adapter or
  repository boundary that applies approval, active-catalog, arrangement,
  visibility, church-instance, package, and snapshot filters before scoring.
- Unapproved, deleted, stale-superseded, wrong-instance, private-out-of-scope, or
  otherwise ineligible compatibility metadata is excluded from recommendation
  filtering and scoring.
- Approved read-model rows can return stable metadata references for
  arrangement, profile, slot, override, reviewer, provenance, and catalog
  snapshot evidence.
- Import and admin workflows identify how candidate compatibility metadata is
  staged, reviewed, rejected, approved, or superseded without bypassing catalog
  governance.

### Restrictions

- Do not query raw import staging records, unapproved drafts, chart text, or
  musician notes during recommendation-time compatibility scoring.
- Do not leak cross-instance private suitability overrides through IDs, names,
  warnings, errors, logs, metrics, audit events, or explanations.
- Do not let compatibility edits bypass role authorization, approval workflows,
  audit logging, or package-governance rules.
- Do not duplicate canonical arrangement or team metadata if an existing
  approved read-model projection can be extended with versioned compatibility
  fields.

## Subtask 3: Model instrumentation slots, substitutions, coverage rules, and unsupported configurations

### Context

ADR-033 requires required instruments, optional instruments, substitutions, and
unsupported configurations. Worship teams often have partial staffing, alternate
players, or service formats where a song arrangement can work only if certain
roles are covered. The model must distinguish hard requirements from optional
color, allowed substitutions from disallowed substitutions, and unsupported
team formats from merely lower-scoring ones.

**Codebase anchors**

- Source ADR in `docs/adr/ADR-033-arrangement-compatibility-and-instrumentation-modeling.md`
- Team and musician assignment plan in
  `docs/implementation-plans/ADR-023-team-and-musician-assignment-model-plan.md`
- Recommendation scoring plan in
  `docs/implementation-plans/ADR-010-recommendation-engine-scoring-architecture-plan.md`
- Search architecture and discovery plan in
  `docs/implementation-plans/ADR-026-search-architecture-and-discovery-plan.md`

### Prompt

Implement the instrumentation compatibility model and deterministic evaluator.
Represent required slots, optional slots, minimum counts, maximum counts where
relevant, role groups, instrument families, concrete instruments, skill floors,
coverage rules, substitutions, substitution penalties, tracks-assisted coverage,
unsupported instrument/team-format combinations, and evidence references. Add
matching logic that compares an arrangement profile against a supplied team
capability snapshot and request policy. Produce hard-filter decisions for
uncovered required slots or unsupported configurations, soft score adjustments
for optional-slot coverage and substitution penalties, and warnings for partial,
ambiguous, or missing metadata.

### Acceptance criteria

- Required, optional, and forbidden instrumentation rules are represented with
  validated structured fields and stable identifiers.
- The evaluator can determine whether a team satisfies required coverage rules,
  including count requirements, role groups, allowed substitutions, skill floors,
  tracks-assisted coverage, and unsupported configuration exclusions.
- Substitutions are deterministic, scored consistently, and explained with
  source slot, matched team capability, substitution rule, penalty, and reason
  code.
- Optional instrument coverage affects score and explanations without blocking
  arrangements unless the active policy explicitly requires optional coverage.
- Tests cover exact matches, missing required instruments, acceptable
  substitutions, unacceptable substitutions, optional coverage, tracks-assisted
  coverage, count mismatches, skill-floor mismatches, and unsupported formats.

### Restrictions

- Do not treat all instruments as interchangeable within a broad family unless a
  reviewed substitution rule explicitly allows it.
- Do not silently convert a missing required slot into a soft penalty when the
  active policy defines it as a hard requirement.
- Do not infer player skill, role coverage, or instrumentation from free-form
  staff notes or unreviewed profile text.
- Do not allow plugin- or church-defined instruments to bypass validation,
  namespace ownership, or deterministic matching rules.

## Subtask 4: Model vocal, choir, congregation-accessibility, and lead-range compatibility

### Context

ADR-033 requires choir support, vocal parts, lead vocal range, harmony
complexity, and congregation accessibility. Vocal suitability is not equivalent
to key or BPM because a song may be musically approved but unsuitable for the
available lead vocalist, harmony team, choir, or congregation. These fields must
also integrate with arrangement transposition rules without letting
compatibility metadata override approval gates.

**Codebase anchors**

- Arrangement transposition plan in
  `docs/implementation-plans/ADR-006-arrangement-transposition-plan.md`
- Team and musician assignment plan in
  `docs/implementation-plans/ADR-023-team-and-musician-assignment-model-plan.md`
- Musical transition analysis plan in
  `docs/implementation-plans/ADR-031-musical-transition-analysis-engine-plan.md`
- Source ADR in `docs/adr/ADR-033-arrangement-compatibility-and-instrumentation-modeling.md`

### Prompt

Design and implement the vocal compatibility model and evaluator. Represent lead
vocal range requirements, acceptable transposition windows, required or optional
lead vocal types, harmony part requirements, harmony complexity, choir support,
choir voicing requirements, choir difficulty, congregation-accessibility bands,
and vocal warnings. Compare arrangement vocal requirements against scheduled
team capabilities, requested service format, transposition policy, and choir
availability. Produce deterministic hard conflicts, soft score impacts,
transposition-related warnings, and explanation evidence.

### Acceptance criteria

- Vocal compatibility records support lead range, lead type, harmony parts,
  harmony complexity, choir support, choir voicing, choir difficulty, and
  congregation-accessibility metadata with validation rules.
- The evaluator detects lead-range mismatch, unavailable required vocal parts,
  missing choir support, excessive harmony complexity, congregation-accessibility
  concerns, and transposition-dependent suitability changes.
- Transposition handling references ADR-006 policy and preserves approval gates
  for arrangements and transposed variants.
- Vocal and choir compatibility results include structured reason codes,
  evidence references, warning severity, and deterministic score components.
- Tests cover solo-led, worship-team harmony, choir-supported, no-choir,
  transposed, range-mismatch, high-harmony-complexity, and low-congregational-
  accessibility scenarios.

### Restrictions

- Do not infer vocal range, congregation accessibility, or choir voicing from
  lyric sentiment, title, key alone, or unreviewed chart notes.
- Do not assume transposition makes an arrangement recommendable unless the
  transposed arrangement or policy path remains approved and auditable.
- Do not expose private vocalist range or skill details in public explanations,
  logs, metrics, or cross-instance responses.
- Do not collapse choir support into a generic boolean when voicing, difficulty,
  and service-format constraints are needed for deterministic decisions.

## Subtask 5: Model complexity, rehearsal burden, rhythmic difficulty, and technical dependency

### Context

ADR-033 requires arrangement complexity, rehearsal difficulty, rhythmic
complexity, and technical dependency. These factors influence whether an
arrangement is practical for a specific team and service timeline even when all
required instruments are available. Technical dependencies such as click,
tracks, stems, pads, sequencer, in-ear monitors, or media assets may be hard
requirements or soft risks depending on church configuration and service format.

**Codebase anchors**

- Rehearsal and workflow lifecycle plan in
  `docs/implementation-plans/ADR-024-rehearsal-and-workflow-lifecycle-plan.md`
- Media and asset management plan in
  `docs/implementation-plans/ADR-025-media-and-asset-management-plan.md`
- Caching and performance strategy plan in
  `docs/implementation-plans/ADR-027-caching-and-performance-strategy-plan.md`
- Source ADR in `docs/adr/ADR-033-arrangement-compatibility-and-instrumentation-modeling.md`

### Prompt

Define and implement structured complexity and technical-dependency metadata for
arrangements. Represent overall complexity, rehearsal burden, rhythmic
complexity, arrangement density, click/track dependency, stem requirements,
media asset requirements, monitor requirements, notation/chart readiness,
minimum rehearsal time, and confidence/provenance fields. Implement evaluator
logic that compares these fields against team skill levels, rehearsal timeline,
service technology capabilities, asset availability, and request policy.
Generate deterministic compatibility scores, hard conflicts, warnings, and
readiness indicators.

### Acceptance criteria

- Complexity and technical-dependency metadata is represented with bounded
  scales, units, validation rules, provenance, confidence, and review state.
- The evaluator handles hard technical requirements, soft technical risks,
  rehearsal burden thresholds, rhythmic complexity thresholds, skill-floor
  expectations, and missing asset warnings deterministically.
- Rehearsal lifecycle integration can surface readiness blockers or warnings
  before a setlist is finalized when arrangement complexity exceeds available
  preparation capacity.
- Media and asset dependencies reference approved asset records or documented
  external references without making unapproved assets recommendation inputs.
- Tests cover low-complexity acoustic arrangements, high-density full-band
  arrangements, tracks-required arrangements, missing track assets, insufficient
  rehearsal time, high rhythmic complexity, and missing complexity metadata.

### Restrictions

- Do not use BPM as the only proxy for complexity or rehearsal burden.
- Do not make unapproved media assets, charts, rehearsal notes, or imported files
  authoritative compatibility inputs.
- Do not hide missing or low-confidence complexity metadata behind a normal
  compatibility score.
- Do not expose private team skill limitations beyond the minimum information
  needed for authorized planning explanations.

## Subtask 6: Integrate ADR-023 team capability snapshots and request policy resolution

### Context

ADR-033 explicitly associates arrangement suitability with team capabilities from
ADR-023. Recommendation requests may provide assigned musicians, a team format,
instruments available, service type, choir availability, tracks policy, or no
team context at all. Cadentia needs deterministic policy resolution so the same
request and team snapshot always produce the same compatibility filters,
scoring weights, and warnings.

**Codebase anchors**

- Team and musician assignment plan in
  `docs/implementation-plans/ADR-023-team-and-musician-assignment-model-plan.md`
- LLM intent extraction contract plan in
  `docs/implementation-plans/ADR-012-llm-intent-extraction-contract-plan.md`
- Guided menu and conversational request flow plan in
  `docs/implementation-plans/ADR-015-guided-menu-and-conversational-request-flow-plan.md`
- Service plan integration model plan in
  `docs/implementation-plans/ADR-018-service-plan-integration-model-plan.md`
- Security roles and permissions plan in
  `docs/implementation-plans/ADR-019-security-roles-and-permissions-plan.md`

### Prompt

Implement team capability snapshot resolution and compatibility request policy
resolution. Define how assigned team members, team templates, service-plan
roles, requested team format, available instruments, choir availability, tracks
policy, rehearsal date, and service type are converted into a privacy-safe
capability snapshot. Define precedence among explicit request constraints,
service-plan assignments, church defaults, profile defaults, and package
defaults. Add behavior for missing team context, partial team context,
conflicting constraints, stale team assignments, and authorization failures.
Ensure LLM intent output can request or pass through allowed policy hints without
inventing team capabilities or compatibility metadata.

### Acceptance criteria

- Compatibility evaluation consumes a stable, versioned team capability snapshot
  with privacy-safe role, instrument, vocal, skill-floor, choir, and technology
  fields.
- Policy precedence is documented and implemented for explicit request values,
  service-plan values, church defaults, arrangement-profile defaults, and
  package defaults.
- Missing or partial team context follows documented behavior, such as advisory
  scoring only, default team-format filtering, or no hard staffing filter unless
  the request requires it.
- Authorization checks prevent users from evaluating private team capabilities
  outside their church instance or role permissions.
- Tests cover explicit team assignments, team templates, no team context,
  conflicting request constraints, stale assignments, unauthorized access, and
  LLM-provided policy hints.

### Restrictions

- Do not let the LLM invent assigned musicians, instruments, skill levels,
  vocal ranges, or technology capabilities.
- Do not evaluate private team data across church-instance boundaries.
- Do not make compatibility scoring nondeterministic by reading mutable team
  state without a versioned snapshot reference.
- Do not require a full team assignment for every recommendation if the active
  policy allows compatibility to be advisory rather than a hard filter.

## Subtask 7: Implement church-specific overrides, packaged defaults, governance, and audit behavior

### Context

ADR-033 requires church-specific arrangement compatibility overrides without
mutating global catalog defaults. ADR-022 establishes package and church
customization boundaries, while ADR-019 and ADR-011 govern authorization and
review. Overrides are needed because one church may consider a song workable in a
stripped-down format while another requires tracks or full band.

**Codebase anchors**

- Packaged deployment and church customization plan in
  `docs/implementation-plans/ADR-022-packaged-deployment-and-church-customization-model-plan.md`
- Admin review and catalog governance UI plan in
  `docs/implementation-plans/ADR-011-admin-review-catalog-governance-ui-plan.md`
- Security roles and permissions plan in
  `docs/implementation-plans/ADR-019-security-roles-and-permissions-plan.md`
- Eventing and async processing plan in
  `docs/implementation-plans/ADR-028-eventing-and-async-processing-architecture-plan.md`

### Prompt

Design and implement the church override model for arrangement compatibility.
Represent override operations such as add slot, remove optional slot, tighten
required slot, loosen allowed substitution, mark unsupported format, adjust
complexity threshold, add local technical dependency, add local reviewer note,
and retire override. Define merge/overlay rules between global package defaults,
arrangement profile versions, church package customizations, and local church
instance overrides. Add governance workflows, role permissions, audit events,
cache invalidation, event publication, rollback behavior, and conflict handling
when upstream package metadata changes.

### Acceptance criteria

- Church-specific overrides are stored separately from global catalog defaults
  and can be applied, versioned, retired, audited, and rolled back per church
  instance.
- Overlay precedence and conflict behavior are deterministic and documented for
  global defaults, package updates, church customization layers, and local
  overrides.
- Override workflows enforce role authorization, approval or review where
  required, provenance, reviewer notes, audit logging, and package-governance
  boundaries.
- Upstream package changes trigger documented revalidation, cache invalidation,
  and conflict-resolution behavior for affected church overrides.
- Tests cover override creation, tightening constraints, loosening constraints,
  unsupported local format, upstream conflict, rollback, unauthorized override,
  and cross-instance isolation.

### Restrictions

- Do not mutate global/package compatibility defaults when applying a local
  church override.
- Do not allow local overrides to bypass arrangement approval, licensing,
  doctrinal review, active-catalog status, or provenance requirements.
- Do not leak another church's override values, reviewer notes, or audit events
  through recommendation results, APIs, logs, metrics, or explanations.
- Do not make overlay rules dependent on write order when version and precedence
  should determine the effective result.

## Subtask 8: Integrate deterministic filtering, scoring, ordering, and Recommendation Engine behavior

### Context

ADR-033 states that the Recommendation Engine may use compatibility metadata for
hard filters, scoring, and explanations, but the LLM must never select songs.
Compatibility must fit into existing recommendation architecture, including
approved-only candidate retrieval, praise/worship count constraints, key-center
and tempo policies, transition analysis, energy arcs, familiarity, and stable
tie-breaking.

**Codebase anchors**

- Recommendation scoring plan in
  `docs/implementation-plans/ADR-010-recommendation-engine-scoring-architecture-plan.md`
- Recommendation read-model plan in
  `docs/implementation-plans/ADR-002-recommendation-read-model-plan.md`
- Musical transition analysis plan in
  `docs/implementation-plans/ADR-031-musical-transition-analysis-engine-plan.md`
- Energy arc modeling plan in
  `docs/implementation-plans/ADR-032-energy-arc-modeling-plan.md`
- Congregational familiarity ADR in
  `docs/adr/ADR-034-congregational-familiarity-model.md`

### Prompt

Implement compatibility-aware candidate filtering and scoring in the
Recommendation Engine. Add a compatibility stage that runs after approval and
catalog eligibility gates and before final ordering. Apply hard filters for
unsupported formats, missing required slots, disallowed technical dependencies,
request-required choir/vocal constraints, and policy-defined required metadata.
Apply score components for suitability flags, optional coverage, substitution
quality, complexity fit, rehearsal burden, vocal fit, technical readiness, and
congregation accessibility. Define deterministic aggregation, weighting,
normalization, warning propagation, tie-breaking, and interaction with existing
key, tempo, transition, energy, familiarity, and theme scoring.

### Acceptance criteria

- Compatibility evaluation is integrated into the Recommendation Engine without
  allowing the compatibility component to select songs independently.
- Approved-only and arrangement-approval gates execute before compatibility
  scoring, and an otherwise compatible but unapproved arrangement remains
  unrecommendable.
- Hard filters and score components are deterministic for the same candidate
  snapshot, compatibility profile, team snapshot, church override, and request
  policy.
- Compatibility scores and warnings participate in final ranking with documented
  weights and do not destabilize existing deterministic tie-breaking rules.
- Tests cover recommendation requests with acoustic team, full band, choir,
  tracks-assisted, stripped-down, missing team context, hard conflict, soft
  warning, and multi-arrangement song candidates.

### Restrictions

- Do not rely on an LLM for song, arrangement, or compatibility selection.
- Do not run compatibility filters before approval and visibility gates in a way
  that could reveal unapproved or unauthorized arrangements.
- Do not hide compatibility conflicts by letting other positive score components
  fully override a hard policy violation.
- Do not make compatibility scoring depend on current wall-clock time except
  through an explicit, versioned request or service-plan snapshot.

## Subtask 9: Expose ADR-021 explanations, API/read-model fields, and admin/search query surfaces

### Context

ADR-033 requires compatibility conflicts to be explainable and auditable, and it
aligns explanations with ADR-021 reason codes. Worship leaders and administrators
need to see why an arrangement was recommended, penalized, or excluded, while
search and admin views need queryable suitability metadata. Explanation and API
surfaces must avoid leaking private team details or cross-instance overrides.

**Codebase anchors**

- Recommendation Engine explainability API plan in
  `docs/implementation-plans/ADR-021-recommendation-engine-explainability-api-plan.md`
- Recommendation explanation system plan in
  `docs/implementation-plans/ADR-013-recommendation-explanation-system-plan.md`
- Search architecture and discovery plan in
  `docs/implementation-plans/ADR-026-search-architecture-and-discovery-plan.md`
- Administrative web interface ADR in
  `docs/adr/ADR-036-administrative-web-interface.md`
- OpenAPI files under `apps/api/src/main/openapi/`

### Prompt

Add compatibility facts, warnings, reason codes, and query fields to explanation,
API, search, and admin surfaces. Define audience-appropriate payloads for
recommendation details, exclusion diagnostics, admin metadata review, search
facets, and audit views. Include reason codes, score components, evidence
references, slot matches, substitution details, missing requirements,
unsupported configurations, vocal/choir findings, complexity findings,
technical-dependency findings, override references, and snapshot identifiers.
Update OpenAPI contracts and generated models when API changes are required, and
verify the split OpenAPI files remain valid.

### Acceptance criteria

- ADR-021 explanation payloads include structured compatibility score facts,
  warnings, conflicts, evidence references, and reason codes for recommended,
  penalized, and excluded arrangements where authorized.
- API and read-model fields support querying arrangement suitability by team
  format, instruments, vocal/choir support, complexity, rehearsal burden,
  technical dependency, and church override status.
- Admin surfaces can review, edit, approve, supersede, retire, and audit
  compatibility metadata without exposing unauthorized private team data.
- Search facets and filters use approved compatibility metadata and respect
  church-instance visibility and override overlays.
- If OpenAPI changes are made, `mvn -pl apps/api -DskipTests generate-sources`
  passes and generated interfaces/models stay in sync.

### Restrictions

- Do not expose private musician identities, private vocal ranges, skill
  limitations, or another church's override details in public recommendation
  explanations.
- Do not return free-form prose as the only explanation for compatibility
  conflicts; include machine-readable reason codes and evidence references.
- Do not add API fields directly to the aggregate OpenAPI entrypoint if they
  belong in the split paths or components files.
- Do not let search index unapproved, retired, deleted, or unauthorized
  compatibility metadata.

## Subtask 10: Add migration, fixtures, testing, observability, rollout controls, and documentation

### Context

Arrangement compatibility touches catalog data, church overrides, team snapshots,
Recommendation Engine ranking, explanation payloads, search filters, admin
workflows, and audit records. A safe rollout requires repeatable migrations,
fixture coverage, performance checks, observability, feature flags, backfill
operations, and documentation for administrators and future AI agents.

**Codebase anchors**

- Observability and telemetry strategy plan in
  `docs/implementation-plans/ADR-029-observability-and-telemetry-strategy-plan.md`
- Eventing and async processing architecture plan in
  `docs/implementation-plans/ADR-028-eventing-and-async-processing-architecture-plan.md`
- Caching and performance strategy plan in
  `docs/implementation-plans/ADR-027-caching-and-performance-strategy-plan.md`
- Runbooks under `docs/runbooks/`
- Seed data documentation in `docs/seed-data.md`

### Prompt

Prepare compatibility modeling for production rollout. Add database migrations,
seed fixtures, golden recommendation scenarios, backfill tooling, validation
scripts, read-model rebuild behavior, cache invalidation behavior, event
contracts, metrics, traces, structured logs, audit events, alert thresholds,
feature flags, rollout phases, rollback steps, and operator documentation.
Create regression fixtures for representative acoustic, full-band, choir,
tracks-assisted, stripped-down, high-complexity, low-complexity, partial-team,
missing-metadata, override, and unauthorized-access scenarios. Document how to
review compatibility metadata, troubleshoot exclusions, and verify that approval
gates remain authoritative.

### Acceptance criteria

- Migrations and backfill tooling can introduce compatibility metadata without
  making unreviewed or incomplete metadata recommendable by default.
- Golden fixtures and automated tests cover persistence, approval gating,
  override overlays, team matching, vocal/choir matching, complexity evaluation,
  Recommendation Engine integration, explanations, API payloads, search filters,
  audit events, and cross-instance isolation.
- Observability includes privacy-safe metrics, logs, traces, audit events, and
  alerts for compatibility evaluation counts, hard-filter rates, missing metadata
  rates, override conflicts, cache invalidations, read-model lag, and scoring
  latency.
- Rollout controls support feature-flagged advisory mode, hard-filter mode,
  per-church enablement, rollback, read-model rebuild, and package update
  revalidation.
- Documentation covers metadata entry, approval workflow, override governance,
  recommendation troubleshooting, explanation reason codes, fixture maintenance,
  and operational runbooks.

### Restrictions

- Do not backfill compatibility metadata from LLM guesses, unreviewed chart
  parsing, raw lyrics, private musician notes, or unsupported external sources.
- Do not enable hard compatibility filters globally until fixtures, metrics,
  rollback procedures, and operator documentation are complete.
- Do not log sensitive team capability details or cross-instance override values
  in observability data.
- Do not let tests depend on mutable wall-clock time, non-deterministic ordering,
  or network-only fixtures.
