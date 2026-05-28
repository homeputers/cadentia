# ADR-019 Security Observability and Incident Response Runbook

## Purpose

This runbook defines observability signals, alert thresholds, triage steps, and emergency remediation procedures for ADR-019 security controls.

It covers:
- authorization allow/deny outcomes
- policy override usage
- reviewer approval anomalies
- approved+active content visibility regressions

## Metrics and Labels

All metrics must avoid user-identifying labels and unbounded cardinality.

### 1) Authorization outcome metrics

- `cadentia_authz_decisions_total`
  - Type: Counter
  - Labels:
    - `operation_class` (bounded enum, e.g. `catalog.read.public`, `catalog.approve.doctrinal`)
    - `role` (bounded enum from ADR-019 role set)
    - `decision` (`allow` | `deny`)
    - `surface` (`controller` | `service_policy`)

- `cadentia_authz_denied_ratio`
  - Type: Recording rule (derived)
  - Formula: denied / (allowed + denied)
  - Window: 5m and 1h

### 2) Policy override metrics

- `cadentia_policy_overrides_total`
  - Type: Counter
  - Labels:
    - `override_type`
    - `actor_role`

- `cadentia_policy_override_open_total`
  - Type: Gauge
  - Description: active, non-expired overrides requiring review.

### 3) Approval workflow anomaly metrics

- `cadentia_approval_decisions_total`
  - Type: Counter
  - Labels:
    - `review_domain` (`doctrinal` | `musical`)
    - `decision` (`approved` | `rejected`)
    - `actor_role`

- `cadentia_approval_latency_seconds`
  - Type: Histogram
  - Labels:
    - `review_domain`

### 4) Visibility gate regression metrics

- `cadentia_visibility_gate_failures_total`
  - Type: Counter
  - Labels:
    - `api_surface` (`catalog_public` | `recommendation_public`)
    - `failure_type` (`unapproved_exposed` | `inactive_exposed`)

## Tracing Requirements

Add spans for every authorization-protected request:

- Span name: `authz.evaluate`
- Required attributes:
  - `authz.operation_class`
  - `authz.required_roles`
  - `authz.actor_role`
  - `authz.decision`
  - `authz.policy_rule_id` (if policy layer involved)

For denied requests, include:
- `error.type=authorization_denied`
- `http.status_code=403`

Do not include user email, full names, or free-form request payloads in span attributes.

## Alerting Rules

### A1: Authorization deny spike

- Condition: 5m denied ratio > 0.20 for any `operation_class` with at least 50 decisions in window.
- Severity: Warning.
- Escalation: Page on-call if sustained for 15m.

### A2: Policy override usage detected

- Condition: any increase in `cadentia_policy_overrides_total` outside approved change window.
- Severity: High.
- Escalation: Immediate security + platform notification.

### A3: Approval anomaly burst

- Condition: approval decisions in one domain increase 3x over 24h baseline or rejection rate exceeds 0.70 for 30m.
- Severity: Warning (investigate workflow misuse or data issue).

### A4: Visibility gate failure

- Condition: `cadentia_visibility_gate_failures_total > 0` in any 5m window.
- Severity: Critical.
- Escalation: Immediate incident.

## Triage Playbook

### Step 1: Classify incident

- **Access anomaly** (deny spikes / authorization failures)
- **Privilege anomaly** (override/role misuse)
- **Content exposure anomaly** (unapproved/inactive leakage)

### Step 2: Correlate telemetry

1. Review alert source metric and operation class.
2. Query traces for same time window and decision path.
3. Query `v_privileged_action_audit_history` for related actor/action records.

Suggested SQL:

```sql
SELECT occurred_at,
       actor_id,
       action,
       target_type,
       target_id,
       metadata
FROM v_privileged_action_audit_history
WHERE occurred_at >= now() - interval '2 hours'
  AND action IN ('ROLE_ASSIGN', 'ROLE_REVOKE', 'POLICY_OVERRIDE_CREATE', 'APPROVAL_DECISION', 'CATALOG_MERGE')
ORDER BY occurred_at DESC;
```

### Step 3: Determine blast radius

- Identify impacted operation classes and APIs.
- Confirm whether exposure included recommendation or catalog public endpoints.
- Capture list of affected actors/roles without including PII in incident notes.

## Emergency Remediation

### R1: Revoke or rotate elevated access

1. Remove temporary or unexpected `ADMIN`/reviewer assignments.
2. Expire active policy overrides.
3. Rotate any break-glass credentials used during incident response.
4. Verify role-change and override-revocation events in audit trail.

### R2: Contain visibility regressions

1. Disable affected public read route behind feature flag if available.
2. Re-enable strict approved+active repository predicate.
3. Re-run regression checks before reopening traffic.

### R3: Recover and validate

1. Replay failed authorization scenarios from test suite.
2. Validate metrics return to baseline and alerts clear.
3. Publish incident summary including timeline, root cause, and prevention tasks.

## Regression Test Requirements

Minimum CI checks for ADR-019 security boundaries:

- API tests proving deny behavior for unauthorized approval and merge actions.
- Repository/read-model tests proving unapproved or inactive songs never appear on user-facing read paths.
- Audit tests proving privileged actions emit records.

## Ownership and Review Cadence

- Primary owner: Platform/API team.
- Security co-owner: Governance & compliance.
- Review cadence: monthly and after every security incident.
