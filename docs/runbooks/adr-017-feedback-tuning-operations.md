# ADR-017 Feedback Tuning Operations Runbook

## Purpose

Define operator workflows for observability, governance, and safe reset
operations for ADR-017 feedback tuning.

## Roles and Authorization Boundaries

- Worship leaders can submit/list feedback in authorized scopes.
- Team leads may inspect team-scope aggregates and initiate team-scope resets
  when governance policy allows.
- Catalog admins (or equivalent governance approvers) own policy/global reset
  decisions and retention compliance reviews.

Reset tooling must remain protected by privileged authorities and never exposed
as anonymous or broad user actions.

## Telemetry and Audit Signals

Track:

- `cadentia_feedback_events_total`
- `cadentia_feedback_ranking_impact_distribution`
- `cadentia_feedback_scope_resets_total`

Ensure audit entries include `actor_id`, `scope_layer`, `scope_id`, operation,
`audit_reference`, and UTC timestamp.

Do not log raw user free text, notes, or sensitive pastoral context in feedback
telemetry.

## Daily / Weekly Operations

Daily:

1. Review feedback volume by `outcome` and `scope_layer`.
2. Confirm no unexpected concentration of `rejected` outcomes for one scope.
3. Validate reset counts are within baseline and tied to approved incidents.

Weekly:

1. Inspect ranking-impact distribution trends for drift.
2. Compare high-impact feedback scopes with recent profile/rule versions.
3. Review audit trail completeness and timestamp continuity.

## Incident: Negative Feedback Spike

1. Confirm spike by outcome/scope metrics.
2. Inspect latest feedback events for controlled replacement-reason patterns.
3. Identify whether spike is localized to personal/team scope or policy level.
4. If policy-level drift suspected, pause profile rollout or revert to previous
   tuning profile version.
5. Document incident and decisions in governance log.

## Incident: Excessive Reset Activity

1. Correlate resets with actor identity and role authorization.
2. Verify each reset maps to an approved operational reason.
3. If misuse suspected, revoke/reset role grants and trigger security review.
4. Restore affected feedback state from backup only when governance approves.

## Retention and Compliance

- Keep feedback events/audit records per environment policy while preserving
  deterministic replay requirements.
- Apply shorter retention windows for personal-scope preference details where
  permitted by policy.
- Never delete audit evidence for privileged reset actions without replacement
  correction records.

## References

- `docs/adr/ADR-017-user-feedback-and-recommendation-tuning.md`
- `docs/implementation-plans/ADR-017-user-feedback-and-recommendation-tuning-plan.md`
- `docs/ARCHITECTURE.md`
