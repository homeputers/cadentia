# Cadentia

AI-assisted worship setlist recommendation platform for church and mission-network teams. Combines a Spring Boot API with a Telegram bot, a deterministic Recommendation Engine, and a React admin console.

## Architecture

| Component | Stack | Port |
|-----------|-------|------|
| API | Java 21 / Spring Boot 3 / Maven | 8080 |
| Admin Web | React 18 / Vite 5 | 5173 |
| Database | PostgreSQL (Replit managed) | 5432 |

### Key paths
- `apps/api` — Spring Boot application (OpenAPI-first, Flyway migrations)
- `apps/api/src/main/openapi/` — split OpenAPI spec (aggregate + paths + components)
- `apps/api/src/main/resources/db/migration/` — 37 Flyway migrations (V001–V037)
- `apps/admin-web` — React admin console (proxies `/api` → port 8080)
- `packages/intent-contracts` — TypeScript/Zod schema for LLM JSON contract
- `packages/provisioning` — church instance provisioning package

## Running on Replit

Two workflows are configured and should run together:

**API (Spring Boot)** — starts the backend on port 8080  
**Admin Web (Vite)** — starts the frontend on port 5173 (proxies `/api` to the API)

The admin console is what the user sees in the preview pane (port 5173).

### Java 21 requirement
The project requires Java 21. The GraalVM module (`java-graalvm22.3`) provides Java 19 which is insufficient. Java 21 is installed as a Nix system package (`jdk21_headless`) and the API workflow sets `JAVA_HOME` explicitly before running Maven.

If the Nix store path changes after a rebuild, update the `JAVA_HOME` path in the API workflow command. Find the current path with:
```bash
dirname $(dirname $(readlink -f $(which java)))
```

## Environment variables

Set via Replit Secrets / Env Vars:

| Variable | Purpose | Default |
|----------|---------|---------|
| `CADENTIA_DB_URL` | JDBC URL for PostgreSQL | Replit managed DB |
| `CADENTIA_DB_USERNAME` | DB user | `postgres` |
| `CADENTIA_DB_PASSWORD` | DB password | Replit managed |
| `CADENTIA_LOCAL_ADMIN_USERNAME` | Admin UI login | `admin` |
| `CADENTIA_LOCAL_ADMIN_PASSWORD` | Admin UI password | `cadentia` |
| `CADENTIA_LLM_ENABLED` | Enable LLM features | `false` |
| `CADENTIA_TELEGRAM_BOT_TOKEN` | Telegram bot token | not set (bot inactive) |
| `CADENTIA_TELEGRAM_WEBHOOK_SECRET` | Telegram webhook secret | not set |

## Admin login

Default credentials (local auth mode):
- **Username:** `admin`
- **Password:** `cadentia`

## API docs (Swagger UI)

Available at `http://localhost:8080/swagger-ui.html` when the API workflow is running.

## Database

Replit's managed PostgreSQL. Flyway runs migrations automatically on startup. The schema is fully managed — no manual setup needed.

The migrations use `pgcrypto`, `unaccent`, and `pg_trgm` extensions (all available in standard PostgreSQL). pgvector is referenced in the Docker Compose file for future semantic search but is not required by any current migration.

## User preferences

- Keep existing project structure and stack unchanged
