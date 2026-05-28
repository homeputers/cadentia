# ADR-018 Service Plan Operations Runbook

## Purpose

Define observability, alerting, and operator procedures for service-plan
lifecycle operations introduced by ADR-018.

## Required Metrics

- `cadentia_service_plan_draft_to_publish_total`
  - Counter incremented on successful draft-to-publish transitions.
  - Labels: `result` (`success`).
- `cadentia_service_plan_publish_total`
  - Counter for publish attempts that complete successfully.
  - Labels: `result` (`success`).
- `cadentia_service_plan_publish_failures_total`
  - Counter for publish conflicts and validation failures.
  - Labels: `reason` (`missing_setlist_version`, `stale_setlist_version`).
- `cadentia_service_plan_block_reorder_total`
  - Counter for explicit service-plan block reorder requests.
  - Labels: `result` (`success`).

## Structured Audit Logging

Publish and revision-sensitive actions must emit structured action codes.

- required fields:
  - `action_code`
  - `service_plan_id`
  - `actor`
  - `before_sequence`
  - `after_sequence`
  - `result`
- current action codes:
  - `SERVICE_PLAN_PUBLISH_SUCCESS`
  - `SERVICE_PLAN_PUBLISH_CONFLICT_MISSING_REFERENCE`
  - `SERVICE_PLAN_PUBLISH_CONFLICT_STALE_REFERENCE`

Do not log free-text service titles, theme notes, or scripture text as metric
labels. Keep labels low-cardinality and operationally bounded.

## Alerts

Baseline alerts for ADR-018:

1. Publish conflict surge:
   - Trigger when `cadentia_service_plan_publish_failures_total` exceeds 2x
     trailing seven-day baseline over 15 minutes.
2. Stale reference conflict concentration:
   - Trigger when `reason=stale_setlist_version` exceeds configured threshold
     for the same planning window.
3. Reorder churn anomaly:
   - Trigger when block reorder volume spikes above normal rehearsal windows.

## Incident Triage

### Publish Failure or Stale Reference Conflict

1. Verify referenced `setlist_id` and `setlist_version_id` attachments for the
   impacted service plan.
2. Confirm whether a newer setlist version exists and coordinate reattachment
   with worship planning owner.
3. Re-run publish once references are refreshed and capture audit log action
   codes in the incident ticket.

### Multi-campus Fork/Share Coordination

1. Keep campus-specific service plans as separate artifacts, even when
   originating from shared setlist lineage.
2. Attach immutable setlist versions explicitly per campus service plan and do
   not auto-repoint to newer versions.
3. For cross-campus updates, publish changes in one campus plan, then apply
   explicit reattachment and reorder actions in each dependent campus plan.

## References

- `docs/adr/ADR-018-service-plan-integration-model.md`
- `docs/implementation-plans/ADR-018-service-plan-integration-model-plan.md`
- `docs/ARCHITECTURE.md`
