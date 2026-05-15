# ADR-004 Implementation Plan: Lyrics Storage Format and Parsing Strategy

Source ADR: [ADR-004: Lyrics Storage Format and Parsing Strategy](../adr/ADR-004-lyrics-storage-format.md)

## Goal

Store raw lyrics or chord-sheet source content with a declared format, then optionally derive parsed structures without replacing the original source.

## Subtask 1: Confirm lyrics document schema fields

### Context

- Relevant ADR: `docs/adr/ADR-004-lyrics-storage-format.md`
- Supported formats: `plain_text`, `chordpro`, `onsong`, and `markdown`.
- Optional derived structures may include parsed sections JSON, chord map, and structural markers.

### Prompt

Review the current lyrics document schema and identify required changes for raw source content, declared format, source versioning, provenance links, and optional derived parse fields.

### Acceptance criteria

- Confirms whether `lyrics_documents` can store raw content and declared format.
- Identifies missing columns for source version, parse status, parsed sections, chord map, and structural markers if needed.
- Confirms links to provenance records and song or arrangement records.
- Produces a short schema-change checklist before migration work starts.

### Restrictions

- Do not discard or transform raw lyrics source as part of schema analysis.
- Do not add parsers in this subtask.
- Do not include full copyrighted lyrics in documentation examples.

### Implementation note

Current schema confirmation before ADR-004 migration work:

- **Raw source storage:** `lyrics_documents.content` is a required `text` column and can preserve raw lyrics or chord-sheet source content without replacing it with a parsed representation. Keep this field as the authoritative raw source field; a future migration may rename or document it as `raw_content` only if the team wants the database name to make that responsibility explicit.
- **Declared format:** `lyrics_documents.format` is required and indexed, but the current database check and Java enum allow `PLAIN_TEXT`, `CHORDPRO`, `OPENLYRICS`, `MARKDOWN`, and `PDF_REFERENCE`. This does not match ADR-004's initial supported set of `plain_text`, `chordpro`, `onsong`, and `markdown`; `ONSONG` is missing and `OPENLYRICS`/`PDF_REFERENCE` need removal, deferral, or explicit ADR follow-up before validation work.
- **Source versioning:** the table already has `version_number`, `is_current`, `content_hash`, `created_by`, and `created_at`, with uniqueness per arrangement/version and arrangement/content hash. These fields provide internal revision tracking for stored lyric documents, but there is no separate external source version field such as source revision label, source ETag, source captured timestamp, parser version, or replacement lineage column. Add only the source-version fields required by importer/admin workflows during the versioning subtask.
- **Provenance and catalog links:** each lyrics document is linked to an arrangement through `lyrics_documents.arrangement_id`, and the arrangement links back to the canonical song. `provenance_records.lyrics_document_id` can point at a lyrics document, while separate provenance rows can point at songs or arrangements because the provenance constraint permits exactly one target entity per row.
- **Derived parse fields:** the current schema has coarse booleans `contains_chords` and `contains_sections`, but it does not store parse status, parse errors, parsed sections JSON, chord map JSON, structural markers JSON, parser name/version, or parsed timestamp. Add these as nullable derived fields or as a separate derived-parse table so failed parsing never blocks or mutates raw source storage.

Schema-change checklist for the next migrations:

- [ ] Align format values across PostgreSQL checks, Java enums, fixtures, and API/admin validation with ADR-004: `plain_text`, `chordpro`, `onsong`, and `markdown` as the accepted external values.
- [ ] Decide whether to keep the database column name `content` or migrate to `raw_content`; in either case, document that this column stores the unmodified source text.
- [ ] Add any importer-facing source-version metadata that cannot be represented by the current `version_number`, `content_hash`, `source_reference`, `created_by`, and `created_at` fields.
- [ ] Add derived parse metadata: `parse_status`, `parse_error`, `parser_version` or parser identifier, and `parsed_at`.
- [ ] Add derived parse payload storage for `parsed_sections`, `chord_map`, and `structural_markers` as JSONB columns or a one-to-one parse-results table.
- [ ] Preserve the existing lyrics-to-arrangement relationship and `provenance_records.lyrics_document_id` linkage; add tests or constraints only if workflows require a lyrics document to have a provenance row before approval.
- [ ] Update fixtures with synthetic, copyright-safe content only; do not include full copyrighted lyrics in migration or parser examples.


## Subtask 2: Enforce supported format validation

### Context

- ADR-004 limits initial declared formats to `plain_text`, `chordpro`, `onsong`, and `markdown`.
- Data integrity depends on rejecting unknown or ambiguous formats.

### Prompt

Add database and application validation so lyrics documents must declare one supported format. Ensure validation errors are clear for import and admin workflows.

### Acceptance criteria

- Database constraints or enum types reject unsupported formats.
- Application validation rejects unsupported formats before writes.
- Tests cover all supported formats and at least one unsupported format.
- Error messages identify the invalid format and accepted values.

### Restrictions

- Do not infer format with an LLM.
- Do not silently coerce unknown formats into `plain_text`.
- Do not remove existing raw content during validation changes.

### Implementation note

Subtask 2 validation is implemented by narrowing `LyricsFormat` to ADR-004 values (`plain_text`, `chordpro`, `onsong`, and `markdown`), storing those declared values directly in PostgreSQL, and rejecting unknown values with messages that include the rejected input and accepted values. Flyway migration `V004__lyrics_supported_formats.sql` replaces the prior broader check constraint and normalizes existing supported uppercase values without altering raw lyric content.

## Subtask 3: Implement raw storage and version handling

### Context

- ADR-004 requires storing raw source content plus declared format.
- Provenance and auditability require preserving historical source versions.

### Prompt

Implement create and update behavior for lyrics documents so raw content is stored intact, changes create auditable versions or revision records according to project conventions, and provenance remains linked.

### Acceptance criteria

- Persists raw source content without lossy normalization.
- Records declared format, source or editor metadata, and timestamps.
- Preserves previous versions or creates audit records when lyrics are edited.
- Includes tests proving raw ChordPro-like syntax, Markdown markers, and plain text line breaks are retained.

### Restrictions

- Do not overwrite lyrics content without audit history.
- Do not parse and reserialize content as the only stored representation.
- Do not store unlicensed content without provenance metadata.

## Subtask 4: Add optional parser interfaces for derived structures

### Context

- ADR-004 allows optional derived structures such as parsed sections JSON, chord map, and structural markers.
- Derived structures must not replace raw source content.

### Prompt

Create parser interfaces and initial deterministic parsers for the supported formats that the team is ready to support now. Store derived data separately from raw content and mark parse status or parse errors.

### Acceptance criteria

- Provides a parser abstraction keyed by declared format.
- Implements deterministic parsing for selected formats with tests.
- Stores parsed sections, chord map, or structural markers only as derived fields.
- Records parse failures without deleting or mutating raw source content.
- Documents unsupported parser details for formats that are stored but not yet parsed.

### Restrictions

- Do not use LLMs to parse lyrics or chords.
- Do not block raw document storage because derived parsing fails.
- Do not claim full ChordPro or OnSong compatibility unless covered by tests.

### Implementation note

Subtask 4 parser scaffolding is implemented with a `LyricsParser` abstraction keyed by `LyricsFormat`, a registry for available deterministic parsers, and parser output commands that update only derived columns on `lyrics_documents`. The initial deterministic parsers cover conservative section, chord occurrence, and structural marker extraction for `plain_text`, `chordpro`, and `markdown`; they are intentionally limited to behaviors covered by unit tests and do not claim full ChordPro compatibility. `onsong` remains a storable declared format, but parser lookup records an `UNSUPPORTED` parse result until OnSong compatibility rules and fixtures are added. Derived parse payloads are stored in `parsed_sections`, `chord_map`, and `structural_markers` JSONB columns with `parse_status`, parser metadata, timestamp, and non-blocking parse error details; `lyrics_documents.content` remains the authoritative raw source and is never mutated by parse updates.

## Subtask 5: Document safe lyrics handling

### Context

- Data Integrity Agent responsibilities include validating lyrics source provenance.
- Lyrics and chord-sheet content may have copyright and licensing constraints.

### Prompt

Document the lyrics storage, format validation, versioning, parser behavior, and copyright-safe fixture policy for developers and future agents.

### Acceptance criteria

- Describes raw-versus-derived storage responsibilities.
- Lists supported declared formats.
- Explains versioning and provenance expectations.
- Provides safe fixture guidance that avoids full copyrighted lyrics.
- Links ADR-004 and implementation files.

### Restrictions

- Do not publish copyrighted lyric examples beyond minimal fair-use-style snippets needed for tests.
- Do not document parser features that are not implemented or tested.

### Implementation note

Subtask 5 safe-handling guidance is documented in `docs/lyrics-handling.md`. The document links ADR-004 to the schema migrations, validation enum, parse-status model, deterministic parser abstraction and implementation, parser registry, parser tests, and shared fixture policy. It clarifies that `lyrics_documents.content` remains authoritative raw source, lists the supported declared formats (`plain_text`, `chordpro`, `onsong`, and `markdown`), explains versioning and provenance expectations, describes current deterministic parser boundaries without claiming untested compatibility, and prohibits full copyrighted lyrics in docs, fixtures, logs, and examples.

