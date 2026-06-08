# AGENTS.md

## Purpose

This document defines context and working instructions for AI agents
collaborating on the Worship Setlist Assistant project.

------------------------------------------------------------------------

## Project Context

This project supports worship teams by recommending structured song sets
based on: - Scripture focus - Spiritual themes - Musical constraints -
Doctrinal review

The system must prioritize: - Safety (no hallucinated songs) -
Transparency - Deterministic selection logic

------------------------------------------------------------------------

## Agent Responsibilities

### 1. LLM Intent Agent

-   Parse free-text inputs
-   Extract structured JSON slots
-   Never invent songs
-   Never output prose when JSON is required
-   Respect defined JSON schema

### 2. Recommendation Agent (REng)

-   Never rely on LLM for song selection
-   Enforce constraints:
    -   10 praise + 5 worship (default)
    -   Minimal key centers
    -   Relative major/minor transitions allowed
    -   Controlled tempo jumps
-   Return scored and ordered result

### 3. Data Integrity Agent

-   Validate lyrics source provenance
-   Enforce approved-only filtering
-   Maintain audit logs

------------------------------------------------------------------------

## JSON Contract (Intent Output)

The LLM must output:

{ "intent": "GENERATE_SETLIST", "slots": { "verseText": "...",
"themeHints": \[\], "counts": {"praise": 10, "worship": 5}, "keyPolicy":
{ "preferSameKey": true, "allowRelativeMajorMinor": true,
"maxKeyCenters": 2 }, "tempoPolicy": { "maxJumpBpm": 12 } } }

------------------------------------------------------------------------

## Guardrails

-   LLM cannot select songs.
-   Only backend REng may generate setlists.
-   All LLM output must pass JSON schema validation.
-   All recommendations must cite dataset references.

------------------------------------------------------------------------

## Coding Guidelines

### GitHub

- All commits should follow conventional commits format

### OpenAPI

- The API contract under `apps/api/src/main/openapi/` is intentionally split into three YAML files:
    - `cadentia-api.yaml` is the aggregate entrypoint used by the OpenAPI generator. Keep API metadata, tags, and top-level `$ref` indexes here.
    - `cadentia-api.paths.yaml` owns path-item definitions. Add or update endpoint operations here, and point shared parameters, schemas, and responses to `cadentia-api.components.yaml` with relative `$ref` values.
    - `cadentia-api.components.yaml` owns reusable parameters, security schemes, schemas, and shared responses.
- Do not collapse the OpenAPI contract back into a single large file unless the generator/tooling requirement changes.
- Prefer expanded YAML style over inline JSON-style objects or arrays in these files for readability in diffs.
- After OpenAPI changes, run `mvn -pl apps/api -DskipTests generate-sources` to verify the aggregate spec still resolves and generated interfaces/models stay in sync.

### Java

- In general, follow [Google Java Style](https://google.github.io/styleguide/javaguide.html), but use four spaces instead of two
- Avoid star imports
- Use imports instead of full class names

#### Testing

- Use assertj library for assertions
- Follow Given/When/Then or Arrange/Act/Assert pattern for tests
- Add captors at class level with `@Captor` annotation instead of inline in tests
- JUnit and mockito
