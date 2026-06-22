# ADR-036 Admin Web Architecture v1

## Decision summary

- **Package:** `apps/admin-web`, registered as the npm workspace `@cadentia/admin-web`.
- **Framework:** React with Vite and TypeScript. React is widely maintained, supports accessible component patterns, and can be tested with Vitest/jsdom without coupling the UI to Spring templates.
- **Routing approach:** a browser SPA route shell under `/admin/*`. The scaffold exposes protected route groups for import review, audit history, diagnostics, and instance settings, but it does not call backend endpoints until the split OpenAPI contract documents those routes.
- **Generated client location:** `apps/admin-web/src/generated/cadentia-api/`, generated from `apps/api/src/main/openapi/cadentia-api.yaml` in ADR-036 Subtask 2.
- **Artifact:** static assets emitted to `apps/admin-web/dist` by `npm run build -w @cadentia/admin-web`.
- **Hosting model:** v1 uses separately hosted static assets behind the same identity provider and church-instance boundary as the Cadentia API. Same-origin reverse proxying remains allowed later if it preserves the OpenAPI client boundary and instance isolation.

## Commands

| Purpose | Command |
| --- | --- |
| Local development | `npm run dev -w @cadentia/admin-web` |
| Build static artifact | `npm run build -w @cadentia/admin-web` |
| Unit tests | `npm run test -w @cadentia/admin-web` |
| Lint gate | `npm run lint -w @cadentia/admin-web` |
| Typecheck | `npm run typecheck -w @cadentia/admin-web` |
| Preview built artifact | `npm run preview -w @cadentia/admin-web` |
| Deployment smoke | `npm run smoke -w @cadentia/admin-web` |
| Generated client check placeholder | `npm run generate:client -w @cadentia/admin-web` |

## Environment contract

The deployment layer supplies these public browser configuration values per church instance:

- `VITE_CADENTIA_API_BASE_URL`
- `VITE_CADENTIA_AUTH_ISSUER_URL`
- `VITE_CADENTIA_IDP_CLIENT_ID`
- `VITE_CADENTIA_CHURCH_INSTANCE_ID`
- `VITE_CADENTIA_FEATURE_FLAGS`
- `VITE_CADENTIA_DIAGNOSTICS_ENABLED`
- `VITE_CADENTIA_ADMIN_BUILD_VERSION`
- `VITE_CADENTIA_ADMIN_BUILD_COMMIT`
- `VITE_CADENTIA_ADMIN_BUILD_TIMESTAMP`

No production API host, issuer URL, tenant ID, church-instance ID, secret, or credential is committed in source or generated artifacts.

## Smoke-test contract

A built artifact must expose:

- `dist/index.html` for SPA/static asset availability.
- `dist/admin-health.json` for static health checks.
- `dist/admin-build.json` with bundle name, version, commit, build timestamp, diagnostics flag, and whether an API base URL was configured.

## Maintenance, accessibility, and testing implications

React/Vite keeps the first milestone lightweight, but it adds Node dependency maintenance and browser accessibility obligations. All future admin screens must use semantic landmarks, labelled navigation, keyboard-operable controls, loading/empty/error states, and automated tests around authorization-visible states. The scaffold uses Vitest for unit tests and TypeScript for lint/typecheck gates; later feature screens should add component-level accessibility assertions before workflow implementation is marked complete.

## Deferred decisions

- Final design system and component library selection are deferred until shared layout work; the scaffold intentionally uses plain semantic HTML and CSS.
- Exact OIDC client library and role-claim mapping are deferred to the authentication subtask.
- CDN versus same-origin reverse proxy is deferred to deployment hardening; v1 only requires static assets behind the same identity provider and instance boundary.
- OpenAPI generator choice and generated-client drift enforcement are deferred to ADR-036 Subtask 2.
