# ADR-010: Recommendation Engine Scoring Architecture

Status: Proposed  
Date: 2026-05-17

## Context

Cadentia's Recommendation Engine is the deterministic system responsible for selecting and ordering setlist candidates. Existing architecture establishes that the LLM may interpret user intent only, approved catalog data gates eligibility, and backend logic must select songs without hallucination.

This ADR formalizes the scoring architecture because recommendation quality, safety, explainability, and repeatability depend on a transparent scoring model.

The engine must support:

- scoring phases
- constraint filtering
- ranking weights
- transition scoring
- energy arc evaluation
- deterministic tie-breaking
- explainability output

## Decision

Implement recommendation as a deterministic multi-phase pipeline. The pipeline filters ineligible records before scoring and emits scored, ordered recommendations with machine-readable explanations.

The phases are:

1. **Request normalization:** validate intent slots, defaults, counts, key policy, tempo policy, language, and theme hints.
2. **Candidate retrieval:** load eligible candidates from the recommendation read model.
3. **Hard constraint filtering:** remove candidates that violate approval, licensing, language, availability, count, required theme, blocked song, or explicit musical constraints.
4. **Candidate feature scoring:** score each candidate independently for thematic, scriptural, musical, usage, and governance fit.
5. **Set construction:** search deterministic combinations that satisfy praise/worship counts, key center policy, and diversity requirements.
6. **Transition scoring:** score adjacent songs for key, mode, tempo, meter, energy, and arrangement compatibility.
7. **Energy arc evaluation:** score the full set against the requested or default arc.
8. **Final ranking:** combine candidate, transition, and set-level scores using versioned weights.
9. **Tie-breaking:** apply stable deterministic tie-breakers.
10. **Explanation generation:** emit evidence for selected and rejected candidates.

## Hard Constraint Filtering

Hard constraints must run before ranking. A candidate is not scoreable when it fails a hard gate.

Hard gates include:

- required approval states from ADR-005
- valid provenance records
- recommendation eligibility from the read model
- licensing status suitable for use
- active song and arrangement status
- supported language when specified
- required counts and category eligibility
- explicit user exclusions
- unavailable arrangements or inactive lyrics documents

The LLM must never influence this phase beyond validated request slots.

## Candidate Feature Scoring

Candidate feature scores should be computed from approved catalog and read-model data only.

Recommended score components:

- **Theme match:** controlled tags, scripture tags, manually reviewed themes, and approved taxonomy aliases.
- **Scripture fit:** explicit scripture references, passage themes, and curated mapping between passages and themes.
- **Song role fit:** praise, worship, response, invitation, communion, sending, seasonal, or other controlled use cases.
- **Musical fit:** key, mode, BPM, meter, vocal range, difficulty, and energy.
- **Freshness and usage:** optional church-specific rotation, recent usage, and overuse avoidance.
- **Governance confidence:** quality of approvals, provenance completeness, and reviewer confidence.

Weights must be versioned. A recommendation response should include the scoring profile version used.

## Transition Scoring

Transition scoring evaluates adjacent arrangement pairs after candidate filtering.

Transition dimensions include:

- same key bonus
- relative major/minor compatibility
- circle-of-fifths or closely related key compatibility
- modulation penalty
- tempo jump penalty based on configured maximum BPM jump
- meter compatibility
- energy continuity or intentional contrast
- ending-to-starting section compatibility when available
- capo or transposition practicality

Transition scores must be directional because moving from song A to song B may not score the same as moving from song B to song A.

## Energy Arc Evaluation

The engine should evaluate the set as a whole against a target energy arc.

Default behavior:

- praise set starts with accessible medium-high energy
- praise intensity may rise or hold steady
- worship section may settle into lower or more reflective energy
- final song may resolve, respond, or send depending on requested service moment

The engine should support named arcs such as:

- `rising`
- `rise_then_reflect`
- `reflective`
- `celebration`
- `response`

Energy arc scoring must not override hard constraints.

## Deterministic Tie-Breaking

Given the same request, catalog snapshot, scoring profile, and feature data, the engine must return the same result.

Tie-breaking order should be stable and documented:

1. higher total score
2. higher hard-priority theme score
3. better transition score
4. better provenance and approval confidence
5. lower recent-usage penalty
6. canonical song identifier sort
7. arrangement identifier sort

Randomness is not allowed in production recommendation unless a future ADR defines seeded exploration behavior.

## Explainability Output

Recommendation responses must include structured explanations suitable for UI rendering and audit logs.

Each selected item should include:

- final score
- component scores
- matched themes and scripture evidence
- source catalog references
- approval and provenance references
- musical fit reasons
- transition reason from previous song when applicable
- any warnings or tradeoffs

Rejected or near-miss candidates may include summarized reasons, especially when helpful for admins.

Diagnostics must be audience-partitioned:

- **Admin diagnostics:** full machine-readable exclusion codes, constraint-relaxation steps, tie-break outcomes, and per-phase candidate counts required for operational debugging and audit.
- **Public diagnostics:** deterministic high-level metadata (profile version, selected arc, stable result identifiers, and summarized non-sensitive tradeoff labels) with admin-only rationale fields redacted.

Public output must not expose sensitive review notes, copyrighted payload excerpts, or internal moderation details.

## Benchmark Matrix and SLO Enforcement

Recommendation performance must be verified using a repeatable benchmark matrix that spans catalog size and request complexity classes.

Required matrix dimensions:

- catalog sizes: `small`, `medium`, `large`
- request complexity: `baseline_defaults`, `theme_dense`, `constraint_heavy`
- diagnostics mode: `public_only`, `admin_enabled`

Each matrix cell must run deterministic benchmark fixtures and assert explicit latency SLO thresholds (for example p50/p95) in CI or equivalent automated gates.

Benchmark gates are valid only when:

- approval/provenance hard filters remain enabled
- network access is not required
- benchmark data snapshots are versioned and reproducible

Failing SLO checks should block profile or engine changes until remediated or formally re-baselined through governance.

Example explanation facts:

- selected because controlled theme tags matched Psalm 24 themes
- transition from G to Em is allowed as relative major/minor movement
- BPM increase stayed within configured maximum jump
- energy arc matched requested increasing praise intensity
- candidate excluded because licensing approval is pending

## Consequences

Benefits:

- recommendation results are repeatable and testable
- scoring can evolve through versioned profiles
- explanations become a first-class product feature
- LLM and REng responsibilities remain separated
- admins can diagnose catalog gaps and quality issues

Tradeoffs:

- the engine requires richer feature data and more tests
- scoring weights must be governed and versioned
- deterministic set construction may be more complex than simple top-N ranking
- benchmark fixture maintenance adds ongoing governance overhead

## Related Decisions

- ADR-001 defines canonical song and arrangement data.
- ADR-002 defines the recommendation read model.
- ADR-005 defines approval gates.
- ADR-007 defines controlled tags.
- ADR-012 defines the LLM intent boundary.
- ADR-013 defines the explanation system.
