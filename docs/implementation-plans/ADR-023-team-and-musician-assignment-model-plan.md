# ADR-023 Implementation Plan: Team and Musician Assignment Model

## Objective

Implement instance-scoped team and musician assignment capabilities so Cadentia
can plan services with real personnel constraints, query arrangement suitability
against available teams, and provide deterministic recommendation diagnostics
without exposing private personnel data or allowing team readiness notes to
bypass catalog approval gates.

## Source ADR

- [ADR-023: Team and Musician Assignment Model](../adr/ADR-023-team-and-musician-assignment-model.md)

## Guiding Principles

- Personnel and assignment data is local to the deployed church instance.
- Musicians, roles, instruments, vocal ranges, skill levels, preferences,
  availability, and assignments are first-class operational data.
- The Recommendation Engine may use team data only through deterministic hard
  filters or scoring inputs configured by versioned recommendation profiles.
- LLM components must not infer musician suitability, select songs, or invent
  team facts from free-form notes.
- Rehearsal readiness and assignment notes may explain operational risk, but
  they must never bypass approved-catalog recommendation gates.

## Subtask 1: Design the team and musician domain schema

### Context

ADR-023 requires musicians to be modeled as instance-scoped people with roles,
controlled vocabularies, optional contact/account links, vocal constraints,
serving preferences, availability, and assignment status. Existing service-plan
and recommendation features need stable foreign keys rather than free-form team
notes.

**Codebase anchors**

- Database migrations under `apps/api/src/main/resources/db/migration/`
- Service-plan schema introduced by ADR-018 in `V018__service_plan_integration_schema.sql`
- Security and privileged-action audit schema from ADR-019 in
  `V019__privileged_action_audit_trail.sql`
- Domain and repository code under `apps/api/src/main/java/com/cadentia/`
- Integration fixtures under `apps/api/src/test/resources/db/fixtures/`

### Prompt

Create the persistence model and domain vocabulary for team planning. Add
migrations and matching domain types for musicians, optional account/contact
links, musician roles, instruments, vocal parts, vocal ranges, skill levels,
serving preferences, team membership, availability windows, service/rehearsal
assignments, assignment status, song-specific assignment overrides, and audit
metadata. Seed conservative controlled-vocabulary defaults that churches can
extend locally while preserving stable codes for deterministic logic.

### Acceptance criteria

- Migrations create normalized tables for musicians, musician-role assignments,
  instruments, vocal parts, vocal ranges, skill levels, serving preferences,
  availability, service assignments, rehearsal assignments, and song-specific
  assignment overrides.
- Controlled vocabularies have stable codes, display names, active/inactive
  status, sort order, audit timestamps, and local-extension support.
- Musician records support optional user-account linkage and optional contact
  fields without requiring private contact data for recommendation logic.
- Availability and assignments can represent accepted, declined, tentative,
  requested, unavailable, and substitute states for both services and rehearsals.
- Database constraints prevent orphaned assignments, invalid ranges, duplicate
  active role/instrument assignments, and song-specific assignments that are not
  tied to the relevant service context.
- Repository integration tests prove schema constraints, default vocabulary
  seeding, and service/rehearsal assignment persistence.

### Restrictions

- Do not store team assignments as free-form service notes only.
- Do not use shared cross-church personnel tables or tenant-row semantics as a
  substitute for isolated deployed instances.
- Do not make private contact fields required for recommendation or assignment
  queries.
- Do not encode skill levels, instruments, vocal parts, or assignment states as
  unvalidated arbitrary strings in core tables.

## Subtask 2: Implement authorization, privacy, and audit controls for personnel data

### Context

ADR-023 explicitly states that personnel data must remain inside the deployed
church instance and follow role-based access rules. Skill levels, ranges,
availability, contact data, and readiness comments can be sensitive. Existing
ADR-019 security work should be reused rather than duplicating scattered role
checks.

**Codebase anchors**

- Security policy and controller guards under `apps/api/src/main/java/com/cadentia/api/security/`
- Privileged audit migration `apps/api/src/main/resources/db/migration/V019__privileged_action_audit_trail.sql`
- Security tests under `apps/api/src/test/java/com/cadentia/api/security/`
- Controller tests under `apps/api/src/test/java/com/cadentia/api/controller/`
- Security runbook `docs/runbooks/adr-019-security-observability-and-response.md`

### Prompt

Add centralized authorization policies for musician profile reads, contact-data
reads, availability management, assignment management, skill/range maintenance,
and team-readiness updates. Wire the policies into API and service entry points,
ensure private fields are redacted for unauthorized roles, and persist audit
events for privileged personnel mutations.

### Acceptance criteria

- Policy checks distinguish at least administrator, worship leader, team
  scheduler, reviewer, assigned musician, and read-only/reporting access needs.
- Unauthorized users cannot read private contact details, sensitive skill/range
  fields, availability notes, or readiness notes unless policy grants access.
- Assigned musicians can view their own upcoming assignments and update allowed
  response fields without receiving broad personnel roster permissions.
- Role, skill-level, vocal-range, contact, availability, substitution, and
  readiness-note changes write non-optional privileged audit records with actor,
  action, target, timestamp, reason/reference metadata, and before/after snapshot
  references.
- Tests cover allowed and denied access for roster views, private fields,
  self-service assignment responses, scheduler operations, and privileged
  mutations.
- Documentation describes personnel-data classification, retention expectations,
  and emergency access-revocation steps.

### Restrictions

- Do not leak whether a private musician record exists through authorization
  error messages.
- Do not log contact details, medical information, pastoral notes, or other
  sensitive free-text content in telemetry labels or audit summaries.
- Do not bypass centralized policy checks in background jobs, internal services,
  or import/synchronization endpoints.
- Do not expose personnel data outside the deployed church instance.

## Subtask 3: Build service and rehearsal assignment workflows

### Context

Services must be able to assign musicians, vocalists, instruments, and service
positions. ADR-024 rehearsal lifecycle and ADR-018 service-plan integration may
provide service/rehearsal context, but ADR-023 needs structured operational
assignments that can feed readiness and recommendation constraints.

**Codebase anchors**

- Service-plan package under `apps/api/src/main/java/com/cadentia/serviceplan/`
- Service-plan tests under `apps/api/src/test/java/com/cadentia/serviceplan/`
- API controller package under `apps/api/src/main/java/com/cadentia/api/controller/`
- Setlist persistence code under `apps/api/src/main/java/com/cadentia/reng/setlist/`
- Existing service-plan integration plan in
  `docs/implementation-plans/ADR-018-service-plan-integration-model-plan.md`

### Prompt

Implement application services, APIs, and validation rules for assigning
musicians to services, rehearsals, service positions, instruments, vocal parts,
and optional song-level responsibilities. Include assignment lifecycle changes,
substitute handling, schedule conflict detection, and roster summaries for
worship leaders and assigned musicians.

### Acceptance criteria

- Worship leaders or schedulers can create, update, remove, and reorder service
  team assignments with musician, role/position, instrument, vocal part,
  assignment status, and optional song-specific responsibility.
- Rehearsal assignments can be linked to service assignments while preserving
  separate response/readiness status for each rehearsal.
- Validation prevents assignment of inactive musicians, inactive vocabulary
  values, unavailable musicians without explicit override, and duplicate
  mutually exclusive positions in the same service context.
- Substitute assignments preserve the original assignment history and expose the
  active assigned musician deterministically.
- APIs can return a service roster, musician-specific upcoming assignments,
  staffing gaps, availability conflicts, and assignment-change history with
  privacy-aware field redaction.
- Tests cover normal assignment creation, declined/tentative responses,
  substitutions, song-specific overrides, schedule conflicts, and unauthorized
  assignment changes.

### Restrictions

- Do not overwrite assignment history when a musician is replaced or a status
  changes.
- Do not allow free-form service positions to drive deterministic recommendation
  logic without mapping to controlled vocabulary values.
- Do not use readiness notes or scheduler overrides to mark unapproved songs or
  arrangements as recommendable.
- Do not send notifications or external calendar updates from this subtask
  unless an approved integration boundary already exists.

## Subtask 4: Model arrangement suitability requirements

### Context

ADR-023 requires arrangement suitability to be queryable by required
instruments, optional instruments, vocal configuration, minimum skill level,
vocalist range constraints, and service context. This must connect to existing
song and arrangement records without changing catalog approval semantics.

**Codebase anchors**

- Catalog domain code under `apps/api/src/main/java/com/cadentia/catalog/`
- Recommendation read-model migrations such as
  `V007__recommendable_arrangements_approval_gated_view.sql` and
  `V011__recommendable_read_model_performance_indexes.sql`
- Arrangement transposition plan
  `docs/implementation-plans/ADR-006-arrangement-transposition-plan.md`
- Arrangement compatibility ADR
  `docs/adr/ADR-033-arrangement-compatibility-and-instrumentation-modeling.md`
- Catalog and recommendation integration tests under `apps/api/src/test/java/com/cadentia/`

### Prompt

Extend arrangement metadata to express deterministic team-suitability
requirements. Add required and optional instrument slots, vocal configuration,
lead-vocal range requirements, backing-vocal requirements, minimum skill levels,
role coverage rules, and queryable suitability views or repositories. Ensure
suitability metadata is versioned with arrangement/catalog governance and remains
separate from approval eligibility.

### Acceptance criteria

- Arrangement records can declare required instruments, optional instruments,
  minimum skill level per role/instrument, vocal configuration, lead-vocal range,
  harmony/backing-vocal requirements, and notes suitable for human review.
- Suitability requirements are versioned or audit-linked so changes can be
  traced to catalog governance actions.
- Query APIs can evaluate an arrangement against a supplied service/team context
  and return pass/fail/warning facts for instrumentation, vocal coverage, skill
  floor, and range conflicts.
- Recommendation candidate reads continue to require catalog approval before
  considering team suitability.
- Tests prove unapproved arrangements remain excluded even when the assigned team
  fully satisfies suitability requirements.
- Documentation clarifies how arrangement suitability differs from doctrinal,
  musical, licensing, or administrative approval.

### Restrictions

- Do not treat suitability metadata as a replacement for approval gates.
- Do not let the LLM infer arrangement requirements from lyrics, titles, notes,
  or musician profiles.
- Do not store required instruments, vocal parts, or skill levels only in
  unstructured text.
- Do not assume every arrangement needs a full band; support sparse and
  acoustic arrangements.

## Subtask 5: Add deterministic recommendation team constraints and scoring inputs

### Context

ADR-023 allows the Recommendation Engine to use team data as deterministic hard
filters or scoring inputs according to a versioned recommendation profile.
Existing guardrails require backend-only song selection, approved-only
candidate eligibility, transparent diagnostics, and no LLM involvement in
selection.

**Codebase anchors**

- Recommendation engine code under `apps/api/src/main/java/com/cadentia/reng/`
- Scoring profile code and tests under `apps/api/src/main/java/com/cadentia/reng/scoring/`
  and `apps/api/src/test/java/com/cadentia/reng/scoring/`
- Candidate retriever tests such as
  `apps/api/src/test/java/com/cadentia/reng/JdbcCandidateRetrieverIntegrationTest.java`
- Intent contract package `packages/intent-contracts/` if API request/response
  shape changes are needed
- Recommendation explainability plan
  `docs/implementation-plans/ADR-021-recommendation-engine-explainability-api-plan.md`

### Prompt

Add team-aware recommendation inputs and profile configuration. Implement a
team-context resolver that loads assignments, availability, instruments, vocal
parts, skill levels, and song-specific overrides for a service. Add deterministic
hard filters and scoring features for missing required instruments, insufficient
skill coverage, vocal range mismatch, missing vocal configuration, unavailable
assigned musicians, and optional-instrument fit.

### Acceptance criteria

- Recommendation requests can reference a service/team context or provide an
  explicit validated team-constraint object without relying on LLM song
  selection.
- Versioned recommendation profiles declare whether each team constraint is a
  hard filter, scoring input, warning-only diagnostic, or disabled.
- Team-aware recommendation generation is deterministic for the same approved
  catalog, profile version, service context, and request parameters.
- Candidate filtering preserves existing approved-only, active-only, tag,
  key-policy, tempo-policy, and scoring behavior before applying team-specific
  logic where configured.
- Tests cover missing required instruments, optional-instrument scoring,
  insufficient skill levels, lead-vocal range mismatch, unavailable assigned
  musicians, incomplete teams, and disabled team constraints.
- Performance tests or query-plan checks confirm team suitability does not cause
  unacceptable candidate retrieval regressions on representative data.

### Restrictions

- Do not allow the LLM intent agent to select songs, invent team constraints, or
  override backend profile configuration.
- Do not make team constraints globally mandatory for every church or every
  recommendation profile.
- Do not let readiness notes, private comments, or contact data affect scoring.
- Do not sacrifice deterministic ordering by introducing nondeterministic tie
  breakers or current-time-dependent behavior outside explicit service context.

## Subtask 6: Extend recommendation diagnostics and explainability for team conflicts

### Context

ADR-023 acceptance criteria require vocalist range and instrumentation conflicts
to be explainable in recommendation diagnostics. ADR-021 already establishes an
explainability API boundary, so team diagnostics should extend that model with
safe, role-aware details.

**Codebase anchors**

- Explanation classes and renderers under `apps/api/src/main/java/com/cadentia/reng/scoring/`
- Explanation contract fixtures under
  `packages/intent-contracts/fixtures/v1/recommendation-explanations/`
- Explanation tests under `apps/api/src/test/java/com/cadentia/reng/scoring/`
- ADR-021 plan in
  `docs/implementation-plans/ADR-021-recommendation-engine-explainability-api-plan.md`

### Prompt

Add team-suitability diagnostics to recommendation explanations. Include
structured evidence for required-instrument coverage, optional-instrument fit,
vocal configuration, lead-vocal range fit, skill-level floor, availability
status, assignment status, and readiness warnings. Apply role-aware redaction so
public or musician-facing explanations do not reveal private personnel details.

### Acceptance criteria

- Explanation payloads can identify why an arrangement was excluded, penalized,
  warned, or accepted for team-suitability reasons.
- Diagnostics cite dataset-backed arrangement suitability fields and service
  assignment records rather than free-form LLM-generated explanations.
- Admin/worship-leader diagnostics can show actionable staffing gaps while
  musician-facing or public views redact private data according to policy.
- Contract fixtures and renderer tests cover instrumentation conflict, missing
  lead-vocal range fit, insufficient skill coverage, optional-instrument bonus,
  unavailable musician, and incomplete-team warning cases.
- Existing explanation contract consumers remain backward compatible or receive
  an explicitly versioned schema update with migration notes.

### Restrictions

- Do not expose private vocal range, skill-level, contact, availability-note, or
  readiness-note data to unauthorized roles.
- Do not emit explanations that imply unapproved songs were considered for user
  recommendation results.
- Do not use prose-only diagnostics where structured machine-readable evidence
  is required.
- Do not let explanation rendering change recommendation ordering.

## Subtask 7: Surface rehearsal readiness without bypassing catalog governance

### Context

ADR-023 requires rehearsal readiness for assigned teams but states readiness
notes must not bypass catalog approval gates. Readiness should help worship
leaders understand operational risk for a planned set, not authorize unsafe or
unapproved catalog content.

**Codebase anchors**

- Rehearsal lifecycle ADR `docs/adr/ADR-024-rehearsal-and-workflow-lifecycle.md`
- Service-plan code under `apps/api/src/main/java/com/cadentia/serviceplan/`
- Setlist versioning code under `apps/api/src/main/java/com/cadentia/reng/setlist/`
- Approval operations documentation `docs/approval-operations.md`
- API/controller tests under `apps/api/src/test/java/com/cadentia/api/controller/`

### Prompt

Implement rehearsal-readiness tracking for service teams and song assignments.
Capture readiness status, blockers, missing people, unresolved arrangement
conflicts, rehearsal attendance/response state, and human notes with privacy
classification. Surface readiness summaries on service plans and setlists while
preserving approval-gated recommendation and catalog visibility rules.

### Acceptance criteria

- Readiness can be recorded at service/team, rehearsal, musician-assignment,
  song-assignment, and arrangement-conflict levels where appropriate.
- Readiness summaries distinguish objective structured blockers from private
  human notes and support policy-based redaction.
- Setlist and service-plan views can show readiness status and staffing gaps for
  approved planned arrangements.
- Tests prove readiness status cannot make unapproved songs or arrangements
  visible, recommendable, exportable as approved, or eligible for final setlist
  publication paths that require approval.
- Audit records capture privileged readiness-note changes and override actions.
- Documentation explains readiness statuses, ownership, review cadence, and the
  boundary between readiness and catalog approval.

### Restrictions

- Do not use readiness notes as catalog approvals, doctrinal review outcomes, or
  licensing clearance.
- Do not expose private readiness notes to unauthorized musicians or public
  service consumers.
- Do not require readiness data before basic approved-catalog recommendations can
  run when team constraints are disabled.
- Do not let LLM components summarize private readiness notes into user-facing
  recommendation explanations.

## Subtask 8: Provide operational documentation, seed data, and regression coverage

### Context

Team and musician modeling changes many workflows: schema, authorization,
service planning, recommendation scoring, explainability, and operations. AI
agents implementing earlier subtasks need shared examples and regression checks
to keep deterministic, privacy-safe behavior intact.

**Codebase anchors**

- Documentation under `docs/`
- Seed and fixture docs such as `docs/seed-data.md`
- API test fixtures under `apps/api/src/test/resources/db/fixtures/`
- Existing architecture documentation `docs/ARCHITECTURE.md`
- Implementation plan index `docs/implementation-plans/README.md`

### Prompt

Publish documentation, sample data, and regression tests for the team assignment
model. Add minimal fixture musicians, roles, instruments, vocal ranges,
arrangement requirements, services, rehearsals, assignments, and expected
recommendation diagnostics. Update architecture and operations documentation so
future maintainers know how to configure vocabularies, profiles, privacy
policies, and troubleshooting workflows.

### Acceptance criteria

- Documentation covers roster setup, controlled-vocabulary maintenance,
  availability collection, service/rehearsal assignment workflow, substitutions,
  team-aware recommendation profiles, diagnostics, readiness, and privacy rules.
- Seed or fixture data includes representative sparse, full-band, vocal-led, and
  incomplete-team scenarios without using real private personnel data.
- Regression tests verify approved-only recommendation eligibility, deterministic
  team filtering/scoring, privacy redaction, assignment lifecycle transitions,
  readiness boundaries, and diagnostic evidence references.
- Operational docs include troubleshooting for missing instruments, range
  conflicts, unavailable musicians, incomplete teams, stale vocabulary values,
  and authorization denials.
- The implementation plan index references this ADR-023 plan in the appropriate
  phase/order section.

### Restrictions

- Do not include real church member contact information, private availability,
  or sensitive notes in fixtures or documentation.
- Do not document manual database edits as the primary operational workflow.
- Do not create examples where unapproved songs become recommendable due to team
  readiness or musician preference.
- Do not omit the LLM boundary: LLMs parse intent only and must not select songs,
  infer suitability, or generate private personnel facts.

### Implementation notes for Subtask 4

- Arrangement suitability is now stored in versioned `arrangement_suitability_profiles` rows with
  `governance_action_ref`, `version_number`, `is_current`, vocal configuration, lead-vocal MIDI
  bounds, backing-vocal counts, and human review notes.
- Structured suitability slots live in `arrangement_suitability_slots` and carry required/optional
  status, role, instrument, vocal part, minimum skill floor, minimum count, and coverage rule. Review
  notes may explain the decision but never replace these structured fields.
- `v_approved_arrangement_suitability_profiles` and `v_approved_arrangement_suitability_slots` join
  through `v_recommendable_arrangements`, so team suitability is evaluated only after doctrinal,
  musical, licensing, editorial, active-arrangement, and current-lyrics approval gates have already
  admitted the arrangement.
- `JdbcArrangementSuitabilityRepository` evaluates a supplied service plan roster against the current
  approved suitability profile and returns deterministic pass/fail/warning facts for approval gating,
  instrumentation, vocal coverage, skill floors, and lead-vocal range conflicts.
- Suitability metadata is planning compatibility data. It is explicitly separate from doctrinal,
  musical, licensing, editorial, administrative, and catalog approval semantics, and it cannot make an
  unapproved arrangement eligible for recommendation.

### Implementation notes for Subtask 6

- Recommendation explanations now include additive `team_suitability` facts for
  required instrumentation, optional instrumentation, vocal configuration,
  lead-vocal range fit, minimum skill coverage, assignment status, availability
  status, and incomplete-team readiness warnings.
- These facts cite `arrangement_suitability` and `service_assignment` evidence
  references and use structured counts/status values. They do not use LLM prose
  or free-form private personnel notes as diagnostic evidence.
- Public explanation views redact team diagnostics. Worship-leader and admin
  views receive aggregate staffing gaps suitable for action while avoiding
  private contact details, availability notes, readiness notes, exact musician
  range values, or raw skill records.
- Explanation generation is downstream of candidate eligibility and scoring; it
  does not change recommendation ordering and cannot make unapproved songs or
  arrangements recommendable.
