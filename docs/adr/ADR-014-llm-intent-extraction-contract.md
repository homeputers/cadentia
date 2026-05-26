# ADR-014: LLM Intent Extraction Contract

Status: Rejected (Duplicate)  
Date: 2026-05-26  
Superseded by: ADR-012

## Context

During continued ADR drafting, this record was proposed to define the LLM intent extraction boundary for setlist generation.

## Problem

The proposal in ADR-014 overlaps materially with an existing accepted/proposed architecture decision: ADR-012 already defines the same concern domain (JSON contract, validation, retries/fallbacks, anti-hallucination guardrails, and deterministic handoff to recommendation services).

Maintaining two ADRs for the same architectural boundary creates governance ambiguity about the canonical contract.

## Decision

Reject ADR-014 as a duplicate and retain **ADR-012** as the single source of truth for the LLM intent extraction contract.

Implementation and validation work must continue to reference ADR-012.

## Requirements

- ADR-012 remains the authoritative contract for LLM intent extraction.
- Any new intent-contract requirements must be proposed as amendments to ADR-012 (or a formal superseding ADR), not parallel duplicate ADRs.
- Planning artifacts and implementation references must not treat ADR-014 as independently implementable.

## Acceptance Criteria

- No implementation plan or code task references ADR-014 as the source of intent contract behavior.
- ADR-012 remains the only ADR referenced for intent extraction schema and validation behavior.
- Documentation readers can unambiguously identify ADR-014 as non-active and duplicate.

## Consequences

Positive:

- Removes conflicting architectural guidance.
- Preserves a single canonical intent contract source.
- Reduces implementation drift risk.

Tradeoffs:

- Requirements drafted uniquely in ADR-014 must be reconciled into ADR-012 through follow-up amendment if still desired.

## Alternatives Considered

1. Keep both ADR-012 and ADR-014 active.
   - Rejected: creates ambiguity and maintenance burden.
2. Rename ADR-014 and narrow scope to a different concern.
   - Rejected for now: no clearly distinct boundary was identified.
3. Supersede ADR-012 with ADR-014.
   - Rejected: ADR-012 is already integrated into ordering and implementation plans.

## Open Questions

- Should the additional slot fields drafted in the original ADR-014 text be merged into ADR-012 via amendment?
- Do we need explicit ADR lifecycle metadata (`Supersedes`, `Superseded by`, `Obsoletes`) standardized across all ADR documents?
