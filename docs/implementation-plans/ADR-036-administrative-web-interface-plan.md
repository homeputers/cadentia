# ADR-036 Implementation Plan: Administrative Web Interface

## Objective

Implement a first-class Cadentia Administrative Web Interface that gives
catalog editors, reviewers, and operators a browser-based console for safe
catalog governance, import review, duplicate resolution, audit inspection,
moderation, rollback, diagnostics, and instance operations while keeping the
backend API, RBAC, provenance rules, and audit trail authoritative.

## Source ADR

- [ADR-036: Administrative Web Interface](../adr/ADR-036-administrative-web-interface.md)

## Status

Overall status: In progress.

- Subtask 1: Complete - admin web application architecture, framework decision,
  package scaffolding, and deployment contract.
- Subtask 2: In progress - OpenAPI-first API gap closure and generated typed client
  workflow.
- Subtask 3: In progress - authentication, authorization, role-aware routing, and
  permission-state UX.
- Subtask 4: In progress - shared admin layout, design system, accessibility,
  loading, empty, and error states.
- Subtask 5: In progress - import candidate queues, filtering, sorting, and triage
  summaries.
- Subtask 6: In progress - candidate detail, provenance, parser evidence, review
  notes, and review history.
- Subtask 7: In progress - duplicate comparison, merge decisions, approval actions,
  and moderation flag workflows.
- Subtask 8: In progress - audit history, rollback preview, rollback execution, and
  high-risk confirmation flows.
- Subtask 9: In progress - recommendation diagnostics, operational configuration,
  feature flags, and safe deferred screens.
- Subtask 10: In progress - testing, CI, deployment, runbook updates, smoke tests,
  and rollout controls.

## Batch Policy

ADR-036 implementation will proceed in small reviewable batches. Each batch
should target no more than roughly 1,500 changed lines, including tests and
documentation, unless the team explicitly approves a larger mechanical
generated-artifact change. API behavior changes must remain OpenAPI-first:
update the split OpenAPI contract, regenerate or verify generated artifacts,
then implement backend and frontend behavior.

## Current Implementation Snapshot

Implemented foundations:

- `apps/admin-web` is a React + Vite static SPA with documented build, test,
  typecheck, accessibility, smoke, preview, and generated-client commands.
- Runtime configuration, build metadata, deployment smoke metadata, and the
  operations runbook are documented.
- The admin shell bootstraps `/admin/session`, handles missing church-instance,
  unauthenticated, expired, forbidden, disabled-feature, and general failure
  states, and renders capability-aware navigation and direct-route guards.
- Shared UI components cover breadcrumbs, page headers, filter panels, semantic
  tables, badges, audit-reference links, diff panels, confirmation dialogs,
  support/debug metadata, form validation, and redacted state panels.
- Import queue, candidate detail, audit/rollback, diagnostics, and instance
  settings routes exist and use documented admin API paths.
- Tests cover environment parsing, session bootstrap, permissions, API client
  headers, generated route drift, accessibility foundations, import queue,
  candidate detail, audit/rollback, operational surfaces, and shared UI states.
- CI builds and tests the admin package, checks generated-client drift, verifies
  OpenAPI generation, and runs backend tests.

Known implementation gaps:

- The frontend client wrapper is typed and generated from OpenAPI route and
  operation metadata, but request/response model types remain manually declared
  at the route-helper layer.
- Several operational mutation endpoints still use local-development in-memory
  state, while operational read models now derive safe summaries from runtime
  instance configuration where available.
- Some high-risk workflows render confirmation and mutation surfaces, but need
  backend-backed allowed-action tests as policies become more granular.
- Admin shell integration smoke tests now cover bootstrap, role-aware navigation,
  direct-route denial, import snapshot loading, missing church-instance startup,
  and redacted failure states; full browser end-to-end coverage is still deferred.
- Connector, bot-channel, scoring-profile, background-job, and broader
  operations-console screens remain deferred until their APIs are implemented.

## API Gap Matrix

| ADR-036 workflow | Current API status | UI status | Gap / next action |
| --- | --- | --- | --- |
| Session bootstrap and capability discovery | Existing: `GET /admin/session` | Implemented in shell bootstrap | Expand production identity-provider integration beyond local Basic-auth development wiring. |
| Role-aware navigation and permission states | Existing session capability schema | Implemented for route visibility, direct-route guards, and common permission states | Continue adding backend-backed role-boundary tests as workflow policies become more granular. |
| Import candidate queue | Existing: `GET /admin/import-candidates` with filters, sort, and pagination | Implemented with URL-addressable filters and safe summaries | Validate against real seeded data and add any missing server filters OpenAPI-first. |
| Candidate detail | Existing: `GET /admin/import-candidates/{candidateId}` | Implemented detail view for provenance, parser evidence, notes, history, duplicate, approval, moderation, and candidate-local audit sections | Continue testing against real seeded data and refine dense UI layout as workflows mature. |
| Reviewer note creation | Existing: `POST /admin/import-candidates/{candidateId}/notes` | Implemented with actor, `If-Match` context, stale-version handling, and non-leaky failure states | Add backend-backed validation fixtures if note policy grows beyond required body/category checks. |
| Duplicate comparison | Existing in candidate detail plus `GET /admin/import-candidates/{candidateId}/duplicates` | Rendered from detail response | Decide whether a dedicated duplicate route is needed or detail route remains sufficient for v1. |
| Merge decisions | Existing: `POST /admin/import-candidates/{candidateId}/merge-decisions` | Implemented confirmed mutation with actor, ETag context, validation/stale/forbidden failure states | Add role-boundary tests only if backend introduces role-specific merge decisions. |
| Approval actions and reversals | Existing: `POST /admin/import-candidates/{candidateId}/approval-actions` | Implemented confirmed mutation with backend validation and failure-state coverage | Add role-boundary tests for doctrinal and musical reviewer variants. |
| Moderation flags | Existing: create, assign, resolve, and escalate endpoints | Create, assign, resolve, escalate, audit attribution, and failure states are implemented in candidate detail | Add backend-backed policy fixtures as moderation rules expand. |
| Candidate audit history | Existing: `GET /admin/import-candidates/{candidateId}/audit-history` | Implemented candidate-local audit history panel plus global audit route links | Expand only if the API later exposes a safe detailed event view. |
| Global audit search | Existing: `GET /admin/audit-events` | Implemented search filters and redacted table | Add deeper audit-event detail view only if the API returns a safe detail shape. |
| Rollback preview | Existing: `POST /admin/rollback-previews` | Implemented preview form, impact rendering, stale preview clearing, blocked-preview gating, and documented error responses | Add backend-backed rollback policy fixtures as rollback target coverage grows. |
| Rollback execution | Existing: `POST /admin/rollbacks` | Implemented exact request-ID confirmation with explicit 400/403/409/412/5xx failure copy and documented error responses | Add integration coverage once rollback persistence/read models exist. |
| Diagnostics | Existing: `GET /admin/diagnostics` | Implemented feature-flag and capability-gated diagnostics view with documented recommendation diagnostics shape and runtime-configuration component | Replace empty recommendation diagnostics with persisted/observed recommendation diagnostics when available. |
| Instance configuration | Existing: `GET/PUT /admin/instance-configuration` | Implemented read/edit flow with actor, version, ETag context, documented errors, validation hardening, and runtime-derived connectors, bot channels, scoring profiles, and operational settings | Connect mutations to persisted instance configuration outside local development. |
| Feature flags | Existing: list, preview, and confirm endpoints | Implemented list, preview, exact confirmation, blockers, documented errors, and validation hardening | Connect to persistent feature-flag storage outside local development. |
| Connectors, bot channels, scoring profiles, background jobs | Deferred / not fully specified for ADR-036 v1 | Deferred placeholders only | Define API contracts OpenAPI-first in later operations-console batches. |

## Proposed Batches

1. Documentation reconciliation and API-gap matrix. Keep this batch
   documentation-only and use it to anchor follow-up implementation scope.
2. Role-boundary and seeded-data validation pass across import queue, candidate
   review, approvals, moderation, audit, rollback, diagnostics, and settings.
3. Operations persistence pass for admin operations where current mutations use
   local-development in-memory settings or feature-flag data.
4. Deferred operations-console API design for connectors, bot channels, scoring
   profiles, and background jobs.
5. Expand smoke coverage from admin shell integration tests into full browser
   tests for candidate detail, audit/rollback, settings, and non-leaky
   unauthorized paths when browser tooling is introduced.

## Guiding Principles

- The admin UI is not a source of truth; it must call documented Cadentia API
  endpoints and render backend-provided facts, eligibility states, allowed
  actions, provenance, audit references, and diagnostics.
- Any change or addition to API behavior must update the split OpenAPI contract
  first, regenerate API artifacts, and only then implement backend controllers,
  services, or frontend client calls.
- Server-side RBAC remains authoritative. Client-side navigation and disabled
  controls are usability aids only and must never be treated as enforcement.
- The UI must never compute recommendation eligibility, approval eligibility,
  duplicate truth, rollback impact, or catalog publication state independently
  from backend responses.
- High-risk operations must use backend preview data, explicit confirmation,
  optimistic concurrency where available, actor attribution, and visible audit
  context.
- Administrative screens must avoid exposing unapproved content, sensitive review
  notes, raw connector payloads, copyrighted lyrics, diagnostics, or
  cross-instance data to unauthorized roles.
- The initial implementation should prioritize safe, auditable catalog
  governance workflows while creating route, client, testing, and deployment
  foundations for later operations-console features.

## Subtask 1: Decide admin web architecture, scaffold package, and define deployment contract

### Context

ADR-036 requires a separate web application package in the repository, but the
ADR intentionally leaves the frontend framework, design system, hosting model,
and deployment artifact open. The repository currently contains the backend API
application and shared TypeScript intent-contract package, so this subtask must
establish the admin UI foundation before feature screens are implemented.

**Codebase anchors**

- Source ADR in `docs/adr/ADR-036-administrative-web-interface.md`
- Existing package and build configuration at the repository root
- API OpenAPI aggregate entrypoint under
  `apps/api/src/main/openapi/cadentia-api.yaml`
- Packaged deployment plan in
  `docs/implementation-plans/ADR-022-packaged-deployment-and-church-customization-model-plan.md`
- Observability plan in
  `docs/implementation-plans/ADR-029-observability-and-telemetry-strategy-plan.md`

### Prompt

Choose and document the v1 admin web architecture, then scaffold the admin web
application package. Define the frontend framework, routing approach, package
location, build command, test command, lint/typecheck command, environment
configuration shape, generated-client location, static asset artifact, and
runtime hosting expectations. Add the minimum route shell and health/build
metadata needed for deployment smoke tests.

### Acceptance criteria

- A committed admin web package exists with documented local development,
  build, test, lint, typecheck, and preview commands.
- The package includes a route shell for authenticated admin pages, not feature
  implementations hidden in ad hoc scripts or backend templates.
- Environment configuration names are documented for API base URL, auth issuer or
  identity-provider integration, church-instance context, feature flags,
  diagnostics enablement, and build metadata.
- The deployment artifact is explicit, including whether the UI is same-origin
  behind the API, separately hosted static assets behind the same identity
  provider, or another ADR-approved model.
- Startup/build smoke tests can verify the admin bundle version, expected API
  base URL configuration, and health/static asset availability.
- Architecture documentation records intentionally deferred decisions, including
  any design system or hosting questions not needed for the first usable
  milestone.

### Restrictions

- Do not implement admin pages as server-rendered Spring templates unless a new
  decision supersedes ADR-036's separate web application direction.
- Do not hard-code production API hosts, identity-provider URLs, tenant IDs,
  church-instance IDs, secrets, or credentials into source, fixtures, or build
  output.
- Do not introduce a frontend framework or design system without documenting its
  maintenance, accessibility, and testing implications.
- Do not create UI routes that call undocumented backend endpoints or mock-only
  contracts as if they were production-ready.

## Subtask 2: Close API gaps OpenAPI-first and establish generated typed client workflow

### Context

ADR-036 requires the admin UI to be generated or typed against
`apps/api/src/main/openapi/cadentia-api.yaml`. Cadentia's OpenAPI contract is
split across three YAML files, and repository instructions require API contract
changes to update the OpenAPI spec first. Some admin APIs already exist for
ADR-011 governance, while diagnostics, instance configuration, bot-channel
settings, scoring profiles, feature flags, or other operational surfaces may
need new or expanded endpoints before UI work can rely on them.

**Codebase anchors**

- OpenAPI aggregate entrypoint under `apps/api/src/main/openapi/cadentia-api.yaml`
- OpenAPI path definitions under
  `apps/api/src/main/openapi/cadentia-api.paths.yaml`
- OpenAPI reusable components under
  `apps/api/src/main/openapi/cadentia-api.components.yaml`
- ADR-011 implementation plan in
  `docs/implementation-plans/ADR-011-admin-review-catalog-governance-ui-plan.md`
- Security roles and permissions plan in
  `docs/implementation-plans/ADR-019-security-roles-and-permissions-plan.md`
- Recommendation explainability API plan in
  `docs/implementation-plans/ADR-021-recommendation-engine-explainability-api-plan.md`

### Prompt

Inventory ADR-036 screen requirements against the current OpenAPI contract and
create an API-gap matrix. For every required API addition or change, update the
OpenAPI specification first, keeping path items in `cadentia-api.paths.yaml`,
reusable schemas/parameters/responses/security definitions in
`cadentia-api.components.yaml`, and aggregate metadata/tags/indexes in
`cadentia-api.yaml`. Regenerate backend API interfaces/models and frontend typed
client artifacts before implementing UI calls.

### Acceptance criteria

- The API-gap matrix maps each ADR-036 workflow to an existing endpoint, a new
  endpoint, a deferred endpoint, or an explicitly unsupported v1 capability.
- Every new or changed backend API operation is represented in the split OpenAPI
  contract before controller, service, or frontend implementation work begins.
- The OpenAPI spec defines authorization requirements, church-instance scoping,
  stable IDs, allowed actions, audit references, optimistic concurrency fields,
  validation errors, non-leaky authorization failures, and redacted diagnostics
  response shapes where relevant.
- Generated backend interfaces/models are refreshed with
  `mvn -pl apps/api -DskipTests generate-sources` after OpenAPI changes.
- The admin web package has a repeatable generated-client command that consumes
  the aggregate OpenAPI entrypoint and fails CI when generated client artifacts
  drift from the spec.
- Contract or snapshot tests prove the generated client exposes only documented
  routes and that high-risk workflows use preview/confirmation endpoint shapes
  rather than direct destructive calls.

### Restrictions

- Do not add frontend client calls, backend controllers, request DTOs, or service
  methods for an API change before the split OpenAPI specification is updated.
- Do not collapse the OpenAPI files into one large contract or use dense inline
  JSON-style YAML where reusable expanded components are appropriate.
- Do not let the UI call private, test-only, actuator, database, or undocumented
  routes to fill feature gaps.
- Do not expose raw connector payloads, copyrighted full lyrics, credentials,
  secrets, sensitive review notes, or cross-instance diagnostics in API schemas.

## Subtask 3: Implement authentication, role-aware routing, and permission-state UX

### Context

ADR-036 requires an authenticated operator/reviewer console for
`CATALOG_EDITOR`, `DOCTRINAL_REVIEWER`, `MUSICAL_REVIEWER`, and `ADMIN`
capabilities. The UI must help users understand their permissions, but backend
RBAC and row-level checks remain authoritative. Unauthorized access must fail
safely without leaking sensitive information.

**Codebase anchors**

- Security roles and permissions plan in
  `docs/implementation-plans/ADR-019-security-roles-and-permissions-plan.md`
- Admin governance plan in
  `docs/implementation-plans/ADR-011-admin-review-catalog-governance-ui-plan.md`
- API security configuration and generated OpenAPI security definitions
- Admin web package created in Subtask 1
- Generated typed API client created in Subtask 2

### Prompt

Implement the admin UI authentication integration, session bootstrap, role-aware
navigation, protected routes, permission-aware action rendering, and safe
handling for unauthorized or forbidden API responses. Ensure the UI obtains
identity and capability information from documented backend or identity-provider
surfaces and consistently passes required authorization headers and
church-instance context through the generated client.

### Acceptance criteria

- Unauthenticated users are redirected to the configured sign-in flow or see a
  documented unauthenticated state without loading protected admin data.
- Authenticated users see only navigation items and action controls appropriate
  to their current capabilities, while direct route access and API calls remain
  blocked by backend RBAC.
- The UI distinguishes unauthenticated, forbidden, missing church-instance,
  expired session, disabled feature, and general failure states without leaking
  resource details to unauthorized users.
- Permission-state tests cover `CATALOG_EDITOR`, `DOCTRINAL_REVIEWER`,
  `MUSICAL_REVIEWER`, `ADMIN`, read-only/viewer, and unauthorized users.
- Mutating workflows pass actor attribution and concurrency headers or payload
  fields required by the OpenAPI contract.
- Audit-visible denied attempts are preserved by backend behavior and surfaced
  to authorized operators only where an API explicitly returns such audit facts.

### Restrictions

- Do not rely on hidden routes, disabled buttons, local storage flags, or decoded
  frontend-only role assumptions as the enforcement boundary.
- Do not store access tokens, refresh tokens, credentials, or identity-provider
  secrets in insecure browser storage if the chosen auth architecture provides a
  safer option.
- Do not show unauthorized users whether a protected candidate, song,
  arrangement, audit event, rollback request, or church instance exists.
- Do not duplicate backend role policy logic beyond presentation-level
  navigation and affordance decisions.

## Subtask 4: Build shared admin shell, design system foundations, and accessibility/error-state patterns

### Context

ADR-036 requires accessibility, auditability, error-state, loading-state, and
empty-state handling for all administrative workflows. Before feature screens are
built, the UI needs shared components and conventions for page layout, tables,
filters, forms, banners, confirmations, audit references, diff panels, status
badges, and redacted error messages.

**Codebase anchors**

- Admin web package created in Subtask 1
- Generated API error and response schemas from Subtask 2
- Accessibility and testing tooling selected for the admin package
- Runbook requirements in
  `docs/runbooks/adr-036-admin-interface-operations.md`

### Prompt

Create the shared admin UI shell and component foundations used by later
screens. Implement navigation, breadcrumbs, page headers, filter panels, data
tables, status badges, role/action badges, confirmation dialogs, diff panels,
audit-reference links, loading skeletons, empty states, retryable error states,
non-leaky forbidden states, and accessible form validation patterns.

### Acceptance criteria

- Shared components meet documented accessibility expectations for keyboard
  navigation, focus management, labels, headings, semantic tables, dialogs,
  color contrast, and screen-reader text.
- Data-fetching components provide consistent loading, empty, partial failure,
  retry, stale data, unauthorized, and forbidden states.
- High-risk confirmation components require explicit user acknowledgement and
  can display backend preview facts, audit attribution, and concurrency/version
  context.
- Redaction rules are documented for errors, logs, client telemetry, and UI copy
  so sensitive payload details are not displayed accidentally.
- Component tests cover critical states and at least one accessibility check for
  dialogs, tables, forms, and navigation.
- The shell exposes build/version metadata and a support/debug panel that is safe
  for authorized users without revealing secrets.

### Restrictions

- Do not bury workflow-specific policy in shared visual components; keep policy
  decisions tied to backend allowed actions and feature-specific screens.
- Do not render full copyrighted lyrics, raw connector payloads, secrets,
  tokens, or sensitive diagnostics in generic error/detail components.
- Do not use color alone to communicate severity, eligibility, approval status,
  or destructive-action warnings.
- Do not implement custom inaccessible widgets where native HTML controls or
  well-tested accessible primitives can satisfy the requirement.

## Subtask 5: Implement import candidate queues, filtering, sorting, and triage summaries

### Context

ADR-036 requires initial screens for import candidate queues. Reviewers need to
triage candidates by status, connector, import batch, duplicate signals, parser
warnings, provenance state, priority, and readiness without relying on raw API
calls. Queue views must summarize enough information for safe triage while
avoiding premature exposure of unapproved content.

**Codebase anchors**

- ADR-011 governance plan in
  `docs/implementation-plans/ADR-011-admin-review-catalog-governance-ui-plan.md`
- Import workflow documentation in `docs/import-workflow.md`
- Generated admin review client operations from Subtask 2
- Shared table, filter, status, and error components from Subtask 4

### Prompt

Build import candidate queue screens using the generated OpenAPI client. Support
filtering, sorting, pagination, row-level status summaries, duplicate indicators,
parser warning summaries, provenance summaries, assigned reviewer context,
allowed actions, and deep links into candidate detail pages. Ensure all queue
state is URL-addressable where useful for sharing and audit/support workflows.

### Acceptance criteria

- Reviewers can list import candidates and filter or sort by status, connector,
  batch, submitted date, assigned reviewer, parser severity, provenance status,
  duplicate confidence, moderation state, and review priority when the API
  supports those fields.
- Queue rows show safe summaries for provenance, parser confidence, duplicate
  signals, approval readiness, allowed actions, and audit references without
  showing full raw payloads or full lyrics.
- Empty, loading, forbidden, unauthorized, stale, and retryable error states use
  shared patterns from Subtask 4.
- Queue URLs preserve meaningful filter/sort/page state and do not place secrets
  or sensitive free-form notes into query strings.
- Tests cover generated-client request parameters, role-specific visibility,
  filter serialization, row rendering, and safe handling of blocked candidates.
- Any missing filter, sort, pagination, status, or summary API support is added
  OpenAPI-first before the UI depends on it.

### Restrictions

- Do not implement client-side-only filtering over partial pages in a way that
  misrepresents server result counts or hides blocked records from reviewers.
- Do not allow bulk approval, bulk rollback, or destructive actions directly
  from queue rows without the detailed review and confirmation workflows.
- Do not expose unapproved imported content in public or recommendation-facing
  routes.
- Do not display full copyrighted lyrics, raw connector payloads, credentials,
  or sensitive review notes in queue summaries.

## Subtask 6: Implement candidate detail, provenance, parser evidence, review notes, and review history

### Context

ADR-036 requires candidate detail, review history, provenance, parser warnings,
and backend-provided eligibility impact. Reviewers need enough context to make
safe decisions, but they must not mutate raw imported payloads or treat notes as
approved metadata. The UI must render backend facts and audit history rather
than creating its own eligibility model.

**Codebase anchors**

- Source ADR in `docs/adr/ADR-036-administrative-web-interface.md`
- ADR-003 import and deduplication plan in
  `docs/implementation-plans/ADR-003-song-import-deduplication-plan.md`
- ADR-009 lyrics parsing and musical analysis plan in
  `docs/implementation-plans/ADR-009-lyrics-parsing-musical-analysis-pipeline-plan.md`
- ADR-005 approval and doctrinal review plan in
  `docs/implementation-plans/ADR-005-approval-doctrinal-review-plan.md`
- Generated candidate detail, provenance, parser evidence, notes, and audit
  client operations from Subtask 2

### Prompt

Build candidate detail screens that display normalized candidate metadata,
source/provenance references, parser evidence, warnings, confidence,
eligibility blockers, duplicate signals, reviewer notes, review history, and
related audit references. Add structured note creation where supported by the
API and make all mutating note or assignment operations use documented endpoints
and concurrency protections.

### Acceptance criteria

- The detail view shows candidate identity, source connector, import batch,
  current status, allowed actions, version/etag, normalized fields, provenance
  references, parser warnings, parser confidence, eligibility blockers,
  duplicate summary, review notes, review history, and audit links where
  returned by the API.
- Raw source references and fingerprints are visible to authorized reviewers,
  but raw connector payloads and full lyrics are redacted or omitted unless a
  documented authorized endpoint explicitly permits safe display.
- Structured reviewer notes can be added, listed, and attributed without
  mutating parser evidence, source provenance, or approved catalog metadata.
- The UI clearly distinguishes backend facts, imported candidate data, reviewer
  notes, parser evidence, and approved catalog data.
- Tests cover ready, blocked, parser-warning, duplicate-suspected, rejected,
  unauthorized, and stale-version candidate states.
- Any needed candidate detail, note, assignment, provenance, parser evidence, or
  review-history API change is made OpenAPI-first.

### Restrictions

- Do not allow reviewers to edit raw imported payloads, parser evidence,
  provenance fingerprints, or audit history in place.
- Do not promote reviewer notes into approved song metadata automatically.
- Do not hide low-confidence parser results or eligibility blockers behind
  collapsed UI without visible warning indicators.
- Do not compute approval readiness or recommendation eligibility in the client;
  render backend-provided statuses and allowed actions.

## Subtask 7: Implement duplicate comparison, merge decisions, approval actions, and moderation flag workflows

### Context

ADR-036 requires duplicate comparison, moderation flags, approval state, and
recommendation eligibility impact screens. ADR-011 defines governance behavior
for catalog review, merge decisions, approvals, and moderation. These workflows
can affect publication eligibility, so every action must use backend validation,
allowed actions, actor attribution, optimistic concurrency, and audit trails.

**Codebase anchors**

- ADR-011 admin review and catalog governance UI plan in
  `docs/implementation-plans/ADR-011-admin-review-catalog-governance-ui-plan.md`
- ADR-005 approval and doctrinal review plan in
  `docs/implementation-plans/ADR-005-approval-doctrinal-review-plan.md`
- Approval operations documentation in `docs/approval-operations.md`
- Generated duplicate, merge-decision, approval, and moderation client
  operations from Subtask 2
- Shared diff, confirmation, form, and audit-reference components from Subtask 4

### Prompt

Build duplicate comparison, field-level merge review, merge-decision submission,
approval action, approval reversal where allowed, and moderation-flag workflows.
Render backend duplicate confidence features, conflicts, eligibility effects,
allowed actions, required approval types, and audit references. Require explicit
confirmation for eligibility-impacting decisions and use documented mutation
endpoints only.

### Acceptance criteria

- Duplicate comparison screens show candidate-versus-existing song or
  arrangement data, provenance, duplicate confidence, matching features,
  conflicts, parser warnings, and current approved catalog state.
- Reviewers can submit documented merge decisions such as create-new,
  merge-existing, reject-duplicate, reject-not-permitted, or defer only when the
  backend returns the action as allowed.
- Approval workflows show required approval types, current approval statuses,
  blockers, allowed transitions, actor attribution, version/etag context, and
  resulting eligibility impact from backend responses.
- Moderation flag workflows support creating and viewing flags with scope,
  reason, eligibility impact policy, current status, and audit references.
- High-risk decisions use confirmation dialogs that summarize backend-provided
  consequences and fail safely on stale versions, validation errors, forbidden
  actions, or incomplete provenance.
- Tests cover duplicate comparison rendering, merge-decision payloads,
  approval/reversal actions, moderation flag creation, role boundaries, and
  non-leaky failures.
- Any missing duplicate, merge, approval, moderation, allowed-action, blocker,
  or eligibility-impact field is added OpenAPI-first.

### Restrictions

- Do not let the UI decide that two records are duplicates without backend
  duplicate signals or reviewer-submitted decisions validated server-side.
- Do not bypass two-person review, required approval types, provenance blockers,
  parser-blocking severity, or backend transition rules in client code.
- Do not allow destructive, suppressive, or eligibility-impacting moderation
  actions without explicit confirmation and audit attribution.
- Do not display full copyrighted lyrics or sensitive review notes in duplicate
  comparison unless a documented authorized endpoint returns a safe excerpt or
  redacted representation.

## Subtask 8: Implement audit history, rollback preview, rollback execution, and high-risk confirmation flows

### Context

ADR-036 requires audit history, rollback preview, rollback execution, and preview
plus explicit confirmation for destructive or eligibility-impacting actions.
Rollback must never be executed based on client-side impact estimates. Audit
history must be queryable and deep-linkable for authorized users while avoiding
sensitive payload leakage.

**Codebase anchors**

- ADR-011 governance plan in
  `docs/implementation-plans/ADR-011-admin-review-catalog-governance-ui-plan.md`
- Audit and approval operations documentation in `docs/approval-operations.md`
- ADR-029 observability plan in
  `docs/implementation-plans/ADR-029-observability-and-telemetry-strategy-plan.md`
- Generated audit, rollback-preview, and rollback-execution client operations
  from Subtask 2
- Shared confirmation, audit-reference, table, and error components from
  Subtask 4

### Prompt

Build audit history search/detail screens, rollback preview creation, rollback
impact display, rollback confirmation, and rollback execution workflows. Use
backend preview responses to show impacted records, eligibility changes,
conflicts, blockers, audit references, actor attribution, and concurrency
context. Require users to explicitly confirm rollback execution using the exact
preview or request identifier returned by the API.

### Acceptance criteria

- Authorized users can search audit events by entity, actor, time range, action
  type, import batch, candidate, song, arrangement, moderation flag, and rollback
  request where the API supports those filters.
- Audit event views show redacted payload summaries, actor, timestamp, action,
  target entity, correlation IDs, audit references, and links back to related
  admin screens without leaking sensitive data.
- Rollback previews display impacted records, eligibility changes, conflict
  blockers, irreversible-change warnings, required permissions, and expiration
  or version context from the backend.
- Rollback execution requires explicit confirmation tied to a backend preview or
  rollback request ID and handles stale preview, forbidden, validation,
  conflict, and retryable server failures safely.
- Tests cover audit filtering, deep links, redaction, rollback preview creation,
  rollback confirmation, stale preview failures, role boundaries, and audit
  visibility after execution.
- Any audit filter, rollback preview, rollback execution, impact, blocker,
  version, or audit-reference API gap is closed OpenAPI-first.

### Restrictions

- Do not execute rollback from a client-computed diff or from a confirmation
  dialog that lacks backend preview data.
- Do not expose raw sensitive payloads, secrets, full connector payloads, full
  lyrics, personal data beyond policy, or cross-instance audit events.
- Do not allow rollback execution to be hidden behind generic submit buttons;
  it must be visibly high-risk and confirmation-gated.
- Do not treat audit history as editable or removable from the UI.

## Subtask 9: Add recommendation diagnostics, operational configuration, feature flags, and safe deferred screens

### Context

ADR-036 requires deterministic admin diagnostics views for recommendation and
scoring data when enabled for authorized roles. It also calls for instance-level
configuration screens for connectors, bot channels, scoring profiles, feature
flags, and operational settings as APIs become available. Some of these surfaces
may not be ready in the initial release, so the UI must distinguish implemented,
disabled, and deferred features safely.

**Codebase anchors**

- ADR-010 recommendation scoring plan in
  `docs/implementation-plans/ADR-010-recommendation-engine-scoring-architecture-plan.md`
- ADR-021 recommendation explainability API plan in
  `docs/implementation-plans/ADR-021-recommendation-engine-explainability-api-plan.md`
- ADR-022 packaged deployment and customization plan in
  `docs/implementation-plans/ADR-022-packaged-deployment-and-church-customization-model-plan.md`
- ADR-029 observability plan in
  `docs/implementation-plans/ADR-029-observability-and-telemetry-strategy-plan.md`
- ADR-035 Telegram bot operations plan in
  `docs/implementation-plans/ADR-035-telegram-bot-e2e-integration-and-operations-plan.md`
- Generated diagnostics and configuration client operations from Subtask 2

### Prompt

Implement authorized diagnostics and operational configuration surfaces that are
ready for v1, and create safe placeholders or disabled navigation for deferred
surfaces. For diagnostics, render backend-provided scoring inputs, reason codes,
eligibility blockers, policy versions, read-model timestamps, cache status, and
redacted trace/correlation IDs. For configuration, render documented
connector/channel/scoring/feature-flag settings and support edits only when the
API provides validated mutation endpoints.

### Acceptance criteria

- Diagnostic views are available only to authorized roles and only when the
  diagnostics feature flag or backend capability indicates they are enabled.
- Diagnostics render backend-provided recommendation/scoring data, reason codes,
  eligibility blockers, policy versions, read-model freshness, cache status,
  correlation IDs, and audit references without recomputing scores in the UI.
- Configuration screens show enabled connectors, bot channels, scoring profiles,
  feature flags, and operational settings only for APIs that exist and are
  authorized for the current user.
- Editable configuration workflows use documented GET/preview/update or
  GET/update endpoints, validation errors, optimistic concurrency, and audit
  attribution where available.
- Deferred screens clearly state that the capability is not yet available for
  the current instance or release without implying missing permissions or data
  existence.
- Tests cover diagnostics gating, role-specific redaction, read-only
  configuration rendering, editable configuration updates, feature-disabled
  states, and deferred placeholders.
- Any diagnostic or configuration API added for these screens is specified in
  OpenAPI before implementation and avoids leaking secrets or cross-instance
  data.

### Restrictions

- Do not compute recommendation scores, eligibility, ranking, cache correctness,
  or operational health conclusions in the client independently from backend
  diagnostics.
- Do not expose model prompts, raw LLM payloads, secrets, connector credentials,
  bot tokens, webhook secrets, cross-instance settings, or private telemetry in
  diagnostics or configuration screens.
- Do not implement settings mutations by writing directly to files, databases,
  environment variables, or unsupported backend routes.
- Do not present deferred features as working or silently hide them in a way that
  blocks operator understanding of release scope.

## Subtask 10: Complete testing, CI, deployment, runbook updates, smoke tests, and rollout controls

### Context

ADR-036 is complete only when operators can deploy the admin UI, verify health,
validate role access, exercise critical workflows, and roll back a broken UI
release independently from catalog data. The repository already includes an
admin interface operations runbook that must be expanded as implementation
choices become concrete. The UI also adds frontend dependency, accessibility,
contract, and release-cadence responsibilities.

**Codebase anchors**

- Admin interface operations runbook in
  `docs/runbooks/adr-036-admin-interface-operations.md`
- Admin web package created in Subtask 1
- OpenAPI contract under `apps/api/src/main/openapi/`
- API tests under `apps/api/src/test/`
- CI, Docker, deployment, and package-management files in the repository as
  applicable

### Prompt

Build the verification and rollout package for the admin web interface. Add unit,
component, accessibility, generated-client, contract, and end-to-end or
integration tests for the implemented workflows. Update CI to build and test the
admin package, verify generated client drift, and run OpenAPI generation after
API changes. Expand the operations runbook with deployment, smoke testing,
permission verification, incident triage, observability, rollback, and support
procedures.

### Acceptance criteria

- CI or documented verification commands cover frontend install/build,
  lint/typecheck, unit/component tests, accessibility checks, generated-client
  drift checks, OpenAPI generation, API tests affected by admin endpoints, and
  smoke tests for the deployed artifact.
- End-to-end or integration tests cover authentication bootstrap, role-aware
  navigation, candidate queue, candidate detail, duplicate comparison,
  approval/moderation actions, audit search, rollback preview, rollback
  execution failure paths, and unauthorized access using deterministic fixtures.
- Accessibility testing verifies keyboard navigation, focus management,
  headings, labels, dialogs, tables, status badges, and high-risk confirmation
  flows.
- Deployment documentation names artifact location, environment variables,
  identity-provider assumptions, API base URL, church-instance configuration,
  feature flags, health checks, cache/static asset behavior, and expected startup
  validation failures.
- The runbook explains smoke tests, role matrix verification, common error
  states, log/metric/correlation-ID checks, incident triage, safe UI rollback,
  cache purge, disabling diagnostics, and escalation paths.
- Rollout controls allow the admin UI or specific high-risk features to be
  disabled without changing catalog data or bypassing backend RBAC.

### Restrictions

- Do not require production credentials, live connector accounts, real Telegram
  tokens, real user personal data, or production catalog records in automated
  tests.
- Do not mark the admin UI production-ready until both successful and failure
  runbook paths have been verified against implemented configuration and
  telemetry names.
- Do not skip OpenAPI generation after API changes or leave generated backend or
  frontend API artifacts out of sync.
- Do not let UI rollback procedures mutate catalog data, audit history, approval
  state, or recommendation read models.
