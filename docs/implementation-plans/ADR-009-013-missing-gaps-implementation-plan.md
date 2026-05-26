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

### Implementation blueprint

#### A) Fingerprint-to-dedupe contract

- Introduce a `FingerprintSupportSignal` contract with immutable fields:
  - `signalCode` (stable enum/code, e.g., `FP_LYRICS_HASH_EXACT`)
  - `comparisonScope` (`song`, `arrangement`, `source_document`)
  - `weight` (fixed decimal set in configuration/code, not runtime user input)
  - `direction` (`supports_duplicate`, `supports_distinct_variant`, `neutral`)
  - `evidence` (redacted hashes, normalized tokens, section/chord fingerprints only)
- Add a deterministic aggregation step in dedupe scoring:
  - score = sum of registered fingerprint support signals + existing non-fingerprint checks.
  - fingerprint contributions must be explainable per-signal in admin review output.
- Keep dedupe as reviewer-assist only:
  - signals may open/raise duplicate-review priority.
  - signals may never auto-merge or auto-reject candidates.

#### B) Parser rollout orchestration contract

- Introduce a `ParserRecalcBatch` identity keyed by:
  - `targetParserName`
  - `targetParserVersion`
  - `selectionPredicateHash` (hash of the canonicalized selection query/criteria)
  - `sourceSnapshotHash` (hash of candidate source-hash ids included at batch creation time)
- Generate deterministic item ordering using:
  1. parser name asc
  2. parser version asc
  3. lyrics document id asc
- Item eligibility rules:
  - include records where `last_parser_version != target_parser_version` OR `last_source_hash != current_source_hash`.
  - exclude records in terminal/legal hold states per approval policy.

#### C) Idempotency and recovery semantics

- Persist per-item execution status in batch history (`pending`, `succeeded`, `failed_retryable`, `failed_terminal`, `skipped_idempotent`).
- Re-running the same batch identity:
  - must not create duplicate parser-run history rows for previously successful items with unchanged source hash + parser version.
  - may re-attempt only retryable failures.
- On partial failure:
  - batch remains queryable with progress counters and failed-item diagnostics.
  - no global rollback of successful per-item parser runs.

#### D) Required test matrix

- Unit: fingerprint signal mapping emits only registered signal codes/weights.
- Unit: deterministic batch ordering remains stable across repeated runs.
- Integration: identical batch identity produces no duplicate successful parser-run records.
- Integration: source-hash change between runs creates a new parser run and supersedes prior run.
- Integration: partial failures preserve succeeded items and expose retryable/terminal markers.

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

### Implementation blueprint

#### A) Rollback dependency-graph contract

- Introduce a typed `RollbackGraphNode` / `RollbackGraphEdge` model with stable node categories:
  - `song`, `arrangement`, `lyrics_version`, `approval_record`, `merge_decision`, `moderation_event`, `read_model_projection`.
- Build graph edges with explicit `edgeType` enums:
  - `depends_on`, `supersedes`, `derived_from`, `gated_by_approval`, `gated_by_moderation`, `projected_into`.
- Require every rollback request to resolve into a deterministic impact set:
  - `directImpactNodeIds` (immediate targets)
  - `transitiveImpactNodeIds` (closure traversal)
  - `eligibilityAffectedNodeIds` (subset impacting recommendable state)

#### B) Blocker policy and machine-readable rollback preview

- Add `RollbackPreview` output contract with:
  - `rollbackAllowed` (boolean)
  - `blockingCodes[]` (stable codes)
  - `warningCodes[]`
  - `requiredManualActions[]`
  - `impactSummary` counters by node/edge type.
- Define irreversible / manual-intervention blocker codes (examples):
  - `RBK_APPROVAL_AUDIT_IMMUTABLE`
  - `RBK_EXTERNAL_LICENSE_HOLD`
  - `RBK_LEGAL_REVIEW_LOCK`
  - `RBK_MISSING_SOURCE_PROVENANCE`
  - `RBK_READMODEL_REBUILD_REQUIRED` (warning if async rebuild is allowed; blocker if policy forbids delayed rebuild).
- Enforce "preview-first" execution semantics:
  - execution endpoint must require a matching preview hash/version.
  - if graph state changed since preview, return conflict and require re-preview.

#### C) Moderation-to-eligibility policy linkage

- Introduce a versioned `ModerationEligibilityPolicy` table/config keyed by:
  - `flagType` (e.g., doctrinal, licensing, quality, provenance)
  - `severity` (`info`, `warning`, `high`, `critical`)
  - `scope` (`song`, `arrangement`, `lyrics_version`)
  - `effect` (`no_change`, `exclude_from_recommendation`, `exclude_until_resolved`, `allow_existing_sets_only`)
  - `requiresApprovalType[]` for reinstatement (e.g., doctrinal re-approval).
- Disallow implicit behavior:
  - unknown flag type/severity/scope combinations must fail validation.
  - recommendation eligibility changes must cite policy version + rule id in audit events.
- Lifecycle semantics:
  - opening a flag applies effect immediately to read-model eligibility.
  - resolving/overriding a flag restores eligibility only after policy-specified approvals are satisfied.

#### D) Public/admin visibility boundary

- Public APIs expose eligibility state + stable moderation effect codes only.
- Admin APIs include full policy traces, actor attribution, and internal notes.
- Confidential reviewer notes must never appear in recommendation/public diagnostics.

#### E) Required test matrix

- Unit: graph traversal returns stable direct/transitive impact sets for fixed fixtures.
- Unit: blocker policy maps graph conditions to deterministic `blockingCodes`.
- Integration: rollback execution denied when preview hash is stale.
- Integration: rollback succeeds when no blockers and updates impacted eligibility/read-model markers.
- Integration: moderation flag create/update/resolve transitions produce policy-linked eligibility changes and required re-approval gating.
- Contract: public API redacts confidential moderation notes while preserving effect codes.

### Acceptance criteria

- Rollback preview reports direct and transitive impacts.
- Irreversible blockers are surfaced with machine-readable codes.
- Moderation flag policy explicitly controls eligibility inclusion/exclusion behavior.
- Eligibility transitions include policy-versioned audit evidence and deterministic reinstatement gating.
- Tests cover rollback with blockers, rollback without blockers, stale-preview conflict handling, and moderation lifecycle impact on eligibility.

### Restrictions

- Do not execute rollback when blockers require manual intervention.
- Do not allow moderation flags to affect eligibility without explicit policy mapping.
- Do not bypass preview-hash concurrency checks on rollback execution.
- Do not expose confidential moderation notes in public responses.

---

## Subtask 8: Add schema-evolution and error-taxonomy governance for intent boundary (ADR-012)

### Context

ADR-012 safety depends on strict versioned schema governance and predictable validation outcomes.

### Prompt

Define schema-evolution rules (compatibility and migration tests) and implement a stable validation-error taxonomy mapped to API behavior (retryable, clarify, unsupported, hard-fail).

### Implementation blueprint

#### A) Schema versioning and compatibility contract

- Introduce explicit intent payload envelope fields:
  - `schemaName` (fixed: `intent.generate_setlist`)
  - `schemaVersion` (semver string, e.g., `1.2.0`)
  - `contractRevision` (monotonic integer for prompt+schema bundle governance)
- Compatibility policy:
  - **Patch** (`x.y.z -> x.y.z+1`): bugfix/clarification only, no structural changes.
  - **Minor** (`x.y -> x.y+1`): additive-only (new optional fields/enums with default behavior).
  - **Major** (`x -> x+1`): breaking change; requires migration adapter and explicit rollout gate.
- Unknown top-level schema names or versions outside supported range must be rejected with stable `unsupported_schema_*` codes.

#### B) Allowed-change matrix and migration governance

- Add machine-readable schema changelog entries with:
  - `fromVersion`, `toVersion`, `changeType`, `compatibilityClass`, `migrationRequired`, `sunsetDate`.
- Allowed without major bump:
  - add optional slot
  - add enum value with deterministic fallback
  - tighten description text without validation impact.
- Requires major bump:
  - remove/rename fields
  - change field type
  - convert optional to required without backend default
  - change semantic meaning of existing field values.
- If `migrationRequired=true`, provide deterministic adapter tests proving old payload maps to new canonical form.

#### C) Validation error taxonomy and API outcome mapping

- Create a centralized `IntentValidationErrorCode` registry with fields:
  - `code`, `category`, `httpStatus`, `outcomeClass`, `retryable`, `userActionHintKey`.
- Required category set:
  - `parse_error` (malformed JSON)
  - `schema_error` (missing required/type/unknown field/range)
  - `intent_error` (unsupported intent for endpoint)
  - `policy_error` (prohibited slot combinations)
  - `boundary_violation` (selection/recommendation payload leakage)
- Deterministic outcome mapping:
  - `retryable`: malformed JSON on first pass, transient model envelope issues.
  - `clarify`: valid intent but insufficient/ambiguous required user constraints.
  - `unsupported`: unsupported intent/schema/action requested.
  - `hard_fail`: policy/boundary violations or repeated invalid output after retry.

#### D) Response contract + orchestration guardrails

- Error response must include:
  - `errorCode`
  - `outcomeClass`
  - `retryEligible`
  - `schemaVersionEvaluated`
  - `correlationId`
  - `details[]` (field path + machine-readable reason code; no prose dependency)
- Enforce gateway guardrail: only `intent=GENERATE_SETLIST` with successful validation can call REng.
- Persist `validationDecision` audit events for every rejected/clarify/unsupported outcome.

#### E) Required test matrix

- Contract: unknown field fails with stable code and does not invoke REng.
- Contract: additive minor-version payloads remain accepted by supported readers.
- Contract: breaking-version payloads return `unsupported` until migration gate enabled.
- Integration: first-pass parse failure retries once; second failure hard-fails with stable code.
- Integration: boundary-violation payload (e.g., selected songs emitted by LLM) is rejected with `hard_fail` and zero REng calls.
- Regression: error-code registry completeness test fails on uncatalogued emitted codes.

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

### Implementation blueprint

#### A) Explanation-code registry lifecycle model

- Add a versioned `ExplanationCodeRegistryEntry` contract with immutable fields:
  - `code`
  - `status` (`active`, `deprecated`, `replaced`)
  - `scope[]`
  - `severity[]`
  - `introducedInVersion`
  - `deprecatedInVersion` (optional)
  - `replacedBy` (required when status is `replaced`)
  - `evidenceContractRef`
- Enforce emission policy in recommendation explanation assembly:
  - current engine version may emit only `active` codes.
  - `deprecated` code emission must be gated by an explicit compatibility flag and sunset date.
  - `replaced` codes are accepted only for historical read/replay paths, not live emission.

#### B) Coverage matrix contract

- Introduce a machine-readable `ExplanationCoverageMatrix` keyed by `componentId`.
- Each matrix row must include:
  - `componentType` (`scoring`, `filter`, `transition`, `quota`, `policy_guard`)
  - `explanationCode` (nullable only for intentional omission)
  - `coverageMode` (`required`, `optional`, `intentional_omission`)
  - `omissionReasonCode` (required when intentional omission)
  - `evidenceContractRef`
- Require matrix updates whenever scoring/filter/transition components are added or renamed.

#### C) Test and CI enforcement

- Unit: registry validation fails on duplicate code, invalid lifecycle transitions, or missing `replacedBy` for `replaced` entries.
- Unit: coverage matrix validation fails when component IDs are missing, duplicated, or reference unknown codes.
- Integration: explanation payload for deterministic scenarios includes all `required` component mappings with evidence-conformant facts.
- CI guard: schema/component inventory snapshot test fails when new components exist without explicit matrix rows.

#### D) Governance and migration behavior

- Publish compatibility rules for deprecated/replaced code handling windows.
- Require migration notes for any lifecycle transition away from `active`.
- Maintain changelog entries mapping old→new explanation codes for analytics/reporting continuity.

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
