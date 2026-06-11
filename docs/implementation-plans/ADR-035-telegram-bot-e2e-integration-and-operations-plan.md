# ADR-035 Implementation Plan: Telegram Bot E2E Integration and Operations

## Objective

Implement a production-ready Telegram bot channel that receives verified
Telegram webhook updates, maps them into Cadentia's shared conversational
workflow, invokes existing API/domain boundaries for intent extraction,
recommendations, explanations, and persistence, and sends safe Telegram-friendly
responses without allowing the bot adapter to select songs or bypass approval,
authorization, observability, or operational controls.

## Source ADR

- [ADR-035: Telegram Bot E2E Integration and Operations](../adr/ADR-035-telegram-bot-e2e-integration-and-operations.md)

## Status

Overall status: Planned.

- Subtask 1: Planned - Telegram integration contract, ownership boundaries,
  configuration model, and open questions.
- Subtask 2: Planned - OpenAPI-first webhook, channel-session, and bot
  management API contract.
- Subtask 3: Planned - secret management, webhook authentication,
  idempotency, replay protection, and request validation.
- Subtask 4: Planned - Telegram update normalization, command routing, callback
  handling, and ADR-015 guided menu mapping.
- Subtask 5: Planned - identity/account linking, authorization, privacy, and
  session persistence.
- Subtask 6: Planned - shared conversation/API facade integration and
  deterministic setlist proposal flow.
- Subtask 7: Planned - Telegram response rendering, outbound send pipeline,
  retries, rate limits, and dead-letter handling.
- Subtask 8: Planned - observability, audit events, operational controls, and
  alerting.
- Subtask 9: Planned - end-to-end testing, fixtures, deployment rollout,
  runbook updates, and support documentation.

## Guiding Principles

- Telegram-specific code owns transport concerns only: webhook verification,
  Telegram payload parsing, command and callback routing, menu rendering,
  outbound Telegram API calls, retry handling, and channel telemetry.
- The bot adapter must call the same Cadentia conversation/session and setlist
  proposal APIs used by other channels; it must not implement its own intent
  extraction, song selection, catalog filtering, approval mutation, or scoring
  logic.
- All API additions must be designed in the OpenAPI split contract first, with
  path operations in `cadentia-api.paths.yaml`, reusable schemas/responses in
  `cadentia-api.components.yaml`, top-level references/tags in
  `cadentia-api.yaml`, and generated sources refreshed afterward.
- Webhook requests must be authenticated with Telegram secret-token validation,
  validated before domain processing, idempotent by update identity, safe to
  retry, and observable without logging bot tokens or unnecessary personal data.
- Telegram chat and user identifiers are channel identity inputs, not catalog or
  recommendation inputs. Authorization and church-instance scoping must be
  resolved before protected Cadentia workflows are invoked.
- Active confirmation flows must survive process restarts through persisted or
  correlated session state, with explicit inactivity and absolute lifetime
  behavior.
- Telegram messages must be concise, deterministic renderings of approved
  backend responses and must respect Telegram message length, callback payload,
  Markdown/HTML escaping, and redaction constraints.
- Operators must be able to register, validate, monitor, rotate, disable, and
  troubleshoot the Telegram channel without disabling the core Cadentia API.

## Subtask 1: Define Telegram integration contract, ownership boundaries, configuration model, and open questions

### Context

ADR-035 makes Telegram a first-class channel adapter around existing Cadentia
API and conversation services. The current codebase contains bot scaffolding,
but the adapter must not become a separate recommendation path. Before endpoint
or adapter implementation starts, the project needs a durable integration
contract that fixes channel ownership, configuration fields, supported commands,
callback semantics, lifecycle states, and deferred choices such as local polling
support, identity provider behavior, explanation rendering depth, and session
lifetimes.

**Codebase anchors**

- Source ADR in
  `docs/adr/ADR-035-telegram-bot-e2e-integration-and-operations.md`
- Guided menu and conversational flow plan in
  `docs/implementation-plans/ADR-015-guided-menu-and-conversational-request-flow-plan.md`
- Security roles and permissions plan in
  `docs/implementation-plans/ADR-019-security-roles-and-permissions-plan.md`
- External integration boundaries plan in
  `docs/implementation-plans/ADR-020-external-integration-boundaries-plan.md`
- Eventing and async processing plan in
  `docs/implementation-plans/ADR-028-eventing-and-async-processing-architecture-plan.md`
- Observability strategy plan in
  `docs/implementation-plans/ADR-029-observability-and-telemetry-strategy-plan.md`
- Telegram operations runbook in
  `docs/runbooks/adr-035-telegram-bot-operations.md`

### Prompt

Create the v1 Telegram channel integration contract. Define the bounded
responsibilities of the Telegram adapter, shared conversation/session facade,
Cadentia API, Recommendation Engine, identity/authorization layer, outbound
Telegram client, retry/dead-letter infrastructure, and operational tooling.
Specify supported commands (`/start`, `/help`, `/newsetlist`, `/status`,
`/cancel`, and gated `/settings`), callback action names, callback payload
shape, channel event fields, session state fields, configuration keys,
feature-flag names, token/secret references, webhook URL shape, polling policy
for local development, default inactivity lifetime, default absolute lifetime,
and explanation rendering policy for v1.

### Acceptance criteria

- A durable document, code contract, or configuration registry defines the v1
  Telegram channel ownership boundaries and confirms that song selection,
  approval gating, LLM intent extraction, recommendation scoring, explanation
  construction, and setlist persistence remain backend/domain responsibilities.
- The supported command list, callback action vocabulary, callback payload
  constraints, menu state mapping, channel event shape, session correlation
  fields, and disabled-channel behavior are explicitly defined.
- Telegram-specific configuration names are documented for bot token secret
  reference, webhook secret reference, webhook public URL, enabled church
  instances, local development mode, retry policy, rate-limit policy, session
  lifetimes, and feature flags.
- ADR-035 open questions are resolved for v1 or intentionally deferred with
  safe defaults that do not block webhook operation, user authorization, or
  deterministic recommendation behavior.
- The contract identifies every downstream API or domain boundary the adapter is
  allowed to call and every boundary it is forbidden to call directly.

### Restrictions

- Do not implement Telegram-specific recommendation, LLM prompt, catalog
  approval, or song filtering logic in the channel contract.
- Do not define free-form callback values, session states, or command outcomes
  that cannot be validated, tested, localized, observed, or mapped back to
  ADR-015.
- Do not store bot tokens, webhook secrets, or raw unnecessary Telegram personal
  data in configuration files, fixtures, logs, metrics, or documentation
  examples.
- Do not make local long polling a production default unless a later ADR changes
  the webhook decision.

## Subtask 2: Design OpenAPI-first webhook, channel-session, and bot management API contract

### Context

ADR-035 requires a webhook endpoint and may require supporting surfaces for bot
status, channel disablement, account linking, session inspection, and settings.
Cadentia's OpenAPI contract is intentionally split across three files, and any
API change must update the OpenAPI specification before implementation. The
contract must keep Telegram transport details explicit while reusing shared
Cadentia response and error conventions.

**Codebase anchors**

- OpenAPI aggregate entrypoint under `apps/api/src/main/openapi/cadentia-api.yaml`
- OpenAPI path definitions under
  `apps/api/src/main/openapi/cadentia-api.paths.yaml`
- OpenAPI reusable components under
  `apps/api/src/main/openapi/cadentia-api.components.yaml`
- API application code under `apps/api/src/main/java/com/cadentia/`
- Security roles and permissions plan in
  `docs/implementation-plans/ADR-019-security-roles-and-permissions-plan.md`
- Observability strategy plan in
  `docs/implementation-plans/ADR-029-observability-and-telemetry-strategy-plan.md`

### Prompt

Update the OpenAPI contract first for all required Telegram surfaces. Add the
Telegram webhook path item, request headers, secret-token verification header,
Telegram update request schema, accepted response schema, structured validation
error responses, authentication/authorization requirements for administrative
management endpoints, bot status response, channel enable/disable operation if
needed, account-linking initiation/confirmation operations if needed, and
session status/cancellation operations if they are not already covered by
shared conversation APIs. Then run the OpenAPI generator and update generated
interfaces/models before implementing controllers or handlers.

### Acceptance criteria

- Every new or changed API operation is first represented in the split OpenAPI
  contract with path definitions in `cadentia-api.paths.yaml`, reusable schemas,
  parameters, security schemes, and responses in `cadentia-api.components.yaml`,
  and aggregate tags or indexes in `cadentia-api.yaml` where required.
- The webhook contract includes Telegram update payload structure, the
  Telegram secret-token header, idempotent accepted semantics, validation
  failures, forbidden/unauthorized failures, retry-safe server failures, and
  redacted error response shapes.
- Any account-linking, bot status, channel settings, session status, or
  cancellation API additions explicitly state authorization requirements,
  church-instance scoping, and expected response codes.
- Generated API interfaces/models are refreshed with
  `mvn -pl apps/api -DskipTests generate-sources`, and generated changes are
  reviewed for naming, validation, and compatibility.
- Contract tests or generated-spec validation prove the aggregate spec resolves
  and the webhook/management surfaces are discoverable by API tooling.

### Restrictions

- Do not add controllers, request DTOs, or API clients for Telegram surfaces
  before the OpenAPI contract is updated and generated artifacts are refreshed.
- Do not collapse the split OpenAPI files or use large inline JSON-style schema
  blocks when reusable expanded YAML components are appropriate.
- Do not expose bot token values, webhook secret values, raw Telegram user
  profile details, or cross-instance diagnostics in response schemas.
- Do not mark webhook requests as synchronously completing full setlist
  generation if the implementation accepts, queues, or retries work
  asynchronously.

## Subtask 3: Implement secret management, webhook authentication, idempotency, replay protection, and request validation

### Context

Telegram webhook delivery must be verified before Cadentia processes updates.
The adapter must authenticate the `X-Telegram-Bot-Api-Secret-Token` header,
validate payload structure, avoid double-processing repeated update IDs, and
emit structured failures that Telegram can safely retry. Credentials must be
loaded from deployment secrets and never logged.

**Codebase anchors**

- API application code under `apps/api/src/main/java/com/cadentia/`
- API configuration under `apps/api/src/main/resources/`
- OpenAPI contract under `apps/api/src/main/openapi/`
- Security roles and permissions plan in
  `docs/implementation-plans/ADR-019-security-roles-and-permissions-plan.md`
- Eventing and async processing plan in
  `docs/implementation-plans/ADR-028-eventing-and-async-processing-architecture-plan.md`
- Observability strategy plan in
  `docs/implementation-plans/ADR-029-observability-and-telemetry-strategy-plan.md`

### Prompt

Implement the webhook ingress security and validation layer. Load bot token and
webhook secret from the configured secret provider or environment-backed secret
references. Validate the secret-token header using constant-time comparison,
reject missing or invalid headers before parsing domain data, validate payload
shape against the generated OpenAPI model and additional Telegram invariants,
record/update idempotency state by Telegram update ID and bot/channel, detect
stale or duplicate updates, and return retry-safe structured responses. Add
safe logging fields for outcome, update ID when available, request ID,
correlation ID, bot/channel identifier, and failure category.

### Acceptance criteria

- Webhook requests with missing, invalid, blank, or rotated-out secret tokens
  are rejected before domain processing, and failure logs do not include secret
  material or raw payload bodies.
- Valid webhook updates are accepted exactly once per update ID and bot/channel,
  while duplicate deliveries return a deterministic already-accepted outcome
  without rerunning command or conversation side effects.
- Malformed, unsupported, stale, or oversized payloads produce structured
  validation outcomes and metrics without leaking raw Telegram personal data.
- Secret references support rotation with an explicit overlap or deployment
  procedure, and tests cover current, previous, invalid, and absent secret
  cases.
- Unit and integration tests cover header verification, idempotency,
  replay/staleness rules, validation errors, retryable failures, and safe log
  redaction.

### Restrictions

- Do not log bot tokens, webhook secrets, authorization headers, raw update
  payloads, message text beyond approved sanitized summaries, or unnecessary
  Telegram names/usernames.
- Do not rely only on network location, obscured URLs, or bot token path
  segments as webhook authentication.
- Do not process duplicate update IDs in a way that sends duplicate messages,
  creates duplicate sessions, or generates duplicate recommendations.
- Do not let validation failures enter the LLM, Recommendation Engine, catalog,
  or setlist persistence layers.

## Subtask 4: Implement Telegram update normalization, command routing, callback handling, and ADR-015 guided menu mapping

### Context

Telegram delivers different update shapes for messages, commands, callback
queries, edited messages, and unsupported events. ADR-035 requires commands and
inline keyboard/menu callbacks for ADR-015 guided fields, while ADR-015 owns the
shared conversational state machine. The adapter must normalize Telegram events
into channel-neutral inputs and ignore or safely reject unsupported events.

**Codebase anchors**

- Bot adapter scaffolding under the existing bot package in
  `apps/api/src/main/java/com/cadentia/`
- Guided menu and conversational flow plan in
  `docs/implementation-plans/ADR-015-guided-menu-and-conversational-request-flow-plan.md`
- LLM intent extraction contract plan in
  `docs/implementation-plans/ADR-012-llm-intent-extraction-contract-plan.md`
- Recommendation explainability plan in
  `docs/implementation-plans/ADR-021-recommendation-engine-explainability-api-plan.md`

### Prompt

Replace the placeholder Telegram adapter with normalization and routing logic.
Parse message text, bot commands, callback queries, chat identifiers, user
identifiers, language hints, message IDs, and callback message references into a
validated channel event. Route `/start`, `/help`, `/newsetlist`, `/status`,
`/cancel`, and enabled `/settings` commands to the shared conversation/session
facade. Map callback actions for scripture/theme, shape/counts, language, key
policy, tempo policy, energy arc, confirmation, revision, and cancellation into
ADR-015-compatible menu selections. Return safe responses for unsupported
commands, unsupported update types, disabled settings, stale callbacks, and
invalid callback payloads.

### Acceptance criteria

- Telegram messages and callback queries are normalized into one channel event
  model with stable command, callback action, text input, chat ID, user ID,
  message ID, update ID, locale/language hint, and correlation fields.
- Supported commands route to the shared session/conversation facade and produce
  deterministic command outcomes for started, continued, already-active,
  cancelled, completed, unauthorized, disabled, and invalid states.
- Callback payloads are validated against the v1 action vocabulary and mapped to
  ADR-015 guided-flow fields without free-form parsing or Telegram-specific
  state-machine forks.
- Unsupported update types and commands are acknowledged or rejected according
  to the contract without invoking intent extraction, recommendation generation,
  or catalog reads.
- Tests cover command routing, callback routing, stale callback handling,
  unsupported events, invalid payloads, disabled settings, and locale/language
  propagation.

### Restrictions

- Do not parse a Telegram text message directly into songs, catalog filters, or
  Recommendation Engine inputs outside the shared intent/session workflow.
- Do not create a Telegram-only conversation state machine that can diverge from
  ADR-015.
- Do not accept callback payloads that exceed Telegram limits, contain
  unvalidated JSON, or include sensitive session data that would be visible to
  Telegram clients.
- Do not treat edited/deleted Telegram messages as authoritative changes to
  confirmed Cadentia requests unless a later contract explicitly supports that
  behavior.

## Subtask 5: Implement identity/account linking, authorization, privacy, and session persistence

### Context

ADR-035 requires mapping Telegram chat/user identifiers to Cadentia user/session
identity without storing bot tokens or unnecessary personal data in logs.
Protected setlist generation and settings operations require church-instance
scoping and role checks. Active confirmation flows must survive restarts and
must expire predictably.

**Codebase anchors**

- Security roles and permissions plan in
  `docs/implementation-plans/ADR-019-security-roles-and-permissions-plan.md`
- Packaged deployment and church customization plan in
  `docs/implementation-plans/ADR-022-packaged-deployment-and-church-customization-model-plan.md`
- Setlist persistence and versioning plan in
  `docs/implementation-plans/ADR-016-setlist-persistence-and-versioning-plan.md`
- API application code under `apps/api/src/main/java/com/cadentia/`

### Prompt

Implement the Telegram identity and session persistence layer. Define how a
Telegram chat/user pair is linked to a Cadentia actor and church instance,
including unlinked-user onboarding, account-link initiation, account-link
confirmation, role lookup, disabled-instance behavior, and revocation. Persist
bot session records or correlation records with channel, chat/user identity
hashes or encrypted references, church instance, actor, current conversation
state, pending confirmation metadata, last update ID, last message reference,
created/updated timestamps, inactivity deadline, absolute expiration, and audit
metadata. Enforce authorization before invoking protected conversation,
settings, setlist, or status operations.

### Acceptance criteria

- Telegram users can be recognized as linked, unlinked, revoked, disabled, or
  unauthorized, and each state has a deterministic safe response.
- Session storage survives application restarts and can resume, cancel, expire,
  or complete active `/newsetlist` flows without losing confirmation context.
- Church-instance authorization is enforced for all Telegram-triggered protected
  actions, including settings, setlist proposal generation, session status, and
  cancellation.
- Stored Telegram identifiers are minimized, hashed or encrypted according to
  the security contract, and excluded or redacted from logs, metrics, errors,
  and audit views unless explicitly permitted.
- Tests cover linked and unlinked users, cross-instance denial, revoked access,
  disabled bot/channel configuration, session resume after restart, expiration,
  cancellation, and privacy-safe logging.

### Restrictions

- Do not assume a Telegram user is authorized because they know the bot username,
  appear in a chat, or previously sent a valid update.
- Do not persist raw unnecessary Telegram names, usernames, profile fields,
  message text, or callback payloads when a stable pseudonymous reference is
  sufficient.
- Do not let an unlinked or unauthorized user invoke the LLM, Recommendation
  Engine, protected catalog reads, setlist persistence, or church settings.
- Do not make session expiration silently generate or confirm setlists; expired
  sessions must require an explicit new flow or restart path.

## Subtask 6: Integrate the shared conversation/API facade and deterministic setlist proposal flow

### Context

The ADR requires that the same user request routed through Telegram and the HTTP
API reaches the same validated intent/session workflow and deterministic
Recommendation Engine behavior. Telegram must invoke shared APIs/facades for
intent extraction, slot validation, clarification, confirmation,
recommendation, explanations, and persistence rather than duplicating domain
logic.

**Codebase anchors**

- Guided menu and conversational flow plan in
  `docs/implementation-plans/ADR-015-guided-menu-and-conversational-request-flow-plan.md`
- LLM intent extraction contract plan in
  `docs/implementation-plans/ADR-012-llm-intent-extraction-contract-plan.md`
- Recommendation scoring plan in
  `docs/implementation-plans/ADR-010-recommendation-engine-scoring-architecture-plan.md`
- Recommendation explainability plan in
  `docs/implementation-plans/ADR-021-recommendation-engine-explainability-api-plan.md`
- Setlist persistence and versioning plan in
  `docs/implementation-plans/ADR-016-setlist-persistence-and-versioning-plan.md`

### Prompt

Wire Telegram channel events into the shared conversation/session facade. Ensure
`/newsetlist` starts the same request collection flow as other channels,
free-text scripture/theme input is passed only to the approved intent extraction
boundary, menu selections update the same slot model, validation errors return
through the same conversation state, confirmation invokes the backend setlist
proposal API, and accepted proposals are persisted through the existing setlist
persistence boundary when configured. Add comparison fixtures that send the
same normalized request through Telegram and HTTP/API paths and verify matching
validated intent, constraint policy, candidate eligibility, recommendation
ordering, and explanation references for the same catalog and policy snapshots.

### Acceptance criteria

- Telegram `/newsetlist` can complete from start through clarification,
  confirmation, deterministic recommendation response, and optional persistence
  by invoking shared Cadentia services.
- The same normalized request, actor, church instance, catalog snapshot, and
  policy snapshot produce the same validated intent/session state and
  Recommendation Engine output whether the request enters through Telegram or
  the HTTP/API flow.
- Telegram adapter code has no direct song-selection, scoring, approval-gate,
  catalog mutation, or LLM prompt-construction logic beyond invoking the
  authorized shared boundaries.
- Clarification prompts, validation errors, safe rejections, cancellation,
  revision, and status requests preserve ADR-015 state semantics.
- Tests cover successful flow, clarification-required flow, validation failure,
  safe rejection, cancellation before confirmation, revision after confirmation
  prompt, deterministic equivalence with HTTP/API, and persistence boundary
  invocation.

### Restrictions

- Do not call the Recommendation Engine directly from Telegram code if the
  approved path is through a shared API/facade that enforces validation,
  authorization, approval gates, and explanations.
- Do not allow Telegram-specific defaults to change counts, key policy, tempo
  policy, energy arc, language, or approval constraints unless those defaults
  are represented in the shared request/session contract.
- Do not persist a setlist from Telegram without the same confirmation and
  authorization checks used by other channels.
- Do not expose unapproved catalog data, rejected candidate details, hidden
  scoring internals, or cross-instance evidence through Telegram responses.

## Subtask 7: Build Telegram response rendering, outbound send pipeline, retries, rate limits, and dead-letter handling

### Context

Telegram has message length limits, formatting rules, callback keyboard limits,
API rate limits, and transient delivery failures. ADR-035 requires rendering
setlist proposals, clarification prompts, validation errors, safe rejections,
and operational failures in Telegram-friendly messages, plus retry,
rate-limit, and dead-letter handling for outbound sends.

**Codebase anchors**

- Eventing and async processing plan in
  `docs/implementation-plans/ADR-028-eventing-and-async-processing-architecture-plan.md`
- Observability strategy plan in
  `docs/implementation-plans/ADR-029-observability-and-telemetry-strategy-plan.md`
- Recommendation explainability plan in
  `docs/implementation-plans/ADR-021-recommendation-engine-explainability-api-plan.md`
- API application code under `apps/api/src/main/java/com/cadentia/`

### Prompt

Implement the response renderer and outbound Telegram client boundary. Convert
shared conversation responses into Telegram messages, inline keyboards, callback
query acknowledgements, status summaries, error messages, and setlist proposal
summaries. Enforce message length limits through deterministic splitting or
summarization, escape Telegram Markdown/HTML safely, include concise approved
explanation/evidence references, and redact sensitive details. Implement
outbound send records with idempotency keys, retry policy, backoff, rate-limit
handling, Telegram error categorization, dead-letter storage, and operator-safe
inspection metadata.

### Acceptance criteria

- All supported conversation response types render into Telegram-safe messages
  or callback acknowledgements with deterministic ordering, safe escaping,
  length handling, and concise user guidance.
- Setlist proposal responses include recommended songs and approved explanatory
  references supplied by backend APIs, while preserving approval gates and
  omitting unapproved candidate details.
- Outbound sends use idempotency/correlation to prevent duplicate user-visible
  messages during retry, webhook redelivery, or worker restart.
- Retry logic distinguishes retryable network/Telegram 5xx/rate-limit failures
  from permanent chat blocked, invalid chat, malformed request, unauthorized
  bot, or disabled-channel failures, and stores dead-lettered records for
  permitted operator review.
- Tests cover rendering for each command outcome, clarification prompt,
  validation error, proposal, cancellation, operational failure, length
  splitting, formatting escaping, retry success, rate limit, permanent failure,
  and dead-letter behavior.

### Restrictions

- Do not include bot tokens, webhook secrets, raw prompt text, raw lyrics,
  unapproved catalog details, hidden scoring internals, or private user data in
  rendered messages, outbound records, errors, or dead-letter inspection views.
- Do not allow retry loops to send duplicate recommendations or repeated
  confirmations for the same accepted update.
- Do not rely on Telegram as the source of durable setlist persistence; use
  Cadentia persistence boundaries.
- Do not render explanations that contradict or expand beyond the approved
  backend explanation payload.

## Subtask 8: Add observability, audit events, operational controls, and alerting

### Context

Operators need to understand webhook verification failures, command outcomes,
session transitions, API failures, Telegram send failures, rate limits, and
stuck sessions without seeing credentials or unnecessary personal data. ADR-035
also requires disabling the Telegram channel without disabling the core API.
These controls should align with the broader observability and security plans.

**Codebase anchors**

- Observability strategy plan in
  `docs/implementation-plans/ADR-029-observability-and-telemetry-strategy-plan.md`
- Security roles and permissions plan in
  `docs/implementation-plans/ADR-019-security-roles-and-permissions-plan.md`
- Eventing and async processing plan in
  `docs/implementation-plans/ADR-028-eventing-and-async-processing-architecture-plan.md`
- Telegram operations runbook in
  `docs/runbooks/adr-035-telegram-bot-operations.md`

### Prompt

Instrument the Telegram channel. Emit structured logs, metrics, traces, and
audit events for webhook receipt, verification outcome, update normalization,
command routing, callback routing, identity state, authorization outcome,
session transition, downstream API/facade call, recommendation completion,
outbound send attempt, retry, rate-limit response, dead-letter creation, channel
disablement, settings change, account-link event, secret rotation, and webhook
registration check. Add operational controls for enabling/disabling the channel
per deployment and per church instance, draining or pausing outbound sends, and
surfacing bot health/status to authorized operators.

### Acceptance criteria

- Metrics and structured logs use stable names and include safe dimensions for
  channel, operation, outcome, latency, retry count, session state, error
  category, and redacted instance/actor references where permitted.
- Audit events are emitted for privileged bot settings changes, account linking
  and unlinking, channel enablement/disablement, secret rotation events,
  webhook registration changes, and operator dead-letter actions.
- Alerts or alert-ready metric thresholds are defined for webhook verification
  spikes, sustained Telegram send failures, rate-limit saturation, dead-letter
  growth, session transition failures, API/facade failures, and disabled-channel
  mismatches.
- Operators can disable the Telegram channel, disable it for a church instance,
  pause outbound sends, inspect safe health/status, and verify the current
  webhook registration without interrupting the core API.
- Tests or observability fixtures verify emitted metric/log/audit names,
  redaction behavior, and disabled-channel behavior.

### Restrictions

- Do not expose raw Telegram payloads, message text, bot credentials, webhook
  secrets, access tokens, raw chat/user identifiers, or cross-instance data in
  logs, metrics, traces, audit views, alerts, or health endpoints.
- Do not make operational status endpoints public or unauthenticated.
- Do not couple Telegram channel disablement to global API shutdown or
  Recommendation Engine availability.
- Do not create high-cardinality metrics from raw chat IDs, user IDs, message
  IDs, session IDs, or free-form errors.

## Subtask 9: Complete end-to-end testing, fixtures, deployment rollout, runbook updates, and support documentation

### Context

ADR-035 is complete only when a configured Telegram bot can complete a
`/newsetlist` flow end to end and operators can register, validate,
troubleshoot, rotate, and disable the channel. The repository already contains a
minimum Telegram operations runbook that must be expanded as implementation
details become concrete.

**Codebase anchors**

- Telegram operations runbook in
  `docs/runbooks/adr-035-telegram-bot-operations.md`
- API application tests under `apps/api/src/test/`
- OpenAPI contract under `apps/api/src/main/openapi/`
- Docker/deployment/configuration files in the repository as applicable
- Existing implementation plans for ADR-015, ADR-019, ADR-028, and ADR-029

### Prompt

Build the verification and rollout package for the Telegram channel. Add
fixture-based tests for Telegram webhook payloads, command flows, callback
flows, duplicate updates, authorization states, session persistence, outbound
send retries, and Telegram/API equivalence. Add an end-to-end test profile using
mocked Telegram API responses and seeded approved catalog data. Document local
development setup, production webhook registration, smoke testing,
configuration, secret rotation, rollback, channel disablement, dead-letter
triage, alert response, user-support scripts, and known limitations. Update the
implementation-plan index if needed.

### Acceptance criteria

- Automated tests cover the full `/newsetlist` path from webhook update through
  session workflow, confirmation, backend recommendation, rendered Telegram
  response, and persisted/correlated session state using deterministic fixtures.
- Tests prove duplicate update handling, webhook secret rejection, unauthorized
  users, disabled channel, stale callbacks, cancellation, retryable outbound
  failures, dead-letter creation, and Telegram-vs-HTTP/API deterministic
  equivalence.
- Deployment documentation names required secret references, environment
  variables/configuration keys, webhook URL format, health checks, feature
  flags, local development mode, and expected startup validation failures.
- The runbook explains webhook registration, smoke tests, metric/log checks,
  troubleshooting steps, secret/token rotation, incident response, dead-letter
  handling, rollback, channel disablement, and user-support escalation.
- CI or documented verification commands include OpenAPI generation,
  unit/integration tests, and any mocked end-to-end Telegram test profile.

### Restrictions

- Do not require real Telegram network calls, real bot credentials, production
  chat IDs, or live personal data in automated tests or CI fixtures.
- Do not record raw production Telegram updates or message text as fixtures;
  use synthetic sanitized payloads.
- Do not mark the channel production-ready until both successful and failure
  runbook paths have been verified against the implemented configuration and
  telemetry names.
- Do not skip OpenAPI generation after API changes or leave generated API
  artifacts out of sync with the split contract.
