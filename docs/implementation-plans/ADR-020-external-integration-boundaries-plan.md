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

### Remediation guide (published)

Use this guide when a branch, ticket, or PR is already in flight and references
ADR-020 as implementation authority.

#### 1) Inventory the in-flight scope and classify concerns

1. Capture every ADR reference in the task body, PR description, linked design
   notes, and changed files.
2. Classify each referenced change by concern type:
   - connector lifecycle / retry-idempotency / provenance
   - staging + dedup boundary
   - approval/review governance
   - lyrics/import format compatibility
3. Map each concern to canonical ADR authority:
   - ADR-008 (connector architecture, provenance, retry/idempotency)
   - ADR-003 (staging + dedup)
   - ADR-011 (approval/review gate)
   - ADR-004 (format compatibility)
4. Record this mapping in the task checklist before any code edits.

#### 2) Replace authority references without changing behavior

1. Update issue/PR narrative text to remove ADR-020 as normative source.
2. Update in-repo docs and inline comments that cite ADR-020 for
   implementation behavior.
3. Keep any ADR-020 mention explicitly historical, e.g.:
   “Originally proposed in ADR-020; implemented per ADR-008/003/011/004.”
4. Do **not** modify runtime behavior, schema fields, workflow transitions, or
   API semantics in this remediation pass unless required for canonical
   alignment and separately approved.

#### 3) Rewire implementation anchors by surface area

For each concern, verify references and ownership in the expected layer:

- **OpenAPI / API contracts**
  - Ensure endpoint descriptions and operation docs cite canonical ADRs only.
  - Confirm `packages/intent-contracts` references (if present) do not treat
    ADR-020 as authority.
- **Java service/repository/controller layers (`apps/api`)**
  - Replace ADR-020 references in service docs/comments with mapped canonical
    ADRs.
  - Confirm behavior remains unchanged (same validations, transitions, retry
    semantics).
- **Infra automation and config boundaries**
  - Repoint runbook/checklist references to ADR-008 (connectors) and ADR-003
    (staging boundary) where applicable.
  - Keep queue/scheduler/secret ownership unchanged unless covered by approved
    canonical ADR work.
- **Observability artifacts**
  - Update metric/log/alert documentation references to canonical ADRs.
  - Preserve metric names, alert thresholds, and dashboards during remediation.
- **Documentation surfaces**
  - Update `docs/ARCHITECTURE.md`, ADR indexes, and implementation-plan links
    to mark ADR-020 as rejected duplicate context.

#### 4) Migration checklist (must be attached to ticket/PR)

- [ ] Concern map completed (each integration concern mapped to ADR-008/003/011/004).
- [ ] ADR-020 removed as normative authority from ticket/PR text.
- [ ] OpenAPI and contract docs updated to canonical ADR references.
- [ ] Java-layer comments/docs updated without behavior changes.
- [ ] Infra/runbook/observability references updated.
- [ ] Evidence attached: before/after reference diff + validation command output.
- [ ] Any net-new requirement logged as ADR amendment candidate (not folded into remediation).

#### 5) Verification commands (reference migration evidence)

Run from repo root and attach outputs to the task:

```bash
rg -n "ADR-020" docs apps packages
rg -n "ADR-(008|003|011|004)" docs apps packages
git diff -- docs apps packages
```

Expected result:
- ADR-020 remains only in historical/rejected context.
- Active implementation references point to ADR-008/003/011/004.
- Diffs show reference rewiring with no unintended behavior changes.

#### 6) No-behavior-delta verification expectations

When remediation only changes references/documentation, teams must demonstrate:

1. No API contract delta (no schema/route/semantic changes).
2. No state machine transition delta for staging/review promotion flows.
3. No connector retry/idempotency logic delta.
4. No import parser compatibility delta.

Evidence may include existing test suite results for touched areas and
“documentation-only” or “reference-only” diff review notes.

#### 7) Worked examples

1. **Connector adapter task in flight**
   - Before: PR cites ADR-020 for connector retries.
   - After: PR cites ADR-008 for retries/idempotency; code logic unchanged.
2. **Import staging task in flight**
   - Before: Ticket says ADR-020 requires staging table.
   - After: Ticket maps staging/dedup decisions to ADR-003 and review gate to
     ADR-011.
3. **Approval gating task in flight**
   - Before: Controller comment references ADR-020 gate policy.
   - After: Comment references ADR-011; transition behavior unchanged.
4. **Lyric-format handling task in flight**
   - Before: Parser doc references ADR-020 format support.
   - After: Parser doc cites ADR-004 for format compatibility and ADR-008 for
     connector entrypoint context.

#### 8) Escalation path for genuinely net-new requirements

If in-flight work reveals requirements not covered by ADR-008/003/011/004:

1. Mark the task section as “net-new requirement candidate.”
2. Document gap statement, impacted modules, risk, and why canonical ADRs are
   insufficient.
3. Open an ADR amendment proposal against the relevant canonical ADR
   (or a new ADR if scope is truly novel).
4. Keep remediation changes separate from feature-governance decisions until
   amendment is approved.
