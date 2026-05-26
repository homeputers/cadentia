# ADR-017: User Feedback and Recommendation Tuning

Status: Proposed  
Date: 2026-05-26

## Context

Worship teams accumulate preferences over time, such as familiarity, seasonal suitability, and congregation response. Cadentia should learn from explicit feedback while preserving deterministic recommendation behavior and governance guardrails.

## Problem

If feedback is applied informally or opaquely:

- recommendations drift unpredictably
- approval and eligibility boundaries may be bypassed
- personal preferences can contaminate global catalog truth
- teams cannot inspect or reset tuning effects

## Decision

Introduce a deterministic feedback influence model where feedback alters ranking weights, never eligibility gates.

- Capture explicit interaction outcomes (`accepted`, `rejected`, `skipped`, `favorited`).
- Capture replacement reasons and familiarity signals.
- Store feedback in dedicated preference/tuning stores separate from canonical catalog metadata.
- Apply feedback through explicit weighted scoring rules versioned with the recommendation profile.

## Requirements

- Persist explicit feedback events with actor, scope, and timestamp.
- Capture replacement reasons with controlled taxonomy.
- Support team and congregation familiarity signals.
- Keep feedback data separate from catalog source-of-truth records.
- Ensure recommendation scoring remains deterministic given:
  - same request
  - same catalog snapshot
  - same feedback state
  - same scoring profile
- Feedback must never bypass approval, provenance, activity, or licensing gates.
- Distinguish personal/team preference layers from global metadata.
- Provide inspection and reset capabilities for feedback state.

### Deterministic Feedback Application

- Define fixed weight functions for each feedback signal.
- Resolve preference scope in deterministic order (personal -> team -> global policy fallback).
- Log per-candidate feedback contributions in explanation facts.
- Version feedback rule configuration with migration notes.

## Acceptance Criteria

- Feedback affects ranking but not base eligibility.
- Approved catalog constraints remain enforced regardless of feedback.
- Same request + same feedback state yields identical output ordering.
- Feedback state can be inspected and reset by authorized roles.
- Replacement reasons are queryable for governance and tuning analysis.

## Consequences

Positive:

- recommendations improve relevance while staying auditable
- teams gain transparent control of preference influence
- governance boundaries remain intact

Tradeoffs:

- additional model complexity for preference scopes and weights
- reset/inspection tooling required for admins and team leads

## Alternatives Considered

1. Use LLM memory to tune future picks implicitly.
   - Rejected: non-deterministic and difficult to audit.
2. Allow feedback to whitelist ineligible songs.
   - Rejected: violates approval and catalog integrity rules.
3. Store feedback directly on catalog entities.
   - Rejected: mixes truth metadata with contextual preference data.

## Open Questions

- How quickly should repeated negative feedback decay a song's score?
- Should familiarity signals be service-type specific (youth, main service, prayer night)?
- What governance is needed for team-level reset actions in multi-campus organizations?
