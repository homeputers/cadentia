# Implementation Plan: Missing Gaps for ADR-009 to ADR-013

Source gap review: [ADR-009 to ADR-013 Missing Gaps Review](./ADR-009-013-missing-gaps-review.md)

## Goal

Close the high-impact architecture and governance gaps left across ADRs 009-013 by adding explicit contracts, backend safeguards, test coverage, and operational controls equivalent in concreteness to ADR-001 through ADR-008 implementation plans.

---

## Subtask 1: Add parser run-history and supersession lineage (ADR-009)

### Context

ADR-009 requires recalculable derived parser outputs with provenance and transparency. Current behavior needs a first-class append-only parser-run history model.

### Prompt

Implement an append-only parser-run history contract and persistence model that records parser run identity, parser name/version, source hash, trigger type, actor, timestamp, supersedes/superseded-by links, status, warnings, and confidence snapshot.

### Acceptance criteria

- A dedicated parser-run history artifact exists and is append-only.
- Supersession lineage can be traced both backward and forward.
- Recalculation writes a new run record rather than mutating prior historical records.
- Tests verify lineage correctness and immutable history across multiple recalculations.

### Restrictions

- Do not overwrite raw source content.
- Do not collapse historical runs into a single mutable latest record.
- Do not allow parser-run deletion outside explicit retention policy code paths.

---

## Subtask 2: Introduce parser capability registry and warning/error codebook (ADR-009)

### Context

Parser plugins require explicit capability declarations and a governed warning/error taxonomy for deterministic diagnostics.

### Prompt

Create a versioned parser capability registry and parser warning/error codebook. Expose capability + code metadata in review/admin responses.

### Acceptance criteria

- Each parser plugin declares capabilities using a structured contract.
- Warning/error codes are centrally registered with severity and remediation hints.
- Unsupported capability usage yields deterministic status and code.
- Tests fail if unregistered warning/error codes are emitted.

### Restrictions

- Do not emit ad hoc free-form warning/error identifiers.
- Do not let the UI infer severity from text.
- Do not use LLMs to classify parser warnings.

---

## Subtask 3: Formalize fingerprint-to-dedupe integration and parser rollout orchestration (ADR-009)

### Context

Fingerprints should drive duplicate review support and parser-version rollout recalculations in an auditable, deterministic manner.

### Prompt

Define and implement a deterministic integration contract for fingerprint signals into duplicate scoring support plus an idempotent batch recalculation orchestration for parser upgrades.

### Acceptance criteria

- Fingerprint features are mapped to explicit duplicate-support signals with fixed weight semantics.
- Recalculation orchestration supports batch runs keyed by parser version and source-hash mismatch.
- Re-running the same orchestration input is idempotent.
- Tests cover duplicate-support signal generation, idempotency, and partial-failure recovery markers.

### Restrictions

- Do not auto-merge candidates solely from fingerprints.
- Do not use wall-clock randomness in orchestration ordering.
- Do not expose full copyrighted lyrics in diagnostics.

---

## Subtask 4: Add scoring profile lifecycle, constraint-relaxation policy, and search diagnostics contract (ADR-010)

### Context

ADR-010 requires deterministic, auditable selection. Runtime profile governance and tradeoff policy must be explicit.

### Prompt

Implement scoring profile lifecycle metadata (draft/active/deprecated), deterministic constraint-relaxation sequence, and search diagnostics that record pruning and tradeoff decisions.

### Acceptance criteria

- Profile lifecycle state is represented and validated.
- Constraint relaxation sequence is deterministic and documented in output diagnostics.
- Search diagnostics expose candidate pruning reasons and selected tradeoffs.
- Tests verify identical input produces identical profile/tradeoff/search diagnostics.

### Restrictions

- Do not allow runtime profile mutation without version/state controls.
- Do not hide tradeoff acceptance when constraints are relaxed.
- Do not use randomness in pruning/tie-break behavior.

---

## Subtask 5: Add benchmark matrix and audience-partitioned diagnostics (ADR-010)

### Context

Performance and diagnostic safety boundaries must be enforceable for admin vs public consumers.

### Prompt

Create repeatable performance benchmarks with explicit SLO thresholds and implement a diagnostics visibility policy separating admin detail from public-safe output.

### Acceptance criteria

- Benchmark suites cover multiple catalog sizes and request complexity profiles.
- Clear pass/fail latency thresholds are enforced in tests or CI gates.
- Admin diagnostics include deeper exclusion/constraint rationale.
- Public diagnostics redact admin-only/sensitive details while preserving deterministic result metadata.

### Restrictions

- Do not compromise approval/provenance gates to improve benchmark results.
- Do not expose sensitive or copyrighted payload details publicly.
- Do not make benchmark tests network-dependent.

---

## Subtask 6: Harden admin authorization, optimistic concurrency, and audit event catalog (ADR-011)

### Context

ADR-011 governance requires strict backend role enforcement, conflict control, and comprehensive auditability.

### Prompt

Implement endpoint/service authorization matrix enforcement, optimistic concurrency on mutating review actions, and a required audit event catalog covering success and denial flows.

### Acceptance criteria

- Role matrix tests validate permissions across viewer/reviewer/approver/rollback-admin roles.
- Mutations fail with explicit conflict semantics when version/etag checks fail.
- Audit events capture both authorized and denied actions with mandatory fields.
- Tests cover conflict retry paths and denied-action audit emission.

### Restrictions

- Do not rely on UI-only permission checks.
- Do not allow last-write-wins behavior on merge/approval/rollback mutations.
- Do not emit mutable or silently editable audit events.

---

## Subtask 7: Expand rollback dependency-graph policy and moderation eligibility linkage (ADR-011)

### Context

Rollback and moderation must be policy-driven with predictable eligibility impact.

### Prompt

Implement rollback dependency graph evaluation (including irreversible blockers) and moderation policy linkage that maps flag types/severities to recommendation eligibility effects.

### Acceptance criteria

- Rollback preview reports direct and transitive impacts.
- Irreversible blockers are surfaced with machine-readable codes.
- Moderation flag policy explicitly controls eligibility inclusion/exclusion behavior.
- Tests cover rollback with blockers, rollback without blockers, and moderation lifecycle impact on eligibility.

### Restrictions

- Do not execute rollback when blockers require manual intervention.
- Do not allow moderation flags to affect eligibility without explicit policy mapping.
- Do not expose confidential moderation notes in public responses.

---

## Subtask 8: Add schema-evolution and error-taxonomy governance for intent boundary (ADR-012)

### Context

ADR-012 safety depends on strict versioned schema governance and predictable validation outcomes.

### Prompt

Define schema-evolution rules (compatibility and migration tests) and implement a stable validation-error taxonomy mapped to API behavior (retryable, clarify, unsupported, hard-fail).

### Acceptance criteria

- Compatibility policy exists for new schema versions.
- Validation errors map deterministically to API outcome classes.
- Tests enforce backward/forward compatibility expectations per policy.
- Error responses are machine-readable with stable codes.

### Restrictions

- Do not accept unknown fields without explicit schema version updates.
- Do not blur retryable vs non-retryable validation categories.
- Do not permit unvalidated intent objects into recommendation orchestration.

---

## Subtask 9: Expand intent orchestration observability and integration-failure proofs (ADR-012)

### Context

Single-retry behavior and safe failure must be auditable and proven never to leak invalid outputs into REng.

### Prompt

Implement retry/failure observability metrics and exhaustive integration tests proving invalid, unsupported, and clarify outcomes cannot invoke recommendation execution.

### Acceptance criteria

- Observability emits structured metrics/events for first-pass failure, retry attempts, retry outcomes, and terminal outcome class.
- Integration tests cover malformed JSON, schema invalid, prohibited fields, selected-song payloads, and unsupported intents.
- Tests assert REng invocation count is zero for invalid/non-generate intents.
- Logging complies with sensitive-content minimization policy.

### Restrictions

- Do not add unbounded retries.
- Do not perform heuristic parsing recovery from prose.
- Do not log full sensitive user payloads.

---

## Subtask 10: Add explanation-code lifecycle governance and coverage matrix enforcement (ADR-013)

### Context

ADR-013 trust depends on stable explanation codes and guaranteed alignment with scoring logic.

### Prompt

Implement explanation-code lifecycle governance (active/deprecated/replaced) plus a test-enforced coverage matrix mapping scoring/filter/transition components to explanation codes or intentional omissions.

### Acceptance criteria

- Explanation code registry supports lifecycle metadata.
- Coverage matrix is machine-checkable and validated in tests.
- Adding new scoring components requires updating explanation mapping or explicit exemption.
- Tests fail on unmapped components.

### Restrictions

- Do not permit ad hoc explanation codes.
- Do not allow free-form rendered text to bypass code registry constraints.
- Do not claim explainability for components lacking evidence mapping.

---

## Subtask 11: Strengthen evidence integrity, admin/public explanation boundaries, and renderer safety (ADR-013)

### Context

Explanation facts must remain evidence-grounded and safely renderable without leaking internal/admin-only data.

### Prompt

Add referential-integrity checks for explanation evidence, enforce admin/public explanation response partitions, and harden renderer template validation with localization-ready fallback policy.

### Acceptance criteria

- Every explanation evidence reference resolves to a valid source artifact.
- Admin-only exclusion/near-miss facts are omitted from public responses by contract.
- Renderer fails safely on missing template values and uses deterministic fallback behavior.
- Tests cover evidence-missing failures, admin/public boundary enforcement, and renderer fallback paths.

### Restrictions

- Do not expose private moderation, provenance, or audit-note data in public explanation payloads.
- Do not use LLMs for explanation rendering in this phase.
- Do not treat rendered text as authoritative over structured explanation facts.
