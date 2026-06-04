# ADR-022 Isolated Instance Provisioning Runbook

This runbook describes how operators provision or reconcile one isolated Cadentia
instance from a validated church configuration package. It supports managed
single-tenant, private-cloud, self-hosted, and church-managed deployments without
a shared SaaS control plane.

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
