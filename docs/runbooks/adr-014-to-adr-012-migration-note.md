# ADR-014 to ADR-012 Migration Note for In-Flight Tasks

## Purpose

ADR-014 is rejected as a duplicate. This note provides deterministic, reference-only migration steps for feature branches and backlog items that still cite ADR-014.

- Source to retire: `docs/adr/ADR-014-llm-intent-extraction-contract.md`
- Canonical source: `docs/adr/ADR-012-llm-intent-extraction-contract.md`

## Scope

Use this note when a task, branch, PR, ticket, test plan, or operational runbook cites the rejected duplicate intent-contract ADR as normative for behavior.

This migration is **reference-only**:

- No behavior changes.
- No schema changes.
- No fallback/retry logic changes.
- No recommendation engine scope changes.

## Section Mapping: ADR-014 References to ADR-012

| Legacy ADR-014 reference topic | Replace with ADR-012 section(s) | Where this applies |
| --- | --- | --- |
| Intent JSON contract definition | ADR-012 "Decision" + "Requirements" | OpenAPI descriptions, intent-contract package docs, API endpoint notes |
| Validation and schema enforcement | ADR-012 "Requirements" + "Consequences" | Java request validation, schema tests, contract checks |
| Retry/fallback behavior for extraction failures | ADR-012 "Decision" + "Consequences" | API service orchestration docs, failure-path tests, runbooks |
| Anti-hallucination boundary (no song selection in LLM stage) | ADR-012 "Requirements" | Service boundary docs, architecture diagrams, reviewer checklist |
| Deterministic handoff to recommendation services | ADR-012 "Decision" + "Requirements" | `apps/api` service docs, REng interface notes |
| Observability/audit expectations for contract drift | ADR-012 "Consequences" | runbooks, alert metadata, telemetry docs |
| Lifecycle/governance status of ADR-014 | ADR-014 header metadata only (Rejected/Superseded) | historical traceability, ADR indexes |

## Deterministic Task Metadata Update Criteria

Update backlog labels or ticket metadata using all of the following criteria:

1. Remove any label/tag indicating the duplicate contract ADR is active (for example `adr-014-contract`).
2. Add/retain canonical tag `adr-012` (or local equivalent).
3. Set task note: `Reference migration only; behavior unchanged.`
4. Confirm acceptance text references ADR-012 for schema/validation/fallback behavior.
5. Keep ADR-014 mention only in a historical note field when needed.

### Suggested ticket annotation

`Migrated from ADR-014 duplicate reference to ADR-012 canonical contract on 2026-05-27. No runtime behavior changes.`

## In-Flight Branch Migration Checklist

Run this checklist before merge:

1. Search branch for ADR-014 references.
2. For each reference, classify as:
   - **Historical mention** (allowed), or
   - **Normative implementation reference** (must migrate to ADR-012).
3. Replace normative references with ADR-012 citations.
4. Re-run docs/tests checks to ensure no behavior or schema change was introduced.
5. Add PR note indicating this was a reference-only migration.

## Validation Checklist (No Behavior Change)

- [ ] No API request/response schema diffs.
- [ ] No changes in intent validation rules.
- [ ] No changes in extraction retry/fallback execution logic.
- [ ] No changes in REng handoff interfaces.
- [ ] No new metrics dimensions except reference/governance annotations.

## Correct vs Incorrect Reference Examples

### Correct

- "Intent extraction contract follows ADR-012. ADR-014 remains historical only."
- "Schema validation requirements are defined by ADR-012 requirements."

### Incorrect

- "Implement duplicate-ADR intent schema updates in controller validation."
- "Rejected duplicate ADR defines fallback behavior for extraction retries."
- "Use ADR-014 and ADR-012 together as co-equal contract sources."

## Search and Replace Hints

- Find suspect references:
  - `ADR-014`
  - `adr-014`
  - `llm-intent-extraction-contract` where linked document is ADR-014
- Typical locations:
  - `docs/`
  - `apps/api/src/test/`
  - `packages/intent-contracts/test/`
  - issue templates, PR descriptions, backlog export files

## Escalation Rule

If a task appears to require behavior changes while migrating references, stop and open a new ADR-012 amendment proposal instead of expanding this migration scope.
