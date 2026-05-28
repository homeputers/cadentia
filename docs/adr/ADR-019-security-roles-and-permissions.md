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


## Permission Matrix

| Operation | VIEWER | WORSHIP_LEADER | CATALOG_EDITOR | DOCTRINAL_REVIEWER | MUSICAL_REVIEWER | ADMIN |
| --- | --- | --- | --- | --- | --- | --- |
| Read approved+active catalog/recommendations | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Read staged/unapproved catalog data | ❌ | ❌ | ✅ | ✅ | ✅ | ✅ |
| Generate setlist recommendations | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Create/update own service plans | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Create/edit staged catalog items | ❌ | ❌ | ✅ | ❌ | ❌ | ✅ |
| Submit item for review | ❌ | ❌ | ✅ | ❌ | ❌ | ✅ |
| Import catalog metadata | ❌ | ❌ | ✅ | ❌ | ❌ | ✅ |
| Merge approved catalog changes | ❌ | ❌ | ✅* | ❌ | ❌ | ✅ |
| Doctrinal approval/rejection | ❌ | ❌ | ❌ | ✅ | ❌ | ✅ |
| Musical approval/rejection | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ |
| Assign/revoke roles & policy overrides | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |

`*` Catalog editor merge is allowed only when policy constraints are satisfied and all required review gates are complete.

## Authorization Contract Surfaces

Authorization must be declared and enforced at these surfaces:

1. **OpenAPI contracts**: every operation declares `security` requirements, required roles, and 401/403 error schemas.
2. **Controller boundaries**: mutation/approval/import/merge/admin endpoints require explicit role checks with deny-by-default behavior.
3. **Service policy layer**: reviewer-domain checks, merge gate checks, and override rules are centralized in policy services (not duplicated in controllers).
4. **Repository/query boundaries**: user-facing read paths enforce approved+active filters; staged content requires privileged role gates.
5. **Audit boundary**: all privileged actions emit auditable records including actor, action, target, and before/after references.
6. **Role assignment workflow**: role changes are explicit admin actions with audit trail and least-privilege defaults for new users.

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

## Privileged Action Audit Infrastructure Controls

The privileged action audit trail is backed by an append-only table (`privileged_action_audit_events`) and a query-oriented view (`v_privileged_action_audit_history`).

Controls:

- **Required fields**: actor, action, target type, occurred-at timestamp, and before/after state references and hashes.
- **High-risk action coverage**: approval decisions, role assignment/revocation, policy overrides, and merge actions.
- **Retention baseline**: records are retained for at least **400 days** via `retention_until` to support incident investigations and access reviews.
- **Tamper resistance**: immutable insert-only semantics at the application layer and hash references for before/after snapshots.
- **Data minimization**: payload metadata is structured JSON with a guard constraint to block obvious secret-bearing keys/tokens.
- **Operational queryability**: indexed actor/action/time and target lookups support incident response queries by actor, action, or time window.
