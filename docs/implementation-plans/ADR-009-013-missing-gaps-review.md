# ADR-009 to ADR-013 Missing Gaps Review

## Scope

This document reviews implementation gaps that remain after comparing:

- ADRs `009` through `013`
- Their implementation plans in `docs/implementation-plans/`
- Current code in `apps/api` and `packages/intent-contracts`

The goal is to identify **high-impact missing behavior** (not micro-refactors) that should be added before these ADRs can be treated as fully implemented.

---

## ADR-009 (Lyrics Parsing & Musical Analysis) — Missing Gaps

1. **Parser output lifecycle is not fully durable/auditable.**
   Current logic updates parse results on lyrics documents, but there is no explicit append-only parser-run history entity with first-class supersession lineage and recalculation trigger metadata (actor, reason, trigger type).

2. **Plugin capability governance is incomplete.**
   Registry resolution exists, but there is no explicit capability matrix contract (e.g., supports sections/chords/Nashville/fingerprints/confidence level) used by review APIs and admin diagnostics.

3. **Structured parse errors and warning taxonomy are not centrally governed.**
   Warnings are emitted, but there is no single versioned registry of parser warning/error codes with severity and remediation hints.

4. **Fingerprint usage integration is partial.**
   Fingerprints are generated, but there is no explicit integration contract that feeds fingerprint bundles into duplicate-review scoring with explainable feature weights.

5. **Recalculation queue/orchestration semantics are missing.**
   Recalculation service behavior exists but does not define idempotent, batch-safe orchestration for parser-version upgrade rollouts.

---

## ADR-010 (Recommendation Scoring Architecture) — Missing Gaps

1. **Scoring profile governance is under-specified in runtime.**
   Profiles exist, but no policy is defined for profile publication lifecycle (draft/candidate/active/deprecated) and compatibility checks against persisted diagnostics.

2. **Ordering/search strategy explainability is incomplete.**
   Transition scoring exists, but there is no documented deterministic search-strategy contract (beam/window limits, pruning rationale, failure modes) surfaced to diagnostics.

3. **Constraint relaxation policy is not explicit.**
   The engine should report when/why it intentionally accepts tradeoffs (tempo/key-center/quota pressure), but policy thresholds and relaxation sequence are not codified.

4. **Performance SLO coverage is thin.**
   There is no explicit benchmark matrix tied to catalog sizes and request patterns (default counts, heavy exclusions, sparse metadata, high duplicate pressure) with pass/fail thresholds.

5. **Diagnostics audience partitioning is incomplete.**
   Admin diagnostic detail vs. public-safe detail boundaries are not fully formalized in a contract.

---

## ADR-011 (Admin Review & Governance UI) — Missing Gaps

1. **Role/permission model needs stronger backend enforcement contract.**
   Admin workflows exist, but there is no complete matrix proving endpoint-by-endpoint action permissions (viewer/reviewer/approver/rollback admin) plus state-transition gating.

2. **Optimistic concurrency and conflict semantics are missing in workflow APIs.**
   Merge/approval/rollback operations need explicit version checks and conflict response codes to prevent reviewer clobbering.

3. **Audit coverage standards are incomplete.**
   Current audit events exist, but there is no comprehensive event catalog requiring both successful and denied action events with mandatory fields.

4. **Rollback impact graph depth is limited.**
   Rollback preview/execute flows exist, but no dependency graph policy is defined for cascaded entities and irreversible blockers.

5. **Moderation policy-to-eligibility linkage is not fully explicit.**
   Moderation flags exist, but escalation rules and eligibility impact policy by flag type/severity are not fully codified.

---

## ADR-012 (LLM Intent Extraction Contract) — Missing Gaps

1. **Schema evolution policy needs formalization.**
   `v1` exists, but there is no forward/backward compatibility policy with migration test requirements across schema versions.

2. **Validation error taxonomy and API mapping are incomplete.**
   Validation exists, but a complete stable error-code map to API responses/retryability/clarification guidance is not fully governed.

3. **Single-retry orchestration observability is under-specified.**
   Retry behavior exists, but missing metrics/event schema for malformed vs schema-failed first pass, retry success rate, and safe-failure categories.

4. **Natural-language endpoint contract tests are not exhaustive.**
   Need explicit integration cases proving recommendation engine invocation is impossible on all invalid/unsupported/clarify paths.

5. **Prompt guardrail drift protection is mostly phrase-level.**
   Guardrail tests exist, but semantic policy tests (structured assertions against prohibited capability classes) should complement literal phrase checks.

---

## ADR-013 (Recommendation Explanation System) — Missing Gaps

1. **Explanation code governance lifecycle is incomplete.**
   Explanation facts exist, but no controlled deprecation/versioning policy for codes and templates is defined.

2. **Coverage completeness against ADR-010 components is not guaranteed.**
   Need a machine-checkable mapping table from scoring/filter/transition components to explanation code(s) or explicit intentional omission.

3. **Admin exclusion/near-miss detail policy is under-specified.**
   Distinction between user-visible and admin-only explanation facts needs stricter contract and response-shape enforcement.

4. **Evidence referential integrity checks should be stronger.**
   Ensure every explanation evidence reference resolves to real request/scoring/filter/transition/provenance artifacts.

5. **Renderer safety and i18n extensibility are not fully planned.**
   Deterministic rendering exists, but template parameter validation, localization strategy, and fallback policy need explicit governance.
