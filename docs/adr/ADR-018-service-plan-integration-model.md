# ADR-018: Service Plan Integration Model

Status: Proposed  
Date: 2026-05-26

## Context

Generated setlists are one part of a real service, which also includes non-song moments, contextual notes, and service-specific adjustments. Cadentia needs a planning model that connects recommendations to operational worship services without mutating canonical catalog records.

## Problem

Without a service plan boundary:

- generated recommendations cannot be contextualized per event
- non-song liturgical moments are not represented
- service-specific transpositions can pollute canonical arrangement data
- final run-of-service order may be lost

## Decision

Model service plans as first-class entities separate from setlist generation artifacts.

- A service plan can attach one or more generated setlists.
- A service plan can include both song and non-song blocks.
- Service-specific overrides (notes/transpositions/order) are stored in plan scope.
- Finalized service sequence is immutable once published (subject to explicit revision flow).

## Requirements

- Define service plan model with fields:
  - service date/time
  - title
  - theme
  - scripture
  - notes
- Support worship block types:
  - praise
  - worship
  - offering
  - altar call
  - communion
  - special
- Allow attaching one or more setlists per service.
- Reserve extension points for team assignments and personnel planning.
- Allow service-specific transpositions and notes.
- Preserve finalized service order and publish state.
- Ensure service-specific edits do not mutate canonical catalog records.

### Plan/Setlist Relationship Rules

- Setlist remains independently versioned (see ADR-016).
- Service plan references specific setlist version(s).
- Service plan may reorder entries for service flow while preserving referenced source.
- Service plan snapshot stores effective order used in finalized service.

## Acceptance Criteria

- Generated setlist can be attached to a service plan.
- Service plan can contain non-song moments alongside songs.
- Final service order is persisted and retrievable.
- Service-specific transpositions and notes are isolated from catalog canonical data.
- Multiple setlists can be attached and composed for one service when needed.

## Consequences

Positive:

- bridges recommendation output to real ministry operations
- supports richer service composition beyond song-only planning
- protects catalog integrity while enabling contextual flexibility

Tradeoffs:

- additional domain model complexity and UI workflow requirements
- must manage synchronization semantics when referenced setlist versions change

## Alternatives Considered

1. Treat setlist itself as service plan.
   - Rejected: insufficient for non-song structure and operational metadata.
2. Copy setlist songs into service records without references.
   - Rejected: loses provenance and update traceability.
3. Allow direct mutation of arrangement metadata per service.
   - Rejected: corrupts canonical catalog truth.

## Open Questions

- Should service plans support template inheritance (e.g., recurring Sunday structure)?
- What publish-lock policy is needed for last-minute rehearsal changes?
- How should multi-campus services share or fork plan artifacts?
