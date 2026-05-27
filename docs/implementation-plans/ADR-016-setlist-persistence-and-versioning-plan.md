# ADR-016 Implementation Plan: Setlist Persistence and Versioning

## Objective

Provide immutable, auditable setlist versioning that preserves deterministic
recommendation baselines and human edits without mutating catalog truth.

## Subtask 1: Define setlist/version OpenAPI resources and diff payloads

### Context

**Codebase anchors**
- API service: `apps/api`
- Intent contracts package: `packages/intent-contracts`
- DB migrations: `apps/api/src/main/resources/db/migration`
- Existing tests to extend: `apps/api/src/test/java` and `packages/intent-contracts/test`

Clients need explicit setlist lineage, immutable versions, and inspectable
changes between baseline and edited snapshots.

### Prompt

**Implementation starting points**
- Existing setlist entrypoints: `apps/api/src/test/java/com/cadentia/api/controller/SetlistControllerTest.java` and `apps/api/src/main/java/com/cadentia/reng/SetlistService.java`.
- Scoring response model touchpoints: `apps/api/src/test/java/com/cadentia/reng/scoring/OrderedSetResponseTest.java`.

Design OpenAPI endpoints and response schemas for creating generated baselines,
submitting edits, retrieving versions, and querying diffs.

### Acceptance criteria

- API distinguishes logical `setlist_id` and immutable `version_id`.
- Responses include provenance (generated vs manual) at list and item levels.
- Diff payloads support reorder, replace, remove, and transpose actions.
- Error semantics prevent editing non-existent or locked versions.

### Restrictions

- Do not expose mutable endpoints that overwrite historical versions.
- Do not model items by free-text song names.
- Do not omit engine/scoring profile references from baseline payloads.

## Subtask 2: Implement persistence model, migrations, and lineage logic

### Context

ADR-016 requires storage of original request, parsed intent, selected
arrangements, ordering, explanation facts, and scoring context.

### Prompt

**Implementation starting points**
- Repository pattern: `apps/api/src/main/java/com/cadentia/catalog/repository/JdbcSongRepository.java`.
- New persistence classes should live under `apps/api/src/main/java/com/cadentia/reng/` or `.../setlist/` package with dedicated repository interfaces.
- Migration conventions from existing `V00x__*.sql` files under `db/migration`.

Implement Java entities/repositories/services and DB migrations for setlist
lineage, immutable snapshots, parent references, and auditable edit commits.

### Acceptance criteria

- Migrations create normalized tables for setlist lineage, versions, items, and edit events.
- Every item references `catalog_arrangement_id`.
- `parent_version_id` supports configured linear or branched lineage policy.
- Edit operations create new immutable version records (or auditable grouped transactions).
- Baseline generation payload is preserved and retrievable unchanged.

### Restrictions

- Do not mutate canonical arrangement key metadata for transposition.
- Do not conflate service-specific overrides with catalog entities.
- Do not persist incomplete versions without transactional integrity.

## Subtask 3: Implement version compare/replay and debugging support

### Context

Reproducibility requires inspection and replay of inputs and engine context.

### Prompt

**Implementation starting points**
- Repro/debug anchors: `apps/api/src/test/java/com/cadentia/reng/scoring/ScoringDiagnosticsTest.java`, `RecommendationExplanationAuditRegressionTest.java`.
- Use deterministic comparison helpers from existing scoring tests before adding version diff utility.

Build services to fetch historical snapshots, compute deterministic version diffs,
and expose debugging data needed to reproduce a generated baseline.

### Acceptance criteria

- Historical versions can be retrieved with full metadata context.
- Diff algorithm deterministically reports structural and attribute changes.
- Replay support includes request, intent, scoring profile, and selected arrangements.
- Documentation defines expected behavior for missing/deprecated dependencies.

### Restrictions

- Do not rely on external mutable state for diff computation.
- Do not hide unavailable replay prerequisites.
- Do not collapse multiple edits into opaque summary-only records.

## Subtask 4: Add observability, retention policy, and operator docs

Status: Completed (2026-05-27)

### Context

Version growth and edit activity need operational controls and visibility.

### Prompt

**Implementation starting points**
- Add counters/timers around new service methods in setlist version service and wire through existing logging style used by `LoggingIntentOrchestrationObserver`.
- Document retention in `docs/ARCHITECTURE.md` + `docs/implementation-plans/README.md` index update if needed.

Instrument create/edit/retrieve paths, define retention behavior for abandoned
drafts, and document runbooks for lineage anomalies and storage growth.

### Acceptance criteria

- Metrics track version creation rate, diff requests, and retrieval latency.
- Logs/audit events capture actor, action, and before/after version references.
- Infrastructure config defines retention/archival policy for drafts if enabled.
- Documentation includes failure handling for partial edit commits and conflict retries.

### Restrictions

- Do not delete immutable published history outside explicit policy.
- Do not emit personally sensitive data in high-cardinality telemetry.
- Do not ship retention behavior without documented recovery procedures.

### Completion Notes

- Observability coverage is specified in `docs/ARCHITECTURE.md` under ADR-016 operations, including version creation/diff/retrieval metrics and conflict counters.
- Draft retention defaults and archival/delete safeguards are documented with explicit recovery expectations.
- Operator runbook now exists at `docs/runbooks/adr-016-setlist-versioning-operations.md`, including partial edit commit remediation and conflict retry procedures.
