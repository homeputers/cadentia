# ADR-024: Rehearsal and Workflow Lifecycle

Status: Proposed  
Date: 2026-05-28

## Context

After a setlist is selected, teams refine transitions, annotate issues, rehearse arrangements, and decide whether a service is ready. Cadentia needs to support this lifecycle without blurring catalog truth, service-specific changes, and recommendation eligibility.

## Problem

If rehearsal notes and one-off modifications are stored only as unstructured comments, teams cannot track unresolved issues or readiness. If rehearsal-specific changes mutate canonical arrangements, catalog integrity and deterministic recommendation behavior can be compromised.

## Decision

Model rehearsal planning as a service-scoped workflow with explicit rehearsal sessions, notes, issue states, readiness states, and service-specific arrangement overrides. Canonical catalog and arrangement metadata remain unchanged unless a separate catalog governance workflow approves a reusable update.

## Requirements

- Support rehearsal sessions attached to services.
- Track rehearsal notes by service, song, transition, arrangement, and team role.
- Track unresolved transitions, difficult songs, blockers, and owner/action status.
- Support readiness states such as `draft`, `planned`, `rehearsing`, `issues_open`, `ready`, and `completed`.
- Support rehearsal-specific arrangement modifications as service-scoped overrides.
- Preserve canonical arrangement data and approval gates.
- Audit readiness state changes and issue resolution.
- Expose workflow status in service planning views and recommendation diagnostics where relevant.

## Acceptance Criteria

- Services can track rehearsal sessions and progress.
- Teams can annotate song-specific and transition-specific rehearsal issues.
- Readiness status is visible and derived from explicit workflow state.
- Service-specific arrangement modifications do not automatically alter the approved catalog.
- Unresolved rehearsal blockers can be reported before a service is marked ready.

## Consequences

Positive:

- Worship leaders gain operational visibility after recommendation generation.
- Rehearsal concerns are tracked in context.
- Catalog truth remains separate from service-specific adaptations.

Tradeoffs:

- Workflow state machines require clear UI and permissions.
- Service-specific overrides add complexity to rendering charts and assets.
- Teams must maintain issue status for readiness to be accurate.

## Alternatives Considered

1. Use free-form notes only.
   - Rejected: insufficient for readiness and unresolved blocker tracking.
2. Modify canonical arrangements for each rehearsal change.
   - Rejected: pollutes approved catalog data with local one-off edits.
3. Treat rehearsal readiness as recommendation score only.
   - Rejected: readiness is an operational workflow, not just selection logic.

## Open Questions

- Which readiness states should be required versus church-configurable?
- Should rehearsal notes support attachments and recordings directly or through ADR-025 assets only?
- How long should completed rehearsal workflow data be retained?
