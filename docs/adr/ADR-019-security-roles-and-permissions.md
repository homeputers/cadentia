# ADR-019: Security, Roles, and Permissions

Status: Proposed  
Date: 2026-05-26

## Context

Cadentia handles doctrinally sensitive content, licensing-related metadata, and privileged catalog governance actions. Clear role boundaries are required so recommendation consumers, editors, and reviewers operate within authorized capabilities.

## Problem

Without a defined permission model:

- unqualified users may approve sensitive content
- catalog integrity can be compromised by uncontrolled edits/imports
- unapproved songs could leak into user-facing recommendation paths
- incident investigation lacks reliable audit records

## Decision

Adopt role-based access control with explicit permission matrix and auditable privileged operations.

Defined roles:

- `VIEWER`
- `WORSHIP_LEADER`
- `CATALOG_EDITOR`
- `DOCTRINAL_REVIEWER`
- `MUSICAL_REVIEWER`
- `ADMIN`

Authorization is enforced server-side for all mutation and approval endpoints. User-facing catalog and recommendation APIs expose only approved, active content.

## Requirements

- Publish permission matrix across read, edit, import, approve, merge, and admin actions.
- Restrict catalog mutation to authorized roles.
- Restrict doctrinal and musical approvals by corresponding reviewer roles.
- Restrict import and merge actions to authorized editorial/admin roles.
- Enforce approved+active filters for user-facing catalog and recommendation reads.
- Audit all privileged actions (who, what, when, before/after state).
- Support least-privilege defaults and explicit role assignment workflows.

### Minimum Permission Expectations

- `WORSHIP_LEADER`: generate recommendations, create service plans, submit feedback, edit own planning artifacts.
- `CATALOG_EDITOR`: create/edit staged catalog items, request reviews, merge only where authorized.
- `DOCTRINAL_REVIEWER`: doctrinal approval/rejection only.
- `MUSICAL_REVIEWER`: musical approval/rejection only.
- `ADMIN`: role assignment, policy overrides, system governance actions.

## Acceptance Criteria

- Worship leaders cannot approve doctrinal content.
- Catalog editors cannot override reviewer approvals unless explicitly authorized by policy.
- User-facing APIs hide unapproved/inactive songs from recommendation consumers.
- Privileged mutations and approvals are auditable with actor and state transitions.
- Authorization failures return consistent, non-leaky error responses.

## Consequences

Positive:

- stronger governance and compliance posture
- reduced risk of unauthorized catalog or approval changes
- better traceability for incident and content review

Tradeoffs:

- role matrix maintenance overhead
- onboarding friction when permissions are too restrictive by default

## Alternatives Considered

1. Coarse admin-only write model.
   - Rejected: operational bottlenecks and poor delegation.
2. Client-side role checks only.
   - Rejected: insecure and bypassable.
3. Attribute-only dynamic permissions without base roles.
   - Rejected: harder to reason about and audit initially.

## Open Questions

- Should role scoping support campus/team boundaries natively?
- Which privileged actions require step-up authentication?
- What is the escalation workflow for emergency content removals?
