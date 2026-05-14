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
