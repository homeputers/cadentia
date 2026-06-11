# ADR-031 Implementation Plan: Musical Transition Analysis Engine

## Objective

Implement a deterministic Musical Transition Analysis Engine that evaluates
ordered arrangement pairs for harmonic, tempo, meter, cadence, and policy
compatibility using approved musical metadata. The engine must provide
machine-readable transition facts, scores, warnings, and evidence to the
Recommendation Engine and ADR-021 explanation surfaces without invoking an LLM or
analyzing arrangements outside the current church-instance authorization scope.

## Source ADR

- [ADR-031: Musical Transition Analysis Engine](../adr/ADR-031-musical-transition-analysis-engine.md)

## Status

Overall status: Planned.

- Subtask 1: Planned - transition domain model, terminology, reason-code, and
  policy specification.
- Subtask 2: Planned - approved metadata inputs, snapshot boundaries,
  confidence handling, and eligibility gates.
- Subtask 3: Planned - key relationship and modulation-distance analyzer.
- Subtask 4: Planned - modulation smoothness, section-boundary, and cadence
  continuity analyzer.
- Subtask 5: Planned - tempo, meter, and energy movement policy evaluator.
- Subtask 6: Planned - deterministic score aggregation, warnings, and directional
  transition result contract.
- Subtask 7: Planned - Recommendation Engine integration and adjacent-pair query
  path.
- Subtask 8: Planned - ADR-021 explanation facts and API/read-model exposure.
- Subtask 9: Planned - test fixtures, golden datasets, and regression coverage.
- Subtask 10: Planned - observability, performance, caching, and operations
  readiness.

## Guiding Principles

- The engine analyzes transitions only; it must not select songs, bypass the
  Recommendation Engine, or create final setlists.
- All analysis must be deterministic for the same ordered arrangement pair,
  metadata snapshot, and transition policy.
- Only approved arrangements and approved musical metadata within the current
  church-instance context may be analyzed.
- Low-confidence, partial, stale, or derived musical metadata must be surfaced as
  warnings and evidence, not silently treated as authoritative truth.
- Relative major/minor compatibility is a first-class rule and must appear in
  both scoring and explanations when applicable.
- Explanation output must be structured facts and reason codes suitable for
  ADR-021; no LLM should be invoked to interpret or generate transition facts.
- Advanced analysis should degrade predictably when optional metadata such as
  ending sections, opening sections, cadence labels, or chord-confidence values
  is unavailable.

## Subtask 1: Define transition domain model, terminology, reason codes, and policy specification

### Context

ADR-031 introduces a dedicated deterministic component for directional
arrangement-pair analysis. Before implementing algorithms, Cadentia needs a
shared vocabulary for keys, modes, tonal centers, modulation distance, cadence
continuity, confidence, evidence, warnings, and policy decisions. This contract
must align with existing recommendation scoring, arrangement transposition,
explainability, observability, and plugin boundaries.

**Codebase anchors**

- Source ADR in `docs/adr/ADR-031-musical-transition-analysis-engine.md`
- Recommendation scoring plan in
  `docs/implementation-plans/ADR-010-recommendation-engine-scoring-architecture-plan.md`
- Recommendation explainability plan in
  `docs/implementation-plans/ADR-021-recommendation-engine-explainability-api-plan.md`
- Arrangement transposition plan in
  `docs/implementation-plans/ADR-006-arrangement-transposition-plan.md`
- Plugin extension plan in
  `docs/implementation-plans/ADR-030-plugin-and-extension-architecture-plan.md`

### Prompt

Create the transition-analysis domain specification and initial code-level
contract. Define directional input and output types for a source arrangement,
destination arrangement, metadata snapshot identifier, church-instance scope,
request context, and transition policy. Enumerate reason-code families for key
relationship, modulation, harmonic compatibility, cadence, tempo movement,
meter continuity, policy overrides, missing metadata, and low-confidence
metadata. Define policy fields for maximum BPM jump, allowed modulation distance,
preferred key centers, relative major/minor handling, parallel-mode handling,
minimum metadata confidence, hard-cut or medley allowances, and scoring weights.
Document the expected score range, score semantics, warning severities,
evidence references, and deterministic ordering rules for returned facts.

### Acceptance criteria

- A durable specification or code contract defines transition-analysis request,
  policy, evidence, warning, reason-code, component-score, and result shapes.
- Directionality is explicit: analyzing `Arrangement A -> Arrangement B` is not
  assumed equivalent to `Arrangement B -> Arrangement A`.
- Reason codes cover same key, relative major/minor, parallel mode,
  circle-of-fifths proximity, modulation distance, cadence compatibility, tempo
  movement, meter continuity, missing metadata, low confidence, and policy
  violations.
- Transition policy defaults match existing recommendation intent expectations
  where applicable, including controlled BPM jumps and limited key centers.
- The specification documents which ADR-031 open questions are answered for v1
  and which are deferred with non-blocking defaults.

### Restrictions

- Do not define a song-selection API or allow the transition engine to produce a
  setlist.
- Do not use free-form prose as the primary machine contract for transition
  results; use enumerations and structured fields.
- Do not rely on an LLM to classify key relationships, cadence quality, or
  transition explanations.
- Do not make low-confidence metadata indistinguishable from approved,
  high-confidence metadata.

## Subtask 2: Establish approved metadata inputs, snapshot boundaries, confidence handling, and eligibility gates

### Context

ADR-031 requires analysis based on approved musical metadata and forbids
analysis of unapproved or private arrangements outside the request instance
context. The engine depends on data produced by song import, lyrics parsing,
musical analysis, approval governance, transposition, and the recommendation
read model. It must know which metadata is source-of-truth, which is derived,
which confidence fields are available, and which authorization filters must run
before analysis.

**Codebase anchors**

- Song data infrastructure plan in
  `docs/implementation-plans/ADR-001-song-data-infrastructure-plan.md`
- Recommendation read-model plan in
  `docs/implementation-plans/ADR-002-recommendation-read-model-plan.md`
- Lyrics parsing and musical analysis plan in
  `docs/implementation-plans/ADR-009-lyrics-parsing-musical-analysis-pipeline-plan.md`
- Approval and doctrinal review plan in
  `docs/implementation-plans/ADR-005-approval-doctrinal-review-plan.md`
- Security roles and permissions plan in
  `docs/implementation-plans/ADR-019-security-roles-and-permissions-plan.md`

### Prompt

Map the transition engine's required and optional metadata inputs to existing
catalog and read-model records. Identify canonical fields for arrangement ID,
song ID, approval status, church-instance scope, visibility, source key,
starting key, ending key, mode, BPM, meter, section boundaries, opening section,
ending section, chord/key analysis, cadence labels, metadata confidence, parser
version, transposition state, provenance, and snapshot identifiers. Implement or
specify a metadata access layer that returns only authorized, approved,
instance-eligible arrangement metadata for a given request. Add confidence
threshold handling and warning creation for missing, partial, stale,
low-confidence, or transposed metadata.

### Acceptance criteria

- The transition engine has a single input adapter or repository boundary for
  retrieving approved arrangement metadata with church-instance and visibility
  filters applied.
- Metadata snapshots include stable identifiers or versions so repeated analysis
  can be traced to the same catalog/read-model state.
- Unapproved, deleted, private-out-of-scope, wrong-instance, or otherwise
  ineligible arrangements are rejected before scoring.
- Required fields and optional advanced-analysis fields are documented with
  fallback behavior when unavailable.
- Low-confidence chord, key, section, or cadence data produces structured
  warnings and reduced component confidence rather than silent full-credit
  analysis.

### Restrictions

- Do not query raw unapproved import staging records for recommendation-time
  transition analysis.
- Do not let cross-instance private arrangements leak through IDs, titles,
  metadata, errors, explanations, logs, or metrics.
- Do not treat parser-derived chord or cadence data as authoritative without
  recording confidence and provenance.
- Do not duplicate source-of-truth musical metadata if an existing read-model
  projection can provide a versioned, approved snapshot.

## Subtask 3: Implement key relationship and modulation-distance analysis

### Context

ADR-031 requires same-key, relative major/minor, parallel mode,
circle-of-fifths proximity, configured modulation allowances, and first-class
relative-key compatibility. This analyzer is the foundation for both scoring and
explainability because worship leaders need clear evidence about why two keys
flow smoothly or require attention.

**Codebase anchors**

- Arrangement transposition rules in `docs/arrangement-transposition-rules.md`
- Arrangement transposition plan in
  `docs/implementation-plans/ADR-006-arrangement-transposition-plan.md`
- Recommendation scoring architecture plan in
  `docs/implementation-plans/ADR-010-recommendation-engine-scoring-architecture-plan.md`
- Source ADR in `docs/adr/ADR-031-musical-transition-analysis-engine.md`

### Prompt

Implement the key relationship analyzer for ordered arrangement pairs. Normalize
keys and modes, including enharmonic equivalents where the project already
supports them. Detect same key, relative major/minor, parallel mode, compatible
closely related keys, circle-of-fifths distance, configured maximum modulation
distance, preferred key-center membership, and disallowed or high-friction key
moves. Return component scores, deterministic reason codes, evidence facts, and
warnings when key data is missing, ambiguous, transposed, or below confidence
thresholds. Add a clear fallback path for v1 if the default harmonic distance
model remains unresolved.

### Acceptance criteria

- Same-key transitions are recognized with a stable high-compatibility reason
  code and evidence.
- Relative major/minor transitions are recognized in both directions and scored
  as a first-class compatibility rule.
- Parallel major/minor and circle-of-fifths proximity are distinguished from
  relative major/minor rather than collapsed into one generic key-change reason.
- Configured allowed modulation distance and preferred key centers influence the
  key component score and policy-violation warnings deterministically.
- Tests cover enharmonic normalization, major/minor mode handling, unknown key
  data, transposed arrangements, relative-key examples, and out-of-policy key
  movement.

### Restrictions

- Do not compare key strings lexically without normalizing pitch class and mode.
- Do not mark every adjacent circle-of-fifths move as acceptable if the active
  transition policy disallows that distance.
- Do not hide ambiguous, missing, or low-confidence key metadata behind a normal
  compatibility score.
- Do not invoke an LLM or external music-theory service for key relationship
  classification.

## Subtask 4: Implement modulation smoothness, section-boundary, and cadence continuity analysis

### Context

ADR-031 requires modulation smoothness based on source ending key/section and
destination starting key/section when available, plus cadence continuity from
ending and opening sections. This analysis should improve transition quality
when richer arrangement metadata exists while preserving deterministic fallback
behavior when only basic key and BPM fields are available.

**Codebase anchors**

- Lyrics parsing and musical analysis plan in
  `docs/implementation-plans/ADR-009-lyrics-parsing-musical-analysis-pipeline-plan.md`
- Recommendation explanation system plan in
  `docs/implementation-plans/ADR-013-recommendation-explanation-system-plan.md`
- Recommendation explainability API plan in
  `docs/implementation-plans/ADR-021-recommendation-engine-explainability-api-plan.md`
- Source ADR in `docs/adr/ADR-031-musical-transition-analysis-engine.md`

### Prompt

Add analyzers for modulation smoothness, arrangement section boundaries, and
cadence continuity. Use ending key, ending section, final cadence or closing
chord confidence, destination starting key, opening section, opening chord or
cadence confidence, and section labels when available. Classify transitions such
as resolved cadence into same key, resolved cadence into relative key, unresolved
or deceptive cadence, abrupt start, intentional hard cut, medley continuation,
and insufficient section data. Produce component scores, reason codes, evidence,
and warnings. Define v1 fallback behavior for arrangements without section or
cadence metadata so the engine can still return a deterministic result.

### Acceptance criteria

- Modulation analysis prefers source ending key and destination starting key over
  generic arrangement key when those approved fields are available.
- Cadence continuity analysis emits distinct reason codes for compatible cadence,
  weak cadence, incompatible cadence, missing cadence metadata, and low-confidence
  cadence metadata.
- Section-boundary analysis recognizes opening and ending section labels where
  available and records evidence references without exposing copyrighted lyrics.
- Hard-cut and medley behavior is represented as explicit policy or arrangement
  metadata rather than an undocumented scoring exception.
- Tests cover full metadata, partial metadata, missing section data,
  low-confidence cadence data, medley continuation, and intentional hard-cut
  policy behavior.

### Restrictions

- Do not inspect or expose raw copyrighted lyrics in transition explanations or
  logs.
- Do not assume arrangement-level key is always the same as ending key or
  starting key when more specific metadata is available.
- Do not fail the whole transition analysis just because optional section or
  cadence metadata is missing.
- Do not infer medleys or intentional hard cuts from song titles or free-text
  notes without approved structured metadata.

## Subtask 5: Implement tempo, meter, and energy movement policy evaluation

### Context

ADR-031 includes tempo movement and meter continuity as transition factors, and
Cadentia's existing recommendation constraints include controlled BPM jumps.
Phase 4 also includes energy-arc modeling, so the transition engine should keep
its scope clear: it evaluates adjacent-pair movement and provides facts that can
be consumed by broader setlist scoring without becoming an energy-arc planner.

**Codebase anchors**

- Recommendation scoring architecture plan in
  `docs/implementation-plans/ADR-010-recommendation-engine-scoring-architecture-plan.md`
- Energy arc ADR in `docs/adr/ADR-032-energy-arc-modeling.md`
- Recommendation explainability API plan in
  `docs/implementation-plans/ADR-021-recommendation-engine-explainability-api-plan.md`
- Source ADR in `docs/adr/ADR-031-musical-transition-analysis-engine.md`

### Prompt

Implement deterministic tempo, meter, and adjacent-energy movement evaluators.
Compare source ending tempo or arrangement BPM to destination starting tempo or
arrangement BPM using the active maximum BPM jump policy. Identify smooth,
acceptable, caution, and out-of-policy tempo moves. Compare meter signatures for
same-meter, compatible meter-family, meter-change caution, and missing-meter
cases. If adjacent energy metadata exists, expose it as an evidence fact and
policy input without taking ownership of whole-set energy-arc planning. Ensure
scores and warnings are directional and deterministic.

### Acceptance criteria

- BPM movement is evaluated against the configured maximum jump and emits stable
  reason codes for within-policy, caution, out-of-policy, missing BPM, and
  low-confidence BPM data.
- Meter continuity is evaluated separately from tempo and produces reason codes
  for same meter, compatible meter family, notable meter change, missing meter,
  and low-confidence meter data.
- Adjacent energy facts, if present, are included as evidence or advisory
  signals without replacing ADR-032 whole-set energy-arc logic.
- Tempo and meter scores can be weighted independently from harmonic and cadence
  scores in the aggregate transition policy.
- Tests cover exact threshold boundaries, missing BPM, compound/simple meter
  cases, large tempo jumps, small tempo moves, and independent weighting.

### Restrictions

- Do not use global default BPM or meter values that make missing metadata look
  precise.
- Do not make tempo compatibility the sole determinant of transition quality.
- Do not implement whole-set energy-arc sequencing in the transition engine.
- Do not expose song titles, service-plan names, or instance identifiers in
  metric labels while evaluating tempo or meter behavior.

## Subtask 6: Implement deterministic score aggregation, warnings, and directional transition result contract

### Context

Individual analyzers must be combined into one directional transition result
that the Recommendation Engine can use consistently. The aggregate must balance
harmonic relationship, modulation smoothness, cadence continuity, tempo, meter,
confidence, and policy overrides while preserving machine-readable details for
explainability and debugging.

**Codebase anchors**

- Recommendation scoring architecture plan in
  `docs/implementation-plans/ADR-010-recommendation-engine-scoring-architecture-plan.md`
- Recommendation explanation system plan in
  `docs/implementation-plans/ADR-013-recommendation-explanation-system-plan.md`
- Recommendation explainability API plan in
  `docs/implementation-plans/ADR-021-recommendation-engine-explainability-api-plan.md`
- Observability plan in
  `docs/implementation-plans/ADR-029-observability-and-telemetry-strategy-plan.md`

### Prompt

Implement the aggregate transition scorer and result contract. Combine component
scores using a versioned policy snapshot, deterministic weights, confidence
adjustments, hard constraints, and warning severities. Return an aggregate score,
component scores, reason codes, policy outcome, evidence references, warnings,
metadata snapshot ID, policy snapshot ID, analyzer version, and deterministic
sort order for facts. Define how hard policy failures, soft cautions, missing
optional metadata, and low-confidence evidence affect the final score. Add a
stable serialization shape suitable for persistence, explanation rendering,
caching, and test fixtures.

### Acceptance criteria

- The same ordered pair, metadata snapshot, analyzer version, and policy snapshot
  always produce byte-stable or semantically equivalent results.
- Aggregate results include component scores, total score, reason codes,
  warnings, evidence references, metadata snapshot ID, policy snapshot ID, and
  analyzer version.
- Hard policy failures and soft warnings are distinguishable and queryable.
- Low-confidence metadata reduces confidence or emits warnings without silently
  becoming full-confidence scoring input.
- Tests cover deterministic serialization, component weighting, hard policy
  failures, missing optional metadata, warning ordering, and analyzer-version
  changes.

### Restrictions

- Do not rely on unordered maps, nondeterministic iteration, wall-clock time, or
  random values in scoring output.
- Do not collapse all warnings into a single human string that clients cannot
  filter.
- Do not make policy overrides invisible to explanation or audit surfaces.
- Do not persist raw chord charts, lyrics, or copyrighted excerpts as evidence.

## Subtask 7: Integrate transition analysis with the Recommendation Engine and adjacent-pair query path

### Context

ADR-031 says the transition engine is an internal component used by the
Recommendation Engine. It must support ordered candidate evaluation and final
adjacent-pair explanations without allowing the engine to recommend songs by
itself. Integration should respect ADR-010 scoring boundaries, approval filters,
read-model snapshots, and deterministic recommendation output.

**Codebase anchors**

- Recommendation scoring architecture plan in
  `docs/implementation-plans/ADR-010-recommendation-engine-scoring-architecture-plan.md`
- Recommendation read-model plan in
  `docs/implementation-plans/ADR-002-recommendation-read-model-plan.md`
- Recommendation explainability API plan in
  `docs/implementation-plans/ADR-021-recommendation-engine-explainability-api-plan.md`
- Source ADR in `docs/adr/ADR-031-musical-transition-analysis-engine.md`

### Prompt

Wire the transition engine into Recommendation Engine candidate ordering and
final setlist analysis. Add internal service interfaces for batch analyzing
ordered adjacent candidate pairs, retrieving transition compatibility for a
specific pair, and attaching transition result references to generated setlist
items. Ensure candidate eligibility, approval filtering, instance isolation,
policy construction, and read-model snapshot selection occur before analysis.
Define how transition scores contribute to recommendation scoring without
allowing transition analysis to introduce new candidates or select unapproved
songs.

### Acceptance criteria

- Recommendation execution can request transition scores for adjacent candidate
  pairs through an internal deterministic service boundary.
- Transition scores contribute to ordering or scoring only after existing
  eligibility and approval filters have removed ineligible arrangements.
- The engine can return pair-level compatibility for final setlist adjacent pairs
  and for diagnostic query paths authorized for the current instance.
- Batch analysis avoids repeated metadata fetching for the same snapshot where
  practical and preserves deterministic ordering of results.
- Tests cover Recommendation Engine integration, pair-query authorization,
  unapproved candidate rejection, private-instance isolation, and deterministic
  final setlist transition facts.

### Restrictions

- Do not let the transition engine expand the candidate pool or directly choose
  final setlist membership.
- Do not analyze every catalog pair indiscriminately at request time if a scoped
  candidate subset is available.
- Do not expose pair compatibility for arrangements the caller could not
  otherwise access.
- Do not make transition scoring depend on client-side ordering or unvalidated
  request payloads.

## Subtask 8: Expose transition explanation facts through ADR-021 surfaces without invoking the LLM

### Context

ADR-031 requires explanation facts to be exposed to ADR-021. Worship leaders and
operators need to understand transition compatibility per adjacent pair, but the
facts must remain deterministic, audience-safe, and backed by approved metadata.
Explanation rendering may turn facts into UI text, but the underlying service
must provide structured reason codes, evidence, warnings, and policy outcomes.

**Codebase anchors**

- Recommendation explainability API plan in
  `docs/implementation-plans/ADR-021-recommendation-engine-explainability-api-plan.md`
- Recommendation explanation system plan in
  `docs/implementation-plans/ADR-013-recommendation-explanation-system-plan.md`
- OpenAPI contract under `apps/api/src/main/openapi/`
- Source ADR in `docs/adr/ADR-031-musical-transition-analysis-engine.md`

### Prompt

Extend explanation models and, where appropriate, OpenAPI/read-model surfaces to
include adjacent transition facts. Provide fields for source arrangement
reference, destination arrangement reference, aggregate transition score,
component scores, reason codes, policy outcome, evidence references, warnings,
metadata snapshot, policy snapshot, and analyzer version. Add audience filtering
so worship planners can see practical transition explanations while admin or
operator views can see deeper confidence and provenance details. Ensure rendered
messages are derived from deterministic facts and localization templates, not an
LLM.

### Acceptance criteria

- ADR-021-compatible explanation output includes transition facts for each
  adjacent pair in a generated or persisted setlist.
- API or read-model contracts expose machine-readable reason codes and warnings,
  not only prose descriptions.
- Audience filtering prevents unauthorized exposure of private instance data,
  privileged metadata, parser internals, or provenance details.
- Relative major/minor, low-confidence metadata, out-of-policy BPM jumps, and
  cadence warnings are explainable with distinct structured facts.
- Contract tests or snapshot tests verify the explanation shape and deterministic
  rendering for representative transition outcomes.

### Restrictions

- Do not call an LLM to create, summarize, or classify transition explanations.
- Do not expose raw lyrics, copyrighted chord charts, privileged review notes,
  or private cross-instance identifiers as explanation evidence.
- Do not add a public API that bypasses recommendation authorization or approval
  eligibility checks.
- Do not collapse warnings into localized prose before clients receive the
  structured facts they need for filtering and accessibility.

## Subtask 9: Build test fixtures, golden datasets, and regression coverage

### Context

Deterministic transition analysis requires broad fixture coverage across music
metadata combinations and policy settings. Golden datasets should verify exact
reason codes, score bands, warnings, and evidence without relying on live
catalog data or copyrighted content.

**Codebase anchors**

- Seed data guidance in `docs/seed-data.md`
- Lyrics handling guidance in `docs/lyrics-handling.md`
- Recommendation scoring architecture plan in
  `docs/implementation-plans/ADR-010-recommendation-engine-scoring-architecture-plan.md`
- Observability plan in
  `docs/implementation-plans/ADR-029-observability-and-telemetry-strategy-plan.md`

### Prompt

Create transition-analysis fixture data and regression tests. Include synthetic
or licensed-safe arrangements that cover same key, relative major/minor, parallel
mode, circle-of-fifths movement, disallowed modulation, smooth cadence,
incompatible cadence, missing section metadata, low-confidence chord/key data,
BPM threshold boundaries, meter changes, medley continuation, hard-cut policy,
unapproved arrangements, private out-of-scope arrangements, and deterministic
batch ordering. Add golden expected outputs for component scores, aggregate
scores, reason codes, warnings, and evidence references.

### Acceptance criteria

- Fixtures are copyright-safe and contain enough approved metadata to exercise
  harmonic, cadence, tempo, meter, confidence, and policy behavior.
- Golden tests verify reason codes, warning severities, aggregate scores,
  component scores, evidence references, and deterministic ordering.
- Negative tests prove unapproved, deleted, private-out-of-scope, and
  wrong-instance arrangements are rejected before scoring.
- Regression tests cover relative major/minor in both directions and at least
  one low-confidence metadata warning per advanced analyzer.
- Test documentation explains how to add future transition cases without using
  copyrighted lyrics or unapproved catalog data.

### Restrictions

- Do not use real copyrighted lyrics or chord charts in fixtures unless the
  project already has documented rights for that exact fixture use.
- Do not make tests depend on external services, live imports, current dates, or
  nondeterministic catalog ordering.
- Do not assert only broad score ranges when reason-code or warning regressions
  would be important to detect.
- Do not skip authorization and approval-gating tests just because the analyzer
  itself is an internal component.

## Subtask 10: Add observability, performance, caching, and operations readiness

### Context

Transition analysis can add recommendation computation cost, especially if many
candidate pairs are evaluated. ADR-029 requires privacy-aware telemetry, and
ADR-027 defines caching strategy. Operators need visibility into latency,
metadata quality warnings, policy failures, cache behavior, and analyzer errors
without leaking sensitive catalog or instance data.

**Codebase anchors**

- Observability and telemetry plan in
  `docs/implementation-plans/ADR-029-observability-and-telemetry-strategy-plan.md`
- Caching and performance strategy plan in
  `docs/implementation-plans/ADR-027-caching-and-performance-strategy-plan.md`
- Eventing and async processing plan in
  `docs/implementation-plans/ADR-028-eventing-and-async-processing-architecture-plan.md`
- Security roles and permissions plan in
  `docs/implementation-plans/ADR-019-security-roles-and-permissions-plan.md`

### Prompt

Instrument the transition engine with safe logs, metrics, traces, and optional
cache support. Measure analyzer latency, batch size, cache hit/miss rates,
metadata-missing counts, low-confidence warning counts, policy-failure counts,
and error outcomes using approved low-cardinality labels. Define cache keys based
on ordered arrangement IDs or safe tokens, metadata snapshot ID, policy snapshot
ID, analyzer version, and church-instance scope. Add invalidation or bypass
rules for metadata changes, approval changes, policy changes, and analyzer
version changes. Document operational troubleshooting steps and release-readiness
checks for transition-analysis behavior.

### Acceptance criteria

- Transition-analysis telemetry records latency, outcome, warning categories,
  policy-failure categories, cache behavior, and analyzer version without
  exposing song titles, raw lyrics, chord charts, actor IDs, request IDs, or
  private instance identifiers as metric labels.
- Cache keys include ordered pair identity, metadata snapshot, policy snapshot,
  analyzer version, and instance scope or a safe scoped token.
- Cache invalidation or bypass behavior is defined for metadata snapshot changes,
  approval status changes, policy changes, analyzer upgrades, and visibility or
  instance-scope changes.
- Performance tests or benchmarks define acceptable latency for representative
  pair and batch sizes and identify fallback behavior when the engine exceeds
  budget.
- Operational documentation explains how to diagnose low-confidence metadata,
  policy failures, cache misses, and rejected arrangement eligibility without
  leaking sensitive data.

### Restrictions

- Do not place raw arrangement titles, lyrics, chord charts, actor IDs, request
  IDs, service-plan names, or instance identifiers in metric labels.
- Do not cache results across church-instance boundaries unless the cache key and
  authorization model explicitly prove the result is safe to share.
- Do not let stale cached transition results survive metadata, approval, policy,
  or analyzer-version changes.
- Do not fail recommendation generation solely because optional transition
  telemetry cannot be emitted.
