# ADR-012: LLM Intent Extraction Contract

Status: Proposed  
Date: 2026-05-17

## Context

Cadentia's architecture depends on a strict boundary: the LLM interprets user intent only. It must not select songs, create catalog records, invent metadata, bypass approval gates, or make recommendation decisions.

User requests may include scripture references, pasted scripture text, service themes, musical constraints, language preferences, set sizes, energy goals, and exclusions. These inputs must be converted into validated JSON slots consumed by backend services.

This ADR formalizes:

- JSON schemas
- allowed slots
- prompt contracts
- validation
- fallback behavior
- hallucination prevention
- deterministic boundaries

## Decision

Define an LLM intent extraction contract that produces schema-validated JSON and nothing else for setlist generation requests. The backend must reject malformed or unsupported outputs and must apply defaults deterministically.

The LLM output for setlist generation must follow this shape:

```json
{
  "intent": "GENERATE_SETLIST",
  "slots": {
    "verseText": "",
    "scriptureReferences": [],
    "themeHints": [],
    "counts": {
      "praise": 10,
      "worship": 5
    },
    "keyPolicy": {
      "preferSameKey": true,
      "allowRelativeMajorMinor": true,
      "maxKeyCenters": 2
    },
    "tempoPolicy": {
      "maxJumpBpm": 12
    },
    "language": null,
    "energyArc": null,
    "excludedSongs": [],
    "serviceMoment": null
  }
}
```

The contract may evolve through versioned schemas, but the LLM must remain limited to intent and slot extraction.

## Allowed Intents

Initial allowed intents:

- `GENERATE_SETLIST`
- `CLARIFY_REQUEST`
- `UNSUPPORTED_REQUEST`

Future intents may include catalog search or admin-assist workflows, but each must have a separate schema and must preserve the rule that the LLM cannot approve or recommend songs.

## Allowed Slots

Allowed `GENERATE_SETLIST` slots include:

- `verseText`: pasted scripture or user-provided passage text
- `scriptureReferences`: normalized scripture references when explicitly provided or confidently extracted
- `themeHints`: user-provided themes or inferred high-level themes from scripture text
- `counts`: requested praise and worship counts
- `keyPolicy`: key center preferences and relative major/minor allowance
- `tempoPolicy`: maximum adjacent BPM jump
- `language`: requested language when provided
- `energyArc`: requested energy arc when provided
- `excludedSongs`: user-provided exclusions by title or catalog reference
- `serviceMoment`: opening, communion, response, altar call, sending, or other controlled service moment when provided

The LLM must not output song selections, arrangement identifiers, approval decisions, provenance records, or database write instructions.

## JSON Schema and Validation

Backend validation is mandatory. Validation must check:

- valid JSON syntax
- known intent value
- schema version compatibility
- required fields and defaults
- type constraints
- numeric bounds for counts and BPM jumps
- enum values for controlled slots
- maximum string lengths
- unsupported field rejection

Invalid output must not be repaired by trusting prose. The backend may either request a retry from the LLM with the same contract or return a clarification prompt to the user.

## Prompt Contract

The system prompt for intent extraction must state:

- return JSON only
- do not select songs
- do not invent songs
- do not infer catalog availability
- do not produce prose, Markdown, or explanations
- use null or empty arrays when a slot is unknown
- preserve user-provided scripture text without embellishment
- map only supported constraints into allowed slots
- return `CLARIFY_REQUEST` when required information is missing
- return `UNSUPPORTED_REQUEST` when the user asks for actions outside the contract

Prompt changes must be versioned and tested against fixtures.

## Fallback Behavior

Fallback behavior must be deterministic:

- if JSON is malformed, reject and retry once with a strict repair prompt
- if schema validation fails after retry, return a clarification or safe error
- if optional slots are missing, backend defaults apply
- if counts are missing, default to 10 praise and 5 worship
- if key policy is missing, default to same-key preference, relative major/minor allowed, and maximum two key centers
- if tempo policy is missing, default to maximum 12 BPM adjacent jump
- if the user requests a named song, treat it only as a user preference or exclusion when schemas support that field; do not treat it as a recommendation

Fallback must never cause the LLM to select songs.

## Hallucination Prevention

The contract prevents hallucination by restricting the LLM to slots that can be validated independently. The LLM must not:

- output song titles as selected recommendations
- claim a song exists in the catalog
- claim a source, license, approval, or provenance record exists
- create scripture quotations not provided by the user
- create CCLI numbers, authors, BPM values, keys, or tags as catalog facts
- override backend eligibility decisions

The backend must ignore or reject unsupported fields even if the LLM emits them.

## Deterministic Boundaries

The deterministic boundary is between intent extraction and recommendation execution.

- The LLM extracts intent and constraints.
- The backend validates slots and applies defaults.
- The Catalog Service retrieves approved candidates.
- The Recommendation Engine filters, scores, orders, and explains results.

Only backend services may generate recommendation outputs. LLMs may later assist with natural-language rendering of already-computed explanations only if they are constrained to the provided facts and cannot add new claims.

## Schema Evolution Governance

Schema evolution must follow explicit compatibility rules tied to semantic versioning.

- Patch upgrades (`x.y.z -> x.y.z+1`) are limited to non-structural clarifications and validation bug fixes.
- Minor upgrades (`x.y -> x.y+1`) are additive only, such as new optional slots or enum values with deterministic backend fallback behavior.
- Major upgrades (`x -> x+1`) are required for removals, renames, type changes, or semantic reinterpretation of existing fields.

The backend must maintain a supported-version window and reject unknown schema names or unsupported versions with machine-readable `UNSUPPORTED_SCHEMA` class codes.

Any migration between major versions must include a deterministic adapter and compatibility fixtures proving canonical equivalence where expected.

## Validation Error Taxonomy and Outcome Classes

Validation failures must use a governed, stable error-code taxonomy rather than ad hoc text.

Minimum required categories:

- `PARSE_ERROR`: malformed JSON or non-JSON output
- `SCHEMA_ERROR`: required field/type/range/enum violations and unknown fields
- `INTENT_ERROR`: unsupported intent for the endpoint contract
- `POLICY_ERROR`: prohibited slot combinations or policy-level constraints
- `BOUNDARY_VIOLATION`: LLM output crossing boundary (for example selecting songs)

Each error code must map deterministically to one API outcome class:

- `RETRYABLE`: transiently recoverable failures (for this ADR, malformed output on first pass)
- `CLARIFY`: valid contract shape but insufficient user constraints
- `UNSUPPORTED`: out-of-contract request, intent, or schema version
- `HARD_FAIL`: unsafe or repeated invalid output that must not proceed

Error responses must be machine-readable and include at least:

- stable `errorCode`
- `outcomeClass`
- `retryEligible`
- `schemaVersionEvaluated`
- `details[]` with field paths and reason codes
- correlation identifier for audit traceability

## Enforcement Boundary

Recommendation execution is forbidden unless validation succeeds and the resulting intent is `GENERATE_SETLIST`.

- Invalid, clarify, and unsupported outcomes must result in zero Recommendation Engine invocations.
- Boundary-violation errors must be treated as hard-fail outcomes.
- Validation outcomes must be audit-logged with stable codes.

## Consequences

Benefits:

- clear separation of probabilistic parsing and deterministic recommendation
- reduced risk of hallucinated songs or metadata
- schema validation enables safe retries and tests
- future intent types can be added without weakening core boundaries

Tradeoffs:

- user requests outside the schema require clarification or future schema work
- prompt and schema versions must be maintained together
- natural-language flexibility is intentionally constrained at the decision boundary

## Related Decisions

- ADR-001 defines catalog authority.
- ADR-002 defines candidate retrieval.
- ADR-010 defines deterministic recommendation scoring.
- ADR-013 defines explanation output after the engine has selected results.
