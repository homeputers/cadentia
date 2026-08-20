# ADR-022 Package Governance, Promotion, and Contributor Runbook

This runbook documents how Cadentia church configuration packages are authored,
reviewed, validated, promoted, and supported. It complements the isolated
instance provisioning runbook by focusing on package content, seed catalog
governance, operator administration rules, and developer guardrails.

## Source-of-truth model

A church configuration package is the customization source of truth for one
isolated deployment. It defines the instance identity, enabled modules, policies,
scoring profile, vocabularies, approval gates, branding, integrations, plugin
allow-list, asset storage, feature flags, observability, and optional seed
package references.

Do not represent church customization by adding shared tenant-filtered
recommendation eligibility. Shared multi-tenant catalog filters are not an
acceptable implementation path. The backend Recommendation Engine must evaluate
only the local approved catalog and local instance configuration for the isolated
deployment.

Internationalization follows the same source-of-truth model: set the required
`instance.locale` value in the reviewed package. The API exposes that effective
locale to the admin web and uses it for Telegram system copy. See
[Internationalization configuration](../i18n-configuration.md) for supported
catalogs, validation, deployment, and fallback behavior.

## Package authoring guidance

Use a reviewed package directory instead of hand-editing production runtime
configuration:

```text
church-config/
  cadentia-church-package.json
  overlays/
    development.json
    staging.json
    production.json
  REVIEW.md
```

Authoring rules:

- Keep `package.schemaVersion`, `package.packageVersion`, and
  `package.applicationCompatibility` current and explicit.
- Use stable, non-secret `instance.instanceId` values suitable for audit and
  support correlation.
- Configure policies, scoring profiles, vocabularies, approval gates,
  workflows, modules, plugins, integrations, assets, feature flags, and
  observability explicitly; do not depend on hidden defaults.
- Store credentials only as secret references such as `env:`, `vault:`,
  `aws-sm:`, `gcp-sm:`, `azure-kv:`, or `secret-manager:`. Never commit
  plaintext tokens, database URLs, webhook secrets, private church data, or
  personal data.
- Do not include copyrighted lyric bodies in example packages, fixtures,
  overlays, review notes, or exported sample artifacts. Use short synthetic test
  text or metadata-only examples.
- Record human review notes, risk decisions, seed-package diffs, and promotion
  approval in `REVIEW.md` or the change ticket.

## Review and validation commands

Validate the base package before applying overlays:

```bash
npm --workspace @cadentia/intent-contracts run validate:church-config -- \
  path/to/church-config/cadentia-church-package.json \
  --app-version=0.1.0
```

Review package content without printing secret values:

```bash
jq '.package,.instance,.modules,.policies,.approvalGates,.pluginAllowList' \
  path/to/church-config/cadentia-church-package.json
jq -e '.. | strings | select(test("^(env|vault|aws-sm|gcp-sm|azure-kv|secret-manager):"))' \
  path/to/church-config/cadentia-church-package.json >/dev/null
git diff -- path/to/church-config/cadentia-church-package.json \
  path/to/church-config/overlays/staging.json \
  path/to/church-config/overlays/production.json \
  path/to/church-config/REVIEW.md
```

Run guardrails before promotion or release:

```bash
npm run check:adr-022-runtime-guardrails
npm run check:adr-022-operator-admin-guardrails
npm test --workspace @cadentia/intent-contracts
npm test --workspace @cadentia/provisioning
```

If any command fails because an author introduced shared tenant-row eligibility,
plaintext secrets, unreviewed critical sections, or package incompatibility, stop
promotion and update the package or code before provisioning.

## Environment overlays and promotion

Promotion is artifact-based:

1. Validate the base package.
2. Apply one environment overlay to a working copy for development, staging, or
   production. Overlays may narrow resource references, integration bindings,
   observability destinations, and environment labels; they must not remove
   required governance sections.
3. Validate the merged package against the target application version.
4. Compare the candidate to the previously promoted package and attach the diff
   summary to the change ticket.
5. Provision or reconcile staging with the exact candidate artifact.
6. Run staging smoke checks, lifecycle verification, and recommendation
   regression tests against the isolated staging resources.
7. Promote the same reviewed artifact to production. Do not hand-edit production
   packages or manifests.

Example promotion review commands:

```bash
npm --workspace @cadentia/intent-contracts run validate:church-config -- \
  deployment/packages/<instance>/staging/cadentia-church-package.json \
  --app-version=0.1.0
node packages/provisioning/bin/provision-isolated-instance.mjs \
  --package=deployment/packages/<instance>/staging/cadentia-church-package.json \
  --output-dir=deployment/provisioned \
  --state-dir=deployment/provisioned/state \
  --operator=ops@example.org \
  --mode=self-hosted
node packages/provisioning/bin/smoke-check-instance.mjs \
  --manifest=deployment/provisioned/manifests/<instance>.staging.provisioning-manifest.json
```

## Seed catalog governance

Global or denominational seed packages are allowed only as reviewed starter
content. Seeded songs, arrangements, tags, provenance, and source metadata must
be copied or synchronized into the instance-local staging area and pass the same
local governance workflow as other imports.

Local approval requirements:

- Doctrinal, musical, licensing, provenance, and policy approval must be recorded
  in the target instance before seeded content becomes recommendable.
- Seed package provenance must include source package identifier, version,
  content digest, import batch, reviewer, and approval decisions.
- Refreshing a seed package must not overwrite local arrangements, tags,
  approval decisions, rejection notes, or audit history without explicit
  reviewer merge actions.
- A globally trusted or denominationally approved package can reduce review
  effort but cannot bypass local church approval gates.
- Recommendation candidate reads and approved-catalog reads must exclude seeded
  records until the target instance marks them locally approved.

Recommended checks during seed review:

```bash
jq '.moduleSpecific.catalogImport.seedPackageRefs' \
  path/to/church-config/cadentia-church-package.json
jq '.approvalGates' path/to/church-config/cadentia-church-package.json
npm run check:adr-022-runtime-guardrails
```

## Operator administration rules

Operator tooling may cross instance boundaries only through explicit operational
workflows. Operators must use scoped, time-bound credentials, provide a target
instance, state a reason, attach manifest or lifecycle evidence, and preserve the
hash-chained operator audit log.

Use the published operator CLI; do not move these actions into normal church
admin UI/API paths:

```bash
node packages/provisioning/bin/operator-admin.mjs \
  --action=<list|inspect|upgrade|backup|restore|export|clone|query-audit> \
  --credential=/secure/operator-credentials/<credential-id>.json \
  --target-instance=<instance-id> \
  --manifest=deployment/provisioned/manifests/<instance>.<environment>.provisioning-manifest.json \
  --lifecycle-plan=deployment/provisioned/lifecycle/<operation-id>.json \
  --output-dir=deployment/provisioned \
  --reason="ticket or incident reason"
```

Operator actions must not log secret values, private lyrics, private personal
data, or full exported artifacts in audit records. Operator catalog assistance
cannot mark shared or seeded content recommendable; local approval still gates
recommendation eligibility.

## Acceptable and unacceptable `instanceId` usage

Acceptable uses:

- Audit events, operator audit records, support tickets, and incident timelines.
- Telemetry correlation using low-cardinality labels that do not expose private
  church data, people, lyrics, or secrets.
- Licensing and entitlement checks for a deployed instance.
- Backup, restore, export, and staging-clone manifests and provenance.
- Secret reference, cache namespace, event namespace, asset namespace, and
  integration binding selection from the validated package and manifest.

Unacceptable uses:

- Tenant columns or tenant filters on shared recommendation candidate tables.
- Shared global or denominational live catalogs that become recommendable because
  a query includes `instanceId` or `tenant_id`.
- Normal user APIs that list, inspect, export, back up, restore, or clone other
  church instances.
- Runtime defaults that activate modules, plugins, integrations, or catalog
  eligibility when a package omits the required setting.

## Testing expectations

Changes that touch ADR-022 behavior should include deterministic tests or checks
for two differently configured instances. Test fixtures must avoid real secrets,
real private church data, production resource IDs, and copyrighted lyric bodies.

Minimum coverage expectations:

- Package contract tests accept valid packages and reject missing required
  governance sections, plaintext secrets, malformed references, incompatible
  application versions, and unknown critical sections.
- Provisioning tests prove reruns are idempotent and resource identifiers remain
  instance-local.
- Runtime guardrail checks fail on shared tenant-row recommendation eligibility
  and cross-instance normal workflow reads.
- Operator guardrail checks prove normal application controllers and normal RBAC
  authorities do not expose cross-instance operator actions.
- Recommendation tests prove each instance uses only its local approved catalog,
  local policies, local scoring profile, and local service context.
- Seed import tests prove seeded but unapproved content is not recommendable.
