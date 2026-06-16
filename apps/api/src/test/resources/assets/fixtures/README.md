# ADR-025 generated asset fixture payloads

Binary fixture payloads are intentionally **not** committed. Tests generate the
small placeholder payloads in memory from deterministic source code so PRs do
not need to carry binary files and so no copyrighted worship charts, commercial
stems, backing tracks, rehearsal recordings, lyrics, note content, personal
information, or real church media can enter the repository.

| Logical fixture | Purpose | Generation strategy | SHA-256 |
| --- | --- | --- | --- |
| `placeholder-chart.pdf` | Small PDF-like chart placeholder for object metadata and checksum tests. | Generated in `AssetOperationsRunbookTest` from static ASCII bytes containing only `Cadentia fixture`. | `3acc349a16e909272af46360ce001585efbab65f8351bb27fc02551a9f8259a8` |
| `placeholder-click.wav` | Tiny mono WAV tone used as copyright-safe audio fixture. | Generated in `AssetOperationsRunbookTest` as a 0.1 second 880 Hz sine wave at 8000 Hz. | `30f3af8781bbd968fc9a7acb387b0fcfa06751e23229c988417f835423505ffb` |
| `placeholder-cue.mid` | Minimal MIDI cue placeholder. | Generated in `AssetOperationsRunbookTest` from a Standard MIDI header and one note event. | `5112aeab8d22a7c3cc42f998cbffe1c6a497ca30cfa300a49fa1e37c1e046682` |

Fixture rules:

- Keep generated payloads small enough for fast unit and integration tests.
- Prefer deterministic in-memory generation over committed binary assets.
- Update this manifest, the runbook, and checksum assertions when adding or
  replacing generated fixture payloads.
- Do not add copyrighted worship charts, commercial stems, backing tracks,
  rehearsal recordings, or real church media.
