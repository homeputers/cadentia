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
