# Tag Taxonomy Governance

This document governs Cadentia's ADR-007 controlled tag vocabulary. It is the
operator-facing companion to the ADR and implementation plan for anyone proposing,
creating, assigning, filtering, or reporting on tags.

## Source files

- ADR: [ADR-007: Tag Taxonomy and Controlled Vocabulary Strategy](./adr/ADR-007-tag-taxonomy.md)
- Implementation plan: [ADR-007 implementation plan](./implementation-plans/ADR-007-tag-taxonomy-plan.md)
- Schema migration: [`V008__controlled_tag_taxonomy_schema.sql`](../apps/api/src/main/resources/db/migration/V008__controlled_tag_taxonomy_schema.sql)
- Starter vocabulary seed: [`V009__seed_controlled_tag_vocabulary.sql`](../apps/api/src/main/resources/db/migration/V009__seed_controlled_tag_vocabulary.sql)
- Recommendation/reporting view: [`V010__recommendable_arrangement_tag_reporting.sql`](../apps/api/src/main/resources/db/migration/V010__recommendable_arrangement_tag_reporting.sql)
- Tag type enum: [`TagType.java`](../apps/api/src/main/java/com/cadentia/catalog/model/TagType.java)
- Tag create/update commands: [`CreateTagCommand.java`](../apps/api/src/main/java/com/cadentia/catalog/model/CreateTagCommand.java), [`UpdateTagCommand.java`](../apps/api/src/main/java/com/cadentia/catalog/model/UpdateTagCommand.java)
- Assignment boundary and service validation: [`TagAssignmentTarget.java`](../apps/api/src/main/java/com/cadentia/catalog/model/TagAssignmentTarget.java), [`CatalogService.java`](../apps/api/src/main/java/com/cadentia/catalog/service/CatalogService.java)
- Repository operations: [`SongRepository.java`](../apps/api/src/main/java/com/cadentia/catalog/repository/SongRepository.java), [`JdbcSongRepository.java`](../apps/api/src/main/java/com/cadentia/catalog/repository/JdbcSongRepository.java)
- Recommendation filters and reporting: [`TagFilter.java`](../apps/api/src/main/java/com/cadentia/reng/TagFilter.java), [`JdbcCandidateRetriever.java`](../apps/api/src/main/java/com/cadentia/reng/JdbcCandidateRetriever.java), [`JdbcTagReportingRepository.java`](../apps/api/src/main/java/com/cadentia/reng/JdbcTagReportingRepository.java)
- Tests: [`CatalogWriteCommandValidationTest.java`](../apps/api/src/test/java/com/cadentia/catalog/model/CatalogWriteCommandValidationTest.java), [`CatalogServiceTest.java`](../apps/api/src/test/java/com/cadentia/catalog/service/CatalogServiceTest.java), [`JdbcSongRepositoryIntegrationTest.java`](../apps/api/src/test/java/com/cadentia/catalog/repository/JdbcSongRepositoryIntegrationTest.java), [`JdbcCandidateRetrieverIntegrationTest.java`](../apps/api/src/test/java/com/cadentia/reng/JdbcCandidateRetrieverIntegrationTest.java)

## Implemented tag types

The production taxonomy supports exactly these tag types:

| Tag type | Intended use |
| --- | --- |
| `THEME` | Reviewed theological or topical content such as gratitude, praise, lament, or trust. |
| `MOOD` | Reviewed emotional or liturgical tone such as celebratory, reflective, or contemplative. |
| `OCCASION` | Service placement or event context such as gathering, communion, sending, or baptism. |
| `SCRIPTURE` | Reviewed biblical linkage at the supported granularity, such as a biblical book or passage family. |
| `SEASON` | Church-calendar or broad scheduling context such as year-round, Advent, Christmas, Lent, or Easter. |
| `MUSICAL_STYLE` | Reviewed musical style category such as contemporary, hymn, gospel, acoustic, or choral. |
| `AUDIENCE` | Intended participation context such as congregation, choir, youth, or kids. |

Do not add new tag types without an ADR update, schema migration, enum update, and
recommendation/reporting review. Production code and migrations use uppercase enum
values; UI copy may display friendlier labels, but persisted values must remain
canonical.

## Canonical tag record

A canonical tag is the only production tag value that may be assigned to catalog
content. Each record has:

- `tag_type`: one of the seven implemented values above.
- `name`: human-readable canonical label, unique case-insensitively within its tag type.
- `slug`: stable deterministic identifier, unique within its tag type.
- `description`: optional reviewer-facing explanation of intended usage.
- `sort_order`: non-negative admin-managed order within a tag type.
- `is_active`: lifecycle flag used to remove a tag from future assignment and recommendation/reporting output without deleting historical rows.

Aliases may exist only as controlled alternate names for an existing canonical tag.
Aliases are review and lookup aids; they do not create new canonical tags and are
not assigned directly to songs, arrangements, or lyrics documents.

## Governance lifecycle

### 1. Propose

Any new tag proposal must include:

- tag type;
- proposed canonical name and slug;
- concise description and intended assignment examples;
- reason existing active tags are insufficient;
- any import source, ministry, or product requirement that motivated the proposal.

Proposals should prefer broad, explainable, denomination-neutral tags. Do not add
local-church shorthand, user-specific planning labels, controversial doctrinal
classifications, or raw scraper/LLM strings as global defaults.

### 2. Review

A product/admin reviewer should confirm that the proposed tag:

- fits exactly one implemented tag type;
- has a stable name and slug;
- does not duplicate an active tag or alias in the same type;
- is broad enough for reuse across the catalog;
- can be assigned consistently by human reviewers;
- does not weaken approval or doctrinal-review requirements.

If the proposal is a synonym or spelling variant of an existing tag, create or use
an alias for admin lookup rather than a new canonical tag.

### 3. Create

Create approved canonical tags through backend admin operations that use
`CreateTagCommand` and repository persistence. Production seed changes belong in a
new Flyway migration; do not edit historical migrations after they have shipped.
Use deterministic slugs and, for seed data, fixed identifiers when test or
operational references require stability.

### 4. Edit

Edits should preserve assignment meaning. Acceptable edits include description
clarification, display-name correction, slug correction before external use, sort
order changes, and active-state changes. Avoid changing a tag into a different
semantic concept while existing assignments remain attached; create a new tag and
reassign through a reviewed data migration instead.

### 5. Deactivate

Deactivate rather than delete tags that have ever been assigned. Deactivation is
the supported way to prevent future use while preserving historical auditability.
Inactive tags must not be assigned by `CatalogService`, and active recommendation
views only expose active tags.

### 6. Assign

Assignments are reviewer/admin decisions. Assign only existing active canonical
tags to supported target entities:

- `SONG`
- `ARRANGEMENT`
- `LYRICS_DOCUMENT`

Duplicate assignments to the same target are rejected. Assignment targets must
exist before assignment. Tags never bypass song, arrangement, lyrics, doctrinal,
editorial, licensing, or musical approval gates.

## Admin workflow expectations

Current backend support covers the core admin operations needed by future admin UI
or API layers:

1. List or search existing tags by type/slug using repository queries.
2. Create reviewed canonical tags with type, name, slug, description, sort order,
   and active state.
3. Update non-identity metadata or deactivate tags instead of deleting in-use
   tags.
4. Assign active canonical tags to songs, arrangements, or lyrics documents
   through the catalog service validation boundary.
5. Use integration tests and database constraints as safety checks for duplicate
   names, duplicate mappings, invalid targets, and inactive-tag assignment.

When a dedicated admin controller/UI is added, it must keep the same rules:
authorized admins only, no hard delete of in-use tags, no free-form production tag
assignment, and explicit reviewer intent for every canonical vocabulary change.

## Import handling

Imports may carry source classifications, scraper labels, or proposed tags, but
those values are not production taxonomy assignments. Import handling must treat
incoming tag-like strings as review evidence only:

- match against existing canonical tags or aliases where deterministic matching is
  implemented;
- surface unmatched strings to an admin review queue instead of creating tags;
- require a human/product reviewer before canonical tag creation or assignment;
- keep production vocabulary seeds separate from test-only fixture tags;
- never mark AI-suggested or scraper-suggested tags canonical without review.

## LLM boundaries

LLMs may not create canonical tags, assign canonical tags directly, or expand the
controlled vocabulary. No active LLM taxonomy-suggestion workflow is currently
implemented. If one is added later, LLM output may only suggest candidate tags for
human/admin review, and the reviewed result must still pass the canonical tag
creation and assignment rules in this document.

## Recommendation and reporting usage

Recommendation candidate retrieval must use controlled tag filters, not raw
user-entered strings. A tag filter identifies a tag by implemented type plus either
canonical tag ID or canonical slug. Include-any and include-all filtering operate
against approved `v_recommendable_arrangement_tags` rows, which expand active tags
from the song, arrangement, and current lyrics document attached to an approved
recommendable arrangement.

Recommendation explanations may cite matched controlled tags by canonical name,
slug, and type. They must not invent themes or imply unreviewed semantic matches.
Reporting groups approved recommendation candidates by canonical tag type, slug,
name, and ID so counts are explainable and deterministic.

## Change checklist

Use this checklist before merging taxonomy changes:

- [ ] The tag type is one of `THEME`, `MOOD`, `OCCASION`, `SCRIPTURE`, `SEASON`, `MUSICAL_STYLE`, or `AUDIENCE`.
- [ ] The canonical name and slug are unique within the tag type.
- [ ] The description explains when reviewers should use the tag.
- [ ] The tag is broad, reusable, and product-reviewed.
- [ ] Any seed change is in a new migration with deterministic references.
- [ ] Assignments target only existing songs, arrangements, or lyrics documents.
- [ ] Inactive tags are not assigned to new targets.
- [ ] Recommendation filters and reports use canonical tag identity or slug.
- [ ] Tests cover any new behavior, migration, or assignment rule affected by the change.
