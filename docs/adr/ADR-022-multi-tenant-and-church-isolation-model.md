# ADR-022: Multi-Tenant and Church Isolation Model

Status: Proposed  
Date: 2026-05-28

## Context

Cadentia is expected to support multiple churches, campuses, and organizations while retaining a curated global catalog. Churches may maintain private arrangements, preferences, usage history, and approval decisions that must not leak to other tenants.

## Problem

A single shared data model without explicit tenant boundaries can expose private song choices, service plans, personnel data, or approval states across churches. It can also make recommendation eligibility ambiguous when a globally approved song has a tenant-specific arrangement or local restriction.

## Decision

Adopt tenant-aware isolation as a platform invariant. Every private operational record belongs to exactly one tenant, while selected catalog entities may be globally scoped and inherited by tenants through explicit visibility and approval rules.

Recommendations execute in a tenant context and may consider:

- global approved catalog entries visible to the tenant
- tenant-owned songs and arrangements approved for that tenant
- tenant-specific preferences, usage history, and restrictions
- inherited approval state only where policy allows it

Tenant isolation must be enforced in authorization, query filters, repository methods, audit records, and background jobs.

## Requirements

- Define tenant as the primary isolation boundary for church-owned data.
- Support global/shared catalog entries visible to all eligible tenants.
- Support tenant-specific songs, arrangements, tags, preferences, usage history, and service plans.
- Support explicit approval inheritance from global catalog to tenant catalog where policy permits.
- Allow tenants to override local eligibility by adding stricter restrictions without mutating global approval.
- Prevent users and jobs from reading or mutating data outside authorized tenant scopes.
- Include tenant identifiers in audit events, imports, assets, recommendations, and observability traces.
- Support future SaaS deployment with tenant onboarding, suspension, export, and deletion workflows.

## Acceptance Criteria

- Users cannot see another tenant's private songs, arrangements, services, assets, or personnel data.
- Shared/global catalog items can be recommended when globally approved and not locally restricted.
- Tenant-private songs are recommendable only inside their tenant after local approval gates pass.
- Recommendation results respect tenant-specific preferences, usage history, and eligibility boundaries.
- Cross-tenant access attempts are denied and auditable.

## Consequences

Positive:

- Enables SaaS and multi-church collaboration safely.
- Preserves global catalog reuse while allowing local governance.
- Makes tenant-scoped recommendations deterministic and auditable.

Tradeoffs:

- Every query and job must carry tenant context.
- Data migrations and tests need tenant fixtures.
- Global-to-local inheritance rules add policy complexity.

## Alternatives Considered

1. Separate deployment per church.
   - Rejected: high operational overhead and poor shared-catalog reuse.
2. Fully shared catalog with no tenant-private data.
   - Rejected: does not support local arrangements, preferences, or privacy needs.
3. Tenant-specific databases only.
   - Deferred: may be an enterprise isolation option, but logical isolation is the baseline.

## Open Questions

- Should campuses be modeled as child tenants or tenant-scoped groups?
- What tenant data export format is required for portability?
- Which global approvals can be inherited automatically versus requiring local review?
