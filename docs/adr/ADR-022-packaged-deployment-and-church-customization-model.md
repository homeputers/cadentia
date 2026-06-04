# ADR-022: Packaged Deployment and Church Customization Model

Status: Accepted  
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


## Church Configuration Package Contract

Each isolated Cadentia instance is driven by a canonical church configuration
package. The v1 contract is published as
`packages/intent-contracts/schemas/church-config/v1/church-config-package.schema.json`
and is validated by the `@cadentia/intent-contracts` local tooling before
provisioning or API startup. A package is a reviewable artifact with this
layout:

```text
church-config/
  cadentia-church-package.json
  overlays/development.json
  overlays/staging.json
  overlays/production.json
  assets/
  REVIEW.md
```

The required base package sections are `package`, `instance`, `modules`,
`policies`, `scoringProfiles`, `vocabularies`, `approvalGates`,
`workflowDefaults`, `branding`, `integrations`, `pluginAllowList`,
`assetStorage`, `featureFlags`, and `observability`. Optional
`moduleSpecific` sections configure enabled modules without creating code forks.
Optional `extensions` may hold non-critical review notes, but unknown critical
top-level sections are rejected so operators cannot accidentally introduce a
parallel customization mechanism.

Version fields are explicit:

- `package.schemaVersion` is the schema contract version. Application release
  `0.1.x` supports `church-config.v1`; versions not in the supported set are
  rejected, deprecated versions emit validation warnings, and explicitly
  rejected versions fail validation.
- `package.packageVersion` is the semantic version of the church package
  content and changes whenever reviewed configuration changes.
- `package.applicationCompatibility.minVersion` and `maxExclusiveVersion`
  define the application release interval allowed to consume the package.

Environment overlays are applied only after the base package validates and must
be validated again after merge. Overlays may change environment-specific
resource bindings, endpoints, feature flags, branding assets, and observability
destinations, but they must not remove mandatory policy, approval, scoring, or
storage sections. Operators promote the exact reviewed artifact from
development to staging to production by validating, producing a JSON diff,
recording human approval in `REVIEW.md`, and archiving the promoted artifact.

Validation fails for missing required sections, unknown critical sections,
malformed integration references, malformed plugin allow-list entries, plaintext
secret values, active scoring profiles that do not exist, module-specific
integration bindings that do not reference declared integrations, and
application releases outside the declared compatibility range. Secrets are never
embedded in the package; only secret keys or secret-manager paths are allowed.

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

## Validation Enforcement

Local tooling and application startup share the same contract intent. Operators
run the TypeScript validator from `@cadentia/intent-contracts` during package
review and provisioning. The API also exposes a startup guard controlled by
`CADENTIA_CHURCH_CONFIG_PATH`; when set, the Spring Boot process reads the
package file and rejects startup if required sections are missing, unknown
critical sections are present, integration or plugin references are malformed,
plaintext secret values are embedded, mandatory approval gates are disabled, or
the running application version is outside the package compatibility interval.
