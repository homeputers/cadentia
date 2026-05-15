# ADR-006 Implementation Plan: Arrangement Transposition Policy

Source ADR: [ADR-006: Arrangement Transposition Policy](../adr/ADR-006-arrangement-transposition.md)

## Goal

Store curated arrangements in canonical base keys and generate transpositions dynamically through deterministic utilities instead of persisting one arrangement per key by default.

## Subtask 1: Define musical key and chord representation rules

### Context

- Relevant ADR: `docs/adr/ADR-006-arrangement-transposition.md`
- Related ADR: `docs/adr/ADR-004-lyrics-storage-format.md` for chord sheets and parsed chord maps.
- Recommendation constraints allow minimal key centers and relative major/minor transitions.

### Prompt

Define the internal representation for keys, modes, accidentals, chord roots, chord qualities, slash chords, Nashville-style notation if supported, and invalid chord handling.

### Deliverable

- Representation rules: [`docs/arrangement-transposition-rules.md`](../arrangement-transposition-rules.md)

### Acceptance criteria

- Documents supported keys and enharmonic spelling rules.
- Defines how major and minor keys are represented.
- Defines how chord symbols are parsed and represented before transposition.
- States which notation variants are unsupported in the first implementation.
- Provides examples using synthetic, non-copyrighted chord progressions.

### Restrictions

- Do not persist a separate arrangement for every transposed key.
- Do not infer vocalist suitability or song selection with an LLM.
- Do not claim support for notation variants that are not testable.

## Subtask 2: Implement deterministic transposition utility

### Context

- ADR-006 requires dynamic transposition from a canonical base key.
- The utility must be deterministic and testable.

### Prompt

Create a transposition utility that accepts a canonical base key, target key, and chord symbols or parsed chord map, then returns transposed chord symbols while preserving non-chord lyrics and structure.

### Acceptance criteria

- Supports transposition across all documented keys.
- Preserves chord qualities, extensions, bass notes, section labels, and non-chord text.
- Uses deterministic enharmonic spelling based on documented rules.
- Returns clear errors for unsupported notation.
- Includes tests for major keys, minor keys, slash chords, extensions, no-op transposition, and invalid input.

### Restrictions

- Do not use an LLM for chord parsing or transposition.
- Do not mutate stored raw lyrics or chord-sheet content when generating a transposition.
- Do not hide unsupported notation by dropping chords.

## Subtask 3: Integrate transposition with arrangement retrieval

### Context

- Stored arrangements have canonical base keys.
- Recommendation output may need target keys for set continuity without creating duplicate arrangements.

### Prompt

Add backend operations that retrieve an arrangement and optionally return a dynamically transposed representation for a requested target key.

### Acceptance criteria

- Leaves stored arrangement and raw document unchanged.
- Returns base key, requested target key, and transposition interval in the response.
- Uses parsed chord maps when available and falls back according to documented behavior.
- Includes tests proving repeated requests for different keys do not create new arrangement rows.

### Restrictions

- Do not persist generated transpositions as default behavior.
- Do not make transposition change approval status or provenance of the base arrangement.
- Do not change recommendation candidate eligibility based solely on dynamic transposition.

## Subtask 4: Add recommendation-engine key policy support

### Context

- Project guardrails include minimal key centers, relative major/minor transitions, and controlled set continuity.
- ADR-006 provides dynamic transposition to support these constraints.

### Prompt

Update the Recommendation Engine interfaces so candidate arrangements can be evaluated in their base key and, when allowed, in dynamically transposed keys according to key policy settings.

### Acceptance criteria

- Honors key policy inputs such as preferred same key, relative major/minor allowance, and maximum key centers.
- Scores or filters possible target keys deterministically.
- Explains when a target key is a dynamic transposition rather than a stored arrangement key.
- Includes tests for same-key, relative-major/minor, max-key-center, and disallowed-transposition cases.

### Restrictions

- Do not let the LLM select target keys.
- Do not bypass vocalist range or arrangement constraints if those constraints exist elsewhere.
- Do not recommend unapproved arrangements to satisfy a key policy.

## Subtask 5: Document transposition behavior

### Context

- Future agents need to understand that dynamic transposition is derived output, not source-of-truth data.

### Prompt

Document transposition rules, supported notation, error behavior, API or service usage, and how transposition interacts with recommendation explanations.

### Acceptance criteria

- Identifies canonical base key storage as the source of truth.
- Lists supported key and chord notation rules.
- Explains that generated transpositions are not persisted by default.
- Links ADR-006, transposition utility files, and tests.

### Restrictions

- Do not document unimplemented notation support.
- Do not include copyrighted chord sheets as examples.
