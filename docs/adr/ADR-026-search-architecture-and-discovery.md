# ADR-026: Search Architecture and Discovery

Status: Accepted  
Date: 2026-05-28

## Context

As the approved catalog grows, worship leaders need fast discovery by title, scripture, tags, lyrics metadata, and musical features. Semantic discovery may help users find relevant songs, but it must never bypass approval gates or become the deterministic recommendation selector.

## Problem

Basic database filtering will not support fuzzy search, autocomplete, scripture discovery, or scalable ranking. Conversely, embedding-driven search can produce opaque or unsafe results if it is treated as recommendation logic or allowed to surface unapproved data.

## Decision

Adopt a search architecture with explicit approved-search indexes, explainable ranking signals, and optional semantic recall. Search returns discoverable catalog candidates; the Recommendation Engine remains the only component that constructs deterministic setlists.

Semantic search may expand or rank discovery results, but all results must pass instance visibility, approval, active-status, and licensing gates before being returned.

## Requirements

- Support title, alternate title, scripture, tag, contributor, key, BPM, and arrangement search.
- Support fuzzy matching and autocomplete for approved visible catalog data.
- Support semantic discovery using embeddings generated from approved metadata only.
- Define ranking strategy using lexical match, curated tag match, scripture proximity, popularity/familiarity where church policy allows, and semantic similarity.
- Define indexing strategy for instance-local catalog data and optional imported seed packages.
- Keep index updates traceable to catalog changes and approval events.
- Define scalability boundaries for catalog size, index refresh latency, and query latency.
- Ensure search explanations expose major ranking factors without leaking private or unapproved data.

## Acceptance Criteria

- Search remains performant at large catalog sizes using defined latency targets.
- Search relevance is explainable through ranking-factor metadata.
- Semantic search never returns unapproved, inactive, unauthorized, or instance-private data.
- Recommendation behavior remains deterministic and independent from nondeterministic semantic exploration.
- Search indexes can be rebuilt from canonical catalog data.


### Subtask 1 design decision: PostgreSQL-owned search projections

**Initial backend.** The first implementation uses PostgreSQL full-text search plus
`pg_trgm` fuzzy matching over derived projection tables in the API database.
Cadentia already operates PostgreSQL as the catalog system of record, Flyway is
available in local/test environments, and Testcontainers can exercise the same
extensions used by production. This keeps the first search slice explainable and
rebuildable without adding an embedded Lucene index or external OpenSearch/Meili
service before the catalog size requires it.

**Operational assumptions.** The baseline is sized for church and starter-package
catalogs up to roughly 100,000 approved song/arrangement documents per database,
with normal deployments expected to be far smaller. Incremental projection
updates should become visible within 60 seconds of catalog, approval, visibility,
license, lyrics-metadata, tag, or package changes; explicit rebuild jobs may take
minutes and should run outside latency-sensitive request paths. User-facing
queries target p95 under 150 ms for lexical search/autocomplete on the expected
catalog and p95 under 300 ms at the 100,000-document boundary. Re-evaluate the
backend when any instance approaches 250,000 approved documents, p95 lexical
latency exceeds 300 ms after index tuning, cross-field ranking becomes too
complex for SQL, or semantic vector recall needs a dedicated ANN index.

**Why not embedded or external first.** An embedded search library would create a
second local storage format that is harder to operate and validate across
containers. An external search service adds deployment, backup, tenant-isolation,
and rebuild complexity before query behavior has stabilized. PostgreSQL `tsvector`,
GIN indexes, prefix normalization, and `pg_trgm` provide the required fuzzy and
autocomplete baseline while preserving transactional traceability.

**Projection model.** User-facing approved search documents are derived from the
canonical catalog and contain only safe fields:

| Projection field | Canonical source boundary | Exposure rule |
| --- | --- | --- |
| `song_id`, `arrangement_id`, `document_type` | Songs and arrangements | Identifiers only for approved, active, visible, license-safe records. |
| `canonical_title`, `alternate_titles` | Song title and approved alias data | Include public/approved aliases only; never import-candidate titles until accepted. |
| `scripture_references` | Scripture tags or normalized scripture metadata | Store normalized book/chapter/verse ranges suitable for discovery/proximity. |
| `approved_tags` | Active controlled tags assigned to songs, arrangements, or approved lyrics metadata | Include tag code, type, label, and slug after taxonomy approval. |
| `contributors` | Contributor/artist/composer credits that are approved for display | Include display names and safe roles; exclude private reviewer notes. |
| `musical_key`, `key_mode`, `tempo_bpm`, `meter` | Arrangement musical features | Include arrangement key, mode, BPM, and time signature/meter. |
| `arrangement_label` | Arrangement name/label | Include approved arrangement labels and identifiers for result hydration. |
| `lyrics_metadata` | Current approved lyrics document and parser review output | Include metadata such as format, section availability, theme/scripture tags, and safe content flags; do not index raw unapproved lyrics. |
| `package_references`, `source_references` | Starter package/import provenance | Include package/source labels, IDs, and license class only when safe to expose; omit private URLs, scraper diagnostics, and review notes. |
| `search_text`, `search_vector` | Concatenated normalized safe fields | Derived solely for lexical/fuzzy ranking and discarded on rebuild. |
| `projection_version`, timestamps | Projection job metadata | Supports traceability, invalidation, and rebuild verification. |

**Index ownership and rebuildability.** Canonical catalog, approval, lyrics,
provenance, package, tag, contributor, and arrangement tables remain the only
source of truth. Search tables are disposable derived artifacts. A full rebuild
may truncate `approved_search_documents` and regenerate it by replaying current
canonical eligibility rules; incremental updates are tracked through
`approved_search_projection_events`. Recommendation read models such as
`v_recommendable_arrangements` remain separate and must not consume search result
ordering.

**Approved/admin boundary.** `approved_search_documents` is the only planned
user-facing discovery index and may contain only records that have passed active,
approved, instance-visibility, authorization, and licensing checks before
ranking or response rendering. Any future unapproved review path uses the
separate `admin_review_search_documents` table or a later external collection
with an admin-only query service, explicit role checks, audit logging, and no
shared result counts, snippets, typo corrections, or ranking explanations with
user-facing search.

**Migration seams.** Application code should access search through a `SearchIndex`
port that accepts projection documents and query objects rather than direct SQL
from controllers. PostgreSQL-specific SQL, `tsvector`, and trigram thresholds stay
inside a PostgreSQL adapter. If Cadentia later moves to OpenSearch, Meilisearch,
or another service, the canonical-to-index projection contract and rebuild job
remain unchanged; only the adapter, collection mappings, aliases, and bulk-loader
implementation change. External indexes must preserve separate approved/admin
collections or aliases and must be rebuildable from the same projection source.

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

- What embedding model/version governance is required for semantic discovery?
