# Telegram Channel Integration Contract v1

## Purpose and status

This document is the v1 implementation contract for ADR-035. It fixes the
Telegram channel boundary before webhook endpoints, adapter code, or operational
controls are implemented. The contract is intentionally channel-specific only at
the transport, rendering, and operations layers; all Cadentia domain decisions
remain owned by existing backend services.

## Non-negotiable ownership rule

The Telegram adapter is a channel adapter, not a recommendation path. The
following responsibilities remain backend/domain responsibilities and must not
be implemented in Telegram-specific code:

- LLM intent extraction and prompt construction.
- Intent JSON schema validation and slot normalization.
- Song selection, recommendation scoring, ordering, and tie-breaking.
- Catalog approval gating, eligibility filtering, and provenance validation.
- Recommendation explanation construction and evidence selection.
- Setlist persistence, audit history, and church-instance data ownership.

Telegram code may render the backend response in a Telegram-friendly format, but
it must not add, remove, reorder, or substitute recommended songs.

## Bounded responsibilities

| Boundary | Owns | May call | Must not call or own |
| --- | --- | --- | --- |
| Telegram adapter | Webhook verification result handling, Telegram update parsing, command dispatch, callback validation, normalization to `ChannelEvent`, menu rendering, response orchestration, channel metrics, disabled-channel responses. | Shared conversation/session facade, identity/authorization layer, outbound Telegram client, retry/dead-letter infrastructure, operational config registry. | LLM provider, Recommendation Engine internals, catalog repositories, approval mutation APIs, setlist persistence repositories, explanation builders. |
| Shared conversation/session facade | ADR-015 state machine, session creation/recovery, slot merge precedence, confirmation/cancellation/expiration transitions, deterministic validation errors. | Cadentia API application services, identity context, event publisher. | Telegram API methods, Telegram callback parsing, Telegram-specific persistence outside channel correlation fields. |
| Cadentia API | Authenticated setlist proposal workflow, intent orchestration, persistence boundaries, response contracts, audit emission. | Recommendation Engine, catalog/read models, explanation service, persistence services. | Telegram transport or Telegram secret handling. |
| Recommendation Engine | Approved-song candidate selection, scoring, ordering, deterministic constraints, explanation fact production inputs. | Approved catalog/read models and scoring policies. | Telegram user/chat identity, Telegram menu state, Telegram callbacks. |
| Identity/authorization layer | Account linking, church-instance resolution, role/permission checks, `/settings` gating, authorization failure contracts. | Configured identity provider, Cadentia user/church stores. | Telegram message rendering, recommendation logic. |
| Outbound Telegram client | `sendMessage`, `editMessageText`, `answerCallbackQuery`, Telegram API error mapping, Telegram rate-limit headers, redaction of secrets. | Retry/rate-limit infrastructure and Telegram HTTPS API. | Cadentia session mutation except through explicit delivery outcome events. |
| Retry/dead-letter infrastructure | Bounded retries, backoff, deduplication, dead-letter records, replay-safe delivery attempts. | Outbound Telegram client, event publisher, operator tooling. | Recommendation retries that re-run deterministic generation without session facade control. |
| Operational tooling | Webhook registration checks, channel enable/disable, health/status views, secret rotation workflow, smoke tests, DLQ inspection, alert triage. | Bot management API, configuration registry, metrics/logs/traces. | Direct data repair of recommendation/catalog state except through existing admin APIs. |

## Allowed and forbidden downstream boundaries

### Allowed adapter calls

The Telegram adapter may call only these downstream boundaries:

1. `ChannelIdentityResolver` to map Telegram sender/chat context to a Cadentia
   principal and church instance.
2. `ChannelAuthorizationService` to authorize command and callback execution.
3. `ConversationSessionFacade` to start, read, update, confirm, cancel, expire,
   and recover ADR-015 sessions.
4. `CadentiaSetlistProposalFacade` only through the shared conversation/API
   confirmation flow when the session is `READY_TO_CONFIRM` or `CONFIRMED`.
5. `TelegramOutboundClient` for Telegram API delivery.
6. `ChannelRetryPublisher` and `DeadLetterPublisher` for retryable delivery or
   processing failures.
7. `TelemetryRecorder` and `AuditEventPublisher` for bounded structured events.
8. `TelegramChannelConfigProvider` for resolved, redacted configuration values.

### Forbidden adapter calls

The Telegram adapter must never call these directly:

- LLM clients, prompt templates, or intent extraction providers.
- Recommendation Engine scoring, ranking, or candidate APIs.
- Catalog import, catalog approval, moderation, or raw song repository APIs.
- Setlist persistence repositories or write models.
- Explanation-construction internals.
- Secret manager APIs for raw token logging, export, or display.
- Any unauthenticated admin or support-only operation.

## Commands

Commands are case-insensitive after Telegram command parsing, but command names
are canonicalized to the exact values below. Unknown commands return
`COMMAND_UNSUPPORTED` and do not mutate session state.

| Command | Availability | Required permission | Behavior | Session effect | Outcome codes |
| --- | --- | --- | --- | --- | --- |
| `/start` | Always when channel enabled. | Linked or linkable Telegram identity. | Show onboarding, account-linking status, and primary actions. | Create or recover `START` session only if no active session exists. | `STARTED`, `LINK_REQUIRED`, `CHANNEL_DISABLED`. |
| `/help` | Always when channel enabled. | None beyond channel access. | Show supported commands and privacy-safe help. | No mutation. | `HELP_SHOWN`, `CHANNEL_DISABLED`. |
| `/newsetlist` | Enabled channel and authorized user. | `SETLIST_REQUEST_CREATE`. | Start a fresh ADR-015 setlist request flow and render first guided menu. | Existing active draft is superseded only after user confirmation or explicit `/cancel`; otherwise returns `ACTIVE_SESSION_EXISTS`. | `SESSION_STARTED`, `ACTIVE_SESSION_EXISTS`, `AUTH_REQUIRED`, `FORBIDDEN`. |
| `/status` | Enabled channel and authorized user. | `SETLIST_REQUEST_READ`. | Show current session state, last safe summary, and next available actions. | No mutation except expiration check. | `STATUS_SHOWN`, `NO_ACTIVE_SESSION`, `SESSION_EXPIRED`. |
| `/cancel` | Enabled channel and authorized user. | `SETLIST_REQUEST_CREATE`. | Cancel the active Telegram-correlated session. | Transition active session to `CANCELLED`. | `SESSION_CANCELLED`, `NO_ACTIVE_SESSION`. |
| `/settings` | Gated by `cadentia.telegram.features.settingsCommandEnabled`. | `CHANNEL_SETTINGS_READ`; writes require future explicit permission. | v1 read-only channel settings summary and account-link status. | No session mutation. | `SETTINGS_SHOWN`, `FEATURE_DISABLED`, `FORBIDDEN`. |

## Callback action vocabulary

Callback actions are closed vocabulary values. Free-form callback actions are
invalid and must return `CALLBACK_UNSUPPORTED` via `answerCallbackQuery` without
mutating session state.

| Action | ADR-015 mapping | Purpose |
| --- | --- | --- |
| `FLOW_START` | `START` -> `COLLECTING` | Begin guided collection from `/start` or `/newsetlist`. |
| `SET_SCRIPTURE_THEME` | scripture/theme slot collection | Open prompt/menu for scripture and theme input. |
| `SET_SHAPE_COUNTS` | shape/counts slot collection | Select or edit praise/worship counts. |
| `SET_LANGUAGE` | language slot collection | Select supported language/locale. |
| `SET_KEY_POLICY` | key policy slot collection | Select same-key, relative major/minor, and key-center policy presets. |
| `SET_TEMPO_POLICY` | tempo policy slot collection | Select maximum tempo jump policy preset. |
| `SET_ENERGY_ARC` | energy arc slot collection | Select energy arc preset. |
| `REVIEW_SUMMARY` | `READY_TO_CONFIRM` preparation | Render normalized request summary. |
| `CONFIRM_GENERATE` | `READY_TO_CONFIRM` -> `CONFIRMED` | Confirm generation through the shared facade. |
| `REVISE_REQUEST` | confirmation revision | Return to `COLLECTING` with revision context. |
| `CANCEL_FLOW` | any active state -> `CANCELLED` | Cancel active session. |
| `STATUS_REFRESH` | session read | Refresh status and available actions. |
| `ACK_ERROR` | no state transition | Acknowledge a validation or operational message. |

## Callback payload shape and constraints

Telegram callback data must be compact, versioned, and fully validated before
use. v1 payloads use pipe-delimited fields to stay below Telegram callback data
limits:

```text
ctg1|<action>|sid:<sessionRef>|rev:<revision>|nonce:<nonce>
```

Constraints:

- Maximum encoded payload length: 64 bytes.
- Prefix: `ctg1` exactly.
- `action`: one of the callback action vocabulary values above.
- `sessionRef`: opaque 8 to 16 character base62 correlation reference, never a
  database primary key and never a Telegram chat/user ID.
- `revision`: unsigned integer matching the expected session revision.
- `nonce`: opaque 6 to 12 character base62 anti-replay value tied to the
  rendered keyboard.
- Payloads must be rejected when expired, malformed, unsupported, replayed,
  associated with a different Telegram chat binding, or associated with a stale
  session revision.
- Human-readable labels are localization keys and are not encoded in callback
  data.

## Channel event shape

`ChannelEvent` is the normalized handoff from Telegram transport to shared
conversation orchestration. Field names are canonical for v1.

| Field | Required | Description |
| --- | --- | --- |
| `eventId` | Yes | Deterministic idempotency key, `telegram:update:<botInstanceId>:<updateId>`. |
| `channel` | Yes | Literal `telegram`. |
| `botInstanceId` | Yes | Internal bot/tenant instance identifier. |
| `churchInstanceId` | Yes after identity resolution | Cadentia church instance scope. |
| `telegramUpdateId` | Yes | Telegram update identifier. |
| `telegramChatRef` | Yes | Redacted/stable hash or opaque reference for chat correlation. |
| `telegramUserRef` | When present | Redacted/stable hash or opaque reference for user correlation. |
| `messageRef` | When present | Opaque Telegram message reference for edits/replies. |
| `callbackQueryRef` | For callbacks | Opaque callback query reference for acknowledgements. |
| `eventType` | Yes | `COMMAND`, `TEXT_MESSAGE`, `CALLBACK`, `DELIVERY_RETRY`, or `SYSTEM`. |
| `command` | For commands | One supported command, canonicalized. |
| `callbackAction` | For callbacks | One supported callback action. |
| `locale` | Optional | Resolved UI locale, defaulting to church instance locale. |
| `receivedAt` | Yes | Server receive timestamp. |
| `idempotencyKey` | Yes | Same as or derived from `eventId`. |
| `correlationId` | Yes | Trace/session correlation id propagated to downstream calls. |
| `rawTextClass` | Optional | Redacted classifier such as `EMPTY`, `COMMAND`, `FREE_TEXT`, `TOO_LONG`; raw text is not logged. |
| `safeText` | Optional | Sanitized user text only when required for intent collection and allowed by retention policy. |

## Session state and correlation fields

The Telegram adapter uses the shared ADR-015 states only:

- `START`
- `COLLECTING`
- `CLARIFICATION_REQUIRED`
- `READY_TO_CONFIRM`
- `CONFIRMED`
- `EXPIRED`
- `CANCELLED`

Telegram session records may add only these channel correlation fields:

| Field | Purpose |
| --- | --- |
| `sessionId` | Shared session identifier owned by the conversation/session facade. |
| `sessionRef` | Short opaque callback reference for inline keyboards. |
| `channel` | Literal `telegram`. |
| `botInstanceId` | Bot instance that owns the session. |
| `churchInstanceId` | Authorized church scope. |
| `principalId` | Cadentia principal after account linking. |
| `telegramChatRef` | Redacted chat correlation reference. |
| `telegramUserRef` | Redacted user correlation reference when available. |
| `state` | One shared ADR-015 state. |
| `revision` | Monotonic session revision used in callback validation. |
| `lastInboundEventId` | Last accepted channel event id. |
| `lastOutboundMessageRef` | Opaque message reference for safe edits. |
| `createdAt` | Creation timestamp. |
| `lastActivityAt` | Last accepted inbound or state-changing outbound timestamp. |
| `inactiveExpiresAt` | Inactivity expiration timestamp. |
| `absoluteExpiresAt` | Absolute expiration timestamp. |
| `cancelledAt` | Cancellation timestamp, when applicable. |
| `expiredAt` | Expiration timestamp, when applicable. |

## Menu state mapping

| ADR-015 state | Telegram rendering |
| --- | --- |
| `START` | Onboarding/help card with `FLOW_START` and `/newsetlist` guidance. |
| `COLLECTING` | Guided menu with slot actions for scripture/theme, counts, language, key policy, tempo policy, and energy arc. |
| `CLARIFICATION_REQUIRED` | Clarification prompt with constrained choices and `REVISE_REQUEST`/`CANCEL_FLOW`. |
| `READY_TO_CONFIRM` | Normalized request summary with `CONFIRM_GENERATE`, `REVISE_REQUEST`, and `CANCEL_FLOW`. |
| `CONFIRMED` | Backend-generated proposal rendering with status and next-step actions. |
| `EXPIRED` | Expiration notice with safe summary and `FLOW_START` restart option. |
| `CANCELLED` | Cancellation acknowledgement with `/newsetlist` restart guidance. |

## Disabled-channel behavior

When `cadentia.telegram.enabled=false` or a church instance is not included in
`cadentia.telegram.enabledChurchInstanceIds`, webhook verification and basic
idempotency checks may still run, but no domain workflow is invoked. The adapter
returns an accepted webhook response to Telegram, emits
`telegram.channel.disabled` telemetry, and sends at most one localized
maintenance message per configured cooldown. Disabled-channel handling must not
leak whether a specific Telegram user is linked to a Cadentia account.

## Configuration registry

Configuration keys are names, not secret values. Secret values are stored only
in the deployment secret manager.

| Key | Default | Description |
| --- | --- | --- |
| `cadentia.telegram.enabled` | `false` | Master channel enablement. |
| `cadentia.telegram.botTokenSecretRef` | none | Secret-manager reference for the Telegram bot token. |
| `cadentia.telegram.webhookSecretTokenSecretRef` | none | Secret-manager reference for Telegram webhook secret-token verification. |
| `cadentia.telegram.webhookPublicUrl` | none | Public HTTPS webhook URL. Shape: `https://<public-host>/api/v1/channels/telegram/{botInstanceId}/webhook`. |
| `cadentia.telegram.enabledChurchInstanceIds` | empty list | Explicit church instances allowed to use Telegram. Empty means disabled for all churches. |
| `cadentia.telegram.localDevelopmentMode` | `webhook` | Allowed values: `webhook`, `polling`. `polling` is local-only and must be rejected in production profiles. |
| `cadentia.telegram.retry.maxAttempts` | `5` | Maximum outbound Telegram delivery attempts. |
| `cadentia.telegram.retry.initialBackoffMs` | `500` | Initial retry backoff for retryable sends. |
| `cadentia.telegram.retry.maxBackoffMs` | `30000` | Maximum retry backoff. |
| `cadentia.telegram.retry.deadLetterAfterAttempts` | `5` | Attempts before dead-lettering. |
| `cadentia.telegram.rateLimit.perChatPerMinute` | `20` | Per-chat outbound budget. |
| `cadentia.telegram.rateLimit.globalPerSecond` | `25` | Global outbound budget below Telegram hard limits. |
| `cadentia.telegram.session.inactivityLifetime` | `PT30M` | Default inactivity lifetime. |
| `cadentia.telegram.session.absoluteLifetime` | `PT24H` | Default absolute lifetime. |
| `cadentia.telegram.rendering.explanationPolicy` | `SUMMARY_WITH_LINK` | v1 explanation rendering policy. |

### Feature flags

| Flag | Default | Description |
| --- | --- | --- |
| `cadentia.telegram.features.webhookEnabled` | `true` | Enables production webhook intake when channel is enabled. |
| `cadentia.telegram.features.localPollingEnabled` | `false` | Allows local long polling only in local development mode. |
| `cadentia.telegram.features.settingsCommandEnabled` | `false` | Enables gated `/settings`. |
| `cadentia.telegram.features.inlineKeyboardsEnabled` | `true` | Enables callback-based guided menus. |
| `cadentia.telegram.features.accountLinkingRequired` | `true` | Requires linked Cadentia identity before protected workflows. |
| `cadentia.telegram.features.explanationDeepLinksEnabled` | `true` | Allows linking to full web/API explanation view when available. |
| `cadentia.telegram.features.deliveryDeadLetterEnabled` | `true` | Enables DLQ after retry exhaustion. |

## Local polling policy

Production uses webhooks. Long polling is allowed only for local development
when all of the following are true:

1. Runtime profile is local/development.
2. `cadentia.telegram.localDevelopmentMode=polling`.
3. `cadentia.telegram.features.localPollingEnabled=true`.
4. No production webhook registration is active for the same bot token.

Polling must use the same normalization, identity, authorization, session, and
outbound rendering paths as webhooks. Polling must not introduce a separate
recommendation path and must not be accepted in production configuration.

## Session lifetime policy

- Default inactivity lifetime: 30 minutes (`PT30M`) from `lastActivityAt`.
- Default absolute lifetime: 24 hours (`PT24H`) from `createdAt`.
- Expiration is checked before every protected command or callback.
- Expired sessions transition to `EXPIRED`; they are not silently reused.
- `/status` may show an expired-safe summary but must offer restart rather than
  generation.

## Explanation rendering policy for v1

`SUMMARY_WITH_LINK` is the v1 policy:

- Telegram renders a concise backend-provided summary: set title, ordered songs,
  key/tempo highlights, and a bounded list of top explanation reasons.
- Full explanation details, audit evidence, and lengthy provenance are linked to
  an authenticated Cadentia web/API view when available.
- If a link is unavailable, Telegram renders only the backend summary and a safe
  message that detailed explanations are unavailable in this channel.
- The adapter must not synthesize new explanation reasons or expose raw private
  review notes.

## ADR-035 open-question resolutions

| Open question | v1 resolution |
| --- | --- |
| Should local development support Telegram long polling? | Yes, but only behind local-only configuration and feature flag. Production remains webhook-only. |
| Which identity provider or account-linking flow maps Telegram users to Cadentia roles? | Self-service onboarding: unauthorized users request access from the bot ("Request access" button or `/requestaccess`), the request is stored PENDING in `telegram_access_request` (raw chat ID purged on decision), an administrator approves/rejects at `/admin/telegram-access` (`MANAGE_TELEGRAM_ACCESS`), and approval creates a `LINKED` identity with the default `ROLE_WORSHIP_LEADER` role and pushes the decision to the requester. Protected commands still return an authorization failure until approval completes. |
| How much explanation payload is rendered directly in Telegram? | Use `SUMMARY_WITH_LINK`: concise backend summary in Telegram and authenticated deep link for full explanation when enabled. |
| What are default session lifetimes? | Inactivity `PT30M`; absolute `PT24H`; expiration is explicit and recoverable but stale sessions cannot generate recommendations. |

## Observability and validation requirements

- Metrics must use bounded labels: command, callback action, outcome code,
  state, church instance, bot instance, and failure class.
- Logs and traces must include `correlationId`, `eventId`, `sessionId` or
  `sessionRef`, outcome code, and redacted Telegram references.
- Logs, metrics, fixtures, and examples must not include bot tokens, webhook
  secrets, raw Telegram personal data, or unnecessary raw message text.
- Contract tests must reject unsupported commands, unsupported callbacks,
  malformed callback payloads, stale revisions, expired nonces, disabled-channel
  protected operations, and production polling configuration.
