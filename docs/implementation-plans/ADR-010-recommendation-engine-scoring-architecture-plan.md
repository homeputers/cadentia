# ADR-010 Implementation Plan: Recommendation Engine Scoring Architecture

## Objective

Implement deterministic recommendation scoring over approval-gated read-model
candidates using hard filters, feature scoring, transition scoring, energy arc
evaluation, and stable tie-breaking. The engine must not rely on an LLM for song
selection.

## Subtask 1: Define scoring request, profile, and response contracts

### Context

ADR-010 requires scoring profiles and deterministic engine phases that can evolve
without changing the LLM boundary.

### Prompt

Create internal request, scoring profile, component score, transition score, and
ordered set response models. Include profile version, applied defaults, candidate
snapshot version when available, and deterministic seed-free ordering rules.

### Acceptance criteria

- Scoring profile version is included in every response.
- Request models accept validated slots from ADR-012 without exposing LLM output
  directly to scoring internals.
- Component scores and transition scores are structured for later ADR-013
  explanations.
- Tests verify response shape and profile version propagation.

### Restrictions

- Do not add LLM-generated text to scoring responses.
- Do not accept unvalidated natural-language prompts as scoring input.
- Do not mutate catalog data during scoring.

## Subtask 2: Implement hard constraint filtering

### Context

Hard filters must enforce approval, provenance, catalog status, role/count needs,
language, service moment, exclusions, key policy constraints, and tempo policy
where applicable.

### Prompt

Build the hard-filter pipeline that removes ineligible candidates before scoring.
Capture filter reasons for excluded candidates so admin diagnostics and later
explanations can use them.

### Acceptance criteria

- Only approval-gated read-model candidates can enter scoring.
- User exclusions, inactive arrangements, missing provenance, and failed approval
  states are filtered out.
- Role and count requirements are enforced for praise and worship defaults.
- Filter reasons are stable codes, not free-form-only strings.
- Tests cover each hard-filter category.

### Restrictions

- Do not allow the LLM to override eligibility.
- Do not score candidates that fail approval or provenance gates.
- Do not silently drop candidates without an exclusion reason.

## Subtask 3: Implement candidate feature scoring

### Context

ADR-010 scores candidates by theme/scripture fit, role fit, musical metadata,
energy, difficulty, freshness, and other approved catalog/read-model facts.

### Prompt

Implement deterministic candidate-level scoring components with configurable
weights. Use controlled vocabulary tags and approved read-model fields for all
feature inputs.

### Acceptance criteria

- Scores are decomposed into named components with numeric contribution values.
- Theme and scripture matching use controlled tags and references, not LLM claims.
- Missing or low-confidence musical metadata receives explicit neutral or penalty
  treatment per profile.
- Tests verify score calculations for representative candidates and missing-data
  cases.

### Restrictions

- Do not infer catalog tags from user prose at scoring time.
- Do not scrape or fetch external metadata during scoring.
- Do not make total scores nondeterministic through unordered collections.

## Subtask 4: Implement transition scoring and ordering search

### Context

Setlists need adjacent transition evaluation for key continuity, relative
major/minor movement, BPM jumps, meter, energy, and arrangement start/end
compatibility.

### Prompt

Implement transition scoring between candidate arrangements and an ordering
algorithm that selects a deterministic set satisfying count, key-center, tempo,
and energy constraints as well as possible.

### Acceptance criteria

- Same-key, relative-key, closely related key, and modulation penalties are
  distinct transition components.
- BPM jump policies are enforced or penalized according to profile rules.
- Ordering is deterministic for identical inputs.
- Tests cover allowed relative major/minor transitions, excessive BPM jumps,
  stable ordering, and key-center limits.

### Restrictions

- Do not use randomness or clock time for tie-breaking.
- Do not exceed configured hard key-center constraints when enough eligible
  candidates exist.
- Do not ignore parser confidence when using arrangement start/end metadata.

## Subtask 5: Implement energy arc evaluation

### Context

ADR-010 requires set-level energy arc evaluation for patterns such as rising
praise intensity followed by reflective worship.

### Prompt

Add energy arc models and evaluators that compare selected ordering against the
requested or default energy arc. Integrate arc scores with candidate and
transition scores.

### Acceptance criteria

- Supported energy arcs are enumerated and versioned.
- Default behavior is deterministic when no arc is requested.
- Arc evaluation produces set-level score components and tradeoff codes.
- Tests cover rising, falling, balanced, and unspecified arc requests.

### Restrictions

- Do not infer emotional intent outside supported arc values.
- Do not use free-form LLM descriptions as arc definitions.
- Do not reorder songs in ways that violate hard filters to improve arc score.

## Subtask 6: Implement deterministic tie-breaking

### Context

ADR-010 requires stable results when candidates have equal or near-equal scores.
Tie-breaking must be auditable.

### Prompt

Define and implement tie-breaking order using score, approval/candidate identity,
arrangement identity, title normalization, freshness metadata where approved,
and stable catalog identifiers.

### Acceptance criteria

- Identical requests over identical candidate snapshots produce identical ordered
  results.
- Tie-breaking fields are documented in profile metadata.
- Tests cover exact ties, near ties, and reordered database result sets.
- No unordered map/set iteration can affect final output.

### Restrictions

- Do not use random seeds, UUID generation, or current time in tie-breaking.
- Do not depend on database default row order.
- Do not hide tie-break decisions from diagnostic output.

## Subtask 7: Add scoring diagnostics and performance tests

### Context

The engine should be diagnosable and performant enough for user-facing setlist
generation while preserving auditability.

### Prompt

Add diagnostics for candidate counts by phase, filter reasons, score ranges,
transition tradeoffs, and selected set summary. Add performance tests around
representative catalog sizes.

### Acceptance criteria

- Diagnostics can be enabled for admins without changing user-facing results.
- Performance tests assert acceptable latency for representative candidate sets.
- Logs avoid full copyrighted lyric content.
- Tests verify diagnostics do not change selection output.

### Restrictions

- Do not expose admin-only exclusion details to normal users by default.
- Do not optimize by skipping approval gates.
- Do not log sensitive source payloads.
