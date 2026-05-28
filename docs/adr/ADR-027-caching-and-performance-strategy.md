# ADR-027: Caching and Performance Strategy

Status: Proposed  
Date: 2026-05-28

## Context

Recommendation, catalog browsing, search, and governance screens depend on read-heavy data. Performance must improve without serving stale eligibility data that could expose unapproved or restricted songs.

## Problem

Naive caching can make Cadentia fast but unsafe if approval, tenant visibility, licensing, or catalog changes are not invalidated immediately. Avoiding caches entirely may make recommendation and search latency unacceptable as catalog size grows.

## Decision

Adopt explicit cache domains with conservative invalidation for eligibility-sensitive data. Cache keys must include tenant, catalog snapshot/version, scoring profile, request shape where applicable, role/audience, and approval-visible state. Recommendation eligibility must be recomputed or invalidated whenever approval or visibility state changes.

## Requirements

- Define cacheable domains: recommendation candidate pools, search results, read models, tag aggregations, and low-risk reference data.
- Define non-cacheable or short-lived domains for privileged review notes and highly sensitive personnel details.
- Include tenant, authorization/audience, catalog version, and policy version in cache keys.
- Invalidate candidate and search caches on approval, rejection, activation, deactivation, licensing, tenant visibility, arrangement, and taxonomy changes.
- Define TTLs by domain, with shorter TTLs for eligibility-sensitive material.
- Define latency targets for recommendation, search, catalog reads, and administrative dashboards.
- Provide cache hit/miss metrics and invalidation audit events.
- Prefer deterministic cache warmups and rebuilds from source data.

## Acceptance Criteria

- Recommendation latency remains within defined target thresholds for supported catalog sizes.
- Cache invalidation removes stale eligibility after approval, visibility, or licensing changes.
- Cached responses cannot expose another tenant's data or unapproved songs.
- Cache behavior is observable through metrics and logs.
- The system can safely bypass or rebuild caches without changing recommendation correctness.

## Consequences

Positive:

- Improves responsiveness for read-heavy workflows.
- Keeps performance strategy aligned with approval gates.
- Makes cache correctness testable through versioned keys and invalidation events.

Tradeoffs:

- Cache key design is complex.
- Event-driven invalidation must be reliable.
- Conservative invalidation may reduce hit rates.

## Alternatives Considered

1. Cache full recommendation responses indefinitely.
   - Rejected: high risk of stale eligibility and policy drift.
2. Avoid caching recommendation data.
   - Rejected: may not meet latency targets at scale.
3. Use only TTL-based invalidation.
   - Rejected: approval and visibility changes require event-driven invalidation.

## Open Questions

- What are the initial p50/p95 latency targets by endpoint?
- Which caches must be distributed versus process-local?
- Should recommendation responses be cached only for saved service plans?
