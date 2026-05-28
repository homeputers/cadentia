# ADR-020 Implementation Plan: Duplicate Governance for External Integrations

## Objective

Treat ADR-020 as rejected duplicate coverage and route all implementation to
canonical ADRs: ADR-008, ADR-003, ADR-011, and ADR-004.

## Subtask 1: Create canonical decision map for integration concerns

### Context

**Codebase anchors**
- API service: `apps/api`
- Intent contracts package: `packages/intent-contracts`
- DB migrations: `apps/api/src/main/resources/db/migration`
- Existing tests to extend: `apps/api/src/test/java` and `packages/intent-contracts/test`

ADR-020 overlaps connector architecture, staging/dedup, governance promotion,
and lyrics format decisions already captured by accepted ADRs.

### Prompt

**Implementation starting points**
- Primary codebase: `apps/api` (Java/Spring controllers, services, repositories, migrations).
- Contract package: `packages/intent-contracts` (schemas/tests) when request/response shape is involved.
- Extend nearest tests first (for example in `apps/api/src/test/java/com/cadentia/{intent,reng,api/controller}` and `packages/intent-contracts/test`).
- Reference docs to update: `docs/ARCHITECTURE.md`, relevant ADR, and existing topic docs in `docs/`.

Publish a mapping that routes each integration concern to its canonical ADR and
implementation-plan anchor, including OpenAPI, Java modules, infra boundaries,
observability expectations, and documentation ownership.

### Acceptance criteria

- Mapping covers connector lifecycle, provenance, retries/idempotency, staging/dedup, review gates, and format compatibility.
- Planning references for integration work cite ADR-008/003/011/004, not ADR-020.
- Backlog templates include canonical ADR reference guidance.
- Readers can identify ADR-020 as historical duplicate context only.

### Restrictions

- Do not split authority between ADR-020 and canonical ADRs.
- Do not leave integration topics unmapped.
- Do not rewrite canonical ADR requirements during mapping publication.

### Canonical decision map (published)

| Integration concern | Canonical ADR authority | Implementation-plan anchors | Delivery/verification artifacts |
| --- | --- | --- | --- |
| Connector lifecycle (registration, adapter boundaries, import contract) | **ADR-008** | `apps/api` connector services/repositories, connector-facing OpenAPI operations, adapter integration tests in `apps/api/src/test/java` | OpenAPI endpoints reference connector domain language from ADR-008; adapter tests cover create/run/sync/deactivate lifecycle paths; docs references point to ADR-008 sections only. |
| Source provenance + auditability for imported content | **ADR-008** + governance handoff in **ADR-011** | DB migration and JPA entities in `apps/api/src/main/resources/db/migration` and `apps/api/src/main/java`, provenance fields in API DTOs/OpenAPI schemas, audit/review tests | Migration includes immutable source/provenance columns; API schema exposes provenance metadata; review workflow verifies provenance before promotion. |
| Retry, backoff, and idempotent reprocessing | **ADR-008** | Connector job orchestration in `apps/api` services, job status persistence, failure metrics/logs instrumentation | Deterministic idempotency key behavior under repeated imports; retry policy documented in runbook/docs and asserted by service tests. |
| Staging import boundary and deduplication before catalog promotion | **ADR-003** | Staging tables/entities + migration scripts, dedup pipeline services, API/admin review endpoints, staging-focused tests | No direct production writes from raw connector payloads; dedup decisions are reproducible and test-covered; staging-to-approved transition explicit. |
| Approval/review gates and doctrinal/governance controls | **ADR-011** | Review state machine logic in `apps/api`, moderation/admin endpoints, policy audit trails, reviewer workflow docs | Promotion requires review state transitions; unreviewed records remain non-recommendable; policy audit events emitted and queryable. |
| Lyrics/import format compatibility (ChordPro, OpenSong, Markdown, CSV mappings) | **ADR-004** (formats) + connector entrypoints from **ADR-008** | Parser/normalizer modules in `apps/api`, import DTO validation, format fixture tests | Supported formats are explicitly enumerated; unsupported/ambiguous formats fail with deterministic validation errors; test fixtures map each format to normalized internal model. |
| OpenAPI authority for external integration surfaces | **ADR-008/003/011/004**, by concern | `apps/api` OpenAPI YAML/annotations, controller contracts, `packages/intent-contracts` only where schema contracts are shared | Every integration endpoint description includes canonical ADR references; no endpoint cites ADR-020 as normative source. |
| Infrastructure boundaries (queues, schedulers, storage, secret/config ownership) | **ADR-008** primary, **ADR-003** for staging persistence boundary | Infra manifests/config under repo infra paths, scheduler/worker configuration, environment docs | Infra docs tie each boundary to canonical ADR; deployment checklists verify staging isolation and connector credential scoping. |
| Observability expectations (logs, metrics, traces for ingestion + review) | **ADR-008** for connector execution, **ADR-011** for governance decisions | Logging/metrics code in `apps/api`, dashboards/alerts docs, ops runbooks | Operational signals cover ingestion success/failure, retries, dedup outcomes, and review gate throughput; alert playbooks cite canonical ADRs. |
| Documentation ownership and backlog hygiene | This rejected ADR (historical only) + canonical ADRs above | `docs/ARCHITECTURE.md`, ADR index/table, backlog/issue templates, PR checklist | Templates instruct contributors to select ADR-008/003/011/004 as implementation authority and mention ADR-020 only as historical duplicate context. |

### Backlog/reference template guidance

Use the following template in implementation issues/PR descriptions for any
integration work:

1. **Canonical ADR reference (required):** ADR-008, ADR-003, ADR-011, or ADR-004.
2. **Concern type (required):** connector lifecycle / staging-dedup / governance gate / format compatibility / provenance / retry-idempotency.
3. **Implementation anchors (required):** specific module(s), OpenAPI surface(s), migration(s), and test suites.
4. **ADR-020 mention (optional):** allowed only as historical duplicate context, never as decision authority.

Example:

- Canonical ADRs: ADR-003 (staging), ADR-011 (promotion gate)
- Anchors: `apps/api` staging service + review controller + migration + integration tests
- Historical note: “Originally proposed in ADR-020; implemented per ADR-003/011.”

## Subtask 2: Add safeguards against duplicate integration planning

### Context

Duplicate ADR activation can recur without automated and process-level checks.

### Prompt

**Implementation starting points**
- Primary codebase: `apps/api` (Java/Spring controllers, services, repositories, migrations).
- Contract package: `packages/intent-contracts` (schemas/tests) when request/response shape is involved.
- Extend nearest tests first (for example in `apps/api/src/test/java/com/cadentia/{intent,reng,api/controller}` and `packages/intent-contracts/test`).
- Reference docs to update: `docs/ARCHITECTURE.md`, relevant ADR, and existing topic docs in `docs/`.

Add lint/review controls that flag implementation artifacts treating ADR-020 as
normative and redirect contributors to canonical documents.

### Acceptance criteria

- Documentation checks detect ADR-020 used as primary implementation reference.
- PR/review checklist includes canonical ADR verification for integration changes.
- Contributor docs provide examples of acceptable ADR-020 mentions.
- Validation workflow is runnable locally and in CI.

### Restrictions

- Do not block legitimate historical citations.
- Do not build checks that require external services.
- Do not enforce fragile exact-string-only matching rules.

## Subtask 3: Issue remediation guide for in-flight integration work

### Context

Existing branches/tasks may already reference ADR-020 and need deterministic
remediation without feature loss.

### Prompt

**Implementation starting points**
- Primary codebase: `apps/api` (Java/Spring controllers, services, repositories, migrations).
- Contract package: `packages/intent-contracts` (schemas/tests) when request/response shape is involved.
- Extend nearest tests first (for example in `apps/api/src/test/java/com/cadentia/{intent,reng,api/controller}` and `packages/intent-contracts/test`).
- Reference docs to update: `docs/ARCHITECTURE.md`, relevant ADR, and existing topic docs in `docs/`.

Write a remediation guide that rewires active tasks to canonical ADR sections,
including expected updates to OpenAPI specs, Java layers, infra automation,
observability, and documentation references.

### Acceptance criteria

- Guide includes step-by-step migration checklist and verification commands.
- Teams can show no behavioral delta when only references are migrated.
- Examples cover connector adapters, import staging, approval gating, and lyric-format handling.
- Documentation explains escalation path for genuinely net-new requirements.

### Restrictions

- Do not introduce new integration behavior under a governance-only remediation task.
- Do not close tasks as complete without reference migration evidence.
- Do not bypass formal ADR amendment process for new requirements.
