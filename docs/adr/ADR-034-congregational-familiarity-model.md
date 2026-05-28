# ADR-034: Congregational Familiarity Model

Status: Proposed  
Date: 2026-05-28

## Context

Healthy worship planning balances familiar songs, fresh songs, and repetition over time. Cadentia needs deterministic familiarity and rotation logic that reflects tenant history without letting popularity override approval or musical constraints.

## Problem

Without a familiarity model, recommendations may overuse favorite songs, introduce too many unfamiliar songs at once, or ignore congregation learning cadence. Manual memory of usage history does not scale across teams and campuses.

## Decision

Introduce a tenant-scoped Congregational Familiarity Model based on song usage frequency, recency, introduction status, repetition cadence, and decay over time. Familiarity contributes deterministic scoring, penalties, and explanations while approval gates and hard musical constraints remain authoritative.

## Requirements

- Track song and arrangement usage by tenant, service, date, context, and setlist role.
- Track recency, rolling frequency, seasonal usage, and overuse thresholds.
- Track introduction cadence for new songs, including planned repetition windows.
- Model familiarity decay over time using versioned deterministic formulas.
- Support tenant-configurable rotation policies and maximum-repeat rules.
- Support gradual introduction of new songs without selecting too many unfamiliar songs in one set.
- Expose familiarity scores, penalties, and reason codes through explanations.
- Prevent usage-derived popularity from making unapproved songs eligible.

## Acceptance Criteria

- Recommendations avoid excessive repetition according to tenant policy.
- New song introduction can be controlled and explained.
- Familiarity scoring is deterministic for the same tenant history snapshot and profile.
- Usage history is tenant-scoped and not visible across unauthorized boundaries.
- Familiarity can influence ranking but cannot bypass approval, licensing, or hard musical constraints.

## Consequences

Positive:

- Supports healthier song rotation and congregation learning.
- Makes overuse and new-song tradeoffs visible.
- Enables tenant-specific planning without changing global catalog metadata.

Tradeoffs:

- Requires accurate service history capture.
- Familiarity formulas must be governed and explainable.
- New tenants may have sparse history and need bootstrap defaults.

## Alternatives Considered

1. Ignore usage history in recommendations.
   - Rejected: increases overuse and poor introduction pacing risk.
2. Use global popularity as familiarity.
   - Rejected: congregation familiarity is tenant-specific.
3. Let the LLM estimate familiarity from user prompts.
   - Rejected: nondeterministic and not grounded in service history.

## Open Questions

- What default decay period best reflects congregational memory?
- How should multi-campus tenants share or separate familiarity history?
- Should manual familiarity overrides require review or leader-only permissions?
