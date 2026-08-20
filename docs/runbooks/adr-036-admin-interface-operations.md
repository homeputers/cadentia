# ADR-036 Administrative Web Interface Operations Runbook

## Purpose

This runbook defines deployment, smoke-test, incident, and rollback procedures for the Cadentia administrative web interface. The admin UI is **not production-ready** until both the successful and failure-path checks below have been run against the implemented configuration, route names, telemetry names, and rollback controls for the target environment.

The admin web's locale is sourced from the church configuration package's
`instance.locale` through the authenticated `/admin/session` response. See
[Internationalization configuration](../i18n-configuration.md) for package
changes, supported catalogs, fallback behavior, and verification steps.

## Artifact and Deployment Model

- Artifact location: `apps/admin-web/dist/` after `npm run build -w @cadentia/admin-web`.
- Health files: `dist/admin-health.json` and `dist/admin-build.json` must be served with the same release as `dist/index.html`.
- Static cache policy:
  - Fingerprinted assets may be immutable and long-lived.
  - `index.html`, `admin-health.json`, and `admin-build.json` must be no-cache or short TTL so rollback and feature-disable changes are visible quickly.
- API contract: the frontend generated route artifact under `apps/admin-web/src/generated/cadentia-api/` must be generated from `apps/api/src/main/openapi/cadentia-api.yaml`.
- Catalog independence: deploying or rolling back the UI must not mutate catalog data, audit history, approval state, moderation state, recommendation read models, or import batches.

## Required Runtime Configuration

Deployment tooling must provide these values per church instance. Do not use production credentials or real connector tokens in tests.

| Variable | Purpose | Expected startup validation failure |
| --- | --- | --- |
| `VITE_CADENTIA_API_BASE_URL` | API origin or same-origin API path used by the generated client. | Smoke metadata reports API base URL as missing; authenticated calls fail closed. |
| `VITE_CADENTIA_AUTH_ISSUER_URL` | OIDC/identity-provider issuer. | Sign-in link falls back to `#admin-auth-not-configured`. |
| `VITE_CADENTIA_IDP_CLIENT_ID` | Public browser client ID. | Sign-in bootstrap cannot build a valid authorize URL. |
| `VITE_CADENTIA_CHURCH_INSTANCE_ID` | Isolated church-instance context passed as `X-Church-Instance-Id`. | Shell renders missing church-instance state before protected data loads. |
| `VITE_CADENTIA_FEATURE_FLAGS` | Comma-separated UI gates such as `admin-ui`, `admin-diagnostics`, and high-risk workflow flags. | Disabled routes show safe unavailable/empty states; backend RBAC still enforces access. |
| `VITE_CADENTIA_DIAGNOSTICS_ENABLED` | Enables diagnostics navigation only when backend capability also allows it. | Diagnostics route remains hidden/unavailable. |
| `VITE_CADENTIA_ADMIN_BUILD_VERSION` | Promoted admin bundle version. | Smoke metadata reports `0.0.0-local` outside CI and should block promotion. |
| `VITE_CADENTIA_ADMIN_BUILD_COMMIT` | Source revision for rollback correlation. | Smoke metadata reports `local`; block promotion unless intentionally local. |
| `VITE_CADENTIA_ADMIN_BUILD_TIMESTAMP` | Build timestamp supplied by CI. | Smoke metadata reports `local`; block promotion unless intentionally local. |

Identity-provider assumptions: browser users authenticate with OIDC authorization-code flow, receive role/capability claims recognized by the backend, and every protected API call is still authorized server-side. The UI may hide controls for usability, but backend RBAC remains authoritative.

## Verification Commands

Run these in CI and before promotion when the related surface changes:

1. `npm ci`
2. `npm run typecheck -w @cadentia/admin-web`
3. `npm run test -w @cadentia/admin-web`
4. `npm run test:a11y -w @cadentia/admin-web`
5. `npm run generate:client:check -w @cadentia/admin-web`
6. `npm run build -w @cadentia/admin-web`
7. `npm run smoke -w @cadentia/admin-web`
8. `CADENTIA_ADMIN_STRICT_SMOKE=true npm run smoke -w @cadentia/admin-web` before promotion, with CI-provided commit/timestamp and API-base-url metadata.
9. `mvn -pl apps/api -DskipTests generate-sources`
10. `mvn -pl apps/api test` or the narrower API test class set affected by admin endpoints.

CI must run the admin package build/tests, generated-client drift check, OpenAPI generation, and artifact smoke test. API changes under `apps/api/src/main/openapi/` require OpenAPI generation before merge.

## Deterministic Workflow Test Coverage

Automated frontend tests must use fixture sessions, candidate records, audit events, rollback previews, and API errors. They must not require production credentials, live connector accounts, real Telegram tokens, real user personal data, or production catalog records.

Required coverage:

- Authentication bootstrap: unauthenticated, expired session, forbidden, missing church instance, and authenticated states.
- Role-aware navigation: `CATALOG_EDITOR`, `DOCTRINAL_REVIEWER`, `MUSICAL_REVIEWER`, `ADMIN`, and read-only users see only capability-appropriate routes/actions.
- Candidate queue: filters, deep links, duplicate/provenance/parser/moderation badges, stale retry, unauthorized/forbidden states.
- Candidate detail: provenance references, parser warnings, duplicate comparison, merge decision confirmation, approval/moderation actions, reviewer notes, audit references, stale/concurrency failures.
- Audit search: redacted summaries, correlation/causation IDs, deep-link filters, unauthorized/forbidden states.
- Rollback: backend preview rendering, exact request-ID confirmation, blockers disabling execution, successful audit result, and 400/403/409/412/5xx failure paths.
- Accessibility: keyboard navigation, focus management, one page `h1`, programmatic labels, dialog semantics, table captions/headers, textual status badges, and high-risk confirmation flows.

## Smoke Test Procedure for a Deployed Artifact

1. Fetch `/admin-health.json` and verify status, release version, commit, and timestamp match the promoted artifact.
2. Fetch `/admin-build.json` and verify API-base-url configuration status, feature flags, diagnostics setting, and church-instance identifier match the target environment.
3. Load `/admin` and verify no protected data renders before session bootstrap completes.
4. Sign in as `CATALOG_EDITOR`; verify import candidate queue access and lack of rollback execution control.
5. Sign in as `DOCTRINAL_REVIEWER` or `MUSICAL_REVIEWER`; verify reviewer-specific navigation/actions and no admin settings access unless backend grants it.
6. Sign in as `ADMIN`; verify audit search, rollback preview access, instance settings, and diagnostics only when enabled.
7. Attempt an unauthorized route/action and verify UI and API fail closed without protected resource names, raw payloads, lyrics, or stack traces.
8. Open candidate detail with fixture data and verify provenance, parser warnings, duplicate comparison, review history, and audit references render from API data.
9. Create a rollback preview in a fixture/non-production environment, verify exact request-ID confirmation is required, then run both a blocked/failure path and an allowed path if the environment is designated for destructive testing.
10. Capture request correlation IDs from browser/network logs and confirm corresponding backend logs/audit events exist.

## Role Matrix Verification

| Role | Expected UI access | Must not show |
| --- | --- | --- |
| `VIEWER` | Read-only permitted routes from backend capabilities. | Mutation buttons, rollback execution, diagnostics without capability. |
| `CATALOG_EDITOR` | Import queue/detail, notes, permitted review actions. | Instance settings, rollback execution unless explicitly granted. |
| `DOCTRINAL_REVIEWER` | Doctrinal approval/moderation workflows granted by backend. | Musical-only or admin-only actions unless granted. |
| `MUSICAL_REVIEWER` | Musical approval/moderation workflows granted by backend. | Doctrinal-only or admin-only actions unless granted. |
| `ADMIN` | Settings, audit, preview/rollback, diagnostics when feature-enabled. | Controls disabled by backend RBAC or feature flags. |

Always verify with backend responses; role labels alone are not sufficient.

## Signals to Monitor

- UI load failures and JavaScript error rate by build version and route.
- Admin API 401/403/409/412/422/5xx rates by endpoint.
- High-risk action preview, confirmation, completion, and failure counts.
- Contract/version mismatch between UI build metadata and API OpenAPI generation version.
- Audit event creation failures for privileged actions.
- Correlation IDs and trace IDs for failed admin requests.
- Cache hit/miss and stale `index.html`/metadata reads after promotion or rollback.

## Common Error States and Triage

1. Static hosting: verify `index.html`, assets, `admin-health.json`, and `admin-build.json` are from the same commit/version.
2. Cache issue: purge CDN/static-host cache for `index.html`, health/build metadata, and the affected asset prefix.
3. Authentication issue: verify issuer URL, client ID, redirect URI, cookie/session policy, and expired-session behavior.
4. Authorization issue: compare UI-visible routes with `/admin/session` capabilities and backend 403s.
5. Contract mismatch: run `mvn -pl apps/api -DskipTests generate-sources` and `npm run generate:client:check -w @cadentia/admin-web`; redeploy matched artifacts.
6. Workflow failure: compare UI action timestamp, actor ID, `If-Match`, request body summary, backend correlation ID, and audit event result.
7. Rollback failure: treat 409/412 as stale preview, 400/422 as backend validation blockers, 403 as RBAC denial, and 5xx as no-confirmation-safe until audit proves execution.

## Rollout Controls

- Disable the whole admin UI by removing the route/static origin from traffic, serving a maintenance page, or clearing the `admin-ui` deployment flag.
- Disable diagnostics with `VITE_CADENTIA_DIAGNOSTICS_ENABLED=false` and by removing `admin-diagnostics` from `VITE_CADENTIA_FEATURE_FLAGS`.
- Disable high-risk UI workflows by removing specific flags such as rollback, feature-flag mutation, or diagnostics flags from `VITE_CADENTIA_FEATURE_FLAGS` and by withholding backend capabilities.
- Never use UI flags to bypass backend RBAC. Backend authorization, optimistic concurrency, audit creation, and catalog integrity checks remain mandatory.

## Safe UI Rollback

1. Identify the last known-good static bundle/container image by `admin-build.json` version and commit.
2. Switch static hosting or image tag back to the known-good artifact.
3. Purge cache for `index.html`, `admin-health.json`, `admin-build.json`, and changed asset prefixes.
4. Verify `/admin-health.json`, `/admin-build.json`, and `/admin` now report the old version.
5. Run smoke tests for sign-in, route visibility, candidate detail, audit search, and rollback preview.
6. Confirm no catalog data, approval state, audit history, or recommendation read models were mutated by the rollback itself.
7. Use audit history only to determine whether high-risk actions completed before rollback; do not edit audit records.

## Disabling Diagnostics

1. Set `VITE_CADENTIA_DIAGNOSTICS_ENABLED=false`.
2. Remove `admin-diagnostics` from `VITE_CADENTIA_FEATURE_FLAGS`.
3. Ensure backend does not grant `VIEW_DIAGNOSTICS` outside approved operators.
4. Redeploy or update runtime configuration, purge metadata cache, and verify diagnostics navigation is hidden/unavailable.

## Support and Escalation

- Application operator: static hosting, deployment metadata, cache purge, and smoke test execution.
- Identity/RBAC owner: OIDC issuer, client configuration, claims, and backend permissions.
- API owner: OpenAPI generation, admin endpoint 4xx/5xx triage, optimistic-concurrency behavior, audit persistence.
- Catalog/data integrity owner: confirms no catalog, approval, moderation, audit, or recommendation data was mutated by UI rollout/rollback.
- Security/on-call: suspicious high-risk action attempts, unauthorized access patterns, leaked diagnostics, or audit failures.
