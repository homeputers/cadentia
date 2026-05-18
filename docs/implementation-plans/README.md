# Implementation Plans Index

This directory contains implementation plans for Cadentia Architecture Decision
Records (ADRs).

Each plan is written as a sequence of AI-agent-ready subtasks. Every subtask
includes:

- Context
- Prompt
- Acceptance criteria
- Restrictions

## Plan list

### Foundation plans

- [ADR-001 Implementation Plan: Song Data Infrastructure and Storage Architecture](./ADR-001-song-data-infrastructure-plan.md)
- [ADR-002 Implementation Plan: Recommendation Candidate Read Model Design](./ADR-002-recommendation-read-model-plan.md)
- [ADR-003 Implementation Plan: Song Import and Deduplication Workflow](./ADR-003-song-import-deduplication-plan.md)
- [ADR-004 Implementation Plan: Lyrics Storage Format and Parsing Strategy](./ADR-004-lyrics-storage-format-plan.md)
- [ADR-005 Implementation Plan: Approval and Doctrinal Review Workflow](./ADR-005-approval-doctrinal-review-plan.md)
- [ADR-006 Implementation Plan: Arrangement Transposition Policy](./ADR-006-arrangement-transposition-plan.md)
- [ADR-007 Implementation Plan: Tag Taxonomy and Controlled Vocabulary Strategy](./ADR-007-tag-taxonomy-plan.md)

### Phase 2 plans

- [ADR-012 Implementation Plan: LLM Intent Extraction Contract](./ADR-012-llm-intent-extraction-contract-plan.md)
- [ADR-008 Implementation Plan: Song Acquisition and Import Connector Architecture](./ADR-008-song-acquisition-import-connector-architecture-plan.md)
- [ADR-009 Implementation Plan: Lyrics Parsing and Musical Analysis Pipeline](./ADR-009-lyrics-parsing-musical-analysis-pipeline-plan.md)
- [ADR-010 Implementation Plan: Recommendation Engine Scoring Architecture](./ADR-010-recommendation-engine-scoring-architecture-plan.md)
- [ADR-011 Implementation Plan: Admin Review and Catalog Governance UI](./ADR-011-admin-review-catalog-governance-ui-plan.md)
- [ADR-013 Implementation Plan: Recommendation Explanation System](./ADR-013-recommendation-explanation-system-plan.md)

## Operational workflow docs

- [Song Import and Deduplication Workflow](../import-workflow.md) documents the
  ADR-003 staged import lifecycle, statuses, reviewer responsibilities,
  deterministic deduplication signals, merge behavior, failure handling, and
  fixture-driven verification commands.
- [Safe Lyrics Handling](../lyrics-handling.md) documents ADR-004 raw-versus-derived
  lyrics storage, format validation, versioning and provenance expectations,
  deterministic parser boundaries, and copyright-safe fixture rules.
- [Approval Operations and Audit Expectations](../approval-operations.md) documents
  ADR-005 approval types, statuses, audit metadata, transition rules,
  recommendation gating, and LLM safety boundaries.
- [Tag Taxonomy Governance](../tag-taxonomy-governance.md) documents
  ADR-007 tag types, controlled-vocabulary lifecycle, assignment rules,
  admin workflows, import handling, recommendation/reporting usage, and
  LLM boundaries.

## Recommended foundation implementation order

The foundation plans should generally be implemented from the source-of-truth
catalog outward. ADR-001 establishes the normalized data foundation, while
ADR-002, ADR-003, ADR-004, ADR-005, ADR-006, and ADR-007 can then build on that
foundation with the dependency order shown below.

```mermaid
flowchart TD
    A[ADR-001: Song data infrastructure] --> B[ADR-004: Lyrics storage format]
    A --> C[ADR-005: Approval and doctrinal review]
    A --> D[ADR-007: Tag taxonomy]
    A --> E[ADR-003: Import and deduplication]
    B --> F[ADR-006: Arrangement transposition]
    C --> G[ADR-002: Recommendation read model]
    D --> G
    E --> C
    F --> G
```

## Recommended Phase 2 implementation order

1. [ADR-012: LLM Intent Extraction Contract](./ADR-012-llm-intent-extraction-contract-plan.md)
2. [ADR-008: Song Acquisition and Import Connector Architecture](./ADR-008-song-acquisition-import-connector-architecture-plan.md)
3. [ADR-009: Lyrics Parsing and Musical Analysis Pipeline](./ADR-009-lyrics-parsing-musical-analysis-pipeline-plan.md)
4. [ADR-010: Recommendation Engine Scoring Architecture](./ADR-010-recommendation-engine-scoring-architecture-plan.md)
5. [ADR-011: Admin Review and Catalog Governance UI](./ADR-011-admin-review-catalog-governance-ui-plan.md)
6. [ADR-013: Recommendation Explanation System](./ADR-013-recommendation-explanation-system-plan.md)

ADR-010 is intentionally placed before the full ADR-011 governance UI because the
deterministic scoring core can be built and tested against already-approved
read-model data without making newly imported content recommendable. ADR-011
still remains the mandatory gate before Phase 2 imported content can become
eligible for recommendation.

## Cross-plan guardrails

- Do not let an LLM select songs or create recommendation results.
- Keep recommendation selection deterministic and backend-owned.
- Require approved catalog data before any song or arrangement becomes recommendable.
- Preserve provenance and auditability for imported or edited content.
- Prefer normalized source-of-truth data plus explicit read models for retrieval.
