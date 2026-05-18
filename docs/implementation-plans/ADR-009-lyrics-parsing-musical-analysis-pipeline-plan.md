# ADR-009 Implementation Plan: Lyrics Parsing and Musical Analysis Pipeline

## Objective

Implement a deterministic, versioned, recalculable parsing pipeline that derives
sections, chords, keys, BPM, meter, fingerprints, Nashville numbers, and
confidence metadata from raw lyrics and chord documents without overwriting source
content.

## Subtask 1: Define versioned parser output models

### Context

ADR-009 requires parser output to be derived data, tied to parser version and
source document version, so it can be recalculated safely.

### Prompt

Design parser result models and persistence for section structure, chords,
key/BPM/meter estimates, confidence values, warnings, and parser provenance.

### Acceptance criteria

- Parser results reference source lyrics document, arrangement, parser name,
  parser version, and source content hash.
- Results can coexist across parser versions or be superseded without modifying
  raw source documents.
- Warnings and confidence values are structured and queryable.
- Tests verify parser result immutability relative to raw source content.

### Restrictions

- Do not overwrite original lyrics, chord charts, or imported source payloads.
- Do not store parser guesses as approved canonical facts without review.
- Do not make parser output recommendable unless the linked catalog record is
  approval-eligible.

## Subtask 2: Implement parser plugin registry and execution pipeline

### Context

The system must support multiple parsers for formats such as ChordPro,
OpenSong, Markdown, plain text, and future plugins.

### Prompt

Create a parser plugin registry that selects an appropriate parser by lyrics
format, source metadata, and parser capability. Add an execution service that
runs parsers deterministically and stores result status.

### Acceptance criteria

- Registry resolves parser plugins deterministically by format and priority.
- Unsupported formats produce explicit unsupported status.
- Parser failures produce structured errors without losing source payloads.
- Tests cover parser selection, unsupported format, successful execution, and
  parser failure.

### Restrictions

- Do not select parser plugins through an LLM.
- Do not execute arbitrary code from imported files.
- Do not hide parser errors behind generic success statuses.

## Subtask 3: Extract sections, lyrics lines, and chord placements

### Context

ADR-009 requires parsing sections and chords so the engine and review UI can
reason about arrangement starts, endings, and musical structure.

### Prompt

Implement deterministic extraction of sections, lyrics lines, chord symbols,
chord positions, repeated sections, and unresolved or malformed markers for the
supported Phase 2 formats.

### Acceptance criteria

- Parsed sections preserve source order and labels such as verse, chorus, bridge,
  tag, intro, and outro.
- Chord symbols are normalized while preserving original spellings for review.
- Malformed or unknown markers produce warnings with source locations.
- Tests include representative ChordPro, OpenSong, Markdown, and plain-text
  fixtures.

### Restrictions

- Do not invent missing sections or chords.
- Do not discard original labels when normalizing them.
- Do not treat parser warnings as doctrinal or licensing approvals.

## Subtask 4: Add key, BPM, meter, and confidence analysis

### Context

Musical analysis is useful only when confidence and evidence are visible. ADR-009
allows estimates but requires transparent confidence.

### Prompt

Implement deterministic analysis for likely key, mode, BPM, meter, tempo range,
and confidence. Prefer explicit source metadata over inferred values and record
which evidence was used.

### Acceptance criteria

- Explicit metadata, chord-derived estimates, and absent-data cases are
  distinguishable.
- Confidence scores and evidence references accompany each derived field.
- BPM estimates are bounded and marked low confidence unless supported by source
  metadata or approved heuristics.
- Tests cover explicit metadata, inferred key from chords, ambiguous key, missing
  BPM, and conflicting metadata.

### Restrictions

- Do not call external audio-analysis services in this subtask.
- Do not present low-confidence estimates as approved catalog data.
- Do not use probabilistic LLM output for key or BPM detection.

## Subtask 5: Compute arrangement fingerprints and duplicate-support signals

### Context

ADR-009 calls for arrangement fingerprinting to support deduplication, merge
review, and recalculation when parser versions change.

### Prompt

Generate deterministic fingerprints from normalized lyrics, section sequence,
chord progression, key-normalized chord movement, and source hashes. Feed these
signals into duplicate review without auto-merging.

### Acceptance criteria

- Fingerprints are stable for equivalent normalized input.
- Raw-source, lyrics-only, chord-progression, and section-sequence fingerprints
  are separately available.
- Duplicate-support signals can be attached to staged import candidates.
- Tests verify stability, intentional differences, and source-hash changes.

### Restrictions

- Do not merge records based solely on fingerprints.
- Do not expose copyrighted full lyrics in fingerprints or logs.
- Do not make fingerprints depend on wall-clock time or nondeterministic order.

## Subtask 6: Add Nashville number support

### Context

Nashville number output helps transpose and explain arrangements, but it must be
based on known or confidently inferred keys.

### Prompt

Add Nashville number conversion for parsed chord charts when key evidence is
available. Include unsupported-chord warnings and confidence propagation from key
analysis.

### Acceptance criteria

- Chord charts with known keys produce Nashville numbers for supported chords.
- Unknown or ambiguous keys skip conversion with a structured warning.
- Unsupported chords are preserved and annotated rather than dropped.
- Tests cover major, minor, relative-key, slash-chord, and unsupported-chord
  cases.

### Restrictions

- Do not guess Nashville numbers when key confidence is below the approved
  threshold.
- Do not overwrite original chord symbols.
- Do not use Nashville conversion as approval evidence.

## Subtask 7: Provide parser review and recalculation hooks

### Context

Admin review needs parser evidence, warnings, and a way to recalculate derived
metadata after parser upgrades or source edits.

### Prompt

Expose service methods and API shapes for parser result retrieval,
recalculation, result supersession, and review annotations.

### Acceptance criteria

- Review clients can fetch latest parser result, warnings, confidence, and
  source references.
- Recalculation creates a new result version or explicit supersession record.
- Source edits mark prior parser results stale when content hashes differ.
- Tests cover recalculation and stale-result detection.

### Restrictions

- Do not delete prior parser results during recalculation unless retention policy
  explicitly allows it.
- Do not allow review annotations to mutate parser evidence.
- Do not expose parser endpoints that make songs recommendable.
