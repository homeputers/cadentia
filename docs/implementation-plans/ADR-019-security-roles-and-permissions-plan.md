# ADR-019 Implementation Plan: Security, Roles, and Permissions

## Objective

Establish server-enforced RBAC with auditable privileged operations and approved+
active content boundaries for user-facing recommendation and catalog APIs.

## Subtask 1: Define permission matrix and authorization contract surfaces

### Context

**Codebase anchors**
- API service: `apps/api`
- Intent contracts package: `packages/intent-contracts`
- DB migrations: `apps/api/src/main/resources/db/migration`
- Existing tests to extend: `apps/api/src/test/java` and `packages/intent-contracts/test`

ADR-019 defines role boundaries (`VIEWER`, `WORSHIP_LEADER`, `CATALOG_EDITOR`,
`DOCTRINAL_REVIEWER`, `MUSICAL_REVIEWER`, `ADMIN`) and minimum capability
expectations.

### Prompt

**Implementation starting points**
- Primary codebase: `apps/api` (Java/Spring controllers, services, repositories, migrations).
- Contract package: `packages/intent-contracts` (schemas/tests) when request/response shape is involved.
- Extend nearest tests first (for example in `apps/api/src/test/java/com/cadentia/{intent,reng,api/controller}` and `packages/intent-contracts/test`).
- Reference docs to update: `docs/ARCHITECTURE.md`, relevant ADR, and existing topic docs in `docs/`.

Publish permission matrix documentation and annotate OpenAPI operations with
required roles/scopes for read, edit, import, approve, merge, planning, and
admin workflows.


### Deliverables

1. **Canonical permission matrix artifact**
   - Add a role-operation matrix to ADR-019 (or linked security doc) covering read, plan, edit, import, approve, merge, and admin actions.
   - Include explicit deny cases for ambiguous actions (for example worship leaders approving doctrinal content).

2. **Authorization contract surface inventory**
   - Enumerate all OpenAPI endpoints by operation class and annotate required roles/scopes.
   - Define shared 401/403 response schemas and non-leaky error semantics.
   - Document approved+active filtering contract for user-facing read endpoints.

3. **Least-privilege role assignment workflow**
   - Document default role assignment, elevation request path, reviewer role separation, and admin override controls.
   - Require auditable role-change events and periodic access review ownership.

### Proposed operation classes for matrix coverage

- `catalog.read.public` (approved+active only)
- `catalog.read.staged`
- `setlist.generate`
- `planning.write.own`
- `catalog.write.staged`
- `catalog.import`
- `catalog.approve.doctrinal`
- `catalog.approve.musical`
- `catalog.merge`
- `security.roles.assign`
- `security.policy.override`

### Acceptance criteria

- Permission matrix maps each role to allowed/denied operations.
- OpenAPI includes security requirements and authorization error schemas.
- User-facing read APIs explicitly describe approved+active filtering behavior.
- Documentation includes least-privilege role assignment workflow.

### Restrictions

- Do not rely on client-only role checks.
- Do not leave mutation endpoints without explicit role requirements.
- Do not publish ambiguous approval authority between reviewer roles.

## Subtask 2: Implement server-side RBAC and policy enforcement

### Context

Authorization must be consistently enforced across controllers, services, and
repositories with non-leaky failure semantics.

### Prompt

**Implementation starting points**
- Primary codebase: `apps/api` (Java/Spring controllers, services, repositories, migrations).
- Contract package: `packages/intent-contracts` (schemas/tests) when request/response shape is involved.
- Extend nearest tests first (for example in `apps/api/src/test/java/com/cadentia/{intent,reng,api/controller}` and `packages/intent-contracts/test`).
- Reference docs to update: `docs/ARCHITECTURE.md`, relevant ADR, and existing topic docs in `docs/`.

Implement Java security configuration, controller/service guards, and policy
checks for catalog mutation, approvals, imports, merges, and admin actions.


### Acceptance criteria

- Worship leaders cannot execute doctrinal/musical approval actions.
- Reviewer actions are limited to their respective approval domains unless policy allows otherwise.
- Catalog mutation/import/merge routes enforce editor/admin constraints.
- Unauthorized access returns consistent non-leaky errors.
- Repository query paths for user-facing catalog/recommendation enforce approved+active visibility.

### Restrictions

- Do not bypass policy checks in background jobs or internal endpoints.
- Do not leak sensitive resource existence through authorization errors.
- Do not hardcode role checks in scattered duplicated logic.

## Subtask 3: Add privileged-action auditability and infrastructure controls

### Context

Incident response and governance require before/after audit records for sensitive
operations.

### Prompt

**Implementation starting points**
- Primary codebase: `apps/api` (Java/Spring controllers, services, repositories, migrations).
- Contract package: `packages/intent-contracts` (schemas/tests) when request/response shape is involved.
- Extend nearest tests first (for example in `apps/api/src/test/java/com/cadentia/{intent,reng,api/controller}` and `packages/intent-contracts/test`).
- Reference docs to update: `docs/ARCHITECTURE.md`, relevant ADR, and existing topic docs in `docs/`.

Add audit-event persistence, migration support, and infrastructure controls for
retention, integrity, and searchable privileged-operation trails.


### Acceptance criteria

- Audit records include actor, action, target, timestamp, and before/after state snapshot references.
- High-risk actions (approvals, role assignment, policy overrides, merges) are auditable.
- Infrastructure policy defines retention and tamper-resistance expectations.
- Operational tooling can query audit history by actor/action/time window.

### Restrictions

- Do not log secrets or credential material in audit payloads.
- Do not make audit writes optional for privileged mutations.
- Do not store unverifiable free-text-only action reasons as sole evidence.

## Subtask 4: Instrument security observability and publish runbooks

### Context

Security posture depends on detecting permission failures, privilege misuse, and
filtering regressions quickly.

### Prompt

**Implementation starting points**
- Primary codebase: `apps/api` (Java/Spring controllers, services, repositories, migrations).
- Contract package: `packages/intent-contracts` (schemas/tests) when request/response shape is involved.
- Extend nearest tests first (for example in `apps/api/src/test/java/com/cadentia/{intent,reng,api/controller}` and `packages/intent-contracts/test`).
- Reference docs to update: `docs/ARCHITECTURE.md`, relevant ADR, and existing topic docs in `docs/`.

Implement metrics, traces, and alerts for authorization outcomes and content
visibility gates; update security runbooks and developer docs.


### Acceptance criteria

- Metrics track allow/deny rates by operation class and role.
- Alerts detect unusual denial spikes, policy-override use, and approval anomalies.
- Tests verify unapproved/inactive content does not appear in user-facing recommendation paths.
- Documentation includes incident triage and emergency remediation procedures.

### Restrictions

- Do not include user-identifying high-cardinality labels unnecessarily.
- Do not deploy without regression tests for approval visibility boundaries.
- Do not omit guidance for rotating/revoking elevated access.

## Subtask 3 Implementation Notes (2026-05-28)

Implemented foundational privileged-action audit infrastructure:

- Added migration `V019__privileged_action_audit_trail.sql` introducing `privileged_action_audit_events` with required actor/action/target/timestamp and before/after state reference fields.
- Added retention and integrity controls:
  - `retention_until` defaulted to 400 days.
  - secret-pattern guardrail check constraint on metadata payload text.
  - hash reference fields for before/after snapshots.
- Added indexed search paths for actor/action/time and target lookups.
- Added `v_privileged_action_audit_history` view for operational query workflows by actor/action/time window.

Follow-up integration work should wire privileged mutation services to persist directly into this audit table for non-optional write enforcement across approvals, role assignment, policy override, and merge flows.

## Subtask 4 Implementation Notes (2026-05-28)

Implemented security observability and operations documentation baseline:

- Added runbook `docs/runbooks/adr-019-security-observability-and-response.md` with:
  - bounded-cardinality authorization, override, approval, and visibility-gate metrics;
  - tracing requirements for authorization decisions;
  - alert definitions for deny spikes, override use, approval anomalies, and visibility regressions;
  - incident triage SQL against `v_privileged_action_audit_history`;
  - emergency remediation steps for revoking/rotating elevated access and containing exposure.
- Updated `docs/ARCHITECTURE.md` with an ADR-019 observability section linking required metrics, tracing expectations, and the new runbook.

Follow-up implementation work should wire metrics emission and alert-rule provisioning in runtime infrastructure, then add/extend automated tests that enforce public read visibility boundaries (`approved=true` and `active=true`) for recommendation and catalog endpoints.
