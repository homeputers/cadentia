# ADR-022 Isolated Instance Provisioning Runbook

This runbook describes how operators provision or reconcile one isolated Cadentia
instance from a validated church configuration package. It supports managed
single-tenant, private-cloud, self-hosted, and church-managed deployments without
a shared SaaS control plane.

For package authoring, seed catalog governance, promotion review, and
contributor guardrails, use
`docs/runbooks/adr-022-package-governance.md` alongside this lifecycle runbook.

## Isolation model

Cadentia treats the deployed instance as the church boundary. Provisioning must
create or bind resources that are unique to the church instance:

- PostgreSQL database/schema identity, represented in the manifest as a distinct
  database resource and secret reference.
- Object storage bucket or namespace from the package `assetStorage` section.
- Cache namespace and connection secret reference.
- Event stream namespace and per-instance stream names.
- Integration, export, database, cache, and encryption-key secret references.
- API runtime configuration that points the application at the validated church
  package and provisioned resource identifiers.

Do not reuse mutable databases, buckets, cache keyspaces, event streams, or
secret material between church instances. Starter catalog content must be copied
through local import and approval workflows instead of read from a shared live
catalog.

## Prerequisites

1. Node.js 20.11 or newer and npm 10 or newer.
2. A reviewed `cadentia-church-package.json` that passes the church config v1
   contract.
3. Operator credentials for the target infrastructure environment.
4. A secret manager or environment-secret resolver that can materialize the
   generated `*_REF` bindings at application start. Generated manifests and env
   files intentionally contain references only, not plaintext credentials.
5. For API startup, the provisioned database must already be reachable by the
   resolved `CADENTIA_DB_URL` secret. Spring Flyway applies migrations from
   `apps/api/src/main/resources/db/migration` when the API starts.

Validate the package before provisioning:

```bash
npm run build --workspace @cadentia/intent-contracts
node packages/intent-contracts/bin/validate-church-config.mjs \
  path/to/cadentia-church-package.json \
  --app-version=0.1.0
```

## Self-hosted or church-managed path

Use the local provisioning CLI to create a repeatable manifest and API env file:

```bash
npm run build --workspace @cadentia/provisioning
node packages/provisioning/bin/provision-isolated-instance.mjs \
  --package=path/to/cadentia-church-package.json \
  --output-dir=deployment/provisioned \
  --state-dir=deployment/provisioned/state \
  --operator=ops@example.org \
  --mode=self-hosted
```

The command writes:

- `deployment/provisioned/manifests/<instance>.<environment>.provisioning-manifest.json`
- `deployment/provisioned/env/<instance>.<environment>.api.env`
- `deployment/provisioned/state/<instance>.<environment>.state.json`

Operators may translate the manifest into Terraform, Kubernetes manifests,
Docker Compose overrides, or direct infrastructure commands. The generated API
env file is a binding template: resolve `CADENTIA_DB_URL_REF` and
`CADENTIA_CACHE_URL_REF` to runtime secrets, then export the remaining values as
application configuration.

## Managed single-tenant or private-cloud path

Managed operators run the same CLI with `--mode=managed-single-tenant` or
`--mode=private-cloud` and keep the state file in their controlled operational
repository or encrypted state backend. The resulting manifest is the audit
record for the instance and should be attached to the change request.

Managed automation can use the manifest fields directly:

- `resources.database.identifier` and `resources.database.jdbcUrlRef` for the
  per-instance database and database-url secret binding.
- `resources.objectStorage.identifier`, `namespacePrefix`, and
  `encryptionKeyRef` for assets.
- `resources.cache.namespace` and `connectionRef` for cache isolation.
- `resources.eventStreams.namespace` and `streams` for event routing.
- `resources.secrets[]` for all secret references that must be present before
  API startup.
- `resources.applicationConfiguration.envFile` for the rendered API config
  template.

## Idempotent reruns

Provisioning state is keyed by instance ID and environment. Re-running the CLI
with the same package or a newer compatible package preserves the original
resource identifiers and records the previous manifest digest. This allows
operators to safely reconcile drift or update application configuration without
duplicating databases, buckets, cache namespaces, event streams, or secrets.

If a package changes a resource that is declared immutable for an existing
instance, treat it as a migration or clone operation rather than editing the
state file by hand.

## Smoke check

After provisioning, run the scripted smoke check against the manifest:

```bash
node packages/provisioning/bin/smoke-check-instance.mjs \
  --manifest=deployment/provisioned/manifests/<instance>.<environment>.provisioning-manifest.json
```

The smoke check verifies that the API env file points at the manifest's instance
ID, package path, database reference, cache namespace, event namespace, and that
manifest/env contents do not contain obvious plaintext credential material. In a
live environment, follow this with API startup using the generated env file and a
secret resolver that materializes the referenced database and cache connection
values.

## Migration behavior

The provisioning manifest records the latest migration file known to the checked
out application and marks migration status as `pending`. Cadentia's API uses
Spring Flyway at startup, so migrations are applied only against the isolated
database referenced by the resolved database secret. Operators should review API
startup logs and Flyway history in the provisioned database before marking the
change complete.

## Rollback behavior

Rollback must not point one church instance at another church's resources.
Preferred rollback order:

1. Stop the API instance.
2. Restore the previous application artifact and the previous generated API env
   file for the same instance.
3. Restore the isolated database from that instance's backup if migrations or
   package changes are not backward compatible.
4. Re-run provisioning with `--action=rollback` and the previous reviewed
   package to regenerate an audit manifest.
5. Run the smoke check and start the API with the resolved secrets for the same
   isolated resources.

Never roll back by sharing another instance's database, object storage bucket,
cache namespace, event stream namespace, or secret references.

## Lifecycle workflow planner

Use the lifecycle workflow planner before upgrade, backup, restore, export, or
staging-clone work. The planner is intentionally a planning and audit-evidence
step: it validates the reviewed church package and provisioning manifest, checks
resource scope, records package/application/schema versions, and emits
verification commands before the operator runs infrastructure-specific database
or object-storage commands.

```bash
npm run build --workspace @cadentia/provisioning
node packages/provisioning/bin/plan-instance-lifecycle.mjs \
  --workflow=<upgrade|backup|restore|export|staging-clone> \
  --package=path/to/cadentia-church-package.json \
  --manifest=deployment/provisioned/manifests/<instance>.<environment>.provisioning-manifest.json \
  --output-dir=deployment/provisioned \
  --app-version=0.1.0 \
  --operator=ops@example.org \
  --reason="change request or ticket"
```

Every generated lifecycle plan is written under
`deployment/provisioned/lifecycle/` and must be attached to the operator change
record. Verify the plan before proceeding:

```bash
node packages/provisioning/bin/verify-lifecycle-workflow.mjs \
  --plan=deployment/provisioned/lifecycle/<operation-id>.json
```

The verification command confirms that compatibility was validated, the workflow
is scoped to one provisioning manifest, secret references remain references, and
starter catalog eligibility remains `instance-local-approval-required`.
Lifecycle tooling must use operator-scoped infrastructure credentials. Do not
use normal church user credentials for restore or export, because those paths
must never be able to enumerate multiple instances through the application data
plane.

## Upgrade workflow

Inputs:

- Reviewed target church package.
- Current provisioning manifest for the same instance and environment.
- Completed backup lifecycle plan for the same instance.
- Target application artifact and migration set under
  `apps/api/src/main/resources/db/migration`.

Plan the pre-upgrade backup first:

```bash
node packages/provisioning/bin/plan-instance-lifecycle.mjs \
  --workflow=backup \
  --package=path/to/cadentia-church-package.json \
  --manifest=deployment/provisioned/manifests/<instance>.<environment>.provisioning-manifest.json \
  --output-dir=deployment/provisioned \
  --operator=ops@example.org \
  --reason="pre-upgrade backup for CR-1234"
```

Then plan the upgrade with the backup evidence:

```bash
node packages/provisioning/bin/plan-instance-lifecycle.mjs \
  --workflow=upgrade \
  --package=path/to/cadentia-church-package.json \
  --manifest=deployment/provisioned/manifests/<instance>.<environment>.provisioning-manifest.json \
  --backup-manifest=deployment/provisioned/lifecycle/<backup-operation-id>.json \
  --output-dir=deployment/provisioned \
  --app-version=0.1.0 \
  --operator=ops@example.org \
  --reason="CR-1234 package/application upgrade"
```

Do not start migrations unless the upgrade plan shows package validation,
manifest identity validation, a validated backup, current schema migration, and
target schema migration. Record pre-migration Flyway state, place the API and
workers in maintenance mode, deploy the target package/application artifact, and
allow Spring Flyway to migrate only the isolated database referenced by the
manifest's database secret reference.

Verification:

```bash
node packages/provisioning/bin/verify-lifecycle-workflow.mjs \
  --plan=deployment/provisioned/lifecycle/<upgrade-operation-id>.json
node packages/provisioning/bin/smoke-check-instance.mjs \
  --manifest=deployment/provisioned/manifests/<instance>.<environment>.provisioning-manifest.json
flyway info -url="$CADENTIA_DB_URL" -table=flyway_schema_history
```

Record pre/post package version, application version, package digest, manifest
digest, and Flyway migration version in the change record.

Rollback:

1. Stop the upgraded API and background workers.
2. Restore the validated pre-upgrade database and asset backup for the same
   instance.
3. Restore the previous package, application artifact, provisioning manifest,
   and API env template.
4. Re-run provisioning with `--action=rollback` against the previous package.
5. Re-run lifecycle verification and smoke checks before returning traffic.

Failure triage:

- Stop immediately if package validation, backup validation, schema version
  capture, or secret-redaction checks fail.
- Compare package digest, manifest digest, backup digest, and Flyway history.
- Confirm the API resolved the database secret reference from this manifest only.
- Do not point the instance at another church database as a rollback shortcut.

Retention: retain upgrade evidence, pre/post version records, and the
pre-upgrade backup per the production backup retention schedule, at least 35
days.

## Backup workflow

Backups must cover all rebuild inputs without storing plaintext secrets:

- Isolated database snapshot from `resources.database.identifier`.
- Object storage assets under `resources.objectStorage.namespacePrefix` only.
- Reviewed church package and provisioning manifest.
- Generated API env template.
- Secret reference inventory from `resources.secrets[]`, not secret values.
- Checksums, backup time, operator identity, and retention class.

Plan and verify:

```bash
node packages/provisioning/bin/plan-instance-lifecycle.mjs \
  --workflow=backup \
  --package=path/to/cadentia-church-package.json \
  --manifest=deployment/provisioned/manifests/<instance>.<environment>.provisioning-manifest.json \
  --output-dir=deployment/provisioned \
  --operator=ops@example.org \
  --reason="scheduled backup"
node packages/provisioning/bin/verify-lifecycle-workflow.mjs \
  --plan=deployment/provisioned/lifecycle/<backup-operation-id>.json
```

Example deterministic infrastructure checks after provider-specific backup
commands complete:

```bash
sha256sum backups/<instance>/<timestamp>/database.dump
sha256sum backups/<instance>/<timestamp>/assets.tar.zst
jq '.resourceScope.database,.resourceScope.objectStorageNamespace' \
  deployment/provisioned/lifecycle/<backup-operation-id>.json
```

Rollback for a failed backup is to delete incomplete artifacts, retain the last
known-good backup, preserve the failed plan and command output, and rerun backup
from the unchanged source instance.

Failure triage:

- Confirm database dump source matches the manifest database identifier.
- Confirm object copy source is limited to the manifest namespace prefix.
- Confirm archived config contains secret references only.
- Confirm checksum files were generated after upload/copy completion.

Retention: retain daily backups for 35 days, monthly backups for 13 months, and
annual backups for 7 years unless the church-approved policy requires longer.

## Restore workflow

Restores must rebuild one isolated instance from a validated backup and must not
introduce shared runtime starter catalog eligibility. Seeded starter catalog
records remain copied data subject to local approval gates after restore.

Plan and verify before restore:

```bash
node packages/provisioning/bin/plan-instance-lifecycle.mjs \
  --workflow=restore \
  --package=path/to/cadentia-church-package.json \
  --manifest=deployment/provisioned/manifests/<instance>.<environment>.provisioning-manifest.json \
  --backup-manifest=deployment/provisioned/lifecycle/<backup-operation-id>.json \
  --restore-backup=backups/<instance>/<timestamp>/backup-manifest.json \
  --output-dir=deployment/provisioned \
  --operator=ops@example.org \
  --reason="restore request IR-456"
node packages/provisioning/bin/verify-lifecycle-workflow.mjs \
  --plan=deployment/provisioned/lifecycle/<restore-operation-id>.json
```

Restore sequence:

1. Stop the API and background workers for the target instance.
2. Verify backup checksums and manifest identity.
3. Restore the database into the target instance database resource only.
4. Restore assets into the target object-storage namespace only.
5. Restore the reviewed package, provisioning manifest, API env template, and
   secret reference inventory.
6. Resolve current environment secrets through the secret manager; do not import
   plaintext secret values from the backup.
7. Start the API and verify local approval-gated catalog eligibility.

Post-restore verification:

```bash
node packages/provisioning/bin/smoke-check-instance.mjs \
  --manifest=deployment/provisioned/manifests/<instance>.<environment>.provisioning-manifest.json
jq '.resourceScope.starterCatalogEligibility' \
  deployment/provisioned/lifecycle/<restore-operation-id>.json
flyway info -url="$CADENTIA_DB_URL" -table=flyway_schema_history
```

Rollback for a failed restore is to stop the restored API, preserve failed
restore evidence, restore the previous known-good backup, and rerun smoke checks
against the same isolated resources.

Failure triage:

- Stop if the backup manifest, package, or provisioning manifest identity does
  not match the target instance.
- Confirm no restore command used credentials that can read multiple instances
  through normal application paths.
- Confirm object storage restore did not write outside the target namespace.
- Confirm starter catalog records are not treated as live shared eligibility
  sources.

Retention: retain restore evidence and source backup checksums for the same
period as the restored backup artifact.

## Export workflow

Exports are for church-owned data portability and must exclude other instances,
operator secrets, secret values, caches, and operational event internals. Export
tooling must use operator-scoped credentials limited to the target instance's
resource set, not normal user credentials.

Plan and verify:

```bash
node packages/provisioning/bin/plan-instance-lifecycle.mjs \
  --workflow=export \
  --package=path/to/cadentia-church-package.json \
  --manifest=deployment/provisioned/manifests/<instance>.<environment>.provisioning-manifest.json \
  --output-dir=deployment/provisioned \
  --operator=ops@example.org \
  --reason="church data export request"
node packages/provisioning/bin/verify-lifecycle-workflow.mjs \
  --plan=deployment/provisioned/lifecycle/<export-operation-id>.json
```

Export scope should include church-owned catalog metadata, local arrangements,
service plans, approval history, locally uploaded assets, and non-secret
configuration references allowed by the church export policy. The export must not
include operator credentials, secret values, another instance's records, shared
starter package live catalog reads, cache data, or private telemetry internals.

Deterministic checks after export generation:

```bash
sha256sum exports/<instance>/<timestamp>/cadentia-export.tar.zst
jq '.exportPolicy' deployment/provisioned/lifecycle/<export-operation-id>.json
jq -e '.resourceScope.crossInstanceNormalUserReadsAllowed == false' \
  deployment/provisioned/lifecycle/<export-operation-id>.json
```

Rollback for a failed export is to revoke the export artifact, delete partial
files, preserve redaction logs, and rerun export after fixing scope or redaction
failures.

Failure triage:

- Confirm export queries use the isolated database from the manifest.
- Confirm asset export reads only the manifest namespace prefix.
- Scan export metadata for secret values before delivery.
- Confirm export redaction report states that other instances and operator
  secrets were excluded.

Retention: retain export artifacts only for the church-approved delivery window;
delete operator working copies within 7 days after acceptance.

## Staging clone workflow

A staging clone is a non-production copy for validation. It must use staging-safe
secret references, disabled or overridden integrations, and explicit clone
provenance. Never copy production secret values into staging.

Plan with a source manifest and a backup plan:

```bash
node packages/provisioning/bin/plan-instance-lifecycle.mjs \
  --workflow=staging-clone \
  --package=path/to/staging/cadentia-church-package.json \
  --manifest=deployment/provisioned/manifests/<instance>.staging.provisioning-manifest.json \
  --source-manifest=deployment/provisioned/manifests/<instance>.production.provisioning-manifest.json \
  --backup-manifest=deployment/provisioned/lifecycle/<production-backup-operation-id>.json \
  --output-dir=deployment/provisioned \
  --operator=ops@example.org \
  --reason="release candidate staging clone"
node packages/provisioning/bin/verify-lifecycle-workflow.mjs \
  --plan=deployment/provisioned/lifecycle/<clone-operation-id>.json
```

Clone sequence:

1. Provision or reconcile the staging package and manifest so resources are
   non-production and isolated.
2. Restore the source backup into staging database and asset resources only.
3. Replace every secret binding with staging-safe references; do not copy
   production secret values.
4. Disable or override outbound integrations, telemetry exports, webhooks,
   scheduled notifications, and any destructive automation.
5. Record source manifest digest, backup digest, target package digest, and
   clone provenance.
6. Start staging and run smoke checks against the staging manifest.

Post-clone verification:

```bash
node packages/provisioning/bin/smoke-check-instance.mjs \
  --manifest=deployment/provisioned/manifests/<instance>.staging.provisioning-manifest.json
jq '.clonePolicy.productionSecretsCopied,.clonePolicy.integrations,.clonePolicy.provenance' \
  deployment/provisioned/lifecycle/<clone-operation-id>.json
jq -e '.environment != "production"' \
  deployment/provisioned/lifecycle/<clone-operation-id>.json
```

Rollback for a failed staging clone is to destroy the staging clone resources or
restore the prior staging backup. Never reconnect staging to production secrets
or production outbound integrations.

Failure triage:

- Stop if the target package environment is `production`.
- Confirm staging database, asset namespace, cache namespace, and event
  namespace differ from production resources.
- Confirm secret references point to staging-safe paths or environment bindings.
- Confirm integrations are disabled or overridden before any staging API startup.

Retention: retain staging clones for the approved test window, normally no more
than 30 days, then destroy or refresh from a new backup.

## Operator-only cross-instance administration

Cross-instance administration is not part of the normal church admin UI or API.
Operators must use the explicit `cadentia-operator-admin` CLI after the
provisioning package has been built. The tool records support/operations audit
evidence separately from church-local user audit events and refuses normal church
RBAC roles such as worship leader, catalog editor, reviewer, or church admin.

### Credential issuance and scope

Security issues short-lived JSON credentials with this shape:

```json
{
  "credentialVersion": "cadentia.operator-credential.v1",
  "credentialId": "cred-2026-06-04T12",
  "operatorId": "ops@example.org",
  "role": "cadentia-operator",
  "issuedAt": "2026-06-04T11:00:00.000Z",
  "expiresAt": "2026-06-04T15:00:00.000Z",
  "scopes": ["operator.instances.inspect", "operator.instances.backup"],
  "allowedInstanceIds": ["river-city-worship"],
  "issuer": "security@example.org",
  "publicKeyRef": "kms:/cadentia/operators/ops@example.org/2026-06-04"
}
```

Rules:

- Prefer one credential per ticket, scoped to the specific instance and action.
- Set expirations in hours, not days. Do not issue broad, long-lived credentials
  when action-scoped and instance-scoped credentials are possible.
- Use `allowedInstanceIds` for explicit targets. A wildcard is reserved for
  incident response leadership and must be documented in the incident record.
- Store credential material outside the repository; do not commit credentials,
  secret values, tokens, database URLs, private lyrics, or personal data.
- Rotate issuer signing keys and operator KMS/public-key references on the
  security calendar and immediately after any suspected disclosure.

### Required operator command pattern

Every operator action requires the credential, explicit target instance, reason,
and (where applicable) manifest and lifecycle evidence:

```bash
npm run build --workspace @cadentia/provisioning
node packages/provisioning/bin/operator-admin.mjs \
  --action=<list|inspect|upgrade|backup|restore|export|clone> \
  --credential=/secure/operator-credentials/<credential-id>.json \
  --target-instance=<instance-id> \
  --manifest=deployment/provisioned/manifests/<instance>.<environment>.provisioning-manifest.json \
  --lifecycle-plan=deployment/provisioned/lifecycle/<operation-id>.json \
  --output-dir=deployment/provisioned \
  --reason="ticket or incident reason"
```

The CLI writes JSONL records to
`deployment/provisioned/operator-audit/operator-audit.jsonl`. Each record has
`activityType=operator-support`, the operator identity, credential ID, explicit
instance target, reason, timestamp, manifest digest, before/after or lifecycle
references, and a hash chain (`previousRecordHash` and `recordHash`) to make
removal or reordering evident.

The `list` action is intentionally operator-only. It is used to confirm a scoped
instance from provisioning manifests, not to expose an application API that normal
church users can call to enumerate other churches.

### Audit queries

Operator audit can be queried by operator, instance, action, and time window with an operator credential that includes `operator.audit.query`:

```bash
node packages/provisioning/bin/operator-admin.mjs \
  --action=query-audit \
  --credential=/secure/operator-credentials/<credential-id>.json \
  --audit-log=deployment/provisioned/operator-audit/operator-audit.jsonl \
  --operator=ops@example.org \
  --target-instance=<instance-id> \
  --filter-action=backup \
  --from=2026-06-04T00:00:00.000Z \
  --to=2026-06-05T00:00:00.000Z
```

Attach the query output to support tickets, change requests, incident records,
and church-requested export/restore evidence as appropriate.

### Break-glass procedure

Break-glass credentials must use `role=cadentia-break-glass-operator` and include
`breakGlass.incidentId` plus `breakGlass.approvedBy`. They are only valid for a
live incident where normal scoped issuance would delay containment or recovery.
After use:

1. Preserve the operator audit log and lifecycle plans.
2. Rotate affected operator credentials and any infrastructure credentials that
   may have been exposed.
3. Review every record for the incident time window by operator, instance, and
   action.
4. Notify church stakeholders according to the incident severity and contract.
5. File a post-incident review explaining why break-glass was needed and what
   follow-up controls will prevent unnecessary future use.

### Incident response and failure handling

If operator tooling fails authorization, scope checks, manifest identity checks,
secret-redaction checks, or audit writes, stop before touching infrastructure.
Do not retry with normal church user credentials and do not move the operation
into the church admin UI. Preserve command output, revoke or rotate the failing
credential if compromise is possible, and escalate to security for triage.

Operator records must not log secret values, private lyrics, or sensitive
personal data. Operator catalog changes cannot mark shared or starter catalog
items recommendable; seeded content still has to pass the target instance's local
approval and governance workflow.

### Guardrail checks

Run the normal-path isolation check before release:

```bash
npm run check:adr-022-operator-admin-guardrails
```

The check verifies that normal application controllers and normal RBAC authority
constants do not expose operator administration roles, scopes, or cross-instance
operator APIs, and that the operator CLI requires explicit credentials, target
instance, reason capture, query support, and hash-chained audit records.
