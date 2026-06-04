# ADR-022 Implementation Plan: Packaged Deployment and Church Customization Model

## Objective

Implement Cadentia as a repeatably packaged, isolated-instance application so
churches can run with separate persistence, assets, secrets, caches, events,
configuration, approval policy, catalogs, integrations, and operational tooling
without relying on shared runtime tenant filters for recommendation eligibility.

## Status

- Subtask 1: Complete - canonical v1 package schema, local validator, fixtures, and documentation are implemented.
- Subtask 2: Planned
- Subtask 3: Planned
- Subtask 4: Planned
- Subtask 5: Complete - lifecycle planner, verification tooling, tests, and runbook workflows cover upgrade, backup, restore, export, and staging clone operations.
- Subtask 6: Planned
- Subtask 7: Planned
- Subtask 8: Planned

## Subtask 1: Define the versioned church configuration package contract

### Context

ADR-022 requires each deployed church instance to be driven by a reviewable and
reproducible configuration package. The package must capture instance identity,
enabled modules, policies, scoring profiles, controlled vocabularies, approval
gates, workflow defaults, branding, integrations, plugin allow-lists, asset
storage settings, feature flags, and observability/export configuration.

**Codebase anchors**
- API service and configuration loading: `apps/api`
- Contract/schema package: `packages/intent-contracts` if shared schema fixtures
  or validation helpers are exposed outside the API
- Infrastructure and deployment artifacts: inspect existing repo directories
  before adding new ones
- Documentation to update: `docs/ARCHITECTURE.md`, `docs/adr/ADR-022-packaged-deployment-and-church-customization-model.md`, and this plan

### Prompt

Create a canonical church configuration package schema and validation workflow.
Define the package file layout, semantic version fields, required sections,
optional module-specific sections, environment overlay rules, validation errors,
and compatibility rules between package versions and application releases.
Implement local validation tooling and tests that prove invalid packages fail
before provisioning or application startup.

### Acceptance criteria

- A documented configuration package format exists with fields for instance
  identity, policies, modules, scoring profiles, vocabularies, approval gates,
  workflow defaults, branding, integrations, plugin allow-lists, asset storage,
  feature flags, and observability/export settings.
- Package versions are explicit and include compatibility rules for supported,
  deprecated, and rejected schema versions.
- Validation rejects missing required sections, unknown critical sections,
  malformed integration/plugin references, and incompatible package versions.
- Tests cover at least one complete valid package and representative invalid
  packages for each required ADR-022 configuration category.
- Documentation explains how operators review, diff, and promote packages across
  development, staging, and production instances.

### Restrictions

- Do not embed church-specific secrets in the package; reference secret keys or
  secret-manager paths only.
- Do not let application defaults silently replace missing mandatory policy,
  approval, scoring, or storage sections.
- Do not introduce runtime song recommendation behavior that depends on a shared
  tenant table or cross-tenant filter.
- Do not create one-off per-church code forks as the customization mechanism.

### Implementation notes

Implemented in this subtask:

- Canonical schema artifact:
  `packages/intent-contracts/schemas/church-config/v1/church-config-package.schema.json`.
- Shared validation helpers and compatibility rules:
  `packages/intent-contracts/src/churchConfig.ts`.
- Local validator command:
  `npm --workspace @cadentia/intent-contracts run validate:church-config -- <package> --app-version=<semver>`.
- Deterministic fixtures covering one complete valid package and invalid
  missing-section, unknown critical section, malformed integration/plugin,
  plaintext secret-reference, and incompatible application-version cases under
  `packages/intent-contracts/fixtures/church-config/v1/`.
- Contract tests in
  `packages/intent-contracts/test/churchConfigContract.test.ts`.

The v1 package requires explicit instance identity, modules, policies, scoring
profiles, vocabularies, approval gates, workflow defaults, branding,
integrations, plugin allow-lists, asset storage, feature flags, and
observability/export settings. Validation intentionally rejects missing
mandatory policy, approval, scoring, and storage sections rather than replacing
them with runtime defaults.

Operator review and promotion are documented in ADR-022 and the architecture
document: validate the base package, apply one environment overlay, validate the
merged package for the target application version, diff against the previously
promoted artifact, record review notes, and promote the same artifact from
development to staging to production.

## Subtask 2: Build isolated-instance provisioning and deployment automation

### Context

ADR-022 chooses deployment isolation as the church boundary. Each church needs a
separate database, object storage bucket or namespace, cache namespace, event
streams, secrets, and application configuration. Provisioning must be repeatable
for self-hosted, private-cloud, managed single-tenant, and church-managed
deployments.

**Codebase anchors**
- Infrastructure/deployment directories discovered in the repository
- API runtime configuration in `apps/api`
- Operational documentation under `docs/` and `docs/runbooks/`

### Prompt

Implement provisioning automation that accepts a validated church configuration
package and creates or updates an isolated Cadentia instance. The automation
should allocate separate infrastructure resources, inject runtime configuration,
apply migrations, initialize cache/event namespaces, bind secrets by reference,
and emit a provisioning manifest that operators can audit.

### Acceptance criteria

- Provisioning can create an instance with isolated database, object storage,
  secret bindings, cache namespace, event stream namespace, and application
  configuration.
- Provisioning manifests record instance ID, package version, application
  version, resource identifiers, migration state, and operator/action metadata.
- Automation supports idempotent reruns without duplicating resources or
  corrupting existing instance data.
- Smoke tests or scripted checks verify that the provisioned API starts with the
  expected package and connects only to the provisioned resources.
- Documentation describes managed single-tenant and self-hosted provisioning
  paths, including prerequisites and rollback behavior.

### Restrictions

- Do not share mutable application databases, buckets, cache keyspaces, event
  streams, or secret material between church instances.
- Do not require normal church users to understand or execute operator
  provisioning steps.
- Do not make provisioning depend on an external SaaS control plane unless a
  local/self-hosted path remains available.
- Do not store plaintext credentials in generated manifests, logs, or package
  files.

## Subtask 3: Enforce instance-local catalog seeding and approval governance

### Context

ADR-022 permits global or denominational starter catalog packages, but those
records must be copied or synchronized into the local instance governance
workflow. No imported shared catalog item can become recommendable until it
passes the local instance's approval gates.

**Codebase anchors**
- Catalog import, staging, deduplication, and approval code in `apps/api`
- Relevant existing ADR plans: ADR-003, ADR-005, ADR-008, and ADR-011 plans in
  `docs/implementation-plans/`
- Recommendation eligibility/read-model code in `apps/api`

### Prompt

Add import/seed package handling for global and denominational catalog baselines.
Route seeded songs, arrangements, tags, provenance, and source metadata into the
same instance-local staging, deduplication, and approval workflows used by other
imports. Update recommendation eligibility checks and tests so recommendable
records must be approved within the current instance, regardless of seed origin.

### Acceptance criteria

- Seed packages can be imported into an instance-local staging area with source
  provenance and package version metadata.
- Seeded songs and arrangements are not recommendable until local approval gates
  mark them eligible.
- Local edits, arrangements, tags, and approval decisions are stored in the
  instance database rather than a shared live catalog.
- Synchronizing a newer starter package version does not overwrite local
  approvals or arrangements without explicit merge/review actions.
- Tests prove imported-but-unapproved seeded content is excluded from
  recommendation candidate reads and user-facing catalog reads that require
  approved content.

### Restrictions

- Do not recommend directly from a shared global or denominational live catalog.
- Do not bypass local doctrinal, musical, licensing, or policy review because a
  seed package is globally trusted.
- Do not erase local catalog governance history when refreshing seed packages.
- Do not let the LLM select or invent songs during seeding, review, or
  recommendation flows.

## Subtask 4: Wire runtime modules to instance configuration without tenant-row semantics

### Context

ADR-022 allows the application to keep an `instanceId` or deployment identifier
for auditability, telemetry correlation, licensing, backups, and support
operations. It does not allow recommendation eligibility or normal workflows to
be implemented as shared data-plane tenant filters.

**Codebase anchors**
- API service configuration and domain services in `apps/api`
- Recommendation engine code and tests in `apps/api`
- Plugin, integration, asset, cache, background-job, and telemetry code paths
  wherever they exist in the repository

### Prompt

Thread the validated instance configuration into runtime modules so they read
local policies, enabled modules, scoring profiles, plugin allow-lists,
integration settings, asset storage settings, cache namespaces, event namespaces,
and telemetry export settings. Add guardrails that distinguish audit/support
`instanceId` usage from forbidden shared-runtime tenant filtering.

### Acceptance criteria

- Recommendation generation uses the instance's local approved catalog, scoring
  profile, policy configuration, and service context.
- Plugins and integrations load only from the instance allow-list and configured
  credentials references.
- Asset, cache, event, background-job, and telemetry clients use configured
  instance-local resource identifiers or namespaces.
- Static checks, tests, or review tooling flag code that introduces shared
  tenant-table recommendation eligibility or cross-instance data reads in normal
  workflows.
- Audit and telemetry events include the deployment/instance identifier without
  exposing private church data or secrets.

### Restrictions

- Do not add tenant IDs to every domain model as a substitute for isolated
  deployments.
- Do not make recommendation candidate queries depend on a runtime tenant filter
  against a shared catalog.
- Do not allow disabled modules, blocked plugins, or unconfigured integrations to
  activate through code defaults.
- Do not include sensitive church data in telemetry labels or high-cardinality
  metric dimensions.

## Subtask 5: Implement upgrade, backup, restore, export, and staging clone workflows

### Context

Operators must be able to provision, upgrade, back up, restore, export, and
clone isolated instances for staging environments without introducing shared
runtime catalog eligibility. These workflows must preserve package/version
traceability and protect secrets.

**Codebase anchors**
- Infrastructure/deployment automation directories
- Database migrations in `apps/api/src/main/resources/db/migration`
- Operational docs under `docs/runbooks/`

### Prompt

Create operator workflows for instance upgrade, backup, restore, export, and
staging clone operations. Each workflow should use the church package and
provisioning manifest as inputs, validate compatibility before changes, preserve
audit evidence, and provide deterministic verification commands after completion.

### Acceptance criteria

- Upgrade workflow validates application/package/schema compatibility before
  migration and records pre/post migration versions.
- Backup and restore workflows cover database, object storage/assets,
  configuration package, provisioning manifest, and non-secret references needed
  to rebuild the instance.
- Export workflow produces church-owned data exports without leaking other
  instances' data or operator secrets.
- Staging clone workflow can create a non-production copy with environment-safe
  secrets, integration disables or overrides, and clear clone provenance.
- Runbooks include verification commands, rollback steps, retention
  expectations, and failure triage for each workflow.

### Restrictions

- Do not copy production secrets into staging clones.
- Do not allow restore/export tooling to read multiple instances through normal
  user credentials.
- Do not perform migrations without a validated backup and compatibility check.
- Do not treat shared starter catalog packages as live eligibility sources after
  restore, export, or clone operations.

### Implementation notes

Implemented in this subtask:

- Lifecycle workflow planner exported by `@cadentia/provisioning` and exposed as
  `packages/provisioning/bin/plan-instance-lifecycle.mjs`. It accepts a reviewed
  church package and provisioning manifest for upgrade, backup, restore, export,
  and staging-clone workflows.
- Lifecycle verification command
  `packages/provisioning/bin/verify-lifecycle-workflow.mjs` checks compatibility
  status, audit evidence, instance-scoped resource policy, secret redaction,
  export isolation policy, and staging-clone safety policy.
- Upgrade and restore planning require a backup manifest before any migration or
  restore steps are emitted. Upgrade plans capture manifest/application package
  versions plus current and target Flyway migration files.
- Backup plans enumerate database, object-storage namespace, church package,
  provisioning manifest, API env template, and non-secret secret-reference
  inventory as rebuild inputs.
- Export plans explicitly mark church-owned-data-only scope and exclude operator
  secrets and other instances. Staging clone plans reject production targets,
  record source manifest provenance, require disabled or overridden integrations,
  and state that production secrets are not copied.
- Tests in `packages/provisioning/test/provisioning.test.ts` cover lifecycle
  planning, required backup validation before upgrades, export redaction policy,
  and staging clone provenance/safety rules.
- Operational steps, verification commands, rollback guidance, retention
  expectations, and failure triage are documented in
  `docs/runbooks/adr-022-isolated-instance-provisioning.md`.

## Subtask 6: Create explicit cross-instance operator administration and audit tooling

### Context

ADR-022 keeps cross-instance administration outside normal user workflows. Any
operator access spanning instances must require explicit tooling, explicit
credentials, and audit logs that distinguish support/operations activity from
church user activity.

**Codebase anchors**
- Security, roles, and audit code in `apps/api`
- Operational scripts or admin tooling directories discovered in the repository
- Security runbooks in `docs/runbooks/`

### Prompt

Design and implement operator-only tooling for listing, inspecting, upgrading,
backing up, restoring, exporting, and cloning isolated instances. Require strong
operator authentication/authorization, scoped credentials, explicit target
instance selection, reason capture, and tamper-resistant audit records for every
cross-instance action.

### Acceptance criteria

- Normal church user roles cannot access cross-instance administration tools or
  APIs.
- Operator actions require explicit target instance selection, operator identity,
  action reason, timestamp, and before/after or manifest references.
- Audit records distinguish operator support actions from church-local user
  actions and can be queried by operator, instance, action, and time window.
- Documentation defines credential issuance, rotation, break-glass procedures,
  and incident response for operator tooling.
- Tests or scripted checks prove normal application paths cannot enumerate or
  read other church instances.

### Restrictions

- Do not hide cross-instance operations inside normal church admin UI flows.
- Do not allow broad, long-lived credentials when scoped or time-bound
  credentials can be used.
- Do not log secret values, private lyrics, or sensitive personal data in
  operator audit records.
- Do not bypass local instance approval/governance workflows through operator
  catalog edits.


### Implementation notes

Implemented in this subtask:

- Operator-only administration model in `@cadentia/provisioning` with explicit
  actions for list, inspect, upgrade, backup, restore, export, and clone.
- `packages/provisioning/bin/operator-admin.mjs` requires an operator credential,
  explicit target instance, reason, and optional manifest/lifecycle references;
  it is not wired into the normal church admin UI or API.
- Operator credentials are short-lived, scope-bearing, instance-scoped JSON
  documents with a separate break-glass role that requires incident metadata.
- Operator audit records use `cadentia.operator-audit.v1`,
  `activityType=operator-support`, target manifest digests, before/after or
  lifecycle references, redaction flags, query keys, and a hash chain.
- Audit query tooling filters by operator, instance, action, and time window.
- `scripts/check-adr-022-operator-admin-guardrails.mjs` proves normal
  application controllers and normal RBAC authorities do not expose
  cross-instance operator administration surfaces.
- Runbook coverage defines credential issuance, rotation, break-glass,
  incident response, audit queries, and guardrail checks.

## Subtask 7: Add isolation, configuration, and recommendation regression tests

### Context

ADR-022's main safety claim is that deployment boundaries and local governance
prevent cross-church leakage and keep recommendation behavior deterministic.
This requires regression coverage that exercises two differently configured
instances and proves their data, settings, and recommendation results remain
separate.

**Codebase anchors**
- API tests in `apps/api/src/test/java`
- Contract/schema tests in `packages/intent-contracts/test`
- Any integration/e2e test harness already present in the repository

### Prompt

Build automated tests for isolated-instance behavior. Cover package validation,
startup configuration, resource namespace selection, seed import governance,
recommendation eligibility, plugin/integration allow-lists, and cross-instance
access failures. Include fixture packages for at least two churches with
different policies, scoring profiles, catalogs, and branding.

### Acceptance criteria

- Tests demonstrate two instances cannot read each other's private songs,
  arrangements, services, assets, people, credentials, or usage history through
  normal application paths.
- Recommendation tests prove each instance uses only its local approved catalog,
  policy, scoring profile, and service context.
- Seeded but unapproved starter catalog content remains excluded until accepted
  by the local approval workflow.
- Plugin, integration, cache, event, asset, and telemetry configuration tests
  verify instance-local settings are honored.
- Test fixtures are deterministic and avoid copyrighted lyrics or private church
  data.

### Restrictions

- Do not use tests that pass only because both fixture instances have identical
  configuration.
- Do not depend on external network services for isolation regression tests.
- Do not include real credentials, production resource IDs, private church data,
  or copyrighted lyric bodies in fixtures.
- Do not assert LLM-selected songs; recommendation assertions must use backend
  deterministic selection logic and approved datasets.

## Subtask 8: Publish deployment, customization, and operations documentation

### Context

ADR-022 affects architecture, operator workflows, church customization,
governance, support, and developer contribution rules. Documentation must make
clear that isolated deployment plus versioned packages are the source of
customization, while shared tenant-filtered recommendation eligibility is not
allowed.

**Codebase anchors**
- Architecture docs: `docs/ARCHITECTURE.md`
- Implementation plan index: `docs/implementation-plans/README.md`
- Runbooks under `docs/runbooks/`
- Relevant ADR and topic docs under `docs/`

### Prompt

Update documentation for the packaged deployment model. Add architecture
narrative, package authoring guidance, provisioning and lifecycle runbooks,
operator administration rules, seed catalog governance instructions, testing
expectations, and contributor guidance for avoiding shared tenant-filtered
runtime designs.

### Acceptance criteria

- `docs/ARCHITECTURE.md` describes isolated instances, configuration packages,
  local catalog governance, and operator-only cross-instance tooling.
- `docs/implementation-plans/README.md` links this implementation plan in the
  appropriate phase.
- Runbooks document provisioning, upgrade, backup, restore, export, staging
  clone, package promotion, and operator audit workflows.
- Developer guidance explains acceptable `instanceId` usage for auditability,
  telemetry, licensing, backups, and support, and unacceptable usage for shared
  recommendation eligibility.
- Documentation includes verification commands and examples for reviewing,
  validating, and promoting church configuration packages.

### Restrictions

- Do not document shared multi-tenant data-plane filters as an acceptable
  implementation path for recommendation eligibility.
- Do not publish example packages with real secrets, real private church data, or
  copyrighted lyric bodies.
- Do not omit local approval requirements for globally or denominationally
  seeded catalog content.
- Do not leave operator tooling instructions accessible only through tribal
  knowledge or unpublished scripts.
