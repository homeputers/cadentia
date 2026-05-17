# ADR-013: Recommendation Explanation System

Status: Proposed  
Date: 2026-05-17

## Context

Cadentia's recommendation quality depends not only on selecting appropriate songs, but also on explaining why the setlist was selected. Worship leaders need to trust the result, understand musical transitions, see thematic alignment, and identify catalog or governance issues.

The explanation system must provide user-facing reasons without weakening deterministic boundaries. Explanations must be generated from engine facts, catalog references, scoring components, provenance, and approval states, not from ungrounded LLM prose.

Examples of useful explanations include:

- selected because a controlled theme matched Psalm 24
- transition from G to Em minimizes modulation through relative major/minor movement
- energy arc target is increasing praise intensity

## Decision

Create a structured recommendation explanation system as part of the Recommendation Engine output. Explanations are facts emitted by deterministic backend logic and may be rendered by UI clients or, in the future, summarized by an LLM constrained to those facts.

Each recommendation response should include:

- request summary and applied defaults
- scoring profile version
- catalog snapshot or read-model version when available
- selected setlist items
- per-item explanation facts
- transition explanation facts
- set-level explanation facts
- warnings and tradeoffs
- provenance and approval references
- optional near-miss or excluded candidate reasons for admin users

## Explanation Model

An explanation fact should be structured rather than free-form only.

Recommended fields:

- `code`: stable explanation code such as `THEME_MATCH`, `RELATIVE_KEY_TRANSITION`, or `LICENSING_EXCLUDED`
- `severity`: `info`, `warning`, or `blocked`
- `scope`: `item`, `transition`, `set`, or `candidate_exclusion`
- `subject`: song, arrangement, transition pair, or set identifier
- `messageTemplate`: UI-safe template key or short default text
- `values`: structured values used for rendering
- `evidence`: catalog, tag, scripture, approval, provenance, or score references
- `scoreImpact`: optional numeric contribution or penalty

Free-form strings may be included for simple clients, but structured facts are the source of truth.

## Item-Level Explanations

Each selected item should explain why it appears in the set.

Item-level explanation categories:

- theme or scripture match
- role fit, such as praise, worship, response, communion, or sending
- musical fit, such as key, BPM, meter, energy, and difficulty
- approval and provenance eligibility
- freshness or rotation fit when enabled
- known tradeoffs, such as minor tempo compromise or low confidence metadata

Example facts:

- `THEME_MATCH`: controlled theme `holiness` matched the requested Psalm 24 focus.
- `ROLE_FIT`: song satisfied the praise slot target.
- `APPROVAL_ELIGIBLE`: required approval records were approved.

## Transition Explanations

Transitions should explain adjacent ordering decisions.

Transition categories:

- same key continuity
- relative major/minor movement
- closely related key movement
- modulation penalty
- BPM increase or decrease within policy
- meter compatibility
- energy build, hold, or release
- arrangement start/end compatibility when parser data is available

Example facts:

- `RELATIVE_KEY_TRANSITION`: transition from G to Em was favored because relative major/minor movement is allowed.
- `TEMPO_POLICY_OK`: adjacent BPM jump stayed within the configured 12 BPM maximum.
- `ENERGY_BUILD`: energy increased to match the requested rising arc.

## Set-Level Explanations

Set-level explanations should summarize how the full recommendation satisfies the request.

Set-level categories:

- praise and worship count satisfaction
- maximum key centers used
- energy arc fit
- theme coverage
- language or service moment fit
- catalog limitations or warnings

Example facts:

- `COUNT_TARGET_MET`: set contains 10 praise songs and 5 worship songs.
- `KEY_CENTER_POLICY_MET`: set uses no more than two key centers.
- `ENERGY_ARC_MATCH`: ordering follows increasing praise intensity before reflective worship.

## Exclusion and Near-Miss Explanations

For admin and debugging views, the engine should explain why high-potential candidates were not selected.

Exclusion reasons include:

- failed approval gate
- missing provenance
- licensing concern
- duplicate or inactive arrangement
- exceeded key center limit
- tempo jump would violate policy
- weaker theme score than selected candidates
- count quota already filled

User-facing clients may hide detailed exclusions by default, while admin clients should expose them to support catalog improvement.

## LLM Rendering Boundary

The explanation system must not depend on the LLM to invent reasons. If an LLM is used for final wording, it may only transform structured explanation facts into natural language and must not add songs, sources, approvals, scores, scripture claims, or musical claims not present in the provided facts.

The deterministic explanation facts remain the auditable output.

## Consequences

Benefits:

- users can trust and adjust recommendations
- admins can diagnose catalog gaps and scoring behavior
- recommendation output becomes auditable
- future UI clients can render explanations consistently
- LLM wording can be optional and constrained

Tradeoffs:

- scoring code must emit structured evidence, not just totals
- explanation codes and templates require governance
- clients must handle warnings and tradeoffs clearly

## Related Decisions

- ADR-010 defines scoring phases and component scores.
- ADR-012 defines the LLM intent boundary.
- ADR-005 defines approval evidence.
- ADR-008 and ADR-009 provide provenance and parser evidence used in explanations.
