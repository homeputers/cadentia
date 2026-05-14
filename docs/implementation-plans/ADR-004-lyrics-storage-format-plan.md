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
