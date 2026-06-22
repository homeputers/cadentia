# Cadentia Admin Web

The admin web package is a separate React + Vite single-page application for ADR-036 administrative workflows. It lives in `apps/admin-web` and builds to static assets in `apps/admin-web/dist`.

## Commands

- `npm run dev -w @cadentia/admin-web` - local development server.
- `npm run build -w @cadentia/admin-web` - writes build metadata, typechecks, and creates the static bundle.
- `npm run test -w @cadentia/admin-web` - Vitest unit tests.
- `npm run lint -w @cadentia/admin-web` - TypeScript lint gate for the scaffold.
- `npm run typecheck -w @cadentia/admin-web` - explicit TypeScript check.
- `npm run preview -w @cadentia/admin-web` - preview the built static artifact.
- `npm run smoke -w @cadentia/admin-web` - verify `dist/index.html`, `dist/admin-build.json`, and `dist/admin-health.json`.
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

## Deployment contract

The v1 artifact is a static SPA in `dist/`. The preferred ADR-036/ADR-022-compatible hosting model is separately hosted static assets behind the same identity provider and church-instance deployment boundary as the API. Operators may reverse-proxy the assets under the API origin later, but the UI must continue to consume only documented OpenAPI routes through `VITE_CADENTIA_API_BASE_URL`.

Smoke tests verify `admin-build.json` for bundle version and API-base-url configuration status, `admin-health.json` for static availability, and `index.html` for SPA availability.
