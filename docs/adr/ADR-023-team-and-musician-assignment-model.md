# ADR-023: Team and Musician Assignment Model

Status: Accepted  
Date: 2026-05-28

## Context

Setlist planning is constrained by the people available for a service. A song that is musically appropriate may be impractical if the assigned team lacks required instruments, vocal range, or rehearsal readiness.

## Problem

Cadentia cannot mature into operational planning if recommendations ignore musicians, vocalists, roles, skills, and availability. Without structured team data, arrangement suitability becomes a subjective note instead of a queryable constraint.

## Decision

Introduce instance-scoped team and musician modeling. Musicians, roles, instruments, vocal ranges, skill levels, availability, and service assignments will be represented as first-class operational data. The Recommendation Engine may use this data as deterministic hard filters or scoring inputs according to a versioned recommendation profile.

## Requirements

- Model musicians as instance-scoped people with roles and optional contact/account links.
- Model instruments, vocal parts, skill levels, and serving preferences using controlled vocabularies.
- Track availability and assignment status for services and rehearsals.
- Associate musicians with services, service positions, and specific songs where needed.
- Model vocalist range constraints and arrangement lead-vocal suitability.
- Model arrangement suitability by required instruments, optional instruments, vocal configuration, and minimum skill level.
- Surface rehearsal readiness for assigned teams without allowing readiness notes to bypass catalog approval gates.
- Ensure personnel data remains inside the deployed church instance and follows role-based access rules.

## Acceptance Criteria

- Services can assign musicians, vocalists, and instruments.
- Recommendation requests can include team constraints and receive deterministic results.
- Arrangement suitability is queryable by team capability and service context.
- Vocalist range and instrumentation conflicts can be explained in recommendation diagnostics.
- Private musician data is not exposed outside the deployed church instance or to unauthorized roles.

## Consequences

Positive:

- Recommendations become more practical for real teams.
- Worship leaders can identify staffing gaps before rehearsal.
- Arrangement selection can be grounded in team capability.

Tradeoffs:

- Personnel data increases privacy and authorization requirements.
- Skill levels and ranges require sensitive local maintenance.
- Recommendations may become more constrained when teams are incomplete.

## Alternatives Considered

1. Keep team assignments outside Cadentia.
   - Rejected: prevents arrangement suitability and readiness workflows.
2. Store assignments as free-form service notes.
   - Rejected: not queryable or deterministic.
3. Let the LLM infer team suitability from notes.
   - Rejected: violates the intent-only LLM boundary and risks hallucination.

## Open Questions

- Which skill-level taxonomy is simple enough for churches to maintain?
- Should availability integrate with external scheduling systems?
- How should substitute musicians and late changes affect recommendation snapshots?
