# ADR-001 Implementation Plan: Song Data Infrastructure and Storage Architecture

Source ADR: [ADR-001: Song Data Infrastructure and Storage Architecture](../adr/ADR-001-song-data-infrastructure.md)

## Goal

Implement PostgreSQL as Cadentia's system of record with normalized tables for canonical songs, arrangements, lyrics documents, tags, provenance records, approval records, and import batches.

## Subtask 1: Map the existing data and persistence structure

Implementation note: [ADR-001 Subtask 1: Existing Data and Persistence Map](../implementation-notes/ADR-001-subtask-1-existing-data-persistence-map.md)

### Context

- Relevant ADR: `docs/adr/ADR-001-song-data-infrastructure.md`
- Related ADRs: `docs/adr/ADR-002-recommendation-read-model.md`, `docs/adr/ADR-003-song-import-deduplication.md`, `docs/adr/ADR-005-approval-doctrinal-review.md`, `docs/adr/ADR-007-tag-taxonomy.md`
- Relevant project docs: `docs/ARCHITECTURE.md`, `docs/diagrams/er-diagrams.md`

### Prompt

Review the repository's current persistence, schema, model, migration, and seed-data conventions. Produce or update a short implementation note describing where PostgreSQL schema artifacts, generated types, and seed fixtures should live before adding migrations.

### Acceptance criteria

- Identifies the current application stack and migration mechanism, or states that no implementation stack exists yet.
- Lists all files or directories that will be affected by the database implementation.
- Confirms how schema changes will be reviewed and run locally.
- Does not change runtime behavior unless schema scaffolding already exists and requires metadata updates.

### Restrictions

- Do not introduce a second persistence technology.
- Do not create tables before confirming the repository's migration conventions.
- Do not add pgvector or semantic-search infrastructure in this subtask.

## Subtask 2: Create the core catalog schema

### Context

- ADR-001 requires normalized PostgreSQL tables centered on `songs`, `arrangements`, `lyrics_documents`, `tags`, `provenance_records`, `approval_records`, and `import_batches`.
- ADR guardrail: LLMs must never invent songs; only persisted curated catalog records may be recommended.

### Prompt

Add the initial database migration for the core catalog schema. Define primary keys, foreign keys, unique constraints, required metadata fields, timestamps, and indexes needed for deterministic catalog lookup.

### Acceptance criteria

- Adds normalized tables for canonical songs, arrangements, lyrics documents, tags, provenance records, approval records, and import batches.
- Ensures arrangements belong to canonical songs and lyrics documents are traceable to either a song or arrangement according to the chosen model.
- Stores provenance and approval records as first-class data rather than embedded unstructured JSON only.
- Adds constraints that prevent orphaned arrangements, approvals, provenance records, and import records.
- Includes indexes for common lookup fields such as title, normalized title, CCLI number when available, song ID, arrangement ID, and approval status.
- Migration can be applied to an empty local database without errors.

### Restrictions

- Do not store generated recommendations in the source-of-truth catalog tables.
- Do not rely on LLM-generated metadata without provenance fields.
- Do not denormalize the source-of-truth schema solely for recommendation query speed; ADR-002 owns the read model.

## Subtask 3: Add application data-access types and validation

### Context

- The schema must support deterministic backend workflows for import, approval, and recommendation.
- Future code should consume typed records rather than ad hoc SQL result shapes.

### Prompt

Add or update the application's data-access layer to expose typed operations for creating and reading songs, arrangements, lyrics documents, tags, provenance records, approval records, and import batches. Validate enum-like values at the application boundary.

### Acceptance criteria

- Provides typed create/read/update operations matching the new schema.
- Validates required fields and enum-like values before database writes.
- Includes tests or fixtures proving invalid records are rejected.
- Keeps database-generated IDs and timestamps authoritative.

### Restrictions

- Do not let client input override database audit timestamps.
- Do not add recommendation scoring or ordering logic in this layer.
- Do not call LLM services from data-access code.

## Subtask 4: Seed minimal reference data

### Context

- Cadentia needs controlled data for development and tests without hallucinated songs.
- Seed data should be clearly marked as test or fixture data if it is not production-approved content.

### Prompt

Add seed or fixture data that exercises the schema with canonical songs, arrangements, lyrics documents, tags, provenance records, approval records, and import batches. Ensure the data is safe for tests and clearly separated from production catalog data.

### Acceptance criteria

- Seeds at least one complete song-to-arrangement-to-lyrics flow with provenance and approval records.
- Clearly labels fixture data so it cannot be mistaken for a production-approved catalog unless intentionally approved.
- Documents how to load and reset the seed data locally.
- Automated tests can use fixtures without reaching external services.

### Restrictions

- Do not include copyrighted full lyrics unless licensing and provenance are explicitly documented.
- Do not mark fixture content as recommendable unless approval requirements are intentionally satisfied.
- Do not import real catalogs from external providers in this subtask.

## Subtask 5: Update architecture documentation

### Context

- Relevant docs: `docs/ARCHITECTURE.md`, `docs/diagrams/er-diagrams.md`, `docs/adr/README.md`
- ADR-001 establishes the source-of-truth persistence model.

### Prompt

Update architecture and ER documentation to describe the implemented PostgreSQL schema, ownership boundaries, and how future ADRs build on the catalog model.

### Acceptance criteria

- Documents the canonical source-of-truth tables and their relationships.
- Explains that pgvector is optional future enrichment and not part of deterministic selection.
- Links ADR-001 and any implementation files or migration names.
- Keeps diagrams consistent with migration names and table relationships.

### Restrictions

- Do not document speculative tables as implemented.
- Do not imply LLMs can create or select catalog songs.
