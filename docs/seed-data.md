# Seed and Fixture Data

Cadentia keeps production catalog migrations separate from test and development
fixtures. Production schema changes live under `apps/api/src/main/resources/db/migration`,
while fixture data lives under `apps/api/src/test/resources/db/fixtures` so it is not
loaded automatically by Flyway in deployed environments.

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
- Approval rows are `PENDING` or `NEEDS_CHANGES`, never `APPROVED`.
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
