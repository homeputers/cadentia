# ADR-037 Implementation Plan: Ollama LLM Integration

## Objective

Implement Cadentia's v1 LLM runtime integration using Ollama while preserving the
ADR-012 intent-only boundary. The implementation must keep Ollama behind an
internal provider adapter, route every model response through schema validation,
provide deterministic tests without live model dependencies, and document
operator setup, smoke testing, and disablement.

## Source ADR

- [ADR-037: Ollama LLM Integration](../adr/ADR-037-ollama-llm-integration.md)

## Status

Overall status: Planned.

- Subtask 1: Planned - define internal LLM client boundary and configuration.
- Subtask 2: Planned - implement Ollama adapter and typed provider errors.
- Subtask 3: Planned - wire ADR-012 prompt, validation, retry, and orchestration.
- Subtask 4: Planned - add deterministic fake client and contract fixtures.
- Subtask 5: Planned - add observability, redaction, and provider health checks.
- Subtask 6: Planned - document local setup, smoke tests, operations, and rollback.

## Guiding Principles

- Ollama is a provider adapter only; it is not a recommendation service or source
  of catalog truth.
- No controller, recommendation service, catalog service, or admin workflow may
  call Ollama HTTP APIs directly.
- All raw model output must pass through ADR-012 JSON parsing, schema validation,
  defaulting, retry, and safe failure before any deterministic service acts on
  it.
- Required automated tests must not require a running Ollama daemon, pulled
  model, network access, or GPU.
- Optional live smoke tests may exist, but they must be opt-in and clearly marked
  as environment-dependent.
- Logs and metrics should identify outcomes and correlation identifiers without
  leaking sensitive user text, raw prompt content, or raw model output by
  default.

## Subtask 1: Define the internal LLM client boundary and configuration model

### Context

ADR-037 requires application code to depend on Cadentia semantics rather than
Ollama HTTP details. The provider boundary must expose only intent-extraction
capabilities and typed provider failures.

### Prompt

Create the backend internal LLM client interface, request/response value objects,
provider error taxonomy, and externalized configuration properties for the
Ollama-backed intent extraction path. Include an enabled flag, provider name,
base URL, model, timeout, and conservative generation options.

### Acceptance criteria

- A narrow internal LLM client interface exists for intent extraction calls.
- Request objects include prompt version, schema version, system prompt, user
  text, correlation metadata, and generation options derived from configuration.
- Response objects carry raw model text plus safe provider metadata.
- Typed errors represent disabled provider, timeout, connection failure,
  non-success status, unsupported response shape, and cancellation.
- Configuration is externalized and can disable LLM calls without changing code.
- No provider credentials, production hostnames, tenant IDs, or church-instance
  identifiers are hard-coded.

### Restrictions

- Do not add song-selection, catalog lookup, approval, provenance, or database
  write methods to the LLM client interface.
- Do not expose provider-specific HTTP classes outside the adapter package.
- Do not place model names or provider settings into prompt templates.

## Subtask 2: Implement the Ollama provider adapter

### Context

Ollama is the selected v1 provider. The adapter must translate Cadentia's narrow
LLM request into Ollama HTTP calls and translate provider responses or failures
back into Cadentia's client response and error types.

### Prompt

Implement an Ollama adapter for the internal LLM client. Prefer the chat-style
Ollama endpoint so system and user messages remain separated. If the runtime
client uses the generate endpoint, document how prompt boundaries are preserved.
Add unit tests with mocked HTTP responses for success, timeout, unavailable
model, non-2xx status, malformed response, and empty content.

### Acceptance criteria

- Ollama requests are built from externalized configuration and per-call prompt
  inputs.
- The adapter returns only raw model text and safe metadata to orchestration.
- HTTP failures and malformed provider responses map to typed provider errors.
- Timeouts are bounded and test-covered.
- Unit tests use mocked HTTP or fake transport and do not require live Ollama.
- The adapter package is the only code that knows Ollama request and response
  wire shapes.

### Restrictions

- Do not parse, repair, or validate intent JSON inside the Ollama adapter.
- Do not retry indefinitely or hide provider failures as successful empty JSON.
- Do not log raw prompts, raw user text, or raw model output by default.

## Subtask 3: Wire prompt loading, validation, retry, and natural-language orchestration

### Context

ADR-012 already defines prompt versioning, JSON validation, deterministic
defaults, one allowed repair retry, and hard boundaries before recommendation
execution. ADR-037 adds the concrete Ollama-backed client that feeds raw model
output into that pipeline.

### Prompt

Connect the natural-language setlist request path to the internal LLM client,
ADR-012 prompt registry, schema validator, defaulting service, retry policy, and
safe outcome mapping. Ensure recommendation execution is invoked only after a
validated `GENERATE_SETLIST` result.

### Acceptance criteria

- The orchestration path loads the configured intent prompt version and schema
  version.
- First-pass model output is parsed and validated through the ADR-012 pipeline.
- Malformed or schema-invalid output triggers at most one strict repair retry
  when eligible.
- `CLARIFY_REQUEST`, `UNSUPPORTED_REQUEST`, provider failures, retry exhaustion,
  and boundary violations return safe structured outcomes.
- Tests prove invalid model outputs, provider errors, and boundary violations do
  not invoke the Recommendation Engine.
- Existing deterministic setlist flows remain usable when LLM integration is
  disabled.

### Restrictions

- Do not perform heuristic extraction from invalid prose.
- Do not pass arbitrary client-submitted LLM output directly to recommendation.
- Do not treat model-emitted song titles, BPM, key, approval, CCLI, source, or
  provenance claims as trusted facts.

## Subtask 4: Add deterministic fake client, fixtures, and regression tests

### Context

Required automated tests must be deterministic and independent of live model
availability. The fake client should exercise orchestration behavior with
controlled raw outputs and failures.

### Prompt

Add a test fake or stub implementation of the internal LLM client and fixture
sets for valid JSON, clarify output, unsupported output, malformed JSON,
selected-song boundary violations, unknown fields, provider timeout, provider
unavailable, retry success, and retry failure.

### Acceptance criteria

- Unit and integration tests can inject fake LLM responses without a running
  Ollama daemon.
- Fixture names document the user scenario and expected backend outcome.
- Positive fixtures cover scripture, themes, counts, key policy, tempo policy,
  language, energy arc, exclusions, and service moment where supported by
  ADR-012.
- Adversarial fixtures with selected songs or catalog facts fail validation.
- Retry tests verify exactly one eligible repair attempt.
- Tests assert zero recommendation invocations for invalid, unsafe, unsupported,
  clarify, and provider-error paths.

### Restrictions

- Do not add required tests that pull models, start containers, require GPU, or
  access the internet.
- Do not use broad snapshots that obscure schema-level assertions.
- Do not add fixtures that imply the LLM can recommend songs.

## Subtask 5: Add observability, redaction, and provider health checks

### Context

Operators need visibility into LLM availability and safety outcomes without
capturing sensitive worship-planning text or raw model content by default.

### Prompt

Instrument the LLM integration with structured logs, metrics, tracing tags, and
health indicators for enabled/disabled state, provider availability, latency,
timeouts, retries, validation outcomes, and boundary violations. Ensure emitted
fields are redacted and low-cardinality.

### Acceptance criteria

- Metrics distinguish provider failure, parse failure, schema failure,
  boundary-violation failure, retry success, retry exhaustion, clarify,
  unsupported, and validated intent outcomes.
- Logs include correlation identifiers and outcome codes without raw prompt,
  user text, or model output by default.
- Health checks distinguish disabled provider, configured provider, reachable
  provider, and unavailable model where safely detectable.
- Alert/runbook hooks are documented for elevated timeout, validation-failure,
  and boundary-violation rates.
- Observability tests verify redaction of sensitive fields.

### Restrictions

- Do not use user request text or raw model output as metric labels.
- Do not mark the whole application unhealthy solely because optional LLM support
  is disabled by configuration.
- Do not expose model diagnostics to unauthorized users.

## Subtask 6: Document local setup, live smoke tests, operations, and rollback

### Context

Ollama introduces operator responsibilities for installation, model selection,
capacity, endpoint security, and disablement. These responsibilities must be
explicit before rollout.

### Prompt

Create operations documentation for enabling the Ollama integration locally and
in an isolated church instance. Include model pull guidance, configuration
examples, optional smoke-test commands, health checks, troubleshooting, capacity
notes, security expectations, and rollback instructions for disabling LLM-backed
natural-language handling without disabling deterministic recommendation.

### Acceptance criteria

- Documentation explains how to install or connect to Ollama and configure the
  Cadentia API.
- Documentation includes an opt-in live smoke test and states that required CI
  tests do not need Ollama.
- Documentation explains how to disable the LLM provider and expected API
  behavior while disabled.
- Troubleshooting covers missing model, unreachable endpoint, timeout, malformed
  output, validation failure, and elevated boundary violations.
- Security notes warn against exposing Ollama directly to browsers or untrusted
  networks.
- Rollback steps preserve deterministic setlist generation and catalog safety.

### Restrictions

- Do not include real secrets, production hostnames, tenant IDs, or private
  church-instance configuration.
- Do not require OpenRouter or another hosted provider for the v1 path.
- Do not document manual bypasses around ADR-012 validation.
