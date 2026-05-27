# ADR-016: Setlist Persistence and Versioning

Status: Complete  
Date: 2026-05-26

## Context

Cadentia recommendations are generated deterministically from validated intent and approved catalog data. Worship leaders then refine results through practical edits (reorder, replace, transpose, remove). These changes must remain auditable without losing original recommendation traceability.

## Problem

Without versioned persistence:

- generated outputs cannot be reproduced for debugging
- manual edits can overwrite original deterministic output
- team collaboration cannot distinguish generated vs curated decisions
- historical service preparation context is lost

## Decision

Implement immutable setlist versions with explicit provenance of generation and edits.

- Every generated recommendation is persisted as an initial version.
- User edits create new versions or append auditable change events.
- Original generation payload remains immutable and retrievable.
- All items reference arrangement IDs from curated catalog data, never free text.

## Requirements

- Persist generated setlists and metadata.
- Store, per generated baseline:
  - original request
  - parsed intent
  - selected arrangement IDs
  - deterministic ordering
  - explanation facts
  - engine/scoring profile version
- Support user edits:
  - reorder
  - replace
  - remove
  - transpose
- Version after each committed edit operation (or grouped transaction).
- Preserve generated vs manually modified status at list and item levels.
- Allow retrieval of previous versions and diffs.
- Ensure reproducibility/debugging by preserving inputs and engine context.

### Data Model Constraints

- `setlist_id` identifies logical setlist lineage.
- `version_id` identifies immutable snapshots.
- `parent_version_id` defines lineage graph (linear or branched per policy).
- Each item must reference `catalog_arrangement_id`.
- Transposition is version-scoped/service-scoped metadata; it must not mutate catalog canonical key data.

## Acceptance Criteria

- Generated setlist can be saved and reopened with complete metadata.
- Edits create a new version or auditable change record.
- Original recommendation remains traceable and unchanged.
- Every setlist item references catalog arrangement ID, not free-text song names.
- Historical versions can be inspected for debugging and comparison.

## Consequences

Positive:

- strong audit and reproducibility guarantees
- safer collaboration and post-service retrospectives
- clear separation of engine output vs human modifications

Tradeoffs:

- increased storage and query complexity
- version-diff rendering required in clients/admin tooling

## Alternatives Considered

1. Mutable single-record setlist storage.
   - Rejected: destroys provenance and reproducibility.
2. Event-only storage without materialized versions.
   - Rejected: reconstruction overhead for common reads.
3. Snapshot-only storage without edit events.
   - Rejected: weaker intent-level audit for individual user actions.

## Resolution Notes

- Branching lineage remains policy-controlled via `parent_version_id`; default deployments should enforce linear head commits unless explicitly configured for collaboration branches.
- Retention policy for abandoned drafts is now defined operationally: archive after 90 days of inactivity and hard-delete after 365 days when no hold exists.
- Replacement-candidate ranking capture remains optional follow-up work and is not required for immutable version traceability in ADR-016 scope.
