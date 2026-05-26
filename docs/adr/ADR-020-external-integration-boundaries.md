# ADR-020: External Integration Boundaries

Status: Rejected (Covered by Existing ADRs)  
Date: 2026-05-26  
Superseded by: ADR-008, ADR-003, ADR-011, ADR-004

## Context

ADR-020 was drafted to define staging-first imports, provenance requirements, connector adapters, licensing boundaries, retry/idempotency behavior, and enrichment safeguards for external integrations.

## Problem

The scope proposed in ADR-020 materially overlaps existing ADRs:

- ADR-008 already defines connector types including OpenSong, ChordPro, CSV, Planning Center, and local Markdown import plus adapter lifecycle, provenance, retries, and policy boundaries.
- ADR-003 already defines staged import and deduplication workflow.
- ADR-011 already defines governance/review gating before promotion.
- ADR-004 already defines supported lyrics formats including chordpro, onsong, and markdown.

Keeping ADR-020 as an active standalone decision creates duplication and uncertainty about which ADR is canonical.

## Decision

Reject ADR-020 as covered by existing ADRs and treat its content as either:

1. already captured by ADR-008/ADR-003/ADR-011/ADR-004, or
2. future extension material to be added by amendment to those ADRs.

ADR-020 must not be used as the primary implementation source.

## Requirements

- External integration implementation must reference ADR-008 as the primary connector architecture decision.
- Staging/deduplication behavior must reference ADR-003.
- Promotion/review gates must reference ADR-011.
- Lyrics-format import compatibility boundaries must reference ADR-004.
- Any genuinely new integration policy (for example, third-party enrichment override policy details) must be proposed as amendments to the authoritative ADRs above.

## Acceptance Criteria

- Planning documents and tasks do not treat ADR-020 as an active normative source.
- References to OpenSong/ChordPro/CSV/Markdown import behavior point to ADR-008 and ADR-004.
- Review and promotion boundaries reference ADR-003 and ADR-011.
- Readers can unambiguously identify ADR-020 as duplicate coverage, not net-new architecture.

## Consequences

Positive:

- Avoids ADR fragmentation and duplicated governance language.
- Preserves one canonical decision chain for import/integration safety.
- Makes it easier to assess implementation status against existing ADRs.

Tradeoffs:

- Contributors must amend existing ADRs rather than adding overlapping new ADRs.

## Alternatives Considered

1. Keep ADR-020 active alongside ADR-008/003/011/004.
   - Rejected: overlapping scope and conflicting authority risk.
2. Narrow ADR-020 to metadata-enrichment-only policy.
   - Deferred: possible future amendment path if requirements exceed current ADRs.
3. Supersede ADR-008 with ADR-020.
   - Rejected: ADR-008 already includes broader and more detailed connector architecture.

## Open Questions

- Should enrichment-provider policy (e.g., YouTube/Spotify metadata precedence and confidence thresholds) be added explicitly to ADR-008?
- Should ADR index docs include an explicit “duplicate/rejected ADR” section for discoverability?
