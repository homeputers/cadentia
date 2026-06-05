# ADR-024 Rehearsal Workflow Operations

## Readiness state machine

Cadentia readiness is an explicit service-scoped workflow state. It is never inferred
from LLM summaries, note tone, recommendation scores, or the absence of comments.
The application service accepts only these normal transitions:

| From | Allowed next states | Operational meaning |
| --- | --- | --- |
| `draft` | `planned` | Rehearsal workflow exists but setup is not yet scheduled or confirmed. |
| `planned` | `draft`, `rehearsing` | Setup can be corrected before rehearsal starts, or moved into active rehearsal. |
| `rehearsing` | `planned`, `issues_open`, `ready` | Team is actively working the service; unresolved items move it to review. |
| `issues_open` | `rehearsing`, `ready` | Known issues are being reviewed; readiness requires blocker/action closure. |
| `ready` | `rehearsing`, `issues_open`, `completed` | Service is confirmed ready, but can be reopened if rehearsal reality changes. |
| `completed` | none through normal flow | Closure is terminal for the ordinary workflow. |

Emergency corrections from `completed` or other otherwise-invalid transitions require
the administrator-only emergency correction operation, a reason, and an audit record.

## Blocker semantics

An issue blocks readiness when it is still open (`open` or `in_progress`) and either:

- its severity is `blocking`; or
- its category is `blocker`.

Required rehearsal actions block readiness when their action status is `todo` or
`in_progress`. Marking a service `ready` or `completed` is rejected until all open
blocking issues and required rehearsal actions are explicitly resolved, deferred,
cancelled, done, or cancelled as appropriate. Service completion must not silently
resolve blockers.

## Manual overrides

Service-specific arrangement overrides are allowed only as service-scoped workflow
records. They do not mutate canonical arrangements and must be accompanied by an
audited lifecycle event. Normal override creation should include a reference such as
a rehearsal note, incident, or planning ticket.

## Emergency correction workflow

Use emergency correction only for operational data repair, for example an accidental
service closure or a readiness state recorded against the wrong rehearsal session.
The operator must:

1. Confirm the current explicit workflow state and the derived readiness status.
2. Verify that unresolved blockers and open required actions are not being hidden.
3. Execute the administrator-only emergency correction with a concise reason and
   external reference.
4. Review audit history for actor, action code, target type/id, service id,
   rehearsal session id where applicable, timestamp, reason/reference metadata, and
   before/after snapshots.

Do not place sensitive free-text rehearsal notes in telemetry labels, audit action
codes, or compact audit summaries. Store human-authored note bodies only in note
records with the correct visibility classification.
