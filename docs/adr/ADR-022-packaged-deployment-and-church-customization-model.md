# ADR-022: Packaged Deployment and Church Customization Model

Status: Proposed  
Date: 2026-06-02

## Context

Cadentia needs to serve multiple churches and organizations, but the preferred operating model is not a single shared multi-tenant data plane. Churches may have different worship vocabularies, approval policies, song catalogs, arrangements, service workflows, integrations, branding, and operational constraints. Those differences should be configurable and packageable so each church can run in an isolated deployment instance.

## Problem

A shared multi-tenant architecture forces every query, job, cache, event, and permission check to carry tenant isolation concerns. That increases leakage risk and implementation complexity before Cadentia has proven which church-specific customizations must be productized. It also makes packaging Cadentia for self-hosted, private-cloud, or church-managed deployments harder because tenant boundaries become embedded throughout the domain model instead of expressed as deployment configuration.

Cadentia needs strong church isolation, but the isolation boundary should be the deployed instance and its configuration package rather than a logical tenant row inside a shared database.

## Decision

Adopt an isolated-instance deployment model. Each church or organization receives a separately configured Cadentia instance with its own database, object storage namespace/bucket, secrets, integrations, cache namespace, event streams, catalog overlays, and operational configuration.

Cadentia will be packaged as a configurable application distribution. A church deployment package defines instance identity, enabled modules, approval policy, catalog seed sources, scoring profiles, taxonomy choices, service workflow defaults, branding, integrations, plugin allow-list, asset storage settings, and observability/export configuration.

The application code may continue to use an `instanceId` or deployment identifier for auditability, telemetry correlation, licensing, backups, and support operations, but recommendation eligibility does not depend on runtime cross-tenant filters. Recommendation execution happens inside one isolated instance against that instance's approved catalog and configuration.

## Requirements

- Package Cadentia so a church can deploy an isolated instance with separate application configuration, database, object storage, cache namespace, event streams, and secrets.
- Define a versioned church configuration package for policies, enabled modules, scoring profiles, controlled vocabularies, approval gates, workflow defaults, integrations, plugin allow-lists, branding, and feature flags.
- Support instance-local catalogs and arrangements, plus optional import/seed packages for global or denominational catalog baselines.
- Treat imported global/shared catalog data as copied or synchronized into the instance's governance workflow rather than directly recommended from a shared live catalog.
- Require every recommendable song and arrangement to pass the local instance's approval gates, even when seeded from a shared package.
- Support repeatable provisioning, upgrades, backup/restore, data export, and instance cloning for staging environments.
- Keep cross-instance administration outside normal user workflows and require explicit operator tooling, credentials, and audit logs.
- Ensure plugins, integrations, assets, caches, background jobs, and telemetry read instance configuration instead of assuming a shared SaaS tenant table.

## Acceptance Criteria

- A church instance can be deployed with isolated persistence, asset storage, secrets, cache namespace, event streams, and configuration.
- Two church instances cannot read each other's private songs, arrangements, services, assets, people, credentials, or usage history through normal application paths.
- Recommendation results use only the deployed instance's approved catalog, local policy, scoring profile, and service context.
- Global or denominational starter catalog packages are not recommendable until accepted by the instance's configured approval workflow.
- Configuration packages are versioned, reviewable, and reproducible across environments.
- Operators can provision, upgrade, back up, restore, and export a church instance without introducing shared runtime catalog eligibility.

## Consequences

Positive:

- Strong isolation is achieved through deployment boundaries rather than pervasive tenant filters.
- Churches can customize policies, vocabularies, scoring, workflows, branding, and integrations without forking core code.
- Self-hosted, private-cloud, managed single-tenant, and denominational package deployments share the same architecture.
- Recommendation determinism becomes easier to reason about because each run uses one instance-local catalog snapshot and configuration package.

Tradeoffs:

- Operating many instances requires deployment automation, observability aggregation, backup orchestration, and upgrade tooling.
- Cross-church collaboration and shared catalog improvements require explicit package/import/synchronization workflows.
- Per-instance infrastructure can cost more than a shared multi-tenant data plane.
- Support tooling must distinguish operator access from normal church user access.

## Alternatives Considered

1. Shared multi-tenant database with tenant IDs on every domain record.
   - Rejected: increases leakage risk and embeds tenancy concerns throughout recommendation, search, caching, events, and authorization paths.
2. One-off custom forks per church.
   - Rejected: unmaintainable; customization must be configuration/package driven.
3. Fully shared global catalog with local preferences only.
   - Rejected: does not preserve local approval, doctrinal review, arrangement, and licensing governance.
4. Separate deployment per church with no reusable packaging.
   - Rejected: isolation is desirable, but repeatable packaging and upgrade automation are required.

## Open Questions

- What is the initial packaging format for church configuration: YAML files, database seed bundles, Helm values, or a combined release artifact?
- Which configuration changes require restart, migration, or governance approval?
- How should denominational catalog packages publish updates into existing church instances without bypassing local review?
- What managed-service tooling is required to operate many isolated instances efficiently?
