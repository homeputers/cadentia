# ADR-001 Subtask 1: Existing Data and Persistence Map

Reference plan: [`docs/implementation-plans/ADR-001-song-data-infrastructure-plan.md`](../implementation-plans/ADR-001-song-data-infrastructure-plan.md)

## Current repository state

Cadentia is currently a documentation-first repository. No runnable application stack, source tree, build files, database migration tooling, generated data-access types, seed fixtures, or runtime persistence configuration are present yet.

The documented target stack is Java 21, Spring Boot 3, PostgreSQL, Docker deployment, and Flyway migrations. Those are architectural intentions only at this point; they have not been scaffolded in the repository.

## Existing data and persistence artifacts

The current repository contains persistence design documentation only:

- `README.md` documents the proposed module layout, including future `catalog/` and `db/migrations/` directories, and names PostgreSQL plus Flyway as the proposed persistence stack.
- `docs/ARCHITECTURE.md` shows PostgreSQL in the data layer and currently displays pgvector as a future/adjacent data capability.
- `docs/diagrams/er-diagrams.md` describes the intended song catalog, recommendation read model, and import staging ER diagrams.
- `docs/adr/ADR-001-song-data-infrastructure.md` records the decision to use PostgreSQL as the system of record for canonical song data, arrangements, lyrics documents, tags, provenance records, approval records, and import batches.
- `docs/implementation-plans/ADR-001-song-data-infrastructure-plan.md` sequences the implementation work and explicitly reserves actual table creation for Subtask 2.

No existing files define database tables, migrations, application entities, repositories, generated types, seed data, or fixture loaders.

## Confirmed migration mechanism

The repository has no concrete migration mechanism yet. The intended mechanism is Flyway, based on the proposed stack documentation. Before Subtask 2 creates tables, the project should either:

1. scaffold the Java/Spring Boot application and Flyway configuration, then place SQL migrations under the configured Flyway location; or
2. add a repository-level `db/migrations/` convention and document how Flyway will be pointed at that directory once the application scaffold exists.

Until one of those conventions is committed, schema changes should remain design-only and should not introduce PostgreSQL tables.

## Expected affected files and directories for the database implementation

The following paths should be created or updated by future ADR-001 database subtasks:

| Path | Expected role | Current status |
| --- | --- | --- |
| `db/migrations/` | Versioned Flyway SQL migrations for PostgreSQL source-of-truth schema | Not present |
| `db/seeds/` or `db/fixtures/` | Non-production seed/fixture records for local development and tests | Not present |
| `catalog/entity/` | Application data records/entities for songs, arrangements, lyrics, tags, provenance, approvals, and import batches | Not present |
| `catalog/repository/` | Typed data-access operations for catalog persistence | Not present |
| `catalog/service/` | Catalog workflows that validate records before repository writes | Not present |
| `scraper-admin/` | Future import and deduplication workflows that write import batches, provenance, and candidate data | Not present |
| `docs/ARCHITECTURE.md` | Architecture updates after migrations and ownership boundaries are implemented | Present |
| `docs/diagrams/er-diagrams.md` | ER diagram updates aligned to actual migration names and relationships | Present |
| `docs/adr/README.md` | ADR index updates if implementation status tracking is added | Present |
| `docs/implementation-plans/ADR-001-song-data-infrastructure-plan.md` | Plan status/link updates as subtasks are completed | Present |

If the Spring Boot scaffold uses the conventional Maven or Gradle layout, Java source files should live under `src/main/java/...` and test fixtures under `src/test/resources/...`; however, those paths do not exist yet and should be introduced by the application-scaffolding task rather than assumed by a schema-only task.

## Local review and execution convention

For the next schema subtask, database changes should be reviewed as pull-requested SQL migrations before any runtime code depends on them. A local validation path should be documented in the same change that introduces migrations and should include:

1. starting or connecting to a local PostgreSQL database;
2. running Flyway against the committed migration location;
3. applying migrations to an empty database;
4. verifying rollback/reset expectations for local development data; and
5. loading fixture data only from clearly separated non-production seed files.

Because no Flyway configuration exists yet, this subtask cannot provide an executable migration command without inventing a convention. Subtask 2 should add that command alongside the first migration or explicitly defer it to application scaffolding.

## Subtask 1 conclusion

There is no existing runtime persistence implementation to modify. ADR-001 should proceed by first establishing the Flyway migration location and local execution command, then adding PostgreSQL catalog tables in Subtask 2. This note intentionally does not add tables, seed data, pgvector infrastructure, or runtime behavior changes.
