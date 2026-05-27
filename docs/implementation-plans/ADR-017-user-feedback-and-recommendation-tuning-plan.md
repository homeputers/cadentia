# ADR-017 Implementation Plan: User Feedback and Recommendation Tuning

## Objective

Introduce deterministic feedback influence on ranking while keeping eligibility
gates and catalog governance boundaries unchanged.

## Subtask 1: Define feedback/tuning API and taxonomy contracts

### Context

**Codebase anchors**
- API service: `apps/api`
- Intent contracts package: `packages/intent-contracts`
- DB migrations: `apps/api/src/main/resources/db/migration`
- Existing tests to extend: `apps/api/src/test/java` and `packages/intent-contracts/test`

Feedback inputs must be explicit, scoped, and machine-interpretable for
controlled deterministic scoring adjustments.

### Prompt

**Implementation starting points**
- Primary codebase: `apps/api` (Java/Spring controllers, services, repositories, migrations).
- Contract package: `packages/intent-contracts` (schemas/tests) when request/response shape is involved.
- Extend nearest tests first (for example in `apps/api/src/test/java/com/cadentia/{intent,reng,api/controller}` and `packages/intent-contracts/test`).
- Reference docs to update: `docs/ARCHITECTURE.md`, relevant ADR, and existing topic docs in `docs/`.

Extend OpenAPI with feedback event endpoints and schemas for outcomes
(`accepted`, `rejected`, `skipped`, `favorited`), replacement reasons, scope,
and inspection/reset operations.

### Acceptance criteria

- OpenAPI includes create/list/reset operations with role-aware authorization notes.
- Replacement reasons use controlled taxonomy enums.
- Schema distinguishes personal, team, and policy/global layers.
- API docs state that feedback influences ranking only, not eligibility.

### Restrictions

- Do not accept arbitrary free-form reason codes as scoring inputs.
- Do not expose reset operations without role protection.
- Do not blur feedback events with catalog metadata updates.

## Subtask 2: Implement feedback persistence and deterministic aggregation

### Context

ADR-017 requires dedicated feedback storage separate from canonical catalog data
and deterministic scope resolution.

### Prompt

**Implementation starting points**
- Primary codebase: `apps/api` (Java/Spring controllers, services, repositories, migrations).
- Contract package: `packages/intent-contracts` (schemas/tests) when request/response shape is involved.
- Extend nearest tests first (for example in `apps/api/src/test/java/com/cadentia/{intent,reng,api/controller}` and `packages/intent-contracts/test`).
- Reference docs to update: `docs/ARCHITECTURE.md`, relevant ADR, and existing topic docs in `docs/`.

Create Java repositories/services and migrations for feedback events, scoped
aggregates, and versioned feedback-rule configurations used by scoring.

### Acceptance criteria

- Migrations create feedback event and aggregate tables independent of catalog truth tables.
- Aggregation resolves scope in deterministic order (personal -> team -> policy fallback).
- Rule configurations are versioned and traceable in recommendation outputs.
- Eligibility filters remain unchanged when feedback is applied.

### Restrictions

- Do not write feedback directly into catalog entity records.
- Do not make ranking dependent on non-deterministic or time-unstable calculations.
- Do not permit feedback to bypass approval/provenance/licensing gates.

## Subtask 3: Integrate feedback contributions into scoring and explanations

### Context

Feedback effects must be inspectable and reproducible for governance and trust.

### Prompt

**Implementation starting points**
- Primary codebase: `apps/api` (Java/Spring controllers, services, repositories, migrations).
- Contract package: `packages/intent-contracts` (schemas/tests) when request/response shape is involved.
- Extend nearest tests first (for example in `apps/api/src/test/java/com/cadentia/{intent,reng,api/controller}` and `packages/intent-contracts/test`).
- Reference docs to update: `docs/ARCHITECTURE.md`, relevant ADR, and existing topic docs in `docs/`.

Wire feedback signals into recommendation scoring as explicit weighted
components and emit per-candidate contribution facts in explanation outputs.

### Acceptance criteria

- Same request + same catalog snapshot + same feedback state + same profile yields identical ordering.
- Explanation facts identify feedback contribution type and magnitude.
- Profile versioning captures tuning-rule changes with migration notes.
- Tests cover positive/negative feedback, conflicting scopes, and reset behavior.

### Restrictions

- Do not apply hidden heuristics outside versioned scoring rules.
- Do not emit explanation claims without backing feedback evidence.
- Do not mix eligibility failure reasons with ranking contributions.

## Subtask 4: Add observability, governance tooling, and docs

### Context

Admins and team leads need tooling to inspect drift, tune policy, and recover
from bad feedback patterns.

### Prompt

**Implementation starting points**
- Primary codebase: `apps/api` (Java/Spring controllers, services, repositories, migrations).
- Contract package: `packages/intent-contracts` (schemas/tests) when request/response shape is involved.
- Extend nearest tests first (for example in `apps/api/src/test/java/com/cadentia/{intent,reng,api/controller}` and `packages/intent-contracts/test`).
- Reference docs to update: `docs/ARCHITECTURE.md`, relevant ADR, and existing topic docs in `docs/`.

Add metrics/traces/audits for feedback ingestion and scoring influence, then
document governance workflows for inspection and reset.

### Acceptance criteria

- Metrics include feedback volume by outcome/scope and ranking-impact distribution.
- Audit logs capture feedback mutations and reset actions with actor identity.
- Alerts detect anomalous spikes in negative feedback or reset frequency.
- Documentation includes role-based operational playbooks and data-retention rules.

### Restrictions

- Do not log sensitive personal context beyond required audit fields.
- Do not create dashboards without mapping metrics to operator actions.
- Do not release reset tooling without documented authorization boundaries.
