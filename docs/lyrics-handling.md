# Safe Lyrics Handling

Cadentia stores lyrics and chord sheets as auditable source documents. This
workflow exists to keep catalog data transparent, preserve source provenance, and
avoid unsafe redistribution of copyrighted lyric content in code, docs, fixtures,
or generated recommendations.

Related decision record: [ADR-004: Lyrics Storage Format and Parsing Strategy](./adr/ADR-004-lyrics-storage-format.md).

## Storage responsibilities

- `lyrics_documents.content` is the authoritative raw source field. Store the
  submitted lyrics or chord-sheet source exactly enough for audit and reuse; do
  not replace it with parser output or a normalized rendering.
- `lyrics_documents.format` is the declared source format. The backend validates
  the declaration instead of asking an LLM to infer the format.
- Derived parse data belongs only in parse metadata and JSON columns such as
  `parse_status`, `parse_error`, `parser_name`, `parser_version`, `parsed_at`,
  `parsed_sections`, `chord_map`, and `structural_markers`.
- Derived fields are disposable and rebuildable. If parsed data conflicts with
  raw content, raw content wins and the parse result must be regenerated or
  marked failed.
- Recommendation, review, and export workflows must not treat parser output as a
  license to publish full lyrics.

## Supported declared formats

ADR-004 currently supports these declared storage formats:

| Storage value | Application enum | Parser status |
| --- | --- | --- |
| `plain_text` | `LyricsFormat.PLAIN_TEXT` | Deterministic section-label parsing is implemented. |
| `chordpro` | `LyricsFormat.CHORDPRO` | Conservative ChordPro directive and bracketed-chord parsing is implemented. |
| `onsong` | `LyricsFormat.ONSONG` | Storage is supported; deterministic parsing is not implemented yet and returns `UNSUPPORTED`. |
| `markdown` | `LyricsFormat.MARKDOWN` | Markdown heading and bracketed-chord parsing is implemented. |

Unknown, blank, legacy-only, or ambiguous values must be rejected with a message
that names the invalid value and lists the accepted storage values. Do not
silently coerce an unknown format to `plain_text`.

## Versioning and provenance expectations

- Store each material lyrics edit as a new `lyrics_documents` version for the
  arrangement instead of overwriting an existing version in place.
- Keep `version_number`, `is_current`, `content_hash`, `source_reference`,
  `created_by`, and `created_at` populated so reviewers can reconstruct what was
  stored, when it was stored, and who or what supplied it.
- Preserve previous versions for auditability. Only one current lyrics document
  may exist per arrangement.
- Create or retain provenance records for imported or edited content before
  approval. A provenance record should identify the source system, import batch
  when applicable, licensing context, and the exact catalog entity it supports.
- Lyrics source content used for duplicate signals or hashes must come only from
  allowed sources and must not bypass human review when provenance or licensing
  is unclear.

## Parser behavior

- Parsers must be deterministic Java code keyed by the declared `LyricsFormat`.
  LLMs must not parse lyrics, infer chords, invent sections, or repair malformed
  lyrics content.
- Parser updates must never mutate `lyrics_documents.content`.
- Parse failures are non-blocking for raw storage. Store `FAILED` with an error
  message when a deterministic parser cannot process a supported format, and
  store `UNSUPPORTED` for formats that are accepted for storage but do not yet
  have an implemented parser.
- Do not document or depend on parser behavior that is not covered by tests. The
  current implementation is intentionally conservative and should not be
  described as full ChordPro, OnSong, or Markdown compatibility.

## Copyright-safe fixture policy

- Prefer synthetic lyrics that are clearly test-only and not derived from known
  songs.
- Keep examples short and structural. For example, tests may use invented section
  labels, bracketed chord tokens, or one-line synthetic phrases to exercise
  parser behavior.
- Do not commit full copyrighted lyrics, proprietary chord sheets, catalog
  exports, credentials, or screenshots that reveal licensed content.
- Do not paste complete lyrics into issue descriptions, ADRs, implementation
  plans, snapshots, logs, or test failure messages.
- If a fixture must reference a real catalog item, store metadata only unless the
  team has documented permission for the lyric text and has attached provenance
  and licensing notes.
- Fixtures that are not production-approved must remain non-recommendable unless
  a test explicitly exercises non-recommendable records.

## Implementation reference files

- Schema baseline: `apps/api/src/main/resources/db/migration/V002__core_catalog_schema.sql`
- Supported-format migration: `apps/api/src/main/resources/db/migration/V004__lyrics_supported_formats.sql`
- Derived parse-result migration: `apps/api/src/main/resources/db/migration/V005__lyrics_parse_results.sql`
- Format validation enum: `apps/api/src/main/java/com/cadentia/catalog/model/LyricsFormat.java`
- Parse status enum: `apps/api/src/main/java/com/cadentia/catalog/model/LyricsParseStatus.java`
- Parser result update command: `apps/api/src/main/java/com/cadentia/catalog/model/UpdateLyricsParseResultCommand.java`
- Deterministic parser abstraction: `apps/api/src/main/java/com/cadentia/catalog/lyrics/LyricsParser.java`
- Deterministic parser implementation: `apps/api/src/main/java/com/cadentia/catalog/lyrics/DeterministicLyricsParser.java`
- Parser registry and unsupported-format behavior: `apps/api/src/main/java/com/cadentia/catalog/lyrics/LyricsParserRegistry.java`
- Parser tests: `apps/api/src/test/java/com/cadentia/catalog/lyrics/DeterministicLyricsParserTest.java`
- Registry tests: `apps/api/src/test/java/com/cadentia/catalog/lyrics/LyricsParserRegistryTest.java`
- General fixture policy: `docs/seed-data.md`
