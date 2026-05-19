# Cadentia

## Overview

Cadentia is an AI-assisted recommendation platform designed to help musicians build cohesive, musically-continuous setlists.

The system combines:

- Guided conversational UI (Telegram / future WhatsApp)
- LLM-based intent interpretation layer
- Deterministic Recommendation Engine (REng)
- Curated Song Dataset
- Admin Song Import/Scraper Tool

The platform is designed for church and mission-network use,
prioritizing: 
- Doctrinal safety 
- Musical continuity (no cuts between songs) 
- Key grouping and energy arc modeling 
- Controlled AI usage (LLM for interpretation, not selection)

------------------------------------------------------------------------

## Core Modules

### 1. UI Layer (Chat Interfaces)

-   Telegram Bot (primary)
-   Optional WhatsApp integration
-   Guided menus to gather structured parameters
-   Final free-text "spiritual sensing" input

### 2. LLM Layer

-   Interprets natural language input
-   Extracts intent and slots
-   Requests clarifications when needed
-   Outputs strict JSON to backend
-   Never selects songs directly

### 3. Recommendation Engine (REng)

-   Deterministic scoring and selection
-   Applies constraints (keys, tempo, transitions, counts)
-   Produces setlist proposal ("quote" style)
-   Provides alternatives and transition notes

### 4. Song Dataset

-   Songs
-   Arrangements
-   Tags
-   Lyrics
-   Metadata (tempo, key, time signature)
-   Approval status

### 5. Song Scraper / Import Admin Tool

-   Import artist discography (via metadata providers)
-   Deduplicate tracks
-   Manual lyrics ingestion
-   License provenance tracking
-   Tag enrichment

------------------------------------------------------------------------

## Suggested Directory Structure


    cadentia/
    ├── api/
    │   ├── controller/
    │   ├── dto/
    │   ├── config/
    │   └── Application.java
    │
    ├── bot/
    │   ├── telegram/
    │   ├── whatsapp/
    │   ├── session/
    │   └── BotAdapter.java
    │
    ├── llm/
    │   ├── IntentService.java
    │   ├── LlmClient.java
    │   ├── OpenRouterClient.java
    │   └── SchemaValidator.java
    │
    ├── reng/
    │   ├── CandidateRetriever.java
    │   ├── KeyCenterChooser.java
    │   ├── SongSelector.java
    │   ├── SetOrderer.java
    │   └── SetlistService.java
    │
    ├── catalog/
    │   ├── entity/
    │   ├── repository/
    │   ├── service/
    │   └── LyricsProvider.java
    │
    ├── scraper-admin/
    │   ├── ArtistResolverService.java
    │   ├── DiscographyService.java
    │   ├── SongDeduper.java
    │   └── ImportService.java
    │
    ├── db/
    │   └── migrations/
    │
    └── docs/
        └── ARCHITECTURE.md

------------------------------------------------------------------------

## Proposed Tech Stack

-   Java 21
-   Spring Boot 3
-   PostgreSQL + pgvector
-   Telegram Bot API
-   OpenRouter or OpenAI (LLM)
-   Docker deployment
-   Flyway migrations


## Scaffolded Implementation

This repository is scaffolded as a Java 21 / Spring Boot 3.1.12 backend with a TypeScript workspace for shared intent contracts.

### Runtime and libraries

- Java 21 with Maven multi-module build
- Spring Boot 3.1.12 for the API service
- Spring Web, Validation, Actuator, Spring Data JDBC, Flyway, and PostgreSQL driver
- PostgreSQL 16 with the pgvector container image available for future semantic enrichment
- TypeScript 5.5 with Zod for strict LLM intent contract validation
- Vitest 4 for TypeScript contract tests

### Key paths

- `apps/api` - Spring Boot application and backend packages
- `apps/api/src/main/openapi/cadentia-api.yaml` - OpenAPI source contract for generated API interfaces and models
- `apps/api/src/main/resources/db/migration` - Flyway migrations
- `apps/api/src/test/resources/db/fixtures` - test-scoped catalog fixtures; see `docs/seed-data.md`
- `packages/intent-contracts` - TypeScript schema for the LLM JSON contract
- `docker-compose.yml` - local PostgreSQL development dependency service
- `scripts/check.sh` - combined backend and TypeScript checks

### Local development

```bash
make deps-up
npm install
make check
```

If you want to run only the API locally:

```bash
docker compose up -d postgres
cd apps/api
mvn spring-boot:run
```

### PostgreSQL connection troubleshooting

If `mvn spring-boot:run` fails with `Connection to localhost:5432 refused`, verify the database is actually reachable before starting Spring Boot:

```bash
docker compose ps postgres
docker compose logs --tail=100 postgres
docker compose exec postgres pg_isready -U cadentia -d cadentia
```

Expected `pg_isready` output is `accepting connections`.

Common causes and fixes:

1. **Container is not running/healthy yet**
   - Wait until `docker compose ps postgres` shows `running (healthy)`.
   - Then retry `mvn spring-boot:run`.

2. **Port `5432` is already used by another local Postgres**
   - Check host port usage:
     ```bash
     lsof -iTCP:5432 -sTCP:LISTEN
     ```
   - If a non-Docker Postgres is already bound to `5432`, either stop it or remap Cadentia's compose port and set `CADENTIA_DB_URL` accordingly.

3. **Wrong datasource environment values**
   - API defaults are:
     - `CADENTIA_DB_URL=jdbc:postgresql://localhost:5432/cadentia`
     - `CADENTIA_DB_USERNAME=cadentia`
     - `CADENTIA_DB_PASSWORD=cadentia`
   - Confirm your shell exports match those values (or your intended overrides) before starting Spring.

The API is contract-first: update `apps/api/src/main/openapi/cadentia-api.yaml`, then use Maven to regenerate Spring interfaces and models during the build. The Recommendation Engine scaffold intentionally returns no song selections until approved catalog retrieval is implemented.

------------------------------------------------------------------------

## Design Philosophy

-   AI assists, but deterministic engine decides
-   Songs always selected from curated dataset
-   Hard constraints enforced in backend
-   Transparent explanations to build user trust
