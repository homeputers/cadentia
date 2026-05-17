# ADR-008: Song Acquisition and Import Connector Architecture

Status: Proposed  
Date: 2026-05-17

## Context

Cadentia must acquire song, arrangement, lyric, chord, and metadata records from multiple sources without weakening the core safety constraints defined by ADR-001, ADR-003, ADR-004, and ADR-005. Sources vary widely in structure, quality, licensing terms, provenance detail, and automation support.

The import architecture must support:

- manual catalog entry
- ChordPro file upload
- OpenSong import
- Planning Center export/import
- Ultimate Guitar adapter, only if legally allowed
- CCLI import, only through permitted access paths
- CSV import
- local Markdown repository import
- future provider-specific connectors

The system must continue to enforce these non-negotiable boundaries:

- imported content is not recommendable until it passes provenance, deduplication, and approval workflows
- connectors may collect and normalize candidates, but they may not create approved catalog records directly
- scraping must not bypass source licensing, robots, terms of service, or copyright restrictions
- every imported candidate must be traceable to a source and import batch

## Decision

Adopt a provider adapter and import connector architecture that separates source-specific acquisition from Cadentia's canonical catalog model.

Each connector implements a common lifecycle:

1. **Configure** provider credentials, legal mode, allowed scopes, source identifiers, and operator options.
2. **Discover** candidate files, exports, records, or manually entered payloads.
3. **Fetch** source payloads within approved boundaries.
4. **Parse** source-specific structure into connector-native records.
5. **Normalize** connector-native records into Cadentia import candidates.
6. **Validate** minimum schema, provenance, licensing metadata, and content hashes.
7. **Stage** candidates into import queues and import batch storage.
8. **Deduplicate** candidates against existing canonical records and other staged records.
9. **Review** candidates through admin workflows.
10. **Promote** approved records into canonical catalog tables.
11. **Audit** lifecycle events, errors, retries, reviewer actions, and final outcomes.

Connectors must use a provider adapter pattern. A provider adapter owns only provider-specific concerns such as authentication, pagination, file traversal, source field mapping, export quirks, rate limits, and provider error translation. The shared import pipeline owns validation, normalization, provenance enforcement, deduplication, review gating, and canonical writes.

## Connector Types

Initial connector types should include:

- **Manual entry connector:** accepts operator-entered song, arrangement, lyric, chord, tag, and provenance data through admin UI forms.
- **ChordPro upload connector:** accepts one or more `.cho`, `.chordpro`, or text files and extracts title, metadata directives, sections, chords, and lyrics.
- **OpenSong connector:** imports OpenSong XML or exported folder structures, preserving source filenames and document hashes.
- **Planning Center connector:** imports operator-provided exports or API-backed data only when authorized by the church account and provider terms.
- **Ultimate Guitar adapter:** remains disabled unless legal review confirms that the planned access path, content usage, and storage behavior are permitted.
- **CCLI connector:** may import only from permitted CCLI-provided exports, licensed APIs, or operator-provided data allowed by the church's license.
- **CSV connector:** imports structured spreadsheets with explicit column mapping and validation reports.
- **Local Markdown repository connector:** imports approved local files from configured repositories, preserving repository URL, commit hash, relative path, and content hash where available.

## Normalization Pipeline

All connectors emit import candidates into a shared normalization pipeline. The pipeline produces a stable intermediate representation before any canonical catalog merge.

The normalized candidate should include, when available:

- source provider and connector type
- source record identifier
- source URL, file path, export name, or repository reference
- source license or permitted-use evidence
- source retrieved timestamp
- raw content hash
- normalized title and alternate titles
- writers, artists, CCLI number, copyright, and publisher metadata
- language
- arrangement metadata such as key, mode, BPM, time signature, duration, energy, and difficulty
- raw lyrics or chord document content
- declared and detected content format
- parsed section hints
- tags or source categories mapped to controlled vocabulary candidates
- warnings, confidence values, and validation errors

Normalization must preserve raw input and must not destructively overwrite source content. Derived fields may be recalculated as parser improvements are released.

## Scraping Boundaries

Cadentia must not implement unrestricted scraping. Automated collection is allowed only when all of the following are true:

- the provider's terms and robots policy permit the access pattern
- the church or operator has the necessary rights for the content
- rate limits and authentication requirements are respected
- the connector stores provenance and permitted-use evidence
- the imported content remains staged until review and approval

If legality is uncertain, the connector must be disabled by default and expose a manual import path instead. For example, an Ultimate Guitar adapter may exist only as a placeholder or metadata-only adapter until legal approval explicitly enables content acquisition.

## Manual vs. Automated Imports

Manual imports are first-class and should be preferred when licensing or source automation is ambiguous. Automated imports are acceptable for owned files, authorized exports, licensed APIs, and local repositories under church control.

Manual imports must still create import batches, content hashes, provenance records, validation results, and review tasks. Manual entry is not a shortcut around governance.

Automated imports must be idempotent. Re-importing the same source payload should produce stable hashes, update the import batch status, and avoid duplicate canonical records unless a reviewer explicitly chooses to create a new arrangement or song.

## Import Queue and Job Model

Imports should execute through a queue-backed job model rather than direct synchronous canonical writes.

Recommended job concepts:

- `import_batch`: operator-visible grouping of an import request
- `import_job`: asynchronous execution unit for discovery, fetch, parse, normalize, or promote steps
- `import_candidate`: staged normalized candidate pending review
- `import_candidate_issue`: validation, provenance, licensing, parser, deduplication, or merge warning
- `import_event`: append-only audit event for state changes, retries, and reviewer actions

Job statuses should include:

- `queued`
- `running`
- `succeeded`
- `succeeded_with_warnings`
- `failed`
- `retry_scheduled`
- `blocked_by_policy`
- `cancelled`

## Retry and Error Handling

Connector failures must be categorized and handled deterministically:

- **Transient provider errors:** retry with bounded exponential backoff and jitter.
- **Rate limiting:** respect provider retry-after guidance and connector-specific quotas.
- **Authentication errors:** stop the job and require operator intervention.
- **Validation errors:** stage the candidate with issues when safe, or reject the candidate when required fields are missing.
- **Provenance or licensing errors:** block promotion until resolved by an authorized reviewer.
- **Parser errors:** preserve raw payload and create a reviewable issue.
- **Duplicate detection conflicts:** require explicit reviewer merge or create decisions.

Retries must be idempotent and must not create duplicate staged candidates for the same source identity and content hash.

## Provenance Enforcement

Every import candidate must carry enough provenance to support audit and recommendation explainability. Promotion to canonical catalog tables is forbidden when provenance is missing or invalid.

Required provenance fields include:

- connector type
- provider name
- source identifier or file reference
- import batch identifier
- retrieved or entered timestamp
- raw content hash
- operator or system actor
- declared rights or license evidence when applicable

Approval workflows must be able to inspect provenance before granting editorial, musical, doctrinal, or licensing approval.

## Consequences

Benefits:

- provider-specific code remains isolated from catalog governance
- legal and licensing boundaries are explicit
- all imports are auditable and reviewable
- manual and automated imports share the same safety model
- future connectors can be added without changing canonical catalog rules

Tradeoffs:

- imports require more infrastructure than direct catalog writes
- operator review remains necessary even for high-quality provider data
- connector development must include policy and provenance modeling, not just parsing

## Related Decisions

- ADR-001 defines canonical catalog storage.
- ADR-003 defines staged import and deduplication.
- ADR-004 defines lyrics storage formats.
- ADR-005 defines approval gates.
- ADR-009 defines parsing and musical analysis.
