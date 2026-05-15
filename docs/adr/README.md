# Architecture Decision Records Index

This directory contains the Architecture Decision Records for Cadentia.

## ADR List

- [ADR-001: Song Data Infrastructure and Storage Architecture](./ADR-001-song-data-infrastructure.md)
- [ADR-002: Recommendation Candidate Read Model Design](./ADR-002-recommendation-read-model.md)
- [ADR-003: Song Import and Deduplication Workflow](./ADR-003-song-import-deduplication.md)
- [ADR-004: Lyrics Storage Format and Parsing Strategy](./ADR-004-lyrics-storage-format.md)
- [ADR-005: Approval and Doctrinal Review Workflow](./ADR-005-approval-doctrinal-review.md)
- [ADR-006: Arrangement Transposition Policy](./ADR-006-arrangement-transposition.md)
- [ADR-007: Tag Taxonomy and Controlled Vocabulary Strategy](./ADR-007-tag-taxonomy.md)


## Implemented schema artifacts

- ADR-001 is implemented by the Flyway migration
  `apps/api/src/main/resources/db/migration/V002__core_catalog_schema.sql`,
  which creates the canonical PostgreSQL source-of-truth catalog tables.
- The implemented ADR-001 ER diagram is maintained in `docs/diagrams/er-diagrams.md`.
- Test-only fixture loading and reset instructions are documented in
  `docs/seed-data.md`; these fixtures are not production catalog data and are
  not recommendable unless a test deliberately opts into that behavior.

## Reading order

Recommended reading order:

1. ADR-001 — core data infrastructure
2. ADR-002 — recommendation candidate read model
3. ADR-003 — import and deduplication workflow
4. ADR-004 — lyrics format and parsing strategy
5. ADR-005 — approval and doctrinal review
6. ADR-006 — arrangement transposition policy
7. ADR-007 — taxonomy and controlled vocabulary

## Cross-cutting principles

Across all ADRs, Cadentia follows these rules:

- The LLM interprets intent only.
- The Recommendation Engine selects songs deterministically.
- Only curated catalog entries may be recommended.
- Approval state must gate recommendation eligibility.
- Musical metadata must support explainable set construction.
