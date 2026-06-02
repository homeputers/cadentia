# ADR-031: Musical Transition Analysis Engine

Status: Accepted  
Date: 2026-05-28

## Context

Musical continuity is a core Cadentia constraint. ADR-010 defines transition scoring at a high level; Phase 4 needs a dedicated engine for harmonic and transition analysis that can evolve independently from general recommendation scoring.

## Problem

Simple key and BPM comparisons are not enough to explain or optimize transitions. Worship leaders need deterministic analysis of key relationships, modulation smoothness, harmonic compatibility, cadence continuity, and policy tradeoffs.

## Decision

Introduce a Musical Transition Analysis Engine as a deterministic internal component used by the Recommendation Engine. It evaluates ordered arrangement pairs and returns directional transition scores, reason codes, and evidence based on approved musical metadata.

The engine will recognize same-key, relative major/minor, closely related keys, modulation distance, cadence compatibility, tempo movement, meter continuity, and configured transition policies.

## Requirements

- Analyze key relationships including same key, relative major/minor, parallel mode, circle-of-fifths proximity, and configured modulation allowances.
- Analyze modulation smoothness based on source ending key/section and destination starting key/section when available.
- Analyze harmonic compatibility using approved chord/key metadata and confidence levels.
- Analyze cadence continuity from ending and opening musical sections when available.
- Generate directional transition scores and machine-readable reason codes.
- Support relative major/minor analysis as a first-class compatibility rule.
- Support configurable transition policies such as maximum BPM jump, allowed modulation distance, and preferred key centers.
- Expose explanation facts to ADR-021 without invoking the LLM.

## Acceptance Criteria

- Transition scoring is deterministic for the same arrangement pair, metadata snapshot, and policy.
- Relative key relationships are recognized and explained.
- Transition compatibility is queryable and explainable per adjacent pair.
- Low-confidence musical metadata is surfaced as a warning rather than silently treated as truth.
- The engine never recommends or analyzes unapproved/private arrangements outside the request instance context.

## Consequences

Positive:

- Improves musical coherence and worship leader trust.
- Separates harmonic analysis from broader setlist construction.
- Provides reusable transition facts for explanations and rehearsal planning.

Tradeoffs:

- Requires richer and more accurate musical metadata.
- Chord/cadence parsing confidence must be maintained.
- Advanced analysis can increase recommendation computation cost.

## Alternatives Considered

1. Keep only simple key and tempo heuristics.
   - Rejected: insufficient for advanced transition quality and explainability.
2. Ask an LLM to evaluate transitions.
   - Rejected: nondeterministic and outside the LLM intent-only boundary.
3. Require manual transition ratings for every pair.
   - Rejected: high maintenance burden and poor scalability.

## Open Questions

- Which harmonic distance model should be the default?
- How should medleys and intentional hard cuts be represented?
- What minimum metadata confidence is required for advanced cadence analysis?
