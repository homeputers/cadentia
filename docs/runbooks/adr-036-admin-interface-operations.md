# ADR-036 Administrative Web Interface Operations Runbook

## Purpose

This runbook defines baseline deployment and incident procedures for the Cadentia administrative web interface.

## Deployment Checks

1. Confirm the admin UI build was generated from the expected OpenAPI contract version.
2. Confirm environment configuration points to the intended Cadentia API instance.
3. Verify authentication redirects through the approved identity provider.
4. Verify static assets or container images are served with the expected release version.
5. Verify security headers, HTTPS, and cache policy are configured for an administrative surface.

## Smoke Test

1. Sign in as `CATALOG_EDITOR` and verify import candidate queue access.
2. Sign in as `DOCTRINAL_REVIEWER` or `MUSICAL_REVIEWER` and verify role-specific navigation.
3. Sign in as `ADMIN` and verify rollback preview access.
4. Attempt an unauthorized route/action and verify the UI and API fail closed without leaking sensitive details.
5. Open a candidate detail page and verify provenance, parser warnings, duplicate signals, review history, and audit history render from API data.
6. Create a rollback preview in a non-production or fixture environment and verify explicit confirmation is required before execution.

## Signals to Monitor

- UI load failures and JavaScript error rate.
- API 401/403/5xx rates from admin routes.
- High-risk action attempts and completion rates.
- Contract/version mismatch between UI and API.
- Audit event creation failures for privileged actions.

## Triage Workflow

1. Determine whether the issue is static hosting, authentication, API availability, or a contract mismatch.
2. Confirm API health and the OpenAPI contract version deployed with the UI.
3. Check browser console reports or frontend error telemetry for route/component failures.
4. Validate role claims and server-side RBAC response codes.
5. For high-risk workflow failures, compare UI action logs with privileged action audit events.
6. Roll back the UI artifact if the API remains healthy and the issue is isolated to frontend behavior.

## Rollback

- Roll back the static asset bundle or container image independently from the database.
- Do not mutate catalog data directly to compensate for UI bugs.
- Use API audit history to confirm whether any high-risk action completed before rollback.
