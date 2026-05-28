# ADR-033: Arrangement Compatibility and Instrumentation Modeling

Status: Proposed  
Date: 2026-05-28

## Context

A song can have multiple arrangements with different instrumentation, complexity, vocal demands, and suitability for acoustic, full-band, choir, or tracks-based teams. Cadentia must model arrangement compatibility so recommendations match real team capabilities.

## Problem

If arrangement metadata is limited to key and BPM, Cadentia may recommend songs that are approved but impractical for the assigned team. Free-form arrangement notes are not sufficient for deterministic filtering or explainability.

## Decision

Extend arrangement modeling with structured compatibility metadata: required and optional instruments, acoustic/electric suitability, choir support, vocal configuration, complexity, rehearsal burden, track dependency, and minimum team capability. The Recommendation Engine may use this metadata for hard filters, scoring, and explanations.

## Requirements

- Support acoustic, electric/full-band, keys-led, choir, stripped-down, tracks-assisted, and other suitability flags.
- Model required instruments, optional instruments, substitutions, and unsupported configurations.
- Model choir support, vocal parts, lead vocal range, harmony complexity, and congregation accessibility.
- Model arrangement complexity, rehearsal difficulty, rhythmic complexity, and technical dependency.
- Associate arrangement suitability with team capabilities from ADR-023.
- Allow tenant-specific arrangement compatibility overrides without mutating global catalog defaults.
- Provide deterministic filtering and scoring based on requested team/instrument constraints.
- Expose compatibility explanations through ADR-021 reason codes.

## Acceptance Criteria

- Recommendations can filter arrangements by instrumentation and team format.
- Team capability constraints are respected when provided.
- Arrangement complexity and suitability are queryable.
- Compatibility conflicts are explainable and auditable.
- Approval gates still determine whether an arrangement is recommendable.

## Consequences

Positive:

- Recommendations become more actionable for available teams.
- Worship leaders can compare arrangement options before rehearsal.
- Complexity and staffing tradeoffs are explicit.

Tradeoffs:

- Arrangement metadata entry and review workload increases.
- Tenant overrides can diverge from global defaults.
- Strict compatibility filters may reduce available results.

## Alternatives Considered

1. Let users manually judge arrangement suitability after recommendation.
   - Rejected: pushes deterministic planning concerns to the user.
2. Infer instrumentation from uploaded charts or audio only.
   - Rejected: parsing may be incomplete and must be reviewed before eligibility.
3. Store compatibility as free-form tags only.
   - Rejected: insufficient for precise filtering and conflict explanations.

## Open Questions

- What minimum compatibility metadata is required before an arrangement can be recommendable?
- How should instrument substitutions be represented and scored?
- Should choir suitability be global, tenant-specific, or both?
