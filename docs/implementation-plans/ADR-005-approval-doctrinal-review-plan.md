# ADR-005 Implementation Plan: Approval and Doctrinal Review Workflow

Source ADR: [ADR-005: Approval and Doctrinal Review Workflow](../adr/ADR-005-approval-doctrinal-review.md)

## Goal

Implement explicit typed approval records so recommendation eligibility requires the necessary doctrinal, editorial, musical, and licensing approvals.

## Subtask 1: Define approval requirements by entity and use case

### Context

- Relevant ADR: `docs/adr/ADR-005-approval-doctrinal-review.md`
- Approval types: `doctrinal`, `editorial`, `musical`, and `licensing`.
- Status values: `pending`, `approved`, `rejected`, and `needs_review`.
- ADR-002 read model must gate recommendation eligibility based on approvals.

### Prompt

Document which entities require each approval type before recommendation eligibility. Define the minimum approval set for songs, arrangements, lyrics documents, and imported content.

### Acceptance criteria

- Specifies required approval types for each recommendable entity path.
- Defines how rejected or needs_review statuses affect eligibility.
- Identifies who or what system role may create, update, or revoke approvals.
- Produces rules that ADR-002 can enforce in `v_recommendable_arrangements`.

### Restrictions

- Do not make any content recommendable without explicit required approvals.
- Do not collapse approval types into a single boolean.
- Do not allow import merge to imply approval automatically.

### Implementation note

Approval requirements for ADR-005 are defined as explicit, typed gates. The
Recommendation Engine and ADR-002 read model must treat a missing approval record
the same as an unapproved approval record. Only the `approved` status satisfies a
gate. `pending`, `rejected`, and `needs_review` all make the scoped entity
ineligible for production recommendation use until a later human/admin decision
sets the required approval type back to `approved`.

#### Required approvals by recommendable entity path

| Entity path | Required approval records before recommendation eligibility | Scope of the approval | Notes |
| --- | --- | --- | --- |
| Canonical song | `doctrinal`, `editorial`, `licensing` | Song-level theological/content identity and catalog metadata | These approvals establish that the canonical song is acceptable to include in the curated catalog. They do not approve every arrangement or lyrics version automatically. |
| Arrangement | `musical`, `editorial` | Arrangement-specific key, tempo, meter, language, default/active status, and usable music metadata | A song-level approval is required first, but arrangement approvals must still be explicit because a poor or incorrect arrangement can be unsuitable even when the song is approved. |
| Current lyrics document used by an arrangement | `doctrinal`, `editorial`, `licensing` | Exact lyrics/chord-sheet source version attached to the arrangement | Lyrics approvals are version-specific. Replacing the current lyrics document or changing its raw source must require fresh approvals for the new lyrics document. |
| Imported candidate content | None for recommendation; imports are never recommendable directly | Import batch/candidate review only | Import review, deduplication, or merge may create or update songs, arrangements, lyrics documents, and provenance, but it must not create implied approvals or bypass the explicit approval workflow. |
| Fully recommendable arrangement row in `v_recommendable_arrangements` | Approved canonical song gates plus approved arrangement gates plus approved current lyrics-document gates | Joined song + arrangement + current lyrics document path | ADR-002 must emit a row only when every required scoped approval exists with status `approved`; otherwise the arrangement is excluded. |

Minimum production eligibility rule for `v_recommendable_arrangements`:

```text
song.doctrinal = approved
song.editorial = approved
song.licensing = approved
arrangement.musical = approved
arrangement.editorial = approved
current_lyrics_document.doctrinal = approved
current_lyrics_document.editorial = approved
current_lyrics_document.licensing = approved
```

If an arrangement has no current lyrics document, it is not recommendable until the
product explicitly introduces an instrumental-only use case with its own ADR and
approval gates.

#### Use-case-specific rules

- **Recommendation generation:** require the full production eligibility rule.
  Callers must not opt out of approval gating, and small candidate pools must not
  trigger fallback to unapproved content.
- **Admin catalog browsing and review queues:** may show `pending`, `rejected`,
  and `needs_review` records, but these views must be clearly separate from
  recommendation candidate retrieval.
- **Import merge and deduplication:** may create draft or in-review canonical
  records, attach provenance, and preserve review notes. Merge completion does
  not satisfy doctrinal, editorial, musical, or licensing approvals.
- **Test fixtures:** may include explicitly approved synthetic records for happy
  paths and clearly named unapproved fixtures for negative tests. Tests must not
  rely on hidden defaults.

#### Status handling

- `approved`: satisfies the matching approval gate for the exact scoped entity
  and approval type.
- `pending`: does not satisfy eligibility; used when review has been requested
  but no final approving decision has been made.
- `rejected`: does not satisfy eligibility; the scoped entity must remain out of
  production recommendations until remediated and re-reviewed.
- `needs_review`: does not satisfy eligibility; use this when a prior decision
  is stale, disputed, affected by changed content, or requires follow-up.
- Missing approval record: does not satisfy eligibility and must be interpreted
  as not approved, not as implicitly pending or implicitly approved.

#### Approval actors

- **Doctrinal reviewer:** a human or designated admin role accountable for
  theological/content review may create, update, or revoke `doctrinal` approvals.
- **Catalog editor:** a human or designated admin role accountable for metadata
  quality may create, update, or revoke `editorial` approvals.
- **Music reviewer:** a human or designated admin role accountable for musical
  usability may create, update, or revoke `musical` approvals.
- **Licensing reviewer:** a human or designated admin role accountable for source
  rights and use permissions may create, update, or revoke `licensing` approvals.
- **System jobs/importers:** may initialize records that need review and may
  record provenance or audit metadata, but must not approve or revoke doctrinal
  or licensing decisions automatically.
- **LLM components:** must not create, update, revoke, or infer approvals. LLMs
  also must not select songs for recommendation.

Every approval mutation must capture the actor identity, decision timestamp,
approval type, status, scoped entity, and optional notes or audit context. If a
previously approved entity changes in a way that affects the reviewed scope, the
responsible application service must mark the affected approval gate as
`needs_review` or create a new pending review version rather than continuing to
serve the stale approval.

#### ADR-002 enforcement contract

`v_recommendable_arrangements` must join the canonical song, active arrangement,
and current lyrics document to their required approval records by entity id and
approval type. The view should expose approval flags or timestamps needed for
explainability, but it must filter to rows where every required gate is
`approved`. Reviewer notes are private governance data and must not be projected
into user-facing recommendation responses.

Implementation alignment required before schema work: ADR-005 names the approval
types `doctrinal`, `editorial`, `musical`, and `licensing`, while the current
Java/database model still uses `COPYRIGHT` and `CATALOG_INCLUSION` in addition
to the other types. Subtask 2 must align enum and constraint values with the ADR
terms before the read model depends on these gates.

## Subtask 2: Implement approval schema constraints and transitions

### Context

- ADR-005 requires explicit typed approval records.
- Approval transitions must be auditable and deterministic.

### Prompt

Add or update schema and application logic for approval records, including type validation, status validation, reviewer identity, timestamps, notes, and transition rules.

### Acceptance criteria

- Enforces supported approval types and statuses at the database and application boundaries.
- Stores reviewer or actor identity, decision timestamp, and optional notes.
- Prevents duplicate active approval records for the same entity and approval type unless historical versioning is explicitly modeled.
- Validates allowed transitions such as pending to approved, pending to rejected, approved to needs_review, and rejected to needs_review.
- Includes tests for valid and invalid transitions.

### Restrictions

- Do not delete historical approval decisions without an audit replacement.
- Do not allow anonymous approval changes if the application has identity context.
- Do not let an LLM approve doctrinal or licensing status.

## Subtask 3: Build doctrinal review workflow support

### Context

- Cadentia must prioritize doctrinal alignment before recommending content.
- Doctrinal review should be traceable and separated from editorial or musical approval.

### Prompt

Implement backend operations or admin workflow support for doctrinal review: assign review status, add notes, approve, reject, or mark needs_review for song or lyrics content according to the approved entity rules.

### Acceptance criteria

- Supports doctrinal review operations with reviewer identity and notes.
- Exposes pending and needs_review queues or query functions.
- Keeps doctrinal review independent from musical, editorial, and licensing statuses.
- Includes tests showing doctrinal rejection prevents recommendation eligibility.

### Restrictions

- Do not replace human doctrinal review with LLM judgment.
- Do not expose review notes in user-facing recommendation output unless explicitly designed and privacy-reviewed.
- Do not use hidden approval defaults.

## Subtask 4: Integrate approval gating with recommendation candidates

### Context

- ADR-002 read model exposes approval flags.
- Cross-cutting principle: approval state must gate recommendation eligibility.

### Prompt

Update the recommendation candidate read model and candidate retrieval functions so only arrangements satisfying required approvals are returned as recommendable candidates.

### Acceptance criteria

- `v_recommendable_arrangements` excludes content missing any required approval.
- Tests cover missing, pending, approved, rejected, and needs_review approval states.
- Candidate records expose enough approval detail for explainability without leaking private notes.
- Existing recommendation tests, if any, are updated to use approved fixtures.

### Restrictions

- Do not let callers opt out of approval gating for production recommendation flows.
- Do not implement fallback behavior that recommends unapproved content when candidate pools are small.
- Do not depend on LLM output to decide approval eligibility.

## Subtask 5: Document approval operations and audit expectations

### Context

- Approval state is a governance and safety boundary.
- Future agents need clear instructions to avoid bypassing review.

### Prompt

Document approval types, statuses, transitions, required reviewer metadata, recommendation gating behavior, and audit expectations.

### Acceptance criteria

- Lists approval types and statuses exactly as implemented.
- Describes allowed status transitions.
- Explains how approvals affect `v_recommendable_arrangements` and the Recommendation Engine.
- Links schema, service, and test files.
- Includes a warning that LLMs must not approve or select songs.

### Restrictions

- Do not document unimplemented roles or permissions as available.
- Do not expose sensitive reviewer notes in public docs.
