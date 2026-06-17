# ADR-030: Plugin and Extension Architecture

Status: Accepted  
Date: 2026-05-28

## Context

Churches and partners may need custom import connectors, export formats, scoring policy adjustments, and church-specific constraints. Extensibility must not compromise deterministic recommendation or approval gating.

## Problem

Embedding custom behavior directly in core code makes upgrades difficult. Allowing arbitrary plugins inside critical paths can bypass catalog governance, instance isolation, or deterministic scoring guarantees.

## Decision

Define a constrained plugin architecture with explicit extension points, versioned service provider interfaces, configuration, instance scoping, and policy enforcement around all plugin outputs. Plugins may contribute candidates, metadata transforms, scoring adjustments, export documents, or constraints only through approved extension contracts. Core Cadentia remains responsible for approval gates, instance isolation, deterministic tie-breaking, and final recommendation eligibility.

## Requirements

- Support plugins for import connectors, scoring policy contributions, export formats, church-specific constraints, and future integrations.
- Define extension SPIs with versioned input/output DTOs and compatibility rules.
- Require plugin registration, configuration, enablement, disablement, and version tracking.
- Scope plugins by church instance, environment, and extension point.
- Prevent plugins from bypassing approval, licensing, instance visibility, and role gates.
- Require deterministic outputs from plugins used in recommendation scoring.
- Isolate plugin failures so they degrade or fail according to policy without corrupting core state.
- Audit plugin execution for privileged or catalog-mutating operations.

## Acceptance Criteria

- Plugins can extend approved extension points without modifying core code.
- Core deterministic recommendation guarantees remain enforced.
- Plugin-provided data is filtered through approval and instance policies before recommendation use.
- Extensions are configurable, versioned, and auditable.
- A failing plugin cannot expose unauthorized data or mark unapproved songs recommendable.

## Consequences

Positive:

- Enables integrations and church-specific behavior without core forks.
- Keeps safety policies centralized.
- Supports marketplace or enterprise extension models over time.

Tradeoffs:

- SPI versioning and compatibility testing are required.
- Plugin isolation may limit flexibility.
- Deterministic plugin requirements may exclude some adaptive algorithms from scoring paths.

## Alternatives Considered

1. No plugin support.
   - Rejected: limits platform extensibility and partner integrations.
2. Allow plugins direct database access.
   - Rejected: bypasses policy, audit, and schema stability boundaries.
3. Let plugins select songs directly.
   - Rejected: violates Recommendation Engine ownership and deterministic selection guarantees.

## Open Questions

- Answered for SPI v1 in the implementation plan: core-maintained plugins may
  run in-process or as managed sidecars, partner certified and church-local
  plugins run out-of-process, and experimental plugins are sandbox-only.
- Answered for SPI v1 in the implementation plan: third-party production
  plugins require signed artifacts, SBOM/checksum metadata, contract fixtures,
  policy-gate tests, failure/timeout tests, and security/license review;
  church-local packages require administrator attestation and contract
  validation with reduced support expectations.
- Answered for SPI v1 in the implementation plan: stable extension points are
  limited to import connectors, staged metadata transforms, export renderers,
  outbound publish-notification hooks, and static package customization
  manifests. Recommendation constraint contributions, scoring policy
  contributions, inbound webhooks, general async event processors, UI
  extensions, observability exporters, and LLM/prompt provider extensions remain
  deferred until their dependencies and policy controls mature.
