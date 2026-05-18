# ADR-012 Implementation Plan: LLM Intent Extraction Contract

## Objective

Implement the schema-validated LLM intent boundary before natural-language
requests can reach recommendation execution. The LLM extracts intent and slots
only; backend services apply defaults, validate schemas, and reject unsupported
claims.

## Subtask 1: Version the intent schema artifact

### Context

ADR-012 requires versioned JSON schemas for supported intents, required fields,
controlled slot values, numeric bounds, maximum lengths, defaults, and rejection
of unsupported fields.

### Prompt

Create the first versioned intent schema artifact for `GENERATE_SETLIST`,
`CLARIFY_REQUEST`, and `UNSUPPORTED_REQUEST`. Place it where backend tests and
shared contract tooling can load it. Include explicit additional-property
rejection and defaults documentation.

### Acceptance criteria

- A schema artifact exists for intent contract version `v1`.
- `GENERATE_SETLIST` validates the approved slot shape from ADR-012.
- Unknown top-level fields and unknown slot fields are rejected.
- Counts, BPM jump, key-center limits, strings, arrays, and enums have bounded
  validation rules.
- Schema fixtures cover valid, missing-optional, malformed, and unsupported-field
  payloads.

### Restrictions

- Do not add song-selection fields, arrangement identifiers, approval decisions,
  provenance records, or database write instructions.
- Do not rely on LLM prose repair as a source of truth.
- Do not make the schema permissive for future fields without explicit versioning.

## Subtask 2: Implement backend validation and default application

### Context

Backend validation is mandatory and defaults must be deterministic when optional
slots are absent.

### Prompt

Implement a validation service that parses LLM JSON, validates it against the
versioned schema, rejects unsupported fields, and applies backend defaults for
missing optional slot groups before producing an internal intent object.

### Acceptance criteria

- Invalid JSON produces a validation failure without invoking recommendation.
- Schema violations include actionable error codes for retry or clarification.
- Missing counts default to 10 praise and 5 worship.
- Missing key policy defaults to same-key preference, relative major/minor
  allowed, and at most two key centers.
- Missing tempo policy defaults to a 12 BPM adjacent jump limit.
- Unit tests verify all default and rejection paths.

### Restrictions

- Do not trust defaults emitted by the LLM over backend defaults when fields are
  missing or invalid.
- Do not silently ignore unknown fields.
- Do not call the Recommendation Engine from validation tests.

## Subtask 3: Harden prompt templates and prompt versioning

### Context

Prompt changes must be versioned and tested against fixtures. The system prompt
must require JSON only and must prohibit song selection or catalog claims.

### Prompt

Create a versioned prompt template for intent extraction and a small prompt
registry that binds prompt version to schema version. Add fixture tests that
assert the prompt includes all non-negotiable ADR-012 guardrails.

### Acceptance criteria

- Prompt version `intent-v1` maps to schema version `v1`.
- Prompt text requires JSON only and forbids prose or Markdown.
- Prompt text explicitly forbids selected songs, invented songs, catalog
  availability, source claims, license claims, approvals, BPM, keys, tags, or
  CCLI numbers as catalog facts.
- Tests fail if required guardrail phrases are removed.

### Restrictions

- Do not embed provider-specific API credentials or model names in the prompt
  artifact.
- Do not allow prompt instructions to override backend validation.
- Do not ask the LLM to infer recommendability.

## Subtask 4: Implement retry, repair, and safe failure behavior

### Context

ADR-012 allows one strict retry for malformed JSON, followed by clarification or
safe error if validation still fails.

### Prompt

Add orchestration around the LLM client that retries once with a strict repair
prompt when JSON is malformed or schema validation fails, then returns either a
validated intent, a clarification response, or a safe unsupported response.

### Acceptance criteria

- Malformed JSON triggers exactly one retry.
- A second malformed or invalid response does not reach recommendation.
- `CLARIFY_REQUEST` and `UNSUPPORTED_REQUEST` are represented as structured
  backend outcomes.
- Retry and final-failure events are logged without storing sensitive raw user
  content beyond approved observability policy.
- Tests cover first-pass success, retry success, retry failure, clarify, and
  unsupported flows.

### Restrictions

- Do not perform multiple unbounded retries.
- Do not attempt heuristic extraction from invalid prose.
- Do not downgrade validation errors into accepted requests.

## Subtask 5: Wire the natural-language endpoint boundary

### Context

Only validated slots may cross into deterministic backend services. The LLM must
not select songs, and the backend must reject unsupported payloads before any
candidate retrieval.

### Prompt

Update the natural-language setlist request path so it accepts user text, invokes
intent extraction, validates the result, applies defaults, and passes only the
validated internal request object to recommendation orchestration.

### Acceptance criteria

- Recommendation execution is not invoked unless the parsed intent is valid
  `GENERATE_SETLIST`.
- Unsupported or clarification outcomes return safe API responses.
- Named songs in user text are treated only as supported preferences or
  exclusions, not as selected recommendations.
- Integration tests prove invalid LLM outputs never invoke the Recommendation
  Engine.

### Restrictions

- Do not allow API clients to submit arbitrary LLM output directly to the engine.
- Do not add LLM-selected song titles to recommendation responses.
- Do not bypass existing approval-gated candidate retrieval.

## Subtask 6: Add contract fixtures and regression tests

### Context

The safety boundary needs durable tests so future prompt, schema, and parser
changes cannot weaken hallucination prevention.

### Prompt

Build a contract fixture suite with representative user requests and mocked LLM
outputs, including adversarial examples where the LLM emits songs, metadata,
approval claims, unsupported fields, or prose.

### Acceptance criteria

- Fixtures include positive examples for scripture, themes, counts, policies,
  language, energy arc, exclusions, and service moment.
- Adversarial fixtures with selected songs or catalog facts fail validation.
- Regression tests are deterministic and do not require network access.
- Fixture names document the user scenario and expected backend outcome.

### Restrictions

- Do not call an external LLM in unit or contract tests.
- Do not use snapshot tests that obscure schema-level assertions.
- Do not add fixtures that imply the LLM can recommend songs.
