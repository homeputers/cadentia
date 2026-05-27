# ADR-014 Implementation Plan: Duplicate Governance for Intent Contract

## Objective

Treat ADR-014 as a rejected duplicate of ADR-012 and prevent parallel
implementation paths for LLM intent extraction behavior.

## Subtask 1: Consolidate normative references to ADR-012

### Context

ADR-014 is explicitly rejected and superseded by ADR-012. Any active design,
API contract, or implementation reference must point to ADR-012 as the single
source of truth.

### Prompt

Audit planning and architecture references, then update them so intent
extraction behavior references ADR-012 only. Keep ADR-014 references only where
duplicate-governance context is needed.

### Acceptance criteria

- Implementation plan references for intent extraction point to ADR-012.
- OpenAPI and API docs do not cite ADR-014 as normative contract source.
- Java module/package documentation for intent parsing references ADR-012.
- Cross-document links preserve ADR-014 only as rejected lifecycle metadata.

### Restrictions

- Do not define a second intent schema in ADR-014 artifacts.
- Do not introduce compatibility branches keyed by ADR-014.
- Do not remove historical traceability that ADR-014 was proposed and rejected.

## Subtask 2: Add guardrails in delivery workflow against duplicate contract activation

### Context

Future work can accidentally reactivate rejected ADRs unless governance checks
exist in CI and review workflows.

### Prompt

Add static checks and review guidance to prevent new implementation tasks from
using ADR-014 as an active source for schema, validation, retry, or fallback
logic.

### Acceptance criteria

- Documentation lint/check step flags ADR-014 as non-normative for implementation tasks.
- PR template or contribution docs include a check for canonical ADR references.
- Observability/runbook docs define how to detect contract drift against ADR-012.
- Team can demonstrate failure case when a new task references ADR-014 as active.

### Restrictions

- Do not block legitimate historical mentions of ADR-014.
- Do not create brittle checks tied to exact phrasing only.
- Do not couple governance checks to external network services.

## Subtask 3: Publish migration note for in-flight tasks

### Context

In-flight feature branches or backlog items may still reference ADR-014 and need
clear remediation instructions.

### Prompt

Create a migration note that maps old ADR-014 references to ADR-012 sections,
including OpenAPI contract points, Java service boundaries, infra assumptions,
observability tags, and docs anchors.

### Acceptance criteria

- Migration note includes a section-by-section mapping to ADR-012.
- Backlog labels or task metadata can be updated with deterministic criteria.
- Teams have a checklist for validating no behavior changed during reference-only migration.
- Documentation includes examples of correct and incorrect references.

### Restrictions

- Do not reinterpret ADR-012 behavior during migration.
- Do not silently close tasks without reference updates.
- Do not mix net-new feature scope into duplicate-governance cleanup.
