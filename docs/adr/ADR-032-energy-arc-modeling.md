# ADR-032: Energy Arc Modeling

Status: Accepted  
Date: 2026-05-28

## Context

Cadentia recommendations must consider not only individual song fit but the emotional and energy flow of the full setlist. Praise and worship structure, transitions, and service moments require deterministic energy modeling.

## Problem

Without explicit energy arc modeling, setlists can contain abrupt intensity changes, poorly timed reflective songs, or weak praise-to-worship transitions even when individual songs score well. Subjective free-form descriptions are not sufficient for reproducible recommendations.

## Decision

Define an Energy Arc Model that assigns approved songs and arrangements deterministic energy attributes and evaluates the ordered setlist against a configured target arc. Energy scoring remains a Recommendation Engine input and explanation source, not an LLM-generated judgment.

## Requirements

- Model praise intensity, worship reflectiveness, emotional trajectory, congregation engagement, and service moment suitability using controlled values.
- Support named arcs such as `rising`, `rise_then_reflect`, `reflective`, `celebration`, `response`, and church-configured variants.
- Evaluate energy movement across adjacent songs and across the full setlist.
- Prevent abrupt energy discontinuities unless the selected arc or explicit policy allows intentional contrast.
- Support default praise/worship structure, including 10 praise and 5 worship when requested by default contract.
- Support versioned energy profiles and deterministic tie-breaking.
- Emit machine-readable energy explanations and warnings.
- Use only curated catalog metadata, reviewed usage tags, and deterministic calculations.

## Acceptance Criteria

- Generated setlists respect configured energy policies when enough eligible catalog data exists.
- Energy transitions are explainable through structured reason codes and metadata references.
- Energy arc scoring is deterministic for the same request, catalog snapshot, and profile.
- Abrupt energy changes are either avoided, penalized, or explicitly explained as intentional policy choices.
- The LLM does not assign energy labels or choose songs.

## Consequences

Positive:

- Setlists feel more cohesive across the full worship flow.
- Energy tradeoffs become visible and tunable.
- Churches can define preferred service arcs without custom code.

Tradeoffs:

- Energy metadata is partly subjective and requires governance.
- Overly strict arcs can reduce eligible recommendations.
- Profile changes require regression fixtures.

## Alternatives Considered

1. Use BPM as the only energy proxy.
   - Rejected: tempo alone does not capture emotional or congregational intensity.
2. Let worship leaders manually order every recommendation result.
   - Rejected: reduces recommendation value and repeatability.
3. Use LLM sentiment analysis at request time.
   - Rejected: nondeterministic and not based solely on curated catalog data.

## Open Questions

- Which energy dimensions should be mandatory in the initial catalog schema?
- How should seasonal or liturgical contexts influence default arcs?
- How should low-confidence or missing energy metadata affect eligibility versus scoring?
