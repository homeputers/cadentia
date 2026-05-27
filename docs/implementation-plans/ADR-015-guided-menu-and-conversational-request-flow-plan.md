# ADR-015 Implementation Plan: Guided Menu and Conversational Request Flow

## Objective

Implement a deterministic session state machine that merges menu and free-text
inputs into a confirmable normalized request before recommendation execution.

## Subtask 1: Define conversation-state API contract and DTOs

### Context

ADR-015 requires shared request accumulation across menu-only, free-text-only,
and mixed interaction modes with explicit confirmation boundaries.

### Prompt

Design and implement OpenAPI contracts and Java DTO validation for session state,
slot updates, clarification prompts, confirmation requests, and expiration
responses.

### Acceptance criteria

- OpenAPI defines endpoints/events for state read, slot update, clarify, confirm, cancel, and recover.
- Schemas represent `START`, `COLLECTING`, `CLARIFICATION_REQUIRED`, `READY_TO_CONFIRM`, `CONFIRMED`, `EXPIRED`, and `CANCELLED`.
- Payloads include source metadata (`menu`, `free_text`, `default`, `user_edit`).
- Validation errors are deterministic and consistent across channels.

### Restrictions

- Do not allow recommendation generation from non-confirmed states.
- Do not return ambiguous free-form state values.
- Do not duplicate intent schema ownership outside the ADR-012 contract.

## Subtask 2: Implement deterministic merge and precedence engine

### Context

Mixed-mode slot accumulation must honor precedence and preserve auditability.

### Prompt

Implement Java services that apply precedence rules, detect conflicts, emit
clarification requirements, and persist revision history for each merge event.

### Acceptance criteria

- Current-turn explicit edits override prior values.
- Menu selections override inferred free-text values unless user explicitly chooses otherwise.
- Free-text values fill only missing slots when no explicit conflict exists.
- Defaults are applied only after user-provided slots are exhausted.
- Repository/migration changes support revision history and source-tagged audit records.

### Restrictions

- Do not overwrite prior values without recording provenance.
- Do not allow non-deterministic conflict resolution.
- Do not hide defaulted values in normalized request summaries.

## Subtask 3: Build session lifecycle persistence, timeout, and recovery

### Context

Stale session reuse creates incorrect recommendations; ADR-015 requires timeout
and recoverable expiration behavior.

### Prompt

Add persistence, TTL/expiration policy, lifecycle jobs, and recovery UX payloads
for expired sessions in both menu and conversational adapters.

### Acceptance criteria

- Database migrations support session state, timestamps, and expiry markers.
- Infrastructure config externalizes inactivity timeout and absolute lifetime policy.
- Expired sessions cannot silently reuse stale constraints.
- Recovery responses include summary of lost/retained context and restart options.

### Restrictions

- Do not delete audit history when expiring active sessions.
- Do not hardcode timeout values in application code.
- Do not produce channel-specific behavior that violates shared state rules.

## Subtask 4: Add observability and operational documentation

### Context

The orchestration layer needs visibility into ambiguity loops, expiration rates,
and confirmation funnels.

### Prompt

Instrument state transitions, clarification triggers, confirmation outcomes, and
session expiry metrics; document dashboards, alerts, and operator runbooks.

### Acceptance criteria

- Metrics track transition counts and time-in-state by channel.
- Traces/logs include deterministic merge decisions and conflict reasons.
- Alerts exist for abnormal expiration or clarification retry loops.
- Documentation explains troubleshooting flow and known failure modes.

### Restrictions

- Do not log sensitive full free-text content when redaction is required.
- Do not emit unbounded-cardinality labels.
- Do not ship without runbook updates for new state-machine operations.
