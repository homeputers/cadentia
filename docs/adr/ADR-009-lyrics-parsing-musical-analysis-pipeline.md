# ADR-009: Lyrics Parsing and Musical Analysis Pipeline

Status: Proposed  
Date: 2026-05-17

## Context

Cadentia stores raw lyrics and chord documents while deriving structured musical metadata needed for deterministic recommendation, review workflows, and explanation output. Imported documents may be plain text, ChordPro, OpenSong, Markdown, OnSong-like exports, CSV fields, or manually entered content.

The parsing system must support:

- chord extraction
- section parsing
- key detection
- BPM estimation
- arrangement fingerprinting
- chord normalization
- future Nashville number support
- parser plugins for new formats

The parser must not become an unchecked source of truth. Derived data requires confidence, provenance, and reviewer visibility.

## Decision

Create a lyrics parsing and musical analysis pipeline that converts raw source documents into structured derived metadata while preserving the original source content.

The pipeline should execute these phases:

1. **Format identification:** use declared format, connector hints, file extension, and content signatures.
2. **Lexical parsing:** tokenize directives, sections, lyrics lines, chord lines, comments, metadata, and whitespace.
3. **Chord extraction:** identify chord symbols, chord positions, slash chords, extensions, capo directives, and ambiguous tokens.
4. **Section parsing:** infer or read structural sections such as verse, chorus, bridge, tag, intro, outro, pre-chorus, instrumental, and refrain.
5. **Chord normalization:** normalize enharmonic spelling, chord quality, extensions, bass notes, and key-relative forms into canonical chord objects.
6. **Key detection:** infer key and mode using declared metadata, chord distribution, tonal center heuristics, final cadence, and reviewer overrides.
7. **Tempo and meter analysis:** read explicit BPM and time signature metadata; optionally estimate BPM from trusted source metadata or future audio analysis.
8. **Arrangement fingerprinting:** compute stable fingerprints from normalized structure, chord progression, key-independent progression, lyrics hash, and source metadata.
9. **Validation:** emit confidence, warnings, parser errors, unsupported syntax, and reviewer action items.
10. **Persistence:** store raw content as canonical source data and store derived parser output as recalculable metadata.

## Parser Plugin System

Use a parser plugin interface per content format. Plugins may parse source-specific syntax, but they must emit a shared intermediate representation.

A parser plugin should define:

- supported formats and file extensions
- parser version
- metadata extraction rules
- section marker rules
- chord grammar support level
- known limitations
- validation warnings
- output schema version

Initial plugins:

- `plain_text`
- `chordpro`
- `opensong`
- `markdown`
- `csv_embedded_text`

Future plugins:

- `onsong`
- `nashville_number`
- `musicxml`
- provider-specific export formats

## Chord Extraction

Chord extraction must distinguish chords from lyrics. The parser should support:

- root notes A-G with sharps and flats
- major, minor, diminished, augmented, suspended, power, and extended qualities
- sevenths, ninths, elevenths, thirteenths, added tones, and altered tones
- slash chords
- capo directives and transposition hints
- inline ChordPro bracket chords
- chord-over-lyrics lines in plain text and Markdown

Ambiguous tokens must be reported as warnings instead of silently treated as trusted chord data.

## Section Parsing

Section parsing should preserve both source labels and normalized labels. For example, `V1`, `Verse 1`, and `VERSE:` may normalize to `verse` with ordinal `1` while keeping the source text.

The section model should include:

- normalized section type
- source label
- ordinal when present
- line ranges or character offsets
- lyrics text without chord annotations
- chord events with positions when available
- repeat or arrangement hints

Reviewer overrides must be possible when parser inference is wrong.

## Key Detection

Key detection should be confidence-based. The system should prefer explicit, trusted metadata when available, but still validate whether the chord vocabulary appears compatible with the declared key.

Key detection inputs include:

- declared key or ChordPro `{key:}` directive
- capo and transposition directives
- first and last chords
- chord frequency and harmonic function
- common worship progression patterns
- mode hints from minor tonal centers
- reviewer-approved arrangement metadata

Parser-derived key data must not override reviewer-approved arrangement key without explicit reviewer action.

## BPM Estimation

BPM should be treated as high-confidence only when provided by an approved source, reviewer, or trusted arrangement metadata. Text-only parsing may infer coarse tempo categories but should not invent precise BPM values.

Future audio or click-track analysis may estimate BPM, but estimated BPM must include method, confidence, and review status.

## Arrangement Fingerprinting

Arrangement fingerprints help detect duplicates, identify variants, and explain differences between versions.

Fingerprints should be computed from:

- normalized title and song identifiers
- lyrics content hash
- normalized section order
- normalized chord progression
- key-independent chord progression when possible
- source arrangement metadata
- parser version

Fingerprinting must distinguish a new arrangement from a duplicate import. Reviewers should see why two candidates were considered similar.


### Fingerprint-to-Dedupe Integration

Fingerprint outputs may contribute to duplicate review support, but only through a governed signal contract.

Required properties:

- stable signal codes with explicit semantics and fixed weights
- deterministic aggregation into duplicate-support scoring
- reviewer-visible evidence using hashes, normalized structure, and non-copyright-sensitive metadata
- no automatic merge or rejection based solely on fingerprint evidence

### Parser Rollout Orchestration

Parser upgrades must use an idempotent batch orchestration model so large-scale recalculations remain auditable and restart-safe.

Orchestration requirements:

- batch identity must be derived from parser name/version and canonicalized selection criteria
- item processing order must be deterministic
- selection must include source-hash mismatch and parser-version mismatch paths
- reruns of the same batch input must avoid duplicate successful parser-run history entries
- partial failures must preserve successful results and expose retryable vs terminal outcomes

## Nashville Number Support

Nashville number parsing is a future capability. The architecture should allow conversion between absolute chord notation and key-relative Nashville notation after key detection is sufficiently reliable.

Nashville support must include:

- key-aware number conversion
- minor-key interpretation rules
- accidentals and borrowed chords
- inversions and bass movement
- compatibility with transposition utilities
- reviewer-visible confidence and limitations

## Consequences

Benefits:

- raw lyrics remain preserved and auditable
- derived musical metadata can improve without destructive migrations
- deterministic recommendation can use normalized musical features
- parser confidence supports safer reviewer workflows
- future formats can be added through plugins

Tradeoffs:

- parser output must be versioned and recalculable
- false positives require reviewer tooling and warnings
- precise BPM and key detection may require human confirmation

## Related Decisions

- ADR-004 defines lyrics storage format.
- ADR-006 defines transposition behavior.
- ADR-008 defines acquisition and import connectors.
- ADR-010 defines how parsed metadata participates in scoring.
