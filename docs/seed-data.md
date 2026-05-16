# Seed and Fixture Data

Cadentia keeps production catalog migrations separate from test and development
fixtures. Production schema changes live under `apps/api/src/main/resources/db/migration`,
while fixture data lives under `apps/api/src/test/resources/db/fixtures` so it is not
loaded automatically by Flyway in deployed environments.

## ADR-007 controlled tag vocabulary seeds

The initial production controlled vocabulary is loaded by Flyway with the schema
migrations, not from the test fixture directory:

```text
apps/api/src/main/resources/db/migration/V009__seed_controlled_tag_vocabulary.sql
```

This migration seeds one broad, noncontroversial starter tag for every ADR-007
tag type using stable UUIDs and stable slugs for deterministic references:

| Tag type | Name | Slug |
| --- | --- | --- |
| `THEME` | Gratitude | `theme-gratitude` |
| `MOOD` | Celebratory | `mood-celebratory` |
| `OCCASION` | Gathering | `occasion-gathering` |
| `SCRIPTURE` | Psalms | `scripture-psalms` |
| `SEASON` | Year Round | `season-year-round` |
| `MUSICAL_STYLE` | Contemporary | `musical-style-contemporary` |
| `AUDIENCE` | Congregation | `audience-congregation` |

Load or update these seeds by applying Flyway migrations through the normal API
startup or Maven test flow:

```bash
mvn -pl apps/api -am test
```

Future production vocabulary changes should be made through a new reviewed Flyway
migration or through audited admin operations once ADR-007 admin management is
implemented. Do not edit an already-applied Flyway migration, do not add
user-specific local church vocabulary to the global defaults, and do not promote
AI-suggested tags to canonical seed data without admin/product review.

## ADR-001 minimal catalog fixture

The ADR-001 fixture is intentionally small and exercises the source-of-truth
catalog tables added for song data infrastructure:

- `songs`
- `arrangements`
- `lyrics_documents`
- `tags`
- `song_tags`
- `arrangement_tags`
- `import_batches`
- `provenance_records`
- `approval_records`

Fixture file:

```text
apps/api/src/test/resources/db/fixtures/adr001_minimal_catalog_fixture.sql
```

Reset file:

```text
apps/api/src/test/resources/db/fixtures/reset_adr001_minimal_catalog_fixture.sql
```

The records are deliberately labeled as test-only data:

- Titles and tags include `[TEST FIXTURE]`.
- Provenance uses `source_system = 'adr-001-minimal-test-fixture'` and
  `import_method = 'TEST_FIXTURE'`.
- The song remains `DRAFT`.
- Approval rows are `PENDING` or `NEEDS_REVIEW`, never `APPROVED`.
- The lyrics document contains two lines of synthetic fixture text, not imported
  catalog lyrics.

Because the fixture is not production-approved, recommendation code must not
use it as eligible catalog data unless a test deliberately asserts behavior for
non-recommendable records.

## Load locally

Start PostgreSQL and apply Flyway migrations before loading fixture data:

```bash
docker compose up -d postgres
mvn -pl apps/api -am test
```

Load the fixture into the local development database:

```bash
docker compose exec -T postgres psql -U cadentia -d cadentia \
  < apps/api/src/test/resources/db/fixtures/adr001_minimal_catalog_fixture.sql
```

The fixture file starts by deleting the fixed fixture IDs and slugs it owns, so
it can be re-run during local development without duplicating rows.

## Reset locally

Remove only the ADR-001 fixture rows:

```bash
docker compose exec -T postgres psql -U cadentia -d cadentia \
  < apps/api/src/test/resources/db/fixtures/reset_adr001_minimal_catalog_fixture.sql
```

Do not add production seed data to this test fixture directory. If Cadentia later
needs production-approved catalog bootstrap data, introduce it through a separate,
reviewed convention with explicit licensing, provenance, and approval semantics.
