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
