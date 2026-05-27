# ADR-015 Conversational Flow Operations Runbook

## Purpose

This runbook defines operational checks for the guided menu and conversational
request state machine introduced by ADR-015.

## Signals to Monitor

### Transition and Funnel Health

- `cadentia_request_state_transition_total`
  - Watch transition volume by channel and state pair.
- `cadentia_request_confirmation_outcome_total`
  - Confirmed vs cancelled ratio by channel.

### Latency and Stalling

- `cadentia_request_state_duration_seconds`
  - p95 time-in-state for `COLLECTING`, `CLARIFICATION_REQUIRED`, and
    `READY_TO_CONFIRM`.

### Error-Prone Behaviors

- `cadentia_request_clarification_total`
  - Clarification prompts by conflict type.
- `cadentia_request_expiry_total`
  - Session expiries by expiry type (`inactivity`, `absolute`) and channel.

## Alert Thresholds

- **Expiry anomaly**: trigger if expiry rate is >2x seven-day baseline for at
  least 15 minutes.
- **Clarification loop risk**: trigger if sessions commonly exceed 3
  clarification retries without reaching `READY_TO_CONFIRM`.
- **Funnel regression**: trigger if confirm/start ratio drops below established
  weekly baseline.

## Triage Workflow

1. Validate whether the spike is channel-specific (`menu`, `free_text`, mixed).
2. Inspect structured logs for `merge_decision` and `conflict_reason` trends.
3. Confirm redaction policy is intact (no raw free-text leakage).
4. Verify timeout configuration values in deployment environment for inactivity
   and absolute lifetime.
5. Check recent releases/migrations for orchestration or schema changes.
6. If needed, rollback recent orchestration changes and monitor metric recovery.

## Known Failure Modes

- Ambiguous free-text creates repeated clarification prompts.
- Adapter regressions may skip confirmation rendering and drive cancellations.
- Misconfigured timeout values can cause abnormal `EXPIRED` transitions.
- Channel mapping bugs can mis-tag source precedence and inflate conflict rates.

## Escalation

Escalate to backend on-call when:

- expiry anomaly lasts >30 minutes,
- clarification loop alert persists after configuration review,
- or redaction constraints are violated in logs.

Include metric screenshots, impacted channels, and a 30-minute window of sample
structured transition logs in the incident report.
