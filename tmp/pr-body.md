## Summary

Implements local Ollama LLM backend integration for natural-language setlist intent parsing, completing the ADR-037 implementation plan.

## What's Changed

### Core LLM Boundary
- **Typed LlmClient interface**: Replaces string-only `complete(String)` with structured `complete(LlmRequest) -> LlmResponse`
- **LlmRequest/LlmResponse**: Carry prompt metadata, user text, correlation IDs, generation options, and safe provider metadata
- **LlmProperties**: Externalized configuration (`cadentia.llm.*`) for provider, base URL, model, timeout, temperature, top-p, max tokens
- **LlmProviderException**: Typed error taxonomy (TIMEOUT, CONNECTION_FAILURE, NON_SUCCESS_STATUS, UNSUPPORTED_RESPONSE_SHAPE)

### Provider Adapters
- **OllamaLlmClient**: Posts to Ollama chat API (`/api/chat`) with connection/read timeouts, non-2xx handling, empty body checks, error response detection
- **DisabledLlmClient**: Default when `cadentia.llm.enabled=false`; throws DISABLED error for graceful degradation
- Removed OpenRouterClient placeholder

### Intent Orchestration Hardening
- **DefaultIntentService**: Wires to typed boundary with UUID correlation IDs; catches LlmProviderException and maps to safe failure outcome
- **stripMarkdownFences()**: Removes ```json / ``` wrappers that local models frequently emit despite instructions
- **JSON null handling**: `IntentValidationService` now treats JSON `null` for int/bool fields as "use default" instead of INVALID_TYPE

### Prompt Improvements
- Added concrete JSON examples for all three intent types (GENERATE_SETLIST, CLARIFY_REQUEST, UNSUPPORTED_REQUEST) to help smaller local models (llama3.1:8b) follow schema

### Configuration
- `application.yml` additions:
  - `cadentia.llm.enabled` (default: false)
  - `cadentia.llm.provider` (default: ollama)
  - `cadentia.llm.ollama.base-url` (default: http://localhost:11434)
  - `cadentia.llm.ollama.model` (default: llama3.1:8b)
  - `cadentia.llm.ollama.timeout` (default: PT20S)
  - `cadentia.llm.ollama.temperature` (default: 0.0)
  - `cadentia.llm.ollama.top-p` (default: 0.9)
  - `cadentia.llm.ollama.max-output-tokens` (default: 800)

### Testing
- **OllamaLlmClientTest**: 6 parameterized tests for markdown fence stripping (standard, inline, no fences, edge cases)
- Updated **DefaultIntentServiceTest** for provider-error and fake-client scenarios
- Updated **IntentOrchestrationFailureIntegrationTest** for typed boundary

### Safety Guardrails (unchanged, preserved)
- LLM never selects songs — only extracts constraints into slots
- Backend validation and deterministic defaults remain authoritative
- One strict repair retry with schema enforcement
- Safe failure mapping for both validation and provider failures

## Verification

Tested locally with Ollama (llama3.1:8b):
- "ephesians 6:11" → accepted GENERATE_SETLIST with scriptureReferences=["Ephesians 6:11"]
- Provider disabled → safe failure with LLM_PROVIDER_UNAVAILABLE

## Related
- ADR-012 (intent extraction contract)
- ADR-037 (Ollama integration plan) — updated to reflect implemented gaps

## Rollout Notes

To enable LLM integration:
1. Install Ollama locally
2. Pull a model: `ollama pull llama3.1:8b`
3. Set `CADENTIA_LLM_ENABLED=true`
4. Optionally override `CADENTIA_LLM_OLLAMA_MODEL`

## Remaining Gaps (future PRs)
- Provider observability metrics/logs (low-cardinality, no raw text exposure)
- Health checks for Ollama connectivity
- Operator runbook for local setup, smoke tests, and troubleshooting