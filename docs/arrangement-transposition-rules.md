# Arrangement Transposition Representation Rules

Related decision records:

- [ADR-006: Arrangement Transposition Policy](./adr/ADR-006-arrangement-transposition.md)
- [ADR-004: Lyrics Storage Format and Parsing Strategy](./adr/ADR-004-lyrics-storage-format.md)

Cadentia stores each curated arrangement in one canonical base key and derives
other keys at request time. These rules define the deterministic key and chord
model that future transposition utilities must implement before any dynamic
transposition is exposed to recommendations or APIs.

## Source-of-truth storage

- The stored arrangement remains the source of truth. Dynamic transpositions are
  generated output and must not create additional `arrangements` rows by default.
- `arrangements.musical_key` stores the canonical tonic spelling, such as `C`,
  `F#`, or `Bb`.
- `arrangements.key_mode` stores the mode as `MAJOR`, `MINOR`, `MODAL`, or
  `UNKNOWN`. Only `MAJOR` and `MINOR` are transposable in the first
  implementation.
- A musical key is represented internally as a pair of `(tonic, mode)` instead
  of as a combined display string. For example, `A MINOR` is represented as
  tonic `A` plus mode `MINOR`, not as the raw text `Am`.
- If either the tonic or mode is missing, `MODAL`, or `UNKNOWN`, the arrangement
  may still be stored, but deterministic transposition must return an explicit
  unsupported-key error instead of guessing.

## Supported key tonics

The first implementation supports these canonical tonic spellings:

| Pitch class | Sharp spelling | Flat spelling |
| --- | --- | --- |
| 0 | `C` | — |
| 1 | `C#` | `Db` |
| 2 | `D` | — |
| 3 | `D#` | `Eb` |
| 4 | `E` | — |
| 5 | `F` | — |
| 6 | `F#` | `Gb` |
| 7 | `G` | — |
| 8 | `G#` | `Ab` |
| 9 | `A` | — |
| 10 | `A#` | `Bb` |
| 11 | `B` | — |

Unsupported key tonics for the first implementation include double accidentals
such as `C##` and `Bbb`, theoretical spellings such as `Cb`, `B#`, `Fb`, and
`E#`, solfege key names, Roman numerals, and locale-specific note names such as
`H`.

## Enharmonic spelling rules

Transposition must use deterministic spelling so the same input always produces
the same output.

1. Preserve the accidental family of the target key when possible:
   - Target keys containing `b` prefer flat output roots.
   - Target keys containing `#` prefer sharp output roots.
2. Target keys without an explicit accidental use this default spelling family:
   - Flat-preferred target keys: `F`.
   - Sharp-preferred target keys: `C`, `G`, `D`, `A`, `E`, `B`.
   Target keys with an explicit accidental are already covered by rule 1.
3. Chord roots and slash bass notes use the same spelling family chosen for the
   target key.
4. Do not emit unsupported theoretical spellings in the first implementation.
   For example, transpose into `B` rather than `Cb`, and into `F` rather than
   `E#`.
5. Preserve the original chord quality and modifiers exactly as parsed; only the
   root and optional slash bass pitch are respelled.

Examples using synthetic progressions:

| Base key | Target key | Input | Output |
| --- | --- | --- | --- |
| `C MAJOR` | `D MAJOR` | `C F G C` | `D G A D` |
| `C MAJOR` | `Bb MAJOR` | `C Am F G` | `Bb Gm Eb F` |
| `A MINOR` | `C MINOR` | `Am Dm E7 Am` | `Cm Fm G7 Cm` |
| `G MAJOR` | `Ab MAJOR` | `G/B Cadd9 D/F#` | `Ab/C Dbadd9 Eb/G` |

## Chord symbol grammar

Chord parsing is deterministic and conservative. A chord symbol is represented
before transposition as:

```text
ChordSymbol {
  root: PitchToken,
  qualityAndModifiers: String,
  bass: PitchToken | null,
  originalText: String
}
```

`PitchToken` is represented as:

```text
PitchToken {
  letter: A | B | C | D | E | F | G,
  accidental: NATURAL | SHARP | FLAT,
  pitchClass: 0..11
}
```

Supported chord syntax for the first implementation:

```text
<root><quality-and-modifiers>[ / <bass> ]
```

where:

- `<root>` is one supported pitch token: `A`, `B`, `C`, `D`, `E`, `F`, `G`,
  `A#`, `C#`, `D#`, `F#`, `G#`, `Ab`, `Bb`, `Db`, `Eb`, or `Gb`.
- `<quality-and-modifiers>` is optional text immediately after the root. It is
  preserved exactly and may include common suffixes such as `m`, `maj7`, `7`,
  `sus4`, `add9`, `dim`, `aug`, `6`, `9`, `11`, `13`, `2`, or parenthesized
  modifiers such as `(no3)`.
- `<bass>` is an optional supported pitch token after one slash, as in `D/F#`.
- Chord tokens may appear in supported parsed chord maps or in chord positions
  already identified by the lyrics parser. The transposer must not scan prose and
  guess that arbitrary words are chords.

The following parsed examples are supported:

| Input chord | Parsed root | Quality/modifiers | Bass |
| --- | --- | --- | --- |
| `C` | `C` | empty string | none |
| `Am7` | `A` | `m7` | none |
| `Bbadd9` | `Bb` | `add9` | none |
| `F#m7b5` | `F#` | `m7b5` | none |
| `G/B` | `G` | empty string | `B` |
| `Ebmaj7/G` | `Eb` | `maj7` | `G` |

## Nashville-style notation

Nashville numbers are not supported in the first implementation. Symbols such as
`1`, `4`, `5`, `6m`, `b7`, and `#4dim` must return an unsupported-notation error
unless they are introduced later with dedicated tests. This avoids ambiguous
interpretation of number tokens in lyrics, section labels, counts, or rehearsal
notes.

## Unsupported notation variants

The first implementation must reject these variants with a clear error that
includes the original token and reason:

- Double accidentals: `C##`, `Ebb`.
- Theoretical spellings outside the supported tonic table: `Cb`, `B#`, `Fb`,
  `E#`.
- Locale-specific note names: `H`, `Do`, `Re`, `Mi`.
- Roman-numeral analysis: `I`, `V/vi`, `bVII`.
- Nashville numbers: `1`, `4/6`, `6m`.
- Multiple slash basses or polychords: `C/G/B`, `C|G`, `C over G`.
- Chord grids whose cells cannot be isolated by the current parser.
- Inline key-change directives, capo directives, or transposition directives
  unless a future parser models them explicitly.
- Unicode accidentals (`♯`, `♭`) unless normalized by an explicitly tested input
  layer before chord parsing.

Unsupported notation must not be silently dropped, coerced to the nearest chord,
or passed through as if transposed.

## Invalid chord handling

- Parsing failures are deterministic validation errors, not LLM repair prompts.
- Errors must identify the offending token, document location when available,
  and the unsupported feature or malformed shape.
- A transposition request that contains any invalid chord token fails as a whole
  unless a future caller explicitly supports partial results. Partial results
  must never hide dropped or untransposed chords.
- Non-chord lyrics, section labels, comments, and structure markers are preserved
  exactly when they come from a parsed chord map or supported chord-sheet format.
- Raw lyrics or stored chord sheets must not be mutated while creating generated
  transposition output.

## Synthetic examples

These examples are invented for tests and documentation and are not derived from
copyrighted songs.

### Bracketed chord line

```text
[Verse]
[C]Rise with hope [F]walk in light [G]home again [C]
```

Transposed from `C MAJOR` to `D MAJOR`:

```text
[Verse]
[D]Rise with hope [G]walk in light [A]home again [D]
```

### Slash chord and extension

```text
[Intro]
G/B Cadd9 D/F# G
```

Transposed from `G MAJOR` to `Ab MAJOR`:

```text
[Intro]
Ab/C Dbadd9 Eb/G Ab
```

### Minor key progression

```text
[Interlude]
Am Dm E7 Am
```

Transposed from `A MINOR` to `C MINOR`:

```text
[Interlude]
Cm Fm G7 Cm
```
