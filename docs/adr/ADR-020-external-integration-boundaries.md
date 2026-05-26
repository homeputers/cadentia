# ADR-020: External Integration Boundaries

Status: Proposed  
Date: 2026-05-26

## Context

Cadentia benefits from importing and enriching song data from external systems, but uncontrolled ingestion can undermine provenance, approval gates, licensing boundaries, and recommendation safety.

The platform must integrate broadly while guaranteeing that only curated and approved catalog data can become recommendable.

## Problem

Without explicit integration boundaries:

- external connectors may inject unvetted songs directly into recommendation pool
- provenance can be lost, weakening trust and auditability
- third-party outages or malformed payloads can corrupt internal state
- licensing constraints may be violated by unrestricted data usage

## Decision

Implement integration adapters behind a staging-first ingestion boundary.

- Supported integration families include:
  - file import
  - CSV import
  - ChordPro import
  - Planning Center export/import
  - SongSelect/CCLI metadata
  - YouTube/Spotify metadata enrichment
- All imported entities must enter staging first.
- Every imported record requires provenance metadata.
- No external source can directly create recommendable songs.
- Promotion to curated catalog requires normal governance and approval flows.

## Requirements

- Define adapter pattern with consistent contracts for fetch, parse, validate, map, and stage.
- Require provenance fields (source system, source identifiers, retrieval time, adapter version).
- Keep staging records isolated from recommendable catalog views.
- Enforce licensing and terms-of-use boundaries per connector type.
- Define connector error handling with retries, dead-letter capture, and idempotency safeguards.
- Define rate limits and backoff behavior for external APIs.
- Prevent metadata enrichment from overwriting approved curated values without review.

### Safety and Promotion Rules

- Import success means “staged,” not “approved.”
- Promotion workflow must pass deduplication, validation, and approval checks.
- Enrichment sources may augment candidate metadata but cannot bypass governance.
- Connector failures must fail safe and preserve catalog consistency.

## Acceptance Criteria

- Imported songs are never immediately recommendable.
- Every imported entity contains provenance attributes.
- Connector failures do not corrupt curated catalog state.
- External metadata enrichment cannot override approved curated metadata without explicit review.
- Retry/idempotency behavior prevents duplicate staging records from transient failures.

## Consequences

Positive:

- safer expansion of external ecosystem integrations
- stronger auditability and compliance posture
- resilience against third-party instability

Tradeoffs:

- additional staging and review workflow complexity
- connector maintenance overhead and monitoring requirements

## Alternatives Considered

1. Direct-write connectors into curated catalog.
   - Rejected: unacceptable integrity and governance risk.
2. Manual copy/paste imports only.
   - Rejected: poor scalability and high operator burden.
3. Trust external metadata as canonical.
   - Rejected: conflicts with Cadentia curated approval model.

## Open Questions

- Which connectors should be prioritized for initial rollout based on partner demand?
- What provenance retention period is required for legal/compliance needs?
- Should enrichment confidence scores be standardized across providers?
