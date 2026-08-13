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

### Live local Telegram webhook testing

For local Telegram testing, expose the API through a public HTTPS tunnel and
register Telegram against the existing webhook endpoint:

```text
https://<public-host>/telegram/webhooks/<botId>
```

Keep real Telegram values in `.env.local` or `.env`; both are ignored by git:

```bash
CADENTIA_TELEGRAM_BOT_TOKEN=...
CADENTIA_TELEGRAM_WEBHOOK_SECRET=...
CADENTIA_TELEGRAM_BOT_ID=local
CADENTIA_TELEGRAM_PUBLIC_BASE_URL=https://<cloudflare-or-tailscale-funnel-host>
```

Start dependencies and the API:

```bash
docker compose up -d postgres
cd apps/api
mvn spring-boot:run
```

Expose port `8080` with the current local tunnel. For Cloudflare quick tunnels:

```bash
cloudflared tunnel --url http://127.0.0.1:8080
```

If using Tailscale Funnel instead, use its public HTTPS URL as
`CADENTIA_TELEGRAM_PUBLIC_BASE_URL`. A normal tailnet-only Tailscale URL is not
enough for Telegram because Telegram must reach the webhook from the public
internet.

Register and verify the webhook:

```bash
scripts/telegram-webhook.sh set
scripts/telegram-webhook.sh info
```

If a Cloudflare quick tunnel is restarted, copy the new public URL into
`CADENTIA_TELEGRAM_PUBLIC_BASE_URL` and run `scripts/telegram-webhook.sh set`
again. If Telegram reports stale `401 Unauthorized` failures from earlier
attempts, clear the pending queue once:

```bash
set -a
source .env
set +a

curl -sS -X POST "https://api.telegram.org/bot${CADENTIA_TELEGRAM_BOT_TOKEN}/setWebhook" \
  -d "url=${CADENTIA_TELEGRAM_PUBLIC_BASE_URL}/telegram/webhooks/${CADENTIA_TELEGRAM_BOT_ID:-local}" \
  -d "secret_token=${CADENTIA_TELEGRAM_WEBHOOK_SECRET}" \
  -d "drop_pending_updates=true"
```

Verify the tunnel and webhook secret before testing from Telegram:

```bash
curl -i -X POST "${CADENTIA_TELEGRAM_PUBLIC_BASE_URL}/telegram/webhooks/${CADENTIA_TELEGRAM_BOT_ID:-local}" \
  -H "Content-Type: application/json" \
  -H "X-Telegram-Bot-Api-Secret-Token: ${CADENTIA_TELEGRAM_WEBHOOK_SECRET}" \
  -d '{"update_id":1786658001,"message":{"message_id":1,"date":1786658001,"chat":{"id":12345,"type":"private"},"from":{"id":12345,"is_bot":false,"first_name":"Local"},"text":"/newsetlist"}}'
```

The expected response is `202 Accepted`. A `401` response with
`WWW-Authenticate: Basic realm="Realm"` means Spring Security is intercepting
the webhook route before Telegram webhook secret validation.

Self-service Telegram account linking is not implemented yet. For local testing,
seed a linked actor after your bot has received a real message and you know the
Telegram private chat/user id:

```bash
export TELEGRAM_CHAT_ID=<your-private-chat-id>
export TELEGRAM_USER_ID=<your-telegram-user-id>
export HASH_SECRET="${CADENTIA_TELEGRAM_IDENTITY_HASH_SECRET:-local-dev-telegram-identity-secret}"
export INSTANCE_ID="${CADENTIA_INSTANCE_ID:-local-development}"

export TELEGRAM_CHAT_HASH="$(printf 'telegram:%s' "$TELEGRAM_CHAT_ID" | openssl dgst -sha256 -hmac "$HASH_SECRET" -binary | xxd -p -c 256)"
export TELEGRAM_USER_HASH="$(printf 'telegram:%s' "$TELEGRAM_USER_ID" | openssl dgst -sha256 -hmac "$HASH_SECRET" -binary | xxd -p -c 256)"

docker compose exec -T postgres psql -U cadentia -d cadentia <<SQL
INSERT INTO telegram_account_link (
    id,
    channel,
    chat_hash,
    user_hash,
    church_instance_id,
    actor_id,
    roles,
    status,
    link_confirmed_at,
    audit_metadata
) VALUES (
    gen_random_uuid(),
    'telegram',
    '${TELEGRAM_CHAT_HASH}',
    '${TELEGRAM_USER_HASH}',
    '${INSTANCE_ID}',
    gen_random_uuid(),
    ARRAY['ROLE_ADMIN'],
    'LINKED',
    now(),
    '{"source":"local_seed"}'::jsonb
)
ON CONFLICT (channel, chat_hash, user_hash, church_instance_id)
DO UPDATE SET
    roles = EXCLUDED.roles,
    status = 'LINKED',
    link_confirmed_at = now(),
    updated_at = now();
SQL
```

The seeded `church_instance_id` must match `CADENTIA_INSTANCE_ID`; otherwise the
bot will respond that the Telegram account is not authorized for this church
instance. `/settings` is disabled by default. To test it locally, set
`CADENTIA_TELEGRAM_SETTINGS_ENABLED=true` and restart the API.

When finished:

```bash
scripts/telegram-webhook.sh delete
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
