# ADR-037: Ollama LLM Integration

Status: Proposed  
Date: 2026-06-24

## Context

ADR-012 defines Cadentia's LLM boundary for intent extraction: the LLM may parse
free-text worship-planning requests into validated JSON slots, but it must never
select songs, invent catalog facts, approve content, or bypass deterministic
recommendation logic.

Cadentia now needs a concrete LLM API integration so natural-language requests
can invoke that intent contract in development and deployable church instances.
The integration must preserve the existing safety boundary while keeping local
operation practical for isolated deployments.

## Problem

The intent contract is documented, but the runtime integration is not yet
specified. Without a concrete provider interface and operational model:

- application code may couple directly to provider-specific request and response
  shapes;
- retry, timeout, validation, and audit behavior may drift across call sites;
- tests may accidentally depend on a live model or network access;
- deployment operators may lack a documented way to configure model endpoints;
- future provider swaps could weaken the ADR-012 boundary; and
- natural-language endpoints could be implemented before the LLM client is
  safely constrained.

## Decision

Implement the first Cadentia LLM runtime integration through **Ollama** rather
than OpenRouter or another hosted provider. Ollama will be accessed through a
small backend-owned adapter that exposes only Cadentia intent-extraction
operations to application services.

The Ollama adapter is an infrastructure concern, not a recommendation authority.
It must accept the versioned prompt and user text, call the configured Ollama
chat or generate endpoint, return raw model text to the ADR-012 validation
pipeline, and provide structured client errors for timeout, transport, model,
and malformed-provider-response failures.

The backend must keep the provider boundary behind an internal `LlmClient` style
interface so orchestration code depends on Cadentia semantics rather than Ollama
HTTP details. No controller, recommendation service, or catalog workflow may call
Ollama directly.

## Requirements

- Use Ollama as the v1 LLM API provider for intent extraction.
- Keep provider configuration externalized, including base URL, model name,
  request timeout, retry eligibility, enabled flag, and optional generation
  parameters.
- Route all model output through ADR-012 JSON parsing, schema validation,
  defaulting, retry, and safe-failure handling before any recommendation engine
  invocation.
- Keep prompts versioned and provider-neutral; provider settings must not be
  embedded into prompt artifacts.
- Provide deterministic fake or stub LLM implementations for unit, contract, and
  integration tests.
- Treat live Ollama smoke tests as optional environment-dependent checks, not as
  required unit tests.
- Redact or avoid storing raw user text and raw model output except where an
  approved observability policy explicitly permits diagnostic capture.
- Emit metrics and structured logs for provider availability, latency, timeout,
  retry, validation outcome, and boundary-violation outcomes.
- Fail closed when Ollama is disabled, unavailable, times out, returns non-text
  content, or returns content that fails validation after the allowed retry.
- Preserve the rule that the LLM never selects songs or asserts catalog facts.

## Provider Boundary

The internal LLM client contract must be narrow:

- input: prompt version, system prompt, user request text, correlation metadata,
  schema version, and model options selected by backend configuration;
- output: raw model text plus provider metadata needed for logging, metrics, and
  troubleshooting;
- errors: typed provider failures such as disabled provider, connection failure,
  timeout, non-success status, unsupported response shape, and cancelled request.

The provider adapter must not parse intent slots, apply defaults, invoke catalog
queries, or interpret model content as trustworthy. Those responsibilities remain
with the ADR-012 validation and orchestration layers.

## Ollama API Usage

The integration should prefer Ollama's chat-style API when it supports the
required deployment target because it cleanly separates system and user messages.
If the selected Ollama deployment requires generate-style requests, the adapter
must preserve equivalent prompt boundaries and document the fallback.

The adapter must configure model options from application properties rather than
code constants. Suggested configuration keys include:

- `cadentia.llm.enabled`
- `cadentia.llm.provider=ollama`
- `cadentia.llm.ollama.base-url`
- `cadentia.llm.ollama.model`
- `cadentia.llm.ollama.timeout`
- `cadentia.llm.ollama.temperature`
- `cadentia.llm.ollama.top-p`
- `cadentia.llm.ollama.max-output-tokens`

The default generation profile for intent extraction should be conservative and
repeatable. Exact values may be tuned during implementation, but the profile
should favor low randomness and concise JSON-only output.

## Validation and Retry Flow

The orchestration flow must remain:

1. Load the versioned ADR-012 intent prompt.
2. Send the prompt and user request to the configured `LlmClient`.
3. Parse the returned raw model text as JSON.
4. Validate against the supported intent schema.
5. Apply deterministic backend defaults.
6. Retry at most once using the strict repair prompt when permitted by ADR-012.
7. Return a validated internal intent, clarification outcome, unsupported
   outcome, or safe failure.
8. Invoke recommendation only for a valid `GENERATE_SETLIST` intent.

Provider failures may produce a safe service-unavailable or clarification-style
response, but they must never trigger heuristic extraction from prose or bypass
schema validation.

## Security and Operations

Ollama endpoints must be treated as internal infrastructure. Production-like
instances should avoid exposing Ollama directly to browsers or untrusted
networks. If a deployment uses a remote Ollama host, transport security,
network-level access controls, and secret handling must be documented in the
instance operations runbook.

Operational documentation must include:

- local developer setup;
- model pull and version pinning guidance;
- configuration examples;
- health and smoke-test commands;
- timeout and capacity guidance;
- provider-disable behavior;
- troubleshooting for unavailable models, slow responses, malformed output, and
  validation failures; and
- rollback steps to disable natural-language LLM handling without disabling the
  deterministic recommendation engine.

## Acceptance Criteria

- Application services depend on an internal LLM client interface, not direct
  Ollama HTTP calls.
- Ollama provider configuration is externalized and safe defaults are documented.
- The natural-language intent path can be exercised with deterministic fake LLM
  responses in tests.
- Optional live Ollama smoke tests are clearly separated from required CI tests.
- Invalid, unsafe, or non-JSON model output never reaches recommendation
  execution.
- Provider errors are surfaced as typed outcomes and observable metrics without
  leaking sensitive prompt or request content.
- Documentation explains how to run, configure, disable, and troubleshoot the
  Ollama integration.

## Consequences

Positive:

- Enables local and isolated-instance LLM intent extraction without depending on
  a hosted LLM broker.
- Keeps the provider implementation replaceable behind a narrow backend adapter.
- Preserves ADR-012 guardrails and deterministic recommendation boundaries.
- Supports offline-friendly development and optional live smoke testing.

Tradeoffs:

- Operators must install, configure, and resource Ollama and selected models.
- Model behavior may vary by selected local model and version, requiring fixture
  coverage and conservative validation.
- Local inference performance may be slower or less predictable on small hosts.
- Remote hosted-provider features such as centralized billing, account controls,
  and managed model upgrades are intentionally not part of the v1 design.

## Alternatives Considered

1. Use OpenRouter for the v1 LLM provider.
   - Rejected: the current decision favors local/isolated deployments and avoids
     adding a hosted broker dependency for the initial integration.
2. Call Ollama directly from controllers or recommendation orchestration.
   - Rejected: this would leak provider details and make safety behavior harder
     to enforce consistently.
3. Implement provider-agnostic abstractions without choosing a provider.
   - Rejected: too vague for implementation and operations; Ollama is selected
     as the concrete v1 adapter while the internal boundary remains replaceable.
4. Skip live LLM integration and use only guided menus.
   - Rejected: guided flows remain valuable, but ADR-012 requires a safe runtime
     path for free-text intent extraction.

## Related Decisions

- ADR-012 defines the canonical LLM intent extraction contract.
- ADR-015 defines guided menu and conversational request flow behavior.
- ADR-019 defines security roles and permission boundaries.
- ADR-029 defines observability and telemetry strategy.
