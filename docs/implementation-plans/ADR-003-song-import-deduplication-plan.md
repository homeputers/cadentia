# ADR-003 Implementation Plan: Song Import and Deduplication Workflow

Source ADR: [ADR-003: Song Import and Deduplication Workflow](../adr/ADR-003-song-import-deduplication.md)

## Goal

Implement a staged import pipeline that stores raw candidates, applies deterministic deduplication heuristics, requires admin review, and merges reviewed candidates into canonical songs or new catalog entries.

## Subtask 1: Define import candidate data model

### Context

- Relevant ADR: `docs/adr/ADR-003-song-import-deduplication.md`
- Depends on ADR-001 `import_batches`, `songs`, `arrangements`, `lyrics_documents`, and `provenance_records`.
- Deduplication signals include normalized title, artist similarity, CCLI number, lyrics hash, and manual reviewer confirmation.

### Prompt

Design and add schema support for raw import candidates and deduplication review state. Ensure imported data remains staged until an authorized merge creates or updates canonical catalog records.

### Acceptance criteria

- Stores raw import candidates linked to an import batch.
- Persists normalized title, source artist metadata, CCLI number when available, lyrics hash when available, source payload, and candidate status.
- Tracks proposed duplicate matches separately from manual reviewer decisions.
- Prevents staged candidates from becoming recommendable before merge and approvals.
- Includes constraints and indexes for batch lookup, normalized title lookup, CCLI lookup, lyrics hash lookup, and review status.

### Restrictions

- Do not write raw import candidates directly into canonical `songs` as approved content.
- Do not discard source payloads or provenance needed for audit.
- Do not use an LLM to decide duplicate identity.

## Subtask 2: Implement normalization and deterministic deduplication heuristics

### Context

- ADR-003 lists normalized title, artist similarity, CCLI number, lyrics hash, and manual confirmation as deduplication signals.
- Safety requires transparent, explainable merge suggestions.

### Prompt

Implement deterministic normalization and deduplication scoring utilities for imported song candidates. Return explainable match candidates without automatically merging them.

### Acceptance criteria

- Normalizes titles consistently by casing, punctuation, whitespace, and parenthetical variants according to documented rules.
- Computes or stores lyrics hashes only from allowed source text.
- Uses CCLI exact matching when present as a strong signal.
- Uses deterministic artist similarity logic with documented thresholds.
- Returns candidate duplicate suggestions with signal-level explanations.
- Includes tests for title variants, missing metadata, CCLI matches, lyrics hash matches, and non-duplicate near misses.

### Restrictions

- Do not call external APIs during deduplication tests.
- Do not automatically merge based on heuristic score alone.
- Do not include full copyrighted lyrics in test fixtures.

## Subtask 3: Build import batch ingestion workflow

### Context

- ADR-003 pipeline: create import batch, store raw candidates, apply heuristics, perform admin review, merge or create canonical entries.
- Imported content must retain source provenance.

### Prompt

Add a service or command that creates import batches, validates incoming candidate records, stores raw candidates, runs deduplication heuristics, and records proposed matches for review.

### Acceptance criteria

- Creates import batches with source, initiator, timestamps, and status.
- Validates incoming candidate shape before persistence.
- Stores every accepted raw candidate with provenance metadata.
- Runs deterministic deduplication after candidate persistence.
- Records proposed matches without modifying canonical song records.
- Provides a repeatable test or local command that imports a small fixture batch.

### Restrictions

- Do not silently drop invalid rows; report validation errors with row or candidate identifiers.
- Do not perform admin review decisions inside the ingestion command.
- Do not make network calls unless explicitly required by an import source adapter and covered by separate configuration.

## Subtask 4: Implement admin review and merge operations

### Context

- Manual reviewer confirmation is required before merging duplicates.
- Canonical catalog updates must preserve auditability and provenance.

### Prompt

Implement review operations that allow an authorized reviewer to confirm a match, reject a match, merge into an existing canonical song, or create a new canonical song. Record reviewer identity, timestamp, decision, and notes.

### Acceptance criteria

- Supports explicit review decisions for each proposed match or candidate.
- Creates or updates canonical records only after a review decision permits it.
- Writes provenance records linking canonical records back to import candidates and batches.
- Leaves approval records pending or needs_review unless explicitly approved through ADR-005 workflow.
- Handles idempotency so repeated merge requests do not duplicate canonical songs or arrangements.
- Includes tests for merge, create-new, reject, and repeated-request scenarios.

### Restrictions

- Do not grant approval implicitly as part of import merge.
- Do not overwrite curated canonical metadata without preserving previous values or audit records.
- Do not expose merge operations without authorization checks if the application has an auth layer.

## Subtask 5: Document the import workflow

### Context

- Data Integrity Agent responsibilities include validating lyrics source provenance and maintaining audit logs.
- Future agents need clear operational boundaries for safe import work.

### Prompt

Document the end-to-end import and deduplication workflow, including statuses, reviewer responsibilities, deduplication signals, merge behavior, and failure handling.

### Acceptance criteria

- Documents every import status and review status.
- Explains why heuristics suggest but do not merge automatically.
- Links implementation commands, tests, and schema migrations.
- Includes examples using fixture data rather than production catalogs.

### Restrictions

- Do not document unsupported import sources as implemented.
- Do not reveal private provider credentials, tokens, or proprietary catalog data.
