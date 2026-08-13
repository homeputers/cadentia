# Telegram New Setlist Gap Closure Batch Plan

## Objective

Close the gap between the documented Telegram `/newsetlist` flow and the current
implementation in small, reviewable batches. The target flow is:

1. Telegram receives a webhook update.
2. The webhook validates freshness, idempotency, and channel policy.
3. `/newsetlist` starts a shared conversation session.
4. Guided menu choices and free text update the same normalized slot model.
5. Free text is interpreted only through the approved LLM intent boundary.
6. The user confirms a normalized request.
7. The shared setlist proposal path invokes REng.
8. REng retrieves approved catalog candidates, scores and orders them
   deterministically, and returns explanations with dataset references.
9. Telegram renders and sends a safe proposal response.

This plan intentionally favors multiple smaller diffs over one large patch. Each
batch should keep production behavior coherent and should avoid broad unrelated
refactors.

## Current Gap Summary

- The production webhook controller validates and acknowledges Telegram updates,
  but does not visibly invoke the bot adapter, renderer, or outbound send service.
- The Telegram adapter and gateway implement the rough route shape, but guided
  callbacks only patch a narrow subset of slots.
- Free-text intent parsing and deterministic slot merging exist, but session
  provenance and revision history are mostly in-memory scaffolding.
- Confirmation currently calls the setlist service, but confirmation-state
  guardrails are incomplete.
- `SetlistService.generate` still returns a
  `PENDING_CATALOG_IMPLEMENTATION` scaffold instead of orchestrating candidate
  retrieval, scoring, ordering, and response mapping.
- Candidate retrieval and deterministic ordering classes exist, but are not
  wired into the shared proposal path.
- Telegram E2E tests prove the intended shape with stubs, not a production
  webhook-to-REng path.

## Batch 1: Production Telegram Wiring

### Context

The webhook endpoint is the public Telegram entrypoint. It already performs
basic validation, stale-update handling, and idempotency recording. The bot
adapter, response renderer, outbound repository, and outbound send service exist
separately.

### Prompt

Wire accepted Telegram webhook updates into the existing bot-processing
pipeline. Inject the Telegram adapter, renderer, and outbound send service into
the webhook processing path. Preserve validation, stale-update rejection,
idempotency semantics, and safe logging. For accepted duplicate updates, avoid
re-running bot logic or sending duplicate messages.

### Acceptance Criteria

- A non-duplicate accepted webhook update is routed through
  `TelegramBotAdapter.handleUpdate`.
- The adapter response is rendered into Telegram-safe messages.
- Rendered messages are sent through `TelegramOutboundSendService`.
- Duplicate accepted updates return the existing duplicate acknowledgement
  without resending.
- Tests cover webhook-to-adapter processing, rendered outbound sends, duplicate
  suppression, invalid payloads, stale updates, and outbound failure behavior.
- The diff should target roughly 800-1,500 LOC.

### Restrictions

- Do not bypass existing webhook validation or idempotency checks.
- Do not log raw Telegram message text, payloads, tokens, or secrets.
- Do not introduce direct song-selection logic in Telegram classes.
- Do not change the public webhook contract unless strictly required by the
  generated API interface.

## Batch 2: Conversation State Guardrails

### Context

ADR-015 requires a confirmable normalized request before recommendation
execution. The current facade can transition and merge slots, but it does not
fully enforce the desired confirmation boundary or expose useful slot-source and
revision data.

### Prompt

Tighten conversation session semantics across Telegram and HTTP. Require an
appropriate ready state before recommendation generation. Preserve clarification,
unsupported, cancelled, and expired states. Populate slot sources and revision
history from merge events in the current in-memory implementation, without
introducing durable persistence yet.

### Acceptance Criteria

- Confirmation can generate only after the session reaches `READY_TO_CONFIRM`.
- Premature confirmation returns a safe continued/clarification response instead
  of invoking REng.
- Cancelled and expired sessions do not generate recommendations.
- Revision after confirmation returns to a collecting/revision state and requires
  reconfirmation before generation.
- `ConversationSessionStateResponse.slotSources` identifies the effective source
  of each populated slot.
- `ConversationSessionStateResponse.revisionHistory` includes source-tagged
  slot-update, confirm, cancel, expire, and recover events.
- Tests cover Telegram and HTTP behavior for premature confirm, ready confirm,
  cancel, expire, revise, and unsupported/clarify outcomes.
- The diff should target roughly 700-1,600 LOC.

### Restrictions

- Do not add durable session persistence in this batch.
- Do not allow ambiguous free-form state values.
- Do not generate a recommendation from a non-ready session state.
- Do not silently overwrite user-provided values without revision history.

## Batch 3: Guided Choice Completion

### Context

The Telegram callback enum includes guided fields for scripture/theme, counts,
language, key policy, tempo policy, and energy arc. Current menu patching handles
only a narrow subset, so the guided path is not yet a real structured collection
experience.

### Prompt

Complete guided menu callback handling for the existing setlist slots. Define
compact callback payload values, map those values into `ConversationSlotUpdate`
patches, and render practical Telegram keyboards for the `/newsetlist` flow.
Keep menu updates on the shared conversation session model.

### Acceptance Criteria

- Guided callbacks can patch language, counts, key policy, tempo policy, energy
  arc, and service moment where supported by the shared contract.
- Unsupported or malformed callback values fail safely.
- Menu selections override inferred free-text values according to the merge
  service precedence rules.
- Telegram messages expose a usable set of guided choices without exceeding
  Telegram callback data limits.
- Tests cover each callback action, callback value validation, slot patch output,
  and merge precedence against prior free-text values.
- The diff should target roughly 700-1,500 LOC.

### Restrictions

- Do not create Telegram-specific defaults that are not represented in the
  shared session/request contract.
- Do not encode large JSON objects in Telegram callback data.
- Do not add LLM prompt logic to Telegram code.

## Batch 4: Real REng Orchestration

### Context

`SetlistService.generate` currently verifies instance policy and creates a
scoring request, but returns a scaffold response. Candidate retrieval, hard
constraints, feature scoring, deterministic ordering, and explanations exist as
lower-level pieces.

### Prompt

Replace the scaffold proposal path with real catalog-backed recommendation
orchestration. Wire the shared setlist service to retrieve approved candidates,
apply deterministic constraints, score candidates, order them, and map the
ordered result into `SetlistProposalResponse` with explanation references.

### Acceptance Criteria

- `SetlistService.generate` retrieves candidates only through approved catalog
  read models.
- The service enforces approved-only and dataset-reference policy.
- Hard constraints exclude disallowed candidates deterministically.
- Candidate scoring and deterministic ordering produce stable results for a
  fixed catalog snapshot and request.
- Empty and insufficient-candidate outcomes return safe audit messages without
  fabricating songs.
- The response includes selected-song explanation entries and evidence refs
  suitable for Telegram rendering.
- Integration tests use seeded approved catalog fixtures and assert deterministic
  order and explanation references.
- The diff may exceed 2,000 LOC if necessary, but should remain focused on
  orchestration and response mapping.

### Restrictions

- Do not let the LLM select songs or provide catalog facts.
- Do not expose rejected/unapproved candidates in public responses.
- Do not mutate catalog data while generating recommendations.
- Do not collapse unrelated REng refactors into this batch.

## Batch 5: Telegram Proposal Rendering And Evidence Safety

### Context

Telegram rendering already has a proposal renderer that can show selected songs
and concise evidence refs, but production usage and evidence redaction need to
be verified against real proposal output.

### Prompt

Finalize Telegram proposal rendering for real `SetlistProposalResponse` objects.
Render concise, useful, Telegram-safe messages with selected songs and approved
references. Ensure public Telegram output does not leak raw lyrics, admin-only
diagnostics, hidden scoring internals, rejected candidates, or cross-instance
evidence.

### Acceptance Criteria

- Telegram proposal output includes selected song order and concise approved
  evidence references.
- Long proposal responses split safely under Telegram message limits.
- Public output redacts or omits admin-only and unsafe facts.
- Renderer tests cover empty proposals, successful proposals, long proposals,
  unsafe audit messages, HTML escaping, and callback acknowledgements.
- The diff should target roughly 500-1,200 LOC.

### Restrictions

- Do not include raw lyrics in Telegram output.
- Do not expose hidden scoring internals or rejected candidate details.
- Do not make Telegram the source of proposal persistence.

## Batch 6: End-To-End Equivalence Verification

### Context

ADR-035 requires Telegram and HTTP/API paths to produce the same normalized
request and Recommendation Engine output for the same actor, instance, catalog
snapshot, and policy snapshot.

### Prompt

Add deterministic end-to-end verification using seeded approved catalog data and
mocked Telegram API responses. Exercise `/newsetlist` from webhook through
session workflow, confirmation, backend recommendation, rendered Telegram
response, and outbound send. Compare against the HTTP/API proposal path for the
same normalized request.

### Acceptance Criteria

- Tests cover the full `/newsetlist` path from webhook update through outbound
  Telegram response.
- Tests prove Telegram and HTTP/API paths produce equivalent normalized request,
  candidate eligibility, recommendation ordering, and explanation references.
- Fixtures cover duplicate updates, webhook-secret rejection, unauthorized
  users, disabled channel, stale callbacks, cancellation, retryable outbound
  failures, and dead-letter creation.
- The Telegram operations runbook documents smoke-test steps and known
  limitations.
- The diff should target roughly 800-1,800 LOC.

### Restrictions

- Do not require real Telegram network calls or production credentials.
- Do not record raw production Telegram updates or private user message text as
  fixtures.
- Do not assert brittle timestamps or generated IDs unless explicitly fixed in
  the fixture clock.

## Batch 7: Durable Session And Proposal Persistence

### Context

The current session facade uses in-memory state. ADR-015 and ADR-035 call for
auditable revision history and optional setlist persistence through the shared
setlist boundary.

### Prompt

Move conversation sessions, slot sources, revision events, and relevant
correlation identifiers into durable storage. Persist accepted generated
proposals through the shared setlist baseline/version boundary when configured.
Preserve recovery behavior across application restarts.

### Acceptance Criteria

- Sessions survive application restarts when durable storage is configured.
- Slot sources and revision history are stored and recoverable.
- Telegram update ID, session ID, recommendation result ID, and outbound send
  records can be correlated without exposing sensitive content.
- Accepted generated proposals can be persisted through the existing setlist
  persistence boundary.
- Recovery behavior distinguishes expired, cancelled, confirmed, and active
  sessions.
- Integration tests verify persistence, recovery, and correlation behavior.
- The diff may exceed 2,000 LOC if necessary and may be split into storage and
  proposal-persistence sub-batches.

### Restrictions

- Do not store raw free-text content unless an approved retention policy exists.
- Do not persist Telegram-specific setlists outside the shared setlist boundary.
- Do not bypass authorization checks when recovering or persisting sessions.

## Recommended Execution Order

1. Batch 1: Production Telegram Wiring
2. Batch 2: Conversation State Guardrails
3. Batch 3: Guided Choice Completion
4. Batch 4: Real REng Orchestration
5. Batch 5: Telegram Proposal Rendering And Evidence Safety
6. Batch 6: End-To-End Equivalence Verification
7. Batch 7: Durable Session And Proposal Persistence

This order gives the project a working but scaffolded Telegram loop first, then
hardens the shared confirmation boundary, improves the guided user experience,
and only then connects real catalog-backed recommendation generation.

## Cross-Batch Verification Commands

Use focused tests while developing a batch, then run the API module test suite
before merging:

```bash
./mvnw -pl apps/api test
```

After OpenAPI changes, verify generated sources still resolve:

```bash
./mvnw -pl apps/api -DskipTests generate-sources
```

## Non-Goals

- Replacing the existing LLM intent contract.
- Allowing the LLM to select or invent songs.
- Building a separate Telegram-only recommendation path.
- Persisting Telegram-specific setlists outside shared setlist/version services.
- Solving every ADR-035 operational task in the first production-wiring batch.
