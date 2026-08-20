# Cadentia Admin Web

The admin web package is a separate React + Vite single-page application for ADR-036 administrative workflows. It lives in `apps/admin-web` and builds to static assets in `apps/admin-web/dist`.

## Commands

- `npm run dev -w @cadentia/admin-web` - local development server.
- `npm run build -w @cadentia/admin-web` - writes build metadata, typechecks, and creates the static bundle.
- `npm run test -w @cadentia/admin-web` - Vitest unit, component, workflow integration, and contract tests.
- `npm run test:a11y -w @cadentia/admin-web` - focused accessibility checks for headings, labels, tables, badges, keyboard focus, dialogs, and high-risk confirmations.
- `npm run lint -w @cadentia/admin-web` - TypeScript lint gate for the scaffold.
- `npm run typecheck -w @cadentia/admin-web` - explicit TypeScript check.
- `npm run preview -w @cadentia/admin-web` - preview the built static artifact.
- `npm run smoke -w @cadentia/admin-web` - verify `dist/index.html`, `dist/admin-build.json`, and `dist/admin-health.json`. Set `CADENTIA_ADMIN_STRICT_SMOKE=true` for promotion checks that must reject missing API-base-url configuration or local build metadata.
- `npm run generate:client -w @cadentia/admin-web` - regenerate documented route artifacts from the aggregate OpenAPI entrypoint.
- `npm run generate:client:check -w @cadentia/admin-web` - fail CI when generated client artifacts drift from the OpenAPI entrypoint.

## Environment

All values are supplied per church instance by deployment tooling. Do not commit production hosts, issuer URLs, tenant IDs, church IDs, secrets, or credentials.

- `VITE_CADENTIA_API_BASE_URL` - API origin or same-origin API path used by the generated client.
- `VITE_CADENTIA_AUTH_ISSUER_URL` - OIDC issuer or identity-provider authority.
- `VITE_CADENTIA_IDP_CLIENT_ID` - public browser client identifier.
- `VITE_CADENTIA_CHURCH_INSTANCE_ID` - current isolated church-instance context.
- `VITE_CADENTIA_FEATURE_FLAGS` - comma-separated UI feature gates.
- `VITE_CADENTIA_DIAGNOSTICS_ENABLED` - enables diagnostics navigation only when backend permissions also allow it.
- `VITE_CADENTIA_ADMIN_BUILD_VERSION` - promoted admin bundle version.
- `VITE_CADENTIA_ADMIN_BUILD_COMMIT` - source revision for smoke tests and rollback.
- `VITE_CADENTIA_ADMIN_BUILD_TIMESTAMP` - reproducible build timestamp supplied by CI.

## Internationalization

The admin web reads the effective locale from the authenticated `/admin/session`
response. That value is sourced from the church configuration package's
`instance.locale`; there is no separate browser locale setting. See
[`docs/i18n-configuration.md`](../../docs/i18n-configuration.md) for supported
catalogs, deployment steps, and fallback behavior.

## Deployment contract

The v1 artifact is a static SPA in `dist/`. The preferred ADR-036/ADR-022-compatible hosting model is separately hosted static assets behind the same identity provider and church-instance deployment boundary as the API. Operators may reverse-proxy the assets under the API origin later, but the UI must continue to consume only documented OpenAPI routes through `VITE_CADENTIA_API_BASE_URL`.

Smoke tests verify `admin-build.json` for bundle version, commit, timestamp, API-base-url configuration status, and diagnostics status; `admin-health.json` for static availability; and `index.html` for SPA availability. CI runs non-strict smoke to validate the bundle shape, while promotion pipelines should set `CADENTIA_ADMIN_STRICT_SMOKE=true`. CI also runs the generated-client drift check against the aggregate OpenAPI entrypoint and `mvn -pl apps/api -DskipTests generate-sources` after API/OpenAPI changes.

## Shared admin UI foundations

The ADR-036 shell exposes reusable React components under `src/routes/admin-ui.tsx` for page headers, breadcrumbs, filter forms, semantic data tables, status/role/action badges, audit links, diff previews, loading/empty/error state panels, high-risk confirmations, and support/debug metadata. Shared components are intentionally policy-neutral: screens must pass backend-provided allowed actions, preview facts, audit attribution, and optimistic concurrency/version context rather than deriving workflow eligibility in the visual layer.

### Accessibility conventions

- Use native links, buttons, forms, selects, tables, and `details` disclosure controls before introducing custom widgets.
- Each page has one `h1`, each reusable region is labelled by a heading, breadcrumbs use `nav aria-label="Breadcrumb"`, and tables include captions plus scoped column headers.
- Dialogs use `role="dialog"`, `aria-modal="true"`, labelled headings, and move focus to the dialog heading when opened. Feature screens that launch dialogs must return focus to the triggering control when closed.
- Form fields must expose programmatic labels, `aria-invalid`, and `aria-describedby` for validation errors. Error text should be actionable without relying on color alone.
- Severity, eligibility, approval, and destructive-action warnings must include text or screen-reader text, not only badge color.

### Data state conventions

Data-fetching screens should route backend responses through the shared state language: loading skeletons, empty states, partial failure warnings, stale-data notices, retryable errors, unauthenticated states, and non-leaky forbidden states. Forbidden and unauthorized states must not render protected resource names, raw payload snippets, review notes, diagnostics, or lyrics.

### Redaction rules

Generic UI, client logs, and telemetry must redact or omit secrets, tokens, passwords, raw connector payloads, copyrighted full lyrics, sensitive diagnostics, and personally identifying contact details unless a feature-specific ADR explicitly allows a limited authorized display. Generic errors should show a stable, supportable message and audit/request references rather than backend stack traces or payload excerpts. The `redactSensitiveError` helper is a last line of defense; feature screens should avoid passing sensitive strings into visual components in the first place.

### Support/debug panel

The support/debug panel may show build version, commit, timestamp, diagnostics enabled/disabled, and whether the API base URL is configured. It must not show access tokens, identity-provider secrets, tenant secrets, raw environment dumps, request bodies, connector payloads, or sensitive diagnostics.
