# ADR-007 Implementation Plan: Tag Taxonomy and Controlled Vocabulary Strategy

Source ADR: [ADR-007: Tag Taxonomy and Controlled Vocabulary Strategy](../adr/ADR-007-tag-taxonomy.md)

## Goal

Implement a controlled tag vocabulary organized by tag type and managed through admin tooling so classification, recommendation, and reporting remain consistent.

## Subtask 1: Define controlled vocabulary schema and lifecycle

### Context

- Relevant ADR: `docs/adr/ADR-007-tag-taxonomy.md`
- Tag types include `theme`, `mood`, `occasion`, `scripture`, `season`, `musical_style`, and `audience`.
- ADR-002 requires aggregated tags in `v_recommendable_arrangements`.

### Prompt

Design or update schema support for controlled tags, tag types, tag aliases if needed, and tag assignment lifecycle. Define how tags attach to songs, arrangements, lyrics documents, or other entities.

### Acceptance criteria

- Enforces supported tag types at the database and application boundaries.
- Supports unique canonical tag names within each tag type.
- Defines whether aliases, descriptions, sort order, and active/inactive states are needed for initial implementation.
- Defines valid tag assignment targets and prevents orphaned assignments.
- Includes indexes for tag type, tag name, and assignment lookup.

### Restrictions

- Do not allow free-form production tags outside the controlled vocabulary.
- Do not let LLM output create new canonical tags automatically.
- Do not use tags as a replacement for approval records.

## Subtask 2: Seed initial controlled vocabulary

### Context

- Controlled vocabularies require deliberate seed data before reliable tagging and reporting can exist.
- Tag types from ADR-007 are the minimum required categories.

### Prompt

Create seed or fixture data for the initial controlled vocabulary, including representative tags for every ADR-defined tag type. Keep production vocabulary separated from test-only fixtures if the project distinguishes them.

### Acceptance criteria

- Provides at least one representative tag for each tag type: theme, mood, occasion, scripture, season, musical_style, and audience.
- Uses stable identifiers or slugs for deterministic references.
- Documents how vocabulary seeds are loaded and updated.
- Includes tests or checks proving duplicate canonical names within the same type are rejected.

### Restrictions

- Do not seed controversial or denomination-specific taxonomy choices without product review.
- Do not include user-specific local church vocabulary as global defaults.
- Do not mark AI-suggested tags as canonical without admin review.

## Subtask 3: Implement admin tag management operations

### Context

- ADR-007 states controlled vocabulary is managed through admin tooling.
- Tags affect recommendations and reporting, so changes require auditability.

### Prompt

Add backend operations or admin UI support to create, edit, deactivate, and list controlled tags. Include audit metadata according to project conventions.

### Acceptance criteria

- Supports creating tags with type, canonical name, slug, description, and active status as implemented.
- Supports editing non-identity metadata without breaking existing assignments.
- Supports deactivation instead of destructive deletion when tags are in use.
- Records actor identity and timestamps when the application has an audit layer.
- Includes tests for create, duplicate rejection, update, deactivate, and list-by-type operations.

### Restrictions

- Do not hard-delete tags with existing assignments unless a migration or archival plan exists.
- Do not allow non-admin production users to mutate controlled vocabulary if authorization exists.
- Do not create tags from raw LLM text without review.

## Subtask 4: Implement tag assignment and validation

### Context

- Tags support classification, recommendation, and reporting.
- Read-model tag aggregation must be deterministic.

### Prompt

Implement operations for assigning controlled tags to supported entities and validating tag assignments during imports or admin edits.

### Acceptance criteria

- Assigns only existing active controlled tags unless explicitly supporting inactive historical tags.
- Prevents duplicate assignments for the same entity and tag.
- Validates supported assignment target types.
- Includes tests for assignment, duplicate prevention, inactive tag behavior, and invalid target handling.
- Provides deterministic ordering for tag aggregation consumed by `v_recommendable_arrangements`.

### Restrictions

- Do not store unvalidated free-form tag strings on recommendable records.
- Do not let tags bypass approval requirements.
- Do not automatically assign tags based solely on LLM output.

## Subtask 5: Integrate tags with recommendation filters and reporting

### Context

- ADR-002 read model exposes aggregated tags for candidate retrieval.
- Recommendations may filter by theme, mood, scripture, season, style, occasion, or audience.

### Prompt

Update candidate retrieval and reporting queries to use controlled tag assignments. Ensure tag filters are deterministic and explainable.

### Acceptance criteria

- Candidate retrieval can filter by tag type and canonical tag identifier or slug.
- Recommendation explanations can cite matched controlled tags without inventing themes.
- Reporting queries can group by tag type and tag slug/name.
- Tests cover include-any and include-all tag filter semantics if both are supported.

### Restrictions

- Do not compare against raw user-entered tag strings in production recommendation queries.
- Do not use semantic similarity as a substitute for controlled tag matching in the initial implementation.
- Do not include unapproved arrangements in tag-filtered recommendation candidates.

## Subtask 6: Document taxonomy governance

### Context

- Uncontrolled vocabularies degrade quality and make recommendation behavior harder to explain.
- Future agents need clear rules for proposing and applying tags.

### Prompt

Document tag types, vocabulary governance, assignment rules, admin workflows, import handling, and recommendation/reporting usage.

### Acceptance criteria

- Lists tag types exactly as implemented.
- Explains how new tags are proposed, reviewed, created, edited, deactivated, and assigned.
- Links schema, seed, service, admin, and test files.
- States that LLMs may suggest tags for review only if such a workflow exists, and may not create canonical tags directly.

### Restrictions

- Do not document free-form production tagging as supported.
- Do not describe unimplemented semantic tagging as active behavior.
