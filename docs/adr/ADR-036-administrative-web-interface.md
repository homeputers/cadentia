# ADR-036: Administrative Web Interface

Status: Accepted  
Date: 2026-06-02

## Context

Cadentia already defines administrative governance concepts in ADR-011 and exposes several admin API surfaces. The OpenAPI contract includes `AdminReview` endpoints for import candidate queues, candidate detail, duplicate review, audit history, moderation flags, rollback previews, and rollback execution. The Spring controller layer implements those admin review operations with role-based method security.

However, the repository currently contains only the backend API application and shared TypeScript intent-contract package. There is no committed web application shell, route structure, generated API client, authentication integration, or operational runbook for an administrative UI. As a result, administrative workflows exist primarily as API contracts and backend services rather than a usable web console for catalog editors, reviewers, and operators.

## Problem

API-only administration is not sufficient for day-to-day governance. Catalog editors and reviewers need a browser-based interface that can safely perform high-risk operations with context, diff views, workflow status, previews, and audit visibility.

Without an administrative web UI:

- Reviewers must rely on raw API calls or ad hoc tooling.
- Import candidate triage, duplicate review, moderation, rollback, and audit history are difficult to perform safely.
- Role-based access requirements are harder to communicate and verify from the user experience.
- Operational staff lack a single console for imports, approvals, recommendation diagnostics, service-plan governance, background jobs, and instance configuration.
- Dangerous actions may be executed without preview, confirmation, or clear rollback context.

## Decision

Build a first-class Administrative Web Interface backed by the Cadentia API. The admin UI will be a separate web application package in the repository, generated or typed against the OpenAPI contract, and deployed alongside each Cadentia church instance as an authenticated operator/reviewer console.

The UI is not a separate source of truth. It must call documented API endpoints, respect server-side RBAC, render server-provided eligibility and audit facts, and never compute recommendation eligibility or catalog approval independently. High-risk operations must use preview/confirm flows and preserve audit attribution.

ADR-011 remains the domain decision for catalog governance UI behavior. ADR-036 defines the application-level admin console architecture, API integration, operational coverage, and deployment requirements for a web UI that can host ADR-011 and future administrative workflows.

## Requirements

- Add a web admin application package with route structure, build/test commands, environment configuration, and deployment artifact definition.
- Generate or maintain a typed API client from `apps/api/src/main/openapi/cadentia-api.yaml` so UI actions remain contract-aligned.
- Integrate authentication and role-aware navigation for `CATALOG_EDITOR`, `DOCTRINAL_REVIEWER`, `MUSICAL_REVIEWER`, and `ADMIN` capabilities.
- Implement initial screens for import candidate queue, candidate detail, duplicate comparison, review history, audit history, moderation flags, rollback preview, and rollback execution.
- Render approval state, provenance, parser warnings, duplicate signals, moderation eligibility effects, and recommendation eligibility impact using backend-provided data.
- Require preview and explicit confirmation for destructive or eligibility-impacting actions such as rollback, rejection, suppression, or approval reversal.
- Prevent client-side-only authorization; all mutations must rely on server-side RBAC and return non-leaky authorization failures.
- Provide deterministic admin diagnostics views for recommendation/scoring data when enabled for authorized roles.
- Support instance-level configuration screens for enabled connectors, bot channels, scoring profiles, feature flags, and operational settings as APIs become available.
- Include accessibility, auditability, error-state, loading-state, and empty-state requirements for all administrative workflows.
- Provide operational documentation and runbooks for deployment, smoke testing, permissions verification, rollback, and incident triage.

## Acceptance Criteria

- An authenticated admin or reviewer can use the web UI to list import candidates, inspect details, review duplicates, view audit history, manage moderation flags, preview rollback impact, and execute authorized rollback actions.
- The admin UI uses the OpenAPI-backed client and does not call undocumented backend routes.
- Server-side RBAC remains authoritative; unauthorized UI routes and API calls are blocked safely.
- High-risk actions include backend preview data, explicit confirmation, actor attribution, and audit trail visibility.
- The UI does not expose unapproved content, sensitive review notes, or admin-only diagnostics to unauthorized roles.
- Operators can follow a runbook to deploy the admin UI, verify health, validate role access, and rollback a broken UI release independently from catalog data.

## Consequences

Positive:

- Administrative workflows become usable without raw API tooling.
- Reviewers get safer context for approval, deduplication, moderation, and rollback decisions.
- The OpenAPI contract becomes the integration boundary between backend and frontend.
- Future Phase 4 operational features have a consistent console surface.

Tradeoffs:

- Adds frontend build, testing, deployment, accessibility, and dependency maintenance responsibilities.
- UI release cadence must coordinate with API contract changes.
- Admin diagnostics require careful redaction and role testing.

## Alternatives Considered

1. Continue with API-only administrative operations.
   - Rejected: too error-prone for review-heavy, high-impact workflows.
2. Build admin screens directly into the Spring Boot API service as server-rendered pages.
   - Deferred: possible for narrow internal tooling, but a separate web application better supports rich diffs, typed clients, and independent UI deployment.
3. Use a generic database admin tool.
   - Rejected: bypasses API policy, RBAC, validation, previews, and audit semantics.
4. Expand ADR-011 only.
   - Rejected: ADR-011 defines governance workflow needs, but a separate ADR is needed for the web application architecture and API integration surface.

## Open Questions

- Which frontend framework and design system should be adopted for the admin application?
- Should admin UI hosting be same-origin with the API or deployed as a separate static application behind the same identity provider?
- Which API gaps must be closed before the first usable admin console milestone?
- Should Telegram bot operations and instance configuration be managed from the initial admin UI release or a later operations-console milestone?
