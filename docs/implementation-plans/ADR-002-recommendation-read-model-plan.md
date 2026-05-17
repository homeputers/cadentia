# ADR-002 Implementation Plan: Recommendation Candidate Read Model Design

Source ADR: [ADR-002: Recommendation Candidate Read Model Design](../adr/ADR-002-recommendation-read-model.md)

## Goal

Create a deterministic candidate retrieval read model, initially as a SQL view named `v_recommendable_arrangements`, exposing approved arrangement data for the Recommendation Engine.

## Subtask 1: Confirm source schema dependencies

### Context

- Relevant ADR: `docs/adr/ADR-002-recommendation-read-model.md`
- Depends on ADR-001 catalog tables and ADR-005 approval records.
- The read model must expose arrangement identity, canonical song identity, language, key, BPM, time signature, energy, aggregated tags, and approval flags.

### Prompt

Inspect the implemented source-of-truth schema and identify the exact tables and columns needed to build `v_recommendable_arrangements`. Document any missing fields that must be added before creating the view.

### Acceptance criteria

- Lists the source tables and columns used by the read model.
- Confirms whether all ADR-required fields are present.
- Opens follow-up schema tasks for missing fields instead of working around them with nullable placeholders.
- Documents approval gating requirements for the read model.

### Source schema dependency confirmation

Confirmed against implemented migrations `V002__core_catalog_schema.sql`,
`V006__approval_schema_constraints_and_transitions.sql`, and
`V008__controlled_tag_taxonomy_schema.sql`. The source-of-truth dependencies for
`v_recommendable_arrangements` are:

| Read-model concern | Source table(s) | Required column(s) | Notes |
| --- | --- | --- | --- |
| Arrangement identity | `arrangements` | `id` | Exposed as `arrangement_id`. |
| Canonical song identity | `arrangements`, `songs` | `arrangements.song_id`, `songs.id` | Join `songs.id = arrangements.song_id`; expose `songs.id` as `song_id`. |
| Current lyrics identity | `lyrics_documents` | `id`, `arrangement_id`, `is_current` | Required for lyrics approval gating and lyrics-level tag aggregation; expose `id` as `current_lyrics_document_id`. |
| Display title | `songs` | `canonical_title` | Useful for candidate explainability and deterministic display; not used for song invention. |
| Language | `arrangements` | `language` | ADR field is present on arrangements; `songs.primary_language` remains canonical-song metadata but should not replace arrangement language for translations. |
| Key and mode | `arrangements` | `musical_key`, `key_mode` | ADR requires key; `key_mode` is also needed for relative major/minor policy. Recommendable rows must require non-null key and `MAJOR`/`MINOR` mode. |
| BPM | `arrangements` | `tempo_bpm` | Expose as `bpm`; recommendable rows must require non-null BPM. |
| Time signature | `arrangements` | `time_signature` | Recommendable rows must require non-null time signature. |
| Energy | `arrangements` | `energy_level` | Expose as `energy`; recommendable rows must require non-null energy. |
| Active catalog filtering | `arrangements`, `songs` | `arrangements.is_active`, `songs.song_status` | Recommendable rows must include only active arrangements and exclude archived songs. |
| Aggregated tags | `song_tags`, `arrangement_tags`, `lyrics_document_tags`, `tags` | `*_tags.*_id`, `*_tags.tag_id`, `tags.id`, `tags.slug`, `tags.is_active` | Aggregate active tag slugs from song, arrangement, and current lyrics document scopes in stable slug order. |
| Approval flags | `approval_records` | `song_id`, `arrangement_id`, `lyrics_document_id`, `approval_type`, `status` | Expose approval statuses for the required approval joins. |

All ADR-required fields are present in the implemented source schema. No
follow-up schema task is required before creating or maintaining the view, and
no nullable placeholders are needed for ADR-required fields.

Approval gating requirements:

- Join `approval_records` once per required approval type and entity scope; do
  not derive eligibility from `songs.song_status` alone.
- Song-level approvals required: `DOCTRINAL`, `EDITORIAL`, and `LICENSING`,
  each with `status = 'APPROVED'`.
- Arrangement-level approvals required: `MUSICAL` and `EDITORIAL`, each with
  `status = 'APPROVED'`.
- Current lyrics-document approvals required: `DOCTRINAL`, `EDITORIAL`, and
  `LICENSING`, each with `status = 'APPROVED'`.
- The source schema constrains approval records to exactly one entity scope and
  has one approval row per entity/type, so the view should use explicit joins
  rather than selecting an arbitrary approval record.

### Restrictions

- Do not create the view against incomplete or guessed column names.
- Do not add recommendation scoring in this subtask.
- Do not use LLM interpretation to fill missing song metadata.

## Subtask 2: Implement `v_recommendable_arrangements`

### Context

- ADR-002 requires an initial SQL view and allows a later materialized view.
- ADR guardrail: recommendation eligibility must be gated by approval state.

### Prompt

Add a database migration that creates `v_recommendable_arrangements` from normalized catalog tables. The view must return one row per recommendable arrangement and expose all ADR-required fields.

### Acceptance criteria

- Creates a SQL view named `v_recommendable_arrangements`.
- Returns arrangement ID, song ID, language, key, BPM, time signature, energy, aggregated tags, and approval flags.
- Excludes arrangements that lack required approvals for recommendation eligibility.
- Handles tag aggregation deterministically with stable ordering.
- Includes migration rollback or down behavior when the migration system supports it.
- Migration can be applied to an empty or seeded local database without errors.

### Restrictions

- Do not create a materialized view unless performance evidence exists and refresh behavior is specified.
- Do not include unapproved arrangements in candidate rows.
- Do not call external services or LLMs from database code.

## Subtask 3: Add read-model data access for the Recommendation Engine

### Context

- Recommendation Engine retrieval should use the read model rather than direct joins over source tables.
- Deterministic filters include key compatibility, tempo ranges, tags, energy, language, and approval state.

### Prompt

Create or update backend data-access functions that query `v_recommendable_arrangements` with deterministic filters. Return typed candidate records suitable for scoring by the Recommendation Engine.

### Acceptance criteria

- Provides typed candidate records matching the view columns.
- Supports filters for language, key, BPM range, energy, tags, and approval flags.
- Uses parameterized SQL or the project's safe query abstraction.
- Includes unit or integration tests for approved-only filtering and deterministic tag matching.
- Does not change final recommendation ordering unless an existing engine consumes the new retrieval function intentionally.

### Restrictions

- Do not perform random ordering unless explicitly seeded and documented.
- Do not let the LLM decide which candidates should be returned.
- Do not bypass approval gating in tests except through clearly named negative test fixtures.

## Subtask 4: Add performance and correctness checks

### Context

- The view must support efficient candidate retrieval as the catalog grows.
- Later optimization may use a materialized view, but only with evidence.

### Prompt

Add tests, explain-plan notes, or benchmark scripts that validate the read model's correctness and identify any required indexes on source tables.

### Acceptance criteria

- Verifies that unapproved arrangements are not returned.
- Verifies deterministic ordering or stable tie inputs where applicable.
- Documents indexes required to support common filters.
- Provides a repeatable command for running the correctness checks.

### Restrictions

- Do not prematurely introduce cache invalidation or materialized refresh jobs.
- Do not optimize by removing required approval or provenance data.

### Subtask 4 implementation notes

Implemented performance and correctness checks for the recommendation read model:

- Correctness coverage lives in `JdbcCandidateRetrieverIntegrationTest` and verifies:
  - unapproved or incompletely approved arrangements remain excluded from candidate retrieval;
  - non-`APPROVED` approval statuses do not pass the approval gate;
  - tag aggregation and controlled tag expansion stay deterministic;
  - rows with matching titles use the stable `ORDER BY title, arrangement_id` tie-breaker.
- Performance support lives in migration `V011__recommendable_read_model_performance_indexes.sql` and documents the source-table indexes needed by common candidate filters:
  - active arrangement filters by language, key, BPM, and energy;
  - approved song, arrangement, and current lyrics gates;
  - active controlled tag type/slug filtering;
  - reverse tag mapping lookups for song, arrangement, and lyrics tag filters/reports.
- The existing `v_recommendable_arrangements` migration remains a normal SQL view. No materialized view, cache invalidation, or refresh job was introduced.

Repeatable correctness command:

```bash
mvn -pl apps/api -Dtest=JdbcCandidateRetrieverIntegrationTest test
```

The integration test class uses Testcontainers PostgreSQL and is annotated with `@Testcontainers(disabledWithoutDocker = true)`, so the command runs the full read-model correctness checks when Docker is available and reports the checks as skipped in Docker-less environments.
