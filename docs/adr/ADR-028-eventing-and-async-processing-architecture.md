# ADR-028: Eventing and Async Processing Architecture

Status: Proposed  
Date: 2026-05-28

## Context

Imports, parsing, read model refreshes, asset processing, notifications, and index updates can be long-running or failure-prone. These workflows should not block user requests or create partial invisible failures.

## Problem

Synchronous processing for every workflow reduces reliability and scalability. Without a domain event strategy, dependent projections, caches, indexes, and notifications may drift from canonical catalog state.

## Decision

Adopt a domain event and async job architecture with durable event records, idempotent handlers, retry policies, dead-letter handling, and traceable job status. Canonical state changes emit events, and asynchronous processors update derived data such as read models, search indexes, asset derivatives, and notifications.

## Requirements

- Support async workflows for imports, parsing, read model refreshes, asset processing, search indexing, cache invalidation, and notifications.
- Define domain events for catalog approval, arrangement changes, instance visibility/configuration changes, imports, asset uploads, service readiness, and recommendation feedback.
- Persist events or outbox records transactionally with canonical state changes.
- Require idempotent handlers with stable event IDs and deduplication.
- Define retry policies with backoff, retry limits, and dead-letter queues.
- Provide job status, progress, error reason, and actor/instance context.
- Ensure failed jobs can be retried safely without duplicating catalog entries or bypassing approval.
- Correlate events with observability traces and audit records.

## Acceptance Criteria

- Long-running jobs execute asynchronously and expose status.
- Events are traceable from source action to derived side effects.
- Failed jobs enter retry or dead-letter states with actionable diagnostics.
- Retrying handlers is safe and idempotent.
- Derived data can be rebuilt from canonical sources and event history where applicable.

## Consequences

Positive:

- Improves responsiveness and resilience for heavy workflows.
- Makes read model, index, and cache updates explicit.
- Provides operational visibility into background processing.

Tradeoffs:

- Event schemas require versioning and governance.
- Eventual consistency must be communicated in UI states.
- Dead-letter operations require administrative tooling.

## Alternatives Considered

1. Keep all workflows synchronous.
   - Rejected: poor scalability and timeout risk.
2. Use fire-and-forget in-process tasks.
   - Rejected: unreliable and difficult to observe.
3. Let derived stores poll canonical tables only.
   - Rejected: inefficient and less traceable than explicit events.

## Open Questions

- Which broker/outbox implementation should be adopted first?
- What event retention period is required for audit and replay?
- Which derived projections must support full replay from events versus rebuild from tables?
