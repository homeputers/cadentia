# ADR-011: Admin Review and Catalog Governance UI

Status: Proposed  
Date: 2026-05-17

## Context

Cadentia depends on curated, approved, provenance-backed catalog data. Import connectors, parsers, deduplication rules, doctrinal review, licensing review, and musical analysis all produce decisions that require human oversight.

The admin UI must make governance practical for worship leaders, catalog editors, and reviewers while preserving deterministic recommendation safety.

The UI must support:

- reviewer workflows
- approval queue
- merge UI
- audit history
- moderation tools
- rollback behavior

## Decision

Build an admin review and catalog governance UI as the only supported path for promoting staged imports into recommendable catalog records, except for controlled seed/test fixtures.

The admin UI should expose workflow-specific queues, detailed candidate review screens, merge tools, approval actions, audit history, moderation controls, and rollback operations.

## Reviewer Workflows

Reviewer workflows should be role-aware and approval-type-aware.

Primary workflows:

- **Import triage:** inspect newly staged import candidates, validation issues, parser warnings, and provenance evidence.
- **Deduplication review:** compare candidate songs or arrangements with existing catalog records and choose merge, create, reject, or defer.
- **Editorial review:** correct title, metadata, lyrics formatting, sections, tags, language, and arrangement details.
- **Musical review:** verify key, BPM, meter, chord accuracy, transposition readiness, energy, and difficulty.
- **Doctrinal review:** evaluate lyrics, scripture alignment, theme tags, and theological concerns.
- **Licensing review:** verify source rights, CCLI references, copyright data, and permitted-use evidence.
- **Publication review:** confirm that required approvals are complete before marking records recommendable.

Review actions must be attributed to an actor and timestamped.

## Approval Queue

The approval queue should present records by urgency, blocker type, import batch, source, and approval type.

Queue filters should include:

- pending approval type
- source connector
- import batch
- validation severity
- duplicate confidence
- language
- theme or tag
- reviewer assignment
- stale or blocked items

A catalog entity must not become recommendable until required approval records are approved.

## Merge UI

The merge UI should support side-by-side comparison of staged candidates and existing canonical records.

Comparison should include:

- normalized and source titles
- CCLI number and writer metadata
- language
- lyrics hash and content diff
- chord and section structure
- key, BPM, meter, energy, and difficulty
- tags and source categories
- provenance records
- approval status
- duplicate confidence signals

Merge decisions should support:

- create new song
- create new arrangement under existing song
- update existing arrangement through a new lyrics document version
- reject as duplicate
- reject as not permitted
- defer for more information

Merges must preserve source provenance and audit events.

## Audit History

Governance actions must produce append-only audit history. The UI should show who changed what, when, why, and from which source state.

Audit history should cover:

- import events
- validation outcomes
- parser versions
- reviewer field edits
- approval decisions
- merge decisions
- recommendation eligibility changes
- moderation actions
- rollback operations

Audit records should be searchable by song, arrangement, lyrics document, import batch, actor, connector, and approval type.

## Moderation Tools

Moderation tools should allow authorized users to protect catalog quality without deleting evidence.

Tools should include:

- deactivate song or arrangement
- mark record as not recommendable
- flag licensing concern
- flag doctrinal concern
- request re-review
- lock canonical metadata fields
- mark duplicate candidate
- suppress connector source until reviewed
- add reviewer notes

Moderation states must be visible to the Recommendation Engine through catalog status or read-model eligibility.

## Rollback Behavior

Rollback should be explicit and auditable. The system should prefer reversible state changes and new versions over destructive deletion.

Rollback cases include:

- undo a mistaken merge
- restore previous lyrics document version
- revert approval from approved to needs-review
- deactivate a problematic arrangement
- remove recommendation eligibility after licensing or doctrinal concern
- re-open an import candidate for review

Rollback must not erase prior audit events, provenance records, or approval history. When canonical data changes, downstream read models should refresh or invalidate affected candidates.

## Consequences

Benefits:

- governance becomes operational rather than theoretical
- reviewer accountability is preserved
- catalog quality issues can be resolved without bypassing safety gates
- imports, parsing, approval, and recommendation eligibility become traceable

Tradeoffs:

- admin UI complexity is significant
- roles and permissions must be carefully designed
- rollback requires versioning and audit-aware data modeling

## Related Decisions

- ADR-003 defines staged import and deduplication.
- ADR-005 defines approval types and status gates.
- ADR-008 defines import connector lifecycle.
- ADR-009 defines parser warnings and derived metadata.
- ADR-010 defines recommendation eligibility and scoring dependencies.
