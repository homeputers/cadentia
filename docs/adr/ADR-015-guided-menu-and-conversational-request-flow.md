# ADR-015: Guided Menu and Conversational Request Flow

Status: Proposed  
Date: 2026-05-26

## Context

Cadentia receives setlist requests from menu-driven interfaces (such as Telegram menus) and free-text conversational input. Users often provide incomplete constraints initially and refine them iteratively.

The platform needs a deterministic orchestration layer that accumulates slots, resolves ambiguity, and confirms a normalized request before deterministic recommendation execution.

## Problem

If flow control is undefined, user requests can become inconsistent or lossy:

- partial constraints may be overwritten unexpectedly
- free-text interpretation may conflict with explicit menu choices
- no confirmation boundary may cause accidental generation
- stale session state can produce incorrect recommendations

## Decision

Define a conversation state machine that supports menu-only, free-text-only, and mixed interactions through shared slot accumulation and normalization.

The request pipeline must:

1. collect inputs from both channels into one session request model
2. parse free text via ADR-012 extraction contract
3. merge slot updates deterministically with source precedence rules
4. detect ambiguity and request clarification
5. confirm normalized request prior to recommendation

## Requirements

- Define states at minimum:
  - `START`
  - `COLLECTING`
  - `CLARIFICATION_REQUIRED`
  - `READY_TO_CONFIRM`
  - `CONFIRMED`
  - `EXPIRED`
  - `CANCELLED`
- Support guided menu inputs for:
  - theme
  - verse
  - shape
  - language
  - key/tempo/energy constraints
- Support free-text extraction and slot mapping.
- Support mixed-mode slot accumulation across turns.
- Provide revision actions for theme, verse, shape, language, and constraints.
- Confirm normalized request before recommendation is invoked.
- Persist temporary session state with revision history.
- Define timeout and expiration behavior with user-visible recovery message.
- Define ambiguity fallback behavior (clarify question, show current assumptions, request confirmation).

### Merge and Precedence Rules

- Explicit user edits in the current turn override previous values.
- Direct menu selection overrides inferred free-text values unless user chooses otherwise.
- Free-text inferred values fill only missing slots when no explicit conflict exists.
- System defaults apply only after user-provided slots are exhausted.
- All merges create audit events with source tags (`menu`, `free_text`, `default`, `user_edit`).

## Acceptance Criteria

- User can build a complete request using guided menus only.
- User can build a complete request using free text only.
- User can mix menu and free-text input and still receive deterministic normalized slots.
- System presents normalized request summary for confirmation before recommendation.
- Expired sessions do not silently reuse stale constraints.

## Consequences

Positive:

- improved user control and transparency
- reduced accidental generation with wrong assumptions
- consistent orchestration across interaction channels

Tradeoffs:

- state machine implementation adds complexity to bot/web adapters
- additional confirmation step may add one interaction turn

## Alternatives Considered

1. Stateless single-message generation only.
   - Rejected: poor support for partial requests and revisions.
2. Separate pipelines for menu and free-text requests.
   - Rejected: duplicated logic and inconsistent normalization.
3. Auto-generate without explicit confirmation.
   - Rejected: higher risk of unintended recommendations.

## Open Questions

- Should sessions expire by inactivity timeout only or also by absolute lifetime?
- What is the optimal clarification prompt style for worship teams with limited technical fluency?
- Should confirmations support “lock this preference for future sessions” as an opt-in?
