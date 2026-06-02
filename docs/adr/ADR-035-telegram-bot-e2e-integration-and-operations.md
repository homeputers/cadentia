# ADR-035: Telegram Bot E2E Integration and Operations

Status: Accepted  
Date: 2026-06-02

## Context

Cadentia's conversational planning model already anticipates bot channels. ADR-015 defines guided menu and conversational request flow, and the API exposes natural-language setlist proposal endpoints that keep the LLM at the intent boundary while the Recommendation Engine selects songs deterministically.

The current codebase contains bot package scaffolding, including `BotAdapter`, `BotSession`, and a `TelegramBotAdapter`. The Telegram adapter currently throws `UnsupportedOperationException`, so it is not wired end-to-end to Telegram webhook updates, API authentication, conversation sessions, setlist proposal generation, persistence, or operational monitoring. There is also no Telegram-specific runbook for webhook setup, secret rotation, incident response, or bot command troubleshooting.

## Problem

Without a complete Telegram integration boundary:

- Telegram messages cannot reliably enter the Cadentia request pipeline.
- Conversation state, confirmation, and cancellation behavior may diverge from ADR-015.
- Bot credentials and webhook secrets may be handled inconsistently.
- Telegram users may receive generated responses that do not preserve approval-gated recommendation constraints.
- Operators lack documented procedures for webhook registration, health checks, retries, rate limits, and incident triage.

The missing gap is not the existence of placeholder methods; it is the absence of an end-to-end, observable, secure channel adapter that maps Telegram interactions into Cadentia's API and conversational workflow.

## Decision

Build a first-class Telegram Bot Integration module as a channel adapter around the existing Cadentia API and conversation services.

The Telegram adapter will receive Telegram updates through a verified webhook endpoint, normalize Telegram messages and callback queries into Cadentia channel events, invoke the shared conversation/session orchestration, and render deterministic responses back to Telegram. The adapter must never select songs directly and must never bypass API authorization, catalog approval gates, or Recommendation Engine ownership.

Telegram-specific code is responsible for transport concerns only: webhook verification, update parsing, command routing, menu rendering, rate-limit handling, Telegram API calls, and channel observability. Intent extraction, slot validation, setlist recommendation, explanations, persistence, and approval eligibility remain backend API/domain responsibilities.

## Requirements

- Replace the placeholder Telegram adapter with a production adapter that handles Telegram webhook updates and outgoing Telegram API calls.
- Define a webhook endpoint with Telegram secret-token verification, payload validation, replay/idempotency handling, and structured error responses.
- Support Telegram commands at minimum: `/start`, `/help`, `/newsetlist`, `/status`, `/cancel`, and `/settings` where enabled by instance configuration.
- Support inline keyboard/menu callbacks for ADR-015 guided flow fields: scripture/theme, shape/counts, language, key policy, tempo policy, energy arc, confirmation, revision, and cancellation.
- Map Telegram chat/user identifiers to Cadentia user/session identity without storing bot tokens or raw unnecessary personal data in logs.
- Invoke the shared conversation/session facade and setlist proposal APIs rather than duplicating recommendation or LLM logic in the bot adapter.
- Persist or correlate bot sessions so restarts do not silently lose active confirmation flows.
- Render setlist proposals, clarification prompts, validation errors, safe rejections, and operational failures in Telegram-friendly messages with length limits and safe redaction.
- Support retry, rate-limit, and dead-letter handling for outbound Telegram sends.
- Emit metrics and structured logs for updates received, command outcomes, session transitions, API failures, Telegram send failures, and webhook verification failures.
- Provide operational documentation and runbooks for webhook registration, token/secret rotation, smoke tests, rollback, alert response, and user support.

## Acceptance Criteria

- A configured Telegram bot can complete a `/newsetlist` flow from Telegram message to confirmed recommendation response.
- The same user request routed through Telegram and the HTTP API reaches the same validated intent/session workflow and deterministic Recommendation Engine behavior.
- The Telegram adapter never selects songs, mutates catalog approval, or uses unapproved catalog data.
- Webhook requests are authenticated, idempotent, observable, and safe to retry.
- Bot credentials are loaded from deployment secrets and are never logged.
- Operators can follow a runbook to register the webhook, validate the bot, diagnose failures, rotate secrets, and disable the channel without disabling the core API.

## Consequences

Positive:

- Telegram becomes a functional end-to-end interaction channel rather than a placeholder adapter.
- Shared conversation orchestration keeps bot, web, and API behavior consistent.
- Operational runbooks reduce deployment and incident risk.
- Channel-specific concerns remain isolated from deterministic recommendation logic.

Tradeoffs:

- Telegram transport introduces external webhook, rate-limit, and delivery failure modes.
- Session identity mapping and privacy controls require careful design.
- Bot UI constraints may require condensed explanations compared with web/API responses.

## Alternatives Considered

1. Keep Telegram as a placeholder adapter until all web flows are complete.
   - Rejected: the scaffold creates an expectation of support but cannot be operated or tested end-to-end.
2. Let the Telegram bot call the LLM and Recommendation Engine directly.
   - Rejected: duplicates orchestration and risks bypassing Cadentia's intent-only LLM boundary and deterministic API contracts.
3. Use polling instead of webhooks as the default production integration.
   - Rejected: polling is useful for local development but webhooks provide clearer production routing, verification, and observability.
4. Implement Telegram-only session logic.
   - Rejected: would diverge from ADR-015 and make behavior inconsistent across channels.

## Open Questions

- Should local development support Telegram long polling in addition to production webhooks?
- Which identity provider or account-linking flow maps Telegram users to Cadentia roles?
- How much of the recommendation explanation payload should be rendered directly in Telegram versus linked to a web view?
- What are the default session inactivity and absolute lifetime values for bot interactions?
