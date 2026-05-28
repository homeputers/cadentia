# ADR-026: Search Architecture and Discovery

Status: Proposed  
Date: 2026-05-28

## Context

As the approved catalog grows, worship leaders need fast discovery by title, scripture, tags, lyrics metadata, and musical features. Semantic discovery may help users find relevant songs, but it must never bypass approval gates or become the deterministic recommendation selector.

## Problem

Basic database filtering will not support fuzzy search, autocomplete, scripture discovery, or scalable ranking. Conversely, embedding-driven search can produce opaque or unsafe results if it is treated as recommendation logic or allowed to surface unapproved data.

## Decision

Adopt a search architecture with explicit approved-search indexes, explainable ranking signals, and optional semantic recall. Search returns discoverable catalog candidates; the Recommendation Engine remains the only component that constructs deterministic setlists.

Semantic search may expand or rank discovery results, but all results must pass tenant visibility, approval, active-status, and licensing gates before being returned.

## Requirements

- Support title, alternate title, scripture, tag, contributor, key, BPM, and arrangement search.
- Support fuzzy matching and autocomplete for approved visible catalog data.
- Support semantic discovery using embeddings generated from approved metadata only.
- Define ranking strategy using lexical match, curated tag match, scripture proximity, popularity/familiarity where tenant policy allows, and semantic similarity.
- Define indexing strategy for global and tenant-scoped catalog data.
- Keep index updates traceable to catalog changes and approval events.
- Define scalability boundaries for catalog size, index refresh latency, and query latency.
- Ensure search explanations expose major ranking factors without leaking private or unapproved data.

## Acceptance Criteria

- Search remains performant at large catalog sizes using defined latency targets.
- Search relevance is explainable through ranking-factor metadata.
- Semantic search never returns unapproved, inactive, unauthorized, or tenant-private data.
- Recommendation behavior remains deterministic and independent from nondeterministic semantic exploration.
- Search indexes can be rebuilt from canonical catalog data.

## Consequences

Positive:

- Catalog discovery scales beyond exact title lookup.
- Search can support exploratory workflows while preserving safety gates.
- Index rebuildability reduces operational risk.

Tradeoffs:

- Search infrastructure adds operational complexity.
- Embeddings and indexes must be refreshed when approval state changes.
- Ranking explanations may be less detailed than recommendation explanations.

## Alternatives Considered

1. Use only relational `LIKE` queries.
   - Rejected: insufficient for fuzzy, autocomplete, and scalable discovery.
2. Let semantic search directly generate setlists.
   - Rejected: violates deterministic Recommendation Engine ownership.
3. Index unapproved content for admin and user search together.
   - Rejected: increases leakage risk; privileged admin indexes must be separated or strongly filtered.

## Open Questions

- Which search backend should be the initial implementation target?
- Should admin review search use a separate unapproved-content index?
- What embedding model/version governance is required for semantic discovery?
