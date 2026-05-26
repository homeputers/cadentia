# ADR-014: LLM Intent Extraction Contract

Status: Proposed  
Date: 2026-05-26

## Context

Cadentia accepts natural-language requests from worship leaders through guided and conversational interfaces. The LLM is responsible for transforming user language into structured intent only. Recommendation logic, candidate eligibility, and ordering remain deterministic backend responsibilities.

To preserve safety, repeatability, and auditability, the Recommendation Engine must consume only validated structured requests. Any LLM output that includes unstructured prose, fabricated songs, or schema-invalid payloads must be blocked before it reaches deterministic recommendation components.

## Problem

Without a strict extraction contract, the system risks:

- leaking non-deterministic LLM output into deterministic scoring
- allowing invalid or partial payloads to break recommendation behavior
- accidental acceptance of LLM-suggested songs outside curated catalog controls
- inconsistent defaults across clients and channels
- weak auditability when request normalization differs run-to-run

## Decision

Adopt a strict JSON extraction contract for intent parsing.

1. The LLM must output JSON only for intent extraction responses.
2. The contract defines canonical slots and normalization rules.
3. Backend validation rejects schema-invalid output and retries extraction with bounded attempts.
4. Song title suggestions emitted by the LLM are ignored, sanitized, and recorded as policy violations for observability.
5. Recommendation Engine accepts only validated and normalized structured request objects.
6. The system stores both raw user input and normalized intent for audit and debugging.

### Canonical Extraction Shape

```json
{
  "intent": "GENERATE_SETLIST",
  "slots": {
    "theme": ["holiness", "gratitude"],
    "verse": {
      "reference": "Psalm 24",
      "text": "Who may ascend the mountain of the Lord?"
    },
    "language": "en",
    "setShape": {
      "praise": 10,
      "worship": 5
    },
    "energyArc": {
      "profile": "rise_then_settle"
    },
    "keyPolicy": {
      "preferSameKey": true,
      "allowRelativeMajorMinor": true,
      "maxKeyCenters": 2
    },
    "tempoPolicy": {
      "maxJumpBpm": 12
    },
    "exclusions": {
      "songIds": [],
      "arrangementIds": []
    },
    "preferences": {
      "artists": [],
      "familiarityBias": "balanced"
    }
  }
}
```

## Requirements

- JSON schema must define required fields, optional fields, type constraints, enum constraints, and bounds.
- Required slot domains include:
  - theme
  - verse
  - language
  - set shape
  - energy arc
  - key policy
  - tempo policy
  - exclusions
  - preferences
- LLM output must be strict JSON with no prose wrappers or markdown.
- Missing optional fields are filled through deterministic defaults.
- Missing required fields trigger extraction retry and, if unresolved, user clarification flow.
- Validation pipeline must include:
  - schema validation
  - normalization pass (case, aliases, code mapping)
  - policy sanitation (remove forbidden fields and LLM song suggestions)
- Retry policy must be bounded and observable (attempt count, reason codes, final state).
- Original user request text and parsed/normalized payload must be persisted for audit.
- Recommendation Engine must reject non-validated payload sources.

### Defaulting Rules

- `intent`: default only to `GENERATE_SETLIST` when request scope is setlist generation and confidence threshold is met.
- `setShape`: default `praise=10`, `worship=5`.
- `keyPolicy`: default `preferSameKey=true`, `allowRelativeMajorMinor=true`, `maxKeyCenters=2`.
- `tempoPolicy`: default `maxJumpBpm=12`.
- `language`: default to user/session locale when absent.
- `energyArc`: default to configured service profile (for example `rise_then_settle`).
- `exclusions` and `preferences`: default to empty structures.

## Acceptance Criteria

- Invalid LLM output never reaches Recommendation Engine.
- Any LLM-selected song titles are ignored or flagged and never treated as eligible inputs.
- Same raw input and same normalization rules produce deterministic normalized slots.
- Raw request, extracted payload, validation errors, retries, and final normalized payload are auditable.
- Recommendation requests accepted by the engine have passed schema + normalization + sanitation checks.

## Consequences

Positive:

- clear safety boundary between language understanding and deterministic recommendation
- stronger reproducibility and debugging
- consistent behavior across Telegram/menu/free-text channels

Tradeoffs:

- stricter contract increases extractor prompt and validator maintenance
- additional retry/clarification steps can increase latency for malformed input

## Alternatives Considered

1. Allow semi-structured LLM prose and parse heuristically.
   - Rejected: too fragile and non-deterministic.
2. Let Recommendation Engine interpret free text directly.
   - Rejected: would couple deterministic engine to language ambiguity.
3. Accept LLM-suggested songs as soft preferences.
   - Rejected: violates curated catalog boundary and anti-hallucination guardrails.

## Open Questions

- What confidence threshold should trigger clarification vs automatic defaults?
- Should language normalization be ISO-only (`en`, `es`) or locale-granular (`en-US`, `es-MX`)?
- Which fields should support user-visible “inferred by system” annotations during confirmation?
