# Approval Operations and Audit Expectations

Cadentia treats approvals as a governance and safety boundary. Approval records
are explicit, typed decisions attached to one catalog entity. Recommendation
code must not infer approval from import status, catalog presence, LLM output, or
small candidate pools.

> **LLM boundary:** LLMs must not create, update, revoke, or infer approvals, and
> LLMs must not select songs for recommendation. Recommendation selection remains
> deterministic backend behavior over approved catalog data only.

## Implemented approval terms

Approval values are implemented as Java enums and stored in PostgreSQL with the
same uppercase string values.

### Approval types

- `DOCTRINAL`
- `EDITORIAL`
- `MUSICAL`
- `LICENSING`

### Approval statuses

- `PENDING`
- `APPROVED`
- `REJECTED`
- `NEEDS_REVIEW`

Only `APPROVED` satisfies a recommendation gate. Missing approval records and
records in `PENDING`, `REJECTED`, or `NEEDS_REVIEW` are not eligible for
production recommendation use.

## Approval record scope and metadata

Each approval record must target exactly one entity:

- a canonical song (`song_id`),
- an arrangement (`arrangement_id`), or
- a lyrics document (`lyrics_document_id`).

Imported candidates are not recommendable entities and must not be approved
directly. Import review or merge may create catalog records and provenance, but
it must not imply that any song, arrangement, or lyrics document is approved.

Every approval decision stores the following audit metadata:

- target entity identifier,
- approval type,
- status,
- non-blank reviewer/actor identity,
- optional private review notes,
- review timestamp (`reviewed_at`), and
- record creation timestamp (`created_at`).

Reviewer notes are governance data. Do not project notes into public docs,
user-facing recommendation payloads, or LLM prompts unless a separate privacy
review explicitly approves that exposure.

## Allowed status transitions

The application and database both enforce deterministic approval transitions.
The current status may be written again to refresh reviewer metadata, notes, or
`reviewed_at`; any actual status change must follow this table.

| From | Allowed next statuses |
| --- | --- |
| `PENDING` | `APPROVED`, `REJECTED` |
| `APPROVED` | `NEEDS_REVIEW` |
| `REJECTED` | `NEEDS_REVIEW` |
| `NEEDS_REVIEW` | `APPROVED`, `REJECTED` |

Do not delete historical decisions as a substitute for audit. If approved
content changes in a way that affects the reviewed scope, update the affected
approval to `NEEDS_REVIEW` or create a new review version through an explicitly
modeled versioning flow before the content is used for recommendation again.

## Operational expectations

- Use repository/service operations so enum validation, exactly-one-target
  validation, reviewer validation, unique active approval constraints, and
  status-transition checks run consistently.
- Do not perform anonymous approval changes when caller identity is available;
  the `reviewer` value must identify the human or controlled process responsible
  for the decision.
- Doctrinal review operations are available for song and lyrics document targets
  through the doctrinal review service. That service creates or updates only
  `DOCTRINAL` records, exposes `PENDING` and `NEEDS_REVIEW` queues, and rejects
  reviewer strings that identify an LLM/AI agent.
- Editorial, musical, and licensing approvals use the shared approval record
  repository operations and are not currently backed by separate role-specific
  permission services in this codebase. Do not document or rely on unavailable
  permission roles.
- System jobs/importers may create catalog and provenance records and may leave
  approvals pending or needing review. They must not auto-approve doctrinal or
  licensing decisions.

## Recommendation gating behavior

`v_recommendable_arrangements` is the approval-gated read model consumed by the
Recommendation Engine candidate retriever. A row exists only when all required
approval records below are present with status `APPROVED`:

| Entity scope | Required approved gates |
| --- | --- |
| Song | `DOCTRINAL`, `EDITORIAL`, `LICENSING` |
| Arrangement | `MUSICAL`, `EDITORIAL` |
| Current lyrics document | `DOCTRINAL`, `EDITORIAL`, `LICENSING` |

The view also requires an active arrangement, a non-archived song, a current
lyrics document, musical key, BPM, time signature, and energy level. It exposes
approval statuses for explainability but intentionally excludes private review
notes.

Recommendation candidate retrieval queries only
`v_recommendable_arrangements`, applies deterministic filters such as language,
key, BPM, and tags, then maps the exposed approval statuses into an
`ApprovalGateSummary`. Production recommendation flows must not add a bypass
flag, fallback query, or LLM-driven eligibility decision for unapproved content.

## Implementation links

- Schema foundation: [`V002__core_catalog_schema.sql`](../apps/api/src/main/resources/db/migration/V002__core_catalog_schema.sql)
  defines `approval_records`, exactly-one-target validation, reviewer metadata,
  review timestamps, and base indexes.
- Schema constraints and transitions: [`V006__approval_schema_constraints_and_transitions.sql`](../apps/api/src/main/resources/db/migration/V006__approval_schema_constraints_and_transitions.sql)
  aligns type/status values, enforces unique scoped approval records, and adds
  the database transition trigger.
- Recommendation read model: [`V007__recommendable_arrangements_approval_gated_view.sql`](../apps/api/src/main/resources/db/migration/V007__recommendable_arrangements_approval_gated_view.sql)
  gates candidate rows on approved song, arrangement, and current lyrics
  approvals while omitting private notes.
- Enums and transition validation:
  [`ApprovalType.java`](../apps/api/src/main/java/com/cadentia/catalog/model/ApprovalType.java),
  [`ApprovalStatus.java`](../apps/api/src/main/java/com/cadentia/catalog/model/ApprovalStatus.java), and
  [`ApprovalStatusTransition.java`](../apps/api/src/main/java/com/cadentia/catalog/model/ApprovalStatusTransition.java).
- Approval record commands and entity:
  [`CreateApprovalRecordCommand.java`](../apps/api/src/main/java/com/cadentia/catalog/model/CreateApprovalRecordCommand.java),
  [`UpdateApprovalRecordCommand.java`](../apps/api/src/main/java/com/cadentia/catalog/model/UpdateApprovalRecordCommand.java), and
  [`ApprovalRecord.java`](../apps/api/src/main/java/com/cadentia/catalog/entity/ApprovalRecord.java).
- Repository operations:
  [`SongRepository.java`](../apps/api/src/main/java/com/cadentia/catalog/repository/SongRepository.java) and
  [`JdbcSongRepository.java`](../apps/api/src/main/java/com/cadentia/catalog/repository/JdbcSongRepository.java).
- Doctrinal workflow service:
  [`DoctrinalReviewService.java`](../apps/api/src/main/java/com/cadentia/catalog/review/DoctrinalReviewService.java) and
  [`DoctrinalReviewCommand.java`](../apps/api/src/main/java/com/cadentia/catalog/model/DoctrinalReviewCommand.java).
- Recommendation candidate retrieval:
  [`JdbcCandidateRetriever.java`](../apps/api/src/main/java/com/cadentia/reng/JdbcCandidateRetriever.java),
  [`RecommendableArrangement.java`](../apps/api/src/main/java/com/cadentia/reng/RecommendableArrangement.java), and
  [`ApprovalGateSummary.java`](../apps/api/src/main/java/com/cadentia/reng/ApprovalGateSummary.java).
- Tests:
  [`JdbcSongRepositoryIntegrationTest.java`](../apps/api/src/test/java/com/cadentia/catalog/repository/JdbcSongRepositoryIntegrationTest.java)
  covers schema/repository behavior, approval transitions, and recommendation
  gating; [`DoctrinalReviewServiceTest.java`](../apps/api/src/test/java/com/cadentia/catalog/review/DoctrinalReviewServiceTest.java)
  covers doctrinal operations, queues, eligibility delegation, and LLM reviewer
  rejection.

## Rehearsal readiness boundary

Rehearsal readiness is operational planning data for service teams. The
readiness model records status (`UNKNOWN`, `READY`, `AT_RISK`, or `BLOCKED`),
structured blockers, missing people, unresolved arrangement conflicts,
rehearsal attendance or response state, and optional human notes with a privacy
classification. Readiness is owned by worship leaders and team schedulers, and
should be reviewed after assignment responses, after each rehearsal, and before
service-plan publication.

Readiness must not be treated as a catalog governance decision. A readiness
entry, note, or override does not approve a song, arrangement, lyrics document,
doctrinal review, musical review, or licensing clearance. Publication paths that
require approved catalog content still check `v_recommendable_arrangements`, and
setlist readiness summaries are scoped to approved planned arrangements only.

Readiness summaries expose objective structured blockers, staffing gaps, and
conflict labels separately from human note text. Private notes are redacted
unless policy grants the viewer access. LLM-facing recommendation explanations
must not receive or summarize private readiness notes; they may only receive
approved-catalog facts and explicitly allowed structured readiness signals.
Privileged readiness-note changes and operational overrides are audit events so
leaders can distinguish readiness risk management from catalog approval.
