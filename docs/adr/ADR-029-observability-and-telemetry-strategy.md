# ADR-029: Observability and Telemetry Strategy

Status: Accepted  
Date: 2026-05-28

## Context

Cadentia must diagnose recommendation behavior, imports, approval workflows, prompt/intent extraction, background jobs, and packaged deployments, instance authorization. Observability should support operations without leaking sensitive catalog, personnel, or licensing details.

## Problem

Without structured logs, metrics, traces, and audit correlation, failures in deterministic recommendation or catalog governance are difficult to diagnose. Conversely, overly verbose telemetry may expose private church data, review notes, or copyrighted content.

## Decision

Adopt a privacy-aware observability strategy with structured logging, metrics taxonomy, distributed tracing, correlation IDs, and explicit audit trails for privileged actions. Recommendation execution will emit traceable phase diagnostics and scoring summaries that are safe for the intended audience.

## Requirements

- Support recommendation tracing across request validation, candidate filtering, scoring, transition analysis, tie-breaking, explanation generation, and response rendering.
- Support scoring diagnostics with profile version, catalog snapshot, candidate counts, exclusion codes, and latency by phase.
- Support import monitoring, parser diagnostics, read model/index refresh metrics, and asset processing metrics.
- Support approval audit trails and privileged action correlation.
- Support prompt diagnostics for LLM intent extraction without logging unsafe or unnecessary raw sensitive payloads.
- Define metrics taxonomy for latency, throughput, errors, queue depth, cache behavior, approval workflow, and recommendation quality signals.
- Use structured logs with instance identifier, actor where permitted, request ID, correlation ID, service plan ID, and event/job ID.
- Redact lyrics excerpts, private notes, credentials, and cross-instance identifiers from inappropriate sinks.

## Acceptance Criteria

- Recommendation execution is traceable end-to-end.
- Failures are diagnosable through correlated logs, metrics, traces, and audit records.
- Metrics support operational debugging and SLO monitoring.
- Telemetry does not leak unapproved content, private church-instance data, secrets, or copyrighted payloads.
- Prompt diagnostics prove the LLM stayed within the intent-extraction boundary.

## Consequences

Positive:

- Operators can diagnose recommendation and import issues faster.
- SLOs and regression detection become measurable.
- Audit and trace correlation improves incident response.

Tradeoffs:

- Telemetry schemas require maintenance.
- Redaction policies must be tested.
- High-cardinality labels can increase observability cost.

## Alternatives Considered

1. Rely on application logs only.
   - Rejected: insufficient for distributed async workflows and scoring diagnostics.
2. Log full request and catalog payloads.
   - Rejected: privacy, licensing, and data minimization risk.
3. Treat audit logs as observability telemetry.
   - Rejected: audits and operational telemetry have different retention, audience, and integrity requirements.

## Open Questions

- Which telemetry backend is the initial production target?
- What metrics become release-blocking SLOs?
- How should church instance administrators access their own operational telemetry?
