# ADR-035 Telegram Bot Operations Runbook

## Purpose

This runbook defines the minimum operational procedures required before the Telegram bot channel can be considered production-ready.

## Deployment Inputs

- Telegram bot token stored in the deployment secret manager.
- Telegram webhook secret token distinct from the bot token.
- Public HTTPS webhook URL for the deployed Cadentia instance.
- Cadentia API base URL reachable by the bot adapter.
- Identity/account-linking configuration for mapping Telegram users to Cadentia roles.

## Smoke Test

1. Register or refresh the Telegram webhook with the expected webhook URL and secret token.
2. Send `/start` to the bot and verify the response includes help or onboarding guidance.
3. Send `/newsetlist` and verify the bot enters the ADR-015 collection flow.
4. Provide a scripture/theme prompt and verify the normalized request confirmation is shown before recommendation.
5. Confirm generation and verify the response references only approved catalog recommendations.
6. Verify metrics/logs contain the update ID, command outcome, session ID, and no bot token or raw sensitive payload leakage.

## Signals to Monitor

- Webhook verification failures.
- Telegram update processing latency.
- Command outcome counts by command and result.
- Conversation session transitions by channel `telegram`.
- Outbound Telegram send failures and rate-limit responses.
- Dead-lettered or repeatedly retried update IDs.

## Triage Workflow

1. Confirm the webhook is still registered with Telegram and points to the expected instance URL.
2. Verify the webhook secret token matches deployment configuration.
3. Check whether failures occur before update normalization, during Cadentia API calls, or during outbound Telegram sends.
4. Confirm the Cadentia API health endpoint and database are healthy.
5. Inspect conversation session state for stuck `CLARIFICATION_REQUIRED` or `READY_TO_CONFIRM` sessions.
6. If credentials are suspected compromised, disable webhook delivery, rotate token/secret, redeploy, and re-register webhook.

## Rollback / Disablement

- Disable the Telegram webhook or route it to a maintenance response without disabling the core API.
- Preserve queued/dead-lettered updates for later inspection where retention policy allows.
- Announce channel outage to operators and direct users to the web/API flow if available.
