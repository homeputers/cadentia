# ADR-035 Telegram Bot Operations Runbook

## Purpose

This runbook defines the operational procedures required before the Telegram bot channel can be considered production-ready. It covers local development, deployment rollout, smoke testing, troubleshooting, credential rotation, rollback, channel disablement, dead-letter handling, and user-support escalation for the `/newsetlist` flow.

## Production Readiness Gate

Do **not** mark Telegram production-ready until all of the following are complete in the target environment:

- `mvn -pl apps/api -DskipTests generate-sources` succeeds against the split OpenAPI contract.
- `mvn -pl apps/api test` or the CI equivalent passes, including `TelegramE2eFixtureTest`.
- A synthetic `/newsetlist` smoke test has verified command receipt, prompt ingestion, confirmation callback handling, deterministic recommendation generation, rendered Telegram response delivery, and correlated session state.
- Failure-path smoke tests have verified invalid webhook secret rejection, disabled-channel behavior, stale callback handling, retry scheduling, and dead-letter creation using synthetic payloads only.

## Required Deployment Inputs

Before enabling production traffic, create the Telegram bot through BotFather,
store all secrets by reference, deploy Cadentia with Telegram disabled, validate
startup, register the webhook, run smoke tests, and only then enable inbound
processing for the target church instance.

### Secret references

Store values in the deployment secret manager; do not put literal credentials in manifests, logs, fixtures, or support tickets.

| Secret | Configuration key | Notes |
| --- | --- | --- |
| Telegram bot token | `cadentia.telegram.webhook.bot-token-ref` | Used by the outbound Telegram API client. Startup/webhook validation fails if the referenced token is unavailable while the channel is enabled. |
| Current webhook secret token | `cadentia.telegram.webhook.secret-token-ref` | Must match the `X-Telegram-Bot-Api-Secret-Token` value configured with Telegram. |
| Previous webhook secret token | `cadentia.telegram.webhook.previous-secret-token-ref` | Optional overlap secret for rotation. Remove after rollout verification. |
| Telegram identifier hash key | `cadentia.telegram.identifier-hash-secret-ref` or deployment equivalent | Used to hash chat/user IDs before persistence and logs. Rotate with a migration plan because identity lookup depends on it. |

### Environment/configuration keys

| Key | Expected production value |
| --- | --- |
| `cadentia.telegram.enabled` | `true` only after secrets, identity linking, and smoke tests are ready. |
| `cadentia.telegram.settings-enabled` | `false` unless `/settings` support is intentionally enabled for the instance. |
| `cadentia.telegram.callback-ttl` | `PT30M` default; stale callbacks are rejected after this window. |
| `cadentia.telegram.session-inactivity-ttl` | `PT30M` default. |
| `cadentia.telegram.session-absolute-ttl` | `PT4H` default. |
| `cadentia.telegram.webhook.max-payload-bytes` | Keep at or below the documented Telegram payload and service limit. |
| `cadentia.telegram.webhook.max-update-age` | Replay/staleness window for message dates. |

Example production-style configuration, using placeholder secret references:

```yaml
cadentia:
  telegram:
    enabled: false
    settings-enabled: false
    callback-ttl: PT30M
    session-inactivity-ttl: PT30M
    session-absolute-ttl: PT4H
    webhook:
      bot-token-ref: secret://cadentia/prod/telegram/bot-token
      secret-token-ref: secret://cadentia/prod/telegram/webhook-secret
      previous-secret-token-ref: ""
      max-payload-bytes: 1048576
      max-update-age: PT10M
```

### Webhook URL format

Use the HTTPS URL for the deployed API instance:

```text
https://<public-api-host>/telegram/webhooks/<botId>
```

`<botId>` must be an opaque deployment identifier, not the bot token and not a human user's chat ID.

## Production Setup Procedure

Use this procedure for a new production Telegram integration. Replace all
angle-bracket placeholders with environment-specific values. Do not paste real
tokens into tickets, shell history shared with other operators, or runbook
updates.

1. **Create or identify the Telegram bot.**
   - Use BotFather to create the bot or retrieve the existing bot token.
   - Record the bot username for operator reference only.
   - Store the bot token in the secret manager path referenced by
     `cadentia.telegram.webhook.bot-token-ref`.
2. **Create the webhook secret token.**
   - Generate a high-entropy token distinct from the bot token.
   - Store it at `cadentia.telegram.webhook.secret-token-ref`.
   - Leave `previous-secret-token-ref` empty for first-time setup.
3. **Create the identifier hash secret.**
   - Store the hash key in the deployment secret manager.
   - Confirm identity-linking jobs and support tooling use the same reference.
4. **Deploy Cadentia with Telegram disabled.**
   - Set `cadentia.telegram.enabled=false`.
   - Confirm application startup succeeds, database migrations are complete, and
     the API health endpoint is healthy.
5. **Validate secret resolution before public traffic.**
   - Confirm the runtime can resolve bot-token, webhook-secret, and identifier
     hash references.
   - Expected failure if a required reference is missing: webhook requests are
     rejected or return a retryable server failure; do not enable the channel.
6. **Register the Telegram webhook.**
   - Use a secure operator terminal with the bot token loaded from the secret
     manager into a local environment variable.
   - Register the webhook with the target URL and secret token:

```bash
curl -sS -X POST "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/setWebhook" \
  -d "url=https://<public-api-host>/telegram/webhooks/<botId>" \
  -d "secret_token=${TELEGRAM_WEBHOOK_SECRET}" \
  -d "drop_pending_updates=false"
```

7. **Verify Telegram registration.**

```bash
curl -sS "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/getWebhookInfo"
```

   Confirm `url` matches the Cadentia webhook URL and investigate any
   `last_error_message` or unexpected pending updates before proceeding.
8. **Enable Cadentia Telegram inbound.**
   - Set `cadentia.telegram.enabled=true` or enable the instance-level Telegram
     channel after successful registration.
   - Keep `/settings` disabled unless separately approved.
9. **Run smoke tests and observe telemetry.**
   - Complete the smoke checklist below with an authorized test account.
   - Confirm logs and metrics include correlation IDs and do not include raw
     tokens, webhook secrets, or unnecessary Telegram profile data.
10. **Record rollout evidence.**
    - Capture the deployment version, bot ID, webhook URL, smoke-test timestamp,
      and operator names in the deployment record.
    - Do not record bot token values, webhook secret values, chat IDs, or raw
      Telegram messages.

## Local Development Mode

Automated tests and local development must not call Telegram or use real credentials.

1. Use synthetic fixtures in `apps/api/src/test/resources/telegram/fixtures/`.
2. Keep `cadentia.telegram.enabled=false` unless testing routing manually.
3. Use a mocked `TelegramOutboundClient` for outbound send behavior.
4. Seed only approved catalog data when exercising recommendation generation; never use raw production Telegram updates or personal message text as fixtures.
5. Run:

```bash
mvn -pl apps/api -Dtest=TelegramE2eFixtureTest test
mvn -pl apps/api -Dtest=TelegramWebhookE2eEquivalenceTest test
```

`TelegramWebhookE2eEquivalenceTest` exercises the production webhook controller,
adapter, shared conversation facade, recommendation service, renderer, outbound
send service, and mocked Telegram client using synthetic updates and seeded
approved catalog candidates. It compares the Telegram confirmation proposal with
the HTTP/API `generateSetlistProposal` path for the same normalized request.

### Live local webhook testing with Cloudflare or Tailscale

Use this only for manual Telegram testing from a local Cadentia API. Do not use
real personal messages as fixtures, and do not commit `.env` or `.env.local`.

1. Create a high-entropy local webhook secret distinct from the bot token.
2. Store local values in `.env.local` or `.env`:

```bash
CADENTIA_TELEGRAM_BOT_TOKEN=...
CADENTIA_TELEGRAM_WEBHOOK_SECRET=...
CADENTIA_TELEGRAM_BOT_ID=local
CADENTIA_TELEGRAM_PUBLIC_BASE_URL=https://<public-tunnel-host>
```

3. Start PostgreSQL and the API:

```bash
docker compose up -d postgres
cd apps/api
mvn spring-boot:run
```

4. Expose the API over public HTTPS. A Cloudflare quick tunnel can point
   directly at the local API:

```bash
cloudflared tunnel --url http://localhost:8080
```

   If using Tailscale, use Tailscale Funnel's public HTTPS URL or route that
   URL through the existing Cloudflare tunnel. Tailnet-only `tailscale serve`
   URLs are not reachable by Telegram.

5. Register Telegram with the public URL:

```bash
scripts/telegram-webhook.sh set
scripts/telegram-webhook.sh info
```

6. Send a sanitized smoke command such as `/newsetlist` from the Telegram test
   account, then verify Cadentia logs contain `telegram_webhook outcome=ACCEPTED`.

7. Deregister the webhook before shutting down the local API:

```bash
scripts/telegram-webhook.sh delete
```

Expected local failure modes:

- `401` or `403`: Telegram's registered secret does not match
  `CADENTIA_TELEGRAM_WEBHOOK_SECRET`.
- `500` with `telegram-secret-unavailable`: the API cannot resolve
  `CADENTIA_TELEGRAM_BOT_TOKEN`.
- Telegram `getWebhookInfo.last_error_message`: the tunnel URL is stale, not
  HTTPS, or not routing to local port `8080`.

## Register or Refresh the Production Webhook

1. Confirm the bot token and current webhook secret references resolve in the runtime secret manager.
2. Confirm the public health endpoint and database are healthy.
3. Register the webhook with Telegram using the bot token and the current secret token value. The webhook URL must match `https://<public-api-host>/telegram/webhooks/<botId>`.
4. Confirm Telegram reports the expected URL and no unexpected pending backlog.
5. Send a synthetic-safe smoke command from an authorized test account.
6. Verify Cadentia logs show `telegram_webhook` entries with `outcome=ACCEPTED` and without raw bot token, raw webhook secret, or unnecessary Telegram profile data.

## Smoke Test Checklist

1. Send `/start`; expect onboarding/help guidance.
2. Send `/newsetlist`; expect the ADR-015 request collection flow to start.
3. Send a sanitized prompt such as `Psalm 100 thanksgiving and joy`; expect confirmation-ready state.
4. Confirm with the inline button; expect deterministic recommendation generation and a Telegram-safe proposal summary.
5. Verify the response cites approved catalog/approval references only.
6. Repeat one update ID through the fixture/mocked path; expect duplicate accepted semantics and no duplicate outbound send.
7. Send with a bad webhook secret in a non-production validation environment; expect rejection before processing.
8. Disable `cadentia.telegram.enabled` or instance-level control; expect a disabled-channel response without disabling core API routes.
9. In automated verification, compare Telegram and HTTP/API proposal status,
   selected-song ordering, and explanation evidence references for the same
   normalized request, policy snapshot, and seeded approved catalog snapshot.

## Health Checks and Telemetry

Monitor these logs/metrics/traces and alert on sustained degradation:

- `telegram_webhook` structured logs with `outcome`, `updateId`, `requestId`, `correlationId`, `botId`, `channelId`, and `failureCategory`.
- `webhook_receipt`, `update_normalization`, `command_routing`, `callback_routing`, and `channel_disablement` operations.
- `outbound_send_attempt`, `outbound_retry`, `rate_limit_response`, and `dead_letter_creation` operations.
- Conversation session transitions for Telegram sessions: `IDLE`, `NEW_SETLIST_ACTIVE`, `PENDING_CONFIRMATION`, `COMPLETED`, `CANCELLED`, and `EXPIRED`.
- Dead-letter counts, retry backlog age, Telegram 429 rate-limit counts, Telegram 5xx counts, unauthorized/disabled response counts, and stale callback counts.

Expected startup or request-time validation failures:

- Missing current webhook secret: webhook requests are rejected.
- Invalid presented webhook secret: webhook requests are rejected.
- Missing bot token reference while webhook processing is enabled: retryable server failure until the secret is restored.
- Payload above `cadentia.telegram.webhook.max-payload-bytes`: rejected before business processing.

## Troubleshooting Workflow

1. Determine the failing stage: webhook authentication, payload validation, normalization/routing, authorization/session state, recommendation generation, rendering, outbound send, or dead-letter replay.
2. Use `correlationId` and `updateId` from structured logs. Do not request raw production Telegram payloads from users.
3. Confirm the webhook URL and secret-token header are still registered with Telegram.
4. Confirm `cadentia.telegram.enabled` and any instance-level channel controls are enabled.
5. Check the linked Telegram identity state: unlinked, revoked, disabled, unauthorized role, cross-instance, or linked.
6. Check the session state and TTLs for stuck `NEW_SETLIST_ACTIVE`, `PENDING_CONFIRMATION`, `CANCELLED`, or `EXPIRED` states.
7. For outbound failures, inspect retry/dead-letter metadata: failure category, sanitized preview, attempt count, next attempt time, and chat hash.
8. Confirm Cadentia API health, database connectivity, and approved catalog seed availability.

## Dead-Letter Triage

1. Group by failure category: `NETWORK`, `TELEGRAM_5XX`, `RATE_LIMIT`, `CHAT_BLOCKED`, `INVALID_CHAT`, `UNAUTHORIZED_BOT`, `DISABLED_CHANNEL`, or `MALFORMED_REQUEST`.
2. Retry only categories that are safe and transient (`NETWORK`, `TELEGRAM_5XX`, `RATE_LIMIT`) after the underlying issue is resolved.
3. Do not replay dead letters that indicate user block, invalid chat, unauthorized bot token, disabled channel, malformed request, or policy rejection without operator approval.
4. Preserve sanitized metadata for incident review; never expand it with raw message text or tokens.
5. Link the dead-letter ID, correlation ID, and approximate timestamp in support tickets.

## Alert Response

- **Webhook verification failures spike:** pause rollout, verify current/previous secret references, verify Telegram webhook registration, and investigate suspicious traffic.
- **Outbound 429/rate limit:** keep inbound enabled if sessions can safely progress, pause nonessential retries, and let `retry_after` scheduling drain.
- **Outbound 5xx/network:** verify egress/DNS/TLS and Telegram API status; retry transient dead letters after recovery.
- **Dead letters rising:** classify by category, disable or pause outbound if user-visible duplication is possible, and communicate degraded Telegram delivery.
- **Unauthorized users spike:** check account-linking status and support documentation; do not bypass role checks.

## Secret and Token Rotation

### Webhook secret rotation

1. Generate a new webhook secret token.
2. Store the old token in `cadentia.telegram.webhook.previous-secret-token-ref` and the new token in `cadentia.telegram.webhook.secret-token-ref`.
3. Redeploy/reload Cadentia.
4. Refresh Telegram webhook registration with the new secret token.
5. Verify both old in-flight and new webhook deliveries are accepted during the overlap window.
6. Remove the previous secret reference after pending old deliveries expire.

### Bot token rotation

1. Disable or pause Telegram inbound if compromise is suspected.
2. Rotate the bot token with Telegram/BotFather.
3. Update `cadentia.telegram.webhook.bot-token-ref` in the secret manager.
4. Redeploy/reload and re-register the webhook.
5. Run smoke tests for `/start`, `/newsetlist`, confirmation, outbound send, and dead-letter absence.
6. Re-enable the channel when successful.

## Deregister the Webhook

Use this when rolling back the channel, changing public hosts, or stopping
Telegram delivery during an incident:

```bash
curl -sS -X POST "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/deleteWebhook" \
  -d "drop_pending_updates=false"
```

Set `drop_pending_updates=true` only when incident command explicitly accepts
losing queued Telegram updates. Preserve Cadentia retry/dead-letter records
according to retention policy.

## Rollback and Channel Disablement

- Prefer channel-specific disablement: set `cadentia.telegram.enabled=false` or use instance-level channel controls. Do not shut down the core API solely for Telegram issues.
- If outbound sends may duplicate user-visible messages, pause outbound before replaying retries.
- Deregister the Telegram webhook or point it at a maintenance endpoint if Cadentia cannot safely accept updates.
- Preserve retry/dead-letter records according to retention policy for later inspection.
- Direct users to the web/API flow during the outage.

## User-Support Scripts

Support staff should use short, sanitized scripts:

- **Unlinked account:** "Please link your Telegram account from Cadentia before using the bot. We cannot complete setlist generation from Telegram until the link is active."
- **Unauthorized role:** "Your Cadentia role does not allow Telegram setlist generation. Ask a Cadentia administrator to review your role."
- **Expired button:** "That Telegram button expired. Send `/status` or start again with `/newsetlist`."
- **Cancelled session:** "Your Telegram setlist session was cancelled. Send `/newsetlist` to begin again."
- **Delivery failure:** "Cadentia generated a response but Telegram delivery failed. Operators are reviewing the delivery record; please use the web flow if urgent."

## Known Limitations

- Automated tests use mocked Telegram API responses and synthetic fixtures only; they do not prove Telegram's public network availability.
- Telegram message rendering is intentionally concise and links operators back to Cadentia for final review/publishing.
- The Telegram adapter must not select songs; deterministic song selection remains in backend recommendation services.
- Identifier hashes support privacy but may complicate support lookup without approved operational tooling.
