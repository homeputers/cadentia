# ADR-020 Implementation Plan: Duplicate Governance for External Integrations

## Objective

Treat ADR-020 as rejected duplicate coverage and route all implementation to
canonical ADRs: ADR-008, ADR-003, ADR-011, and ADR-004.

## Subtask 1: Create canonical decision map for integration concerns

### Context

ADR-020 overlaps connector architecture, staging/dedup, governance promotion,
and lyrics format decisions already captured by accepted ADRs.

### Prompt

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

## Subtask 2: Add safeguards against duplicate integration planning

### Context

Duplicate ADR activation can recur without automated and process-level checks.

### Prompt

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
