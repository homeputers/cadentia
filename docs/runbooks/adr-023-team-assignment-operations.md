# ADR-023 Team Assignment Operations Runbook

This runbook explains how worship leaders, schedulers, catalog reviewers, and
operators maintain team-aware planning without weakening Cadentia's approval and
LLM safety boundaries.

## Boundaries and ownership

- **LLM boundary:** LLMs parse request intent and structured slots only. They do
  not select songs, infer arrangement suitability, score personnel, summarize
  private readiness notes, or generate private personnel facts.
- **Recommendation boundary:** REng starts from `v_recommendable_arrangements`.
  Team readiness, availability, and musician preferences can filter or score an
  already-approved arrangement according to the active scoring profile, but they
  cannot make an unapproved song, arrangement, or lyrics document recommendable.
- **Personnel privacy boundary:** Musician contact data, exact vocal ranges, raw
  skill records, private availability notes, and readiness notes stay inside the
  church instance and require personnel-planning authorization.
- **Operational ownership:** Worship leaders own service planning decisions;
  schedulers maintain assignments and responses; catalog reviewers own
  arrangement suitability profiles and controlled-vocabulary governance;
  operators monitor access denials, stale vocabulary, and readiness alerts.

## Roster setup workflow

1. Create synthetic or real local musician profiles through the roster/admin UI
   or approved import/synchronization endpoint. Do not use manual database edits
   as the primary workflow.
2. Record only planning data that the church needs: display name,
   account-principal link when self-service is enabled, active status, optional
   contact fields, serving preference, and broad vocal range category.
3. Add role, instrument, and vocal-part assignments using controlled vocabulary
   codes. Store skill floors as structured values; do not rely on free-form
   notes for deterministic matching.
4. Keep sensitive notes out of roster fields unless they are explicitly governed
   by the personnel privacy policy and privileged audit path.
5. Deactivate profiles instead of deleting historical assignment anchors.

## Controlled-vocabulary maintenance

System-default values for musician roles, instruments, vocal parts, vocal
ranges, skill levels, serving preferences, assignment statuses, and readiness
statuses must stay stable because tests, profiles, and diagnostics reference
codes directly. Churches may add local extensions when the UI or governance
workflow records:

- a stable uppercase code;
- display name and operational description;
- active/inactive lifecycle state;
- reviewer or operator reference;
- migration guidance if an old value becomes stale.

When retiring a value, mark it inactive and migrate active musician profiles,
arrangement suitability slots, and service assignments through the application
workflow. Do not delete vocabulary rows that are still referenced by historical
assignments or diagnostics.

## Availability collection

- Collect availability in structured windows tied to the musician and, when
  known, a service plan.
- Use controlled response/status values such as `REQUESTED`, `TENTATIVE`,
  `ACCEPTED`, `DECLINED`, and `UNAVAILABLE`.
- Private availability notes are not recommendation evidence. Diagnostics may
  cite aggregate availability status and counts only.
- A scheduler override can permit a deliberate assignment despite an
  unavailable window, but the override remains operational; it is not an
  approval, readiness, or suitability shortcut.

## Service and rehearsal assignment workflow

1. Create or update the service plan.
2. Add service team assignments with musician, role, optional instrument,
   optional vocal part, assignment order, and status.
3. Add rehearsal events and link rehearsal assignments to the same service plan.
4. Capture assignment responses separately for service and rehearsal attendance.
5. Use song-specific assignment overrides only when a service-plan block needs a
   different lead, instrument, or vocal part for that song.
6. Review readiness after initial scheduling, after rehearsal, and before final
   publication.

Assignments are lifecycle records. Status changes and substitutions preserve
history through audit events and `substitute_for_assignment_id` links rather
than overwriting the original row.

## Substitutions

When a musician declines or becomes unavailable:

1. Keep the original assignment and update its status through the assignment
   workflow.
2. Create a substitute assignment that references the original assignment.
3. Ensure active roster summaries deterministically prefer the substitute for
   current coverage while retaining the original assignment for history.
4. Re-run team-aware diagnostics to detect new instrument, skill, vocal range,
   or rehearsal-response conflicts.

## Team-aware recommendation profiles

Profiles decide how team facts affect an approved candidate:

- `DISABLED`: ignore a team fact for the profile.
- `SCORING_INPUT`: apply deterministic scoring/penalties without excluding the
  candidate.
- `HARD_FILTER`: exclude the candidate after approved-only eligibility if the
  configured fact fails.

Recommended defaults are to hard-filter missing required instruments and lead
vocal range conflicts for team-constrained planning, score optional instrument
fit, and warn on incomplete teams until the roster is final. Basic approved
catalog recommendations must still run when team constraints are disabled.

## Diagnostics and evidence

Team explanation facts are additive and structured. They may include required
instrumentation, optional instrumentation, vocal configuration, lead-vocal range
fit, minimum skill coverage, assignment status, availability status, and
incomplete-team warnings. Evidence must cite deterministic data references such
as `arrangement_suitability` and `service_assignment`.

Public explanations redact team diagnostics. Worship-leader and admin views may
show aggregate staffing gaps but must not expose private contact details,
availability-note text, readiness-note text, exact musician range values, or raw
skill records.

## Readiness rules

Readiness describes operational risk for an already planned service team. It may
record objective blockers, missing people, unresolved arrangement conflicts,
rehearsal response state, privacy-classified notes, and operational override
markers. Readiness never approves songs, arrangements, lyrics, doctrine,
licensing, editorial status, exports that claim approval, or publication paths
that require approved catalog content.

## Troubleshooting

### Missing instruments

- Check the arrangement suitability slots for required instruments and minimum
  counts.
- Confirm the service roster has accepted or substitute assignments with active
  instrument codes.
- If the instrument exists locally but diagnostics still miss it, check whether
  the vocabulary value is inactive or not mapped to the slot's controlled code.

### Vocal range conflicts

- Confirm the arrangement profile lead-vocal MIDI bounds were reviewed and are
  current.
- Confirm assigned lead vocalists have broad range data recorded.
- Use transposition or a different approved arrangement if the range is outside
  the assigned lead's comfortable range. Do not expose exact range values in
  public or musician-facing diagnostics.

### Unavailable musicians

- Review structured availability windows and assignment statuses.
- Ask the musician to update their response through the self-service workflow or
  let a scheduler record an explicit operational override.
- Re-run diagnostics after substitutions; do not use private availability notes
  as scoring evidence.

### Incomplete teams

- Confirm whether the service is intentionally sparse, still being scheduled, or
  missing required coverage.
- Use readiness status `AT_RISK` or `BLOCKED` for objective staffing blockers.
- Keep recommendations deterministic: incomplete-team readiness may warn or
  filter according to profile, but it cannot admit unapproved catalog records.

### Stale vocabulary values

- Search active musician profiles, service assignments, and arrangement slots for
  inactive vocabulary codes.
- Migrate active data through the admin workflow to an active controlled value.
- Keep old codes for historical audit and diagnostics references.

### Authorization denials

- Verify the actor has the correct role: worship leader, team scheduler,
  assigned musician self-service, admin, or reviewer.
- Confirm the action is inside the deployed church instance and does not request
  private fields for an unauthorized audience.
- Check privileged-action audit events for denied personnel mutations, but keep
  contact details and sensitive note text out of logs and telemetry labels.
