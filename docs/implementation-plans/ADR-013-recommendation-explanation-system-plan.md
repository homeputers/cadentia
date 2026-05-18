# ADR-013 Implementation Plan: Recommendation Explanation System

## Objective

Emit structured, deterministic explanation facts from recommendation inputs,
filters, scores, transitions, provenance, and approval references. Explanations
must be grounded in backend facts and may not depend on LLM invention.

## Subtask 1: Define explanation fact contracts and code registry

### Context

ADR-013 requires structured explanation facts with stable codes, severity, scope,
subject, template, values, evidence, and optional score impact.

### Prompt

Create explanation fact models and a governed registry of explanation codes for
item, transition, set, warning, and candidate-exclusion scopes.

### Acceptance criteria

- Explanation facts include code, severity, scope, subject, template key or safe
  default text, structured values, evidence references, and optional score impact.
- Code registry includes initial codes for theme match, role fit, approval
  eligible, same-key transition, relative-key transition, tempo policy, energy
  arc, count target, key-center policy, and exclusion reasons.
- Tests verify unknown codes cannot be emitted accidentally.

### Restrictions

- Do not make free-form strings the source of truth.
- Do not include facts that cannot be traced to catalog, request, scoring,
  transition, approval, or provenance evidence.
- Do not allow client code to invent new backend fact codes.

## Subtask 2: Emit item-level explanation facts from scoring components

### Context

Selected songs need explanations for theme/scripture match, role fit, musical
fit, approval/provenance eligibility, freshness, and tradeoffs.

### Prompt

Map ADR-010 candidate scoring components and hard-filter eligibility data into
item-level explanation facts for every selected setlist item.

### Acceptance criteria

- Every selected item has at least one role-fit and one eligibility explanation.
- Theme/scripture explanations cite controlled tags or scripture references when
  those inputs contributed to score.
- Low-confidence metadata produces warning facts rather than unsupported claims.
- Tests verify facts and score impacts for representative scoring components.

### Restrictions

- Do not claim a theme or scripture match unless scoring evidence contains it.
- Do not expose full lyrics as evidence.
- Do not let an LLM generate item reasons.

## Subtask 3: Emit transition explanation facts

### Context

Adjacent song transitions should explain key movement, BPM policy, meter,
energy, and parser-derived arrangement start/end compatibility.

### Prompt

Map transition score components into transition-scoped explanation facts for
each adjacent pair in the ordered setlist.

### Acceptance criteria

- Same-key, relative-key, closely related key, modulation penalty, tempo policy,
  meter compatibility, and energy movement can be represented as structured
  facts.
- Transition facts identify both source and target item subjects.
- Parser-derived facts include parser result references and confidence warnings.
- Tests cover positive, warning, and penalty transition facts.

### Restrictions

- Do not invent arrangement start/end facts when parser data is unavailable.
- Do not hide transition policy violations that were accepted as tradeoffs.
- Do not use prose-only explanations for transition evidence.

## Subtask 4: Emit set-level explanation facts and warnings

### Context

Users need a summary of how the full recommendation satisfies counts, key
centers, energy arc, theme coverage, language, service moments, and catalog
limitations.

### Prompt

Generate set-scoped explanation facts from request defaults, selected items,
score summaries, and diagnostics. Include warnings for compromises and catalog
limitations.

### Acceptance criteria

- Set-level facts cover count target, key-center policy, energy arc, and theme
  coverage where applicable.
- Applied defaults are included in the request summary.
- Warnings identify compromises such as insufficient candidates, low-confidence
  metadata, or accepted tempo tradeoffs.
- Tests cover fully satisfied, partially satisfied, and insufficient-catalog
  scenarios.

### Restrictions

- Do not claim all constraints were satisfied when the engine accepted a tradeoff.
- Do not reveal admin-only candidate details in normal user responses.
- Do not summarize unsupported user requests as fulfilled.

## Subtask 5: Add admin exclusion and near-miss explanations

### Context

Admin and debugging views should explain why high-potential candidates were not
selected, while normal user clients may hide these details.

### Prompt

Use hard-filter reasons, score ranking, quota decisions, and transition conflicts
to emit admin-scoped candidate-exclusion and near-miss explanation facts.

### Acceptance criteria

- Exclusion facts cover failed approval gate, missing provenance, licensing
  concern, inactive arrangement, duplicate state, key-center limit, tempo policy,
  weaker score, and filled quota.
- Admin responses can request exclusion facts without changing selected results.
- Normal user responses omit admin-only details by default.
- Tests verify selected set stability with and without admin explanation detail.

### Restrictions

- Do not expose sensitive provenance payloads or copyrighted lyrics in exclusion
  facts.
- Do not leak admin-only review notes to public clients.
- Do not recompute selection differently for explanation mode.

## Subtask 6: Implement UI-safe rendering support

### Context

ADR-013 allows UI clients, and possibly future constrained LLM renderers, to turn
structured facts into natural language without inventing new claims.

### Prompt

Create template keys and simple deterministic rendering helpers for explanation
facts. Provide API examples showing structured facts as the source of truth and
rendered text as optional convenience.

### Acceptance criteria

- Rendering templates use only values present in explanation facts.
- Missing values render safe fallback text or validation errors.
- API examples include item, transition, set, warning, and admin exclusion facts.
- Tests verify rendered text does not require external services.

### Restrictions

- Do not use an LLM for Phase 2 rendering.
- Do not let templates call external systems or fetch catalog data implicitly.
- Do not make rendered text authoritative over structured facts.

## Subtask 7: Add explanation audit and regression coverage

### Context

Explanations are part of trust and auditability, so regression tests must ensure
facts stay grounded as scoring evolves.

### Prompt

Add tests that compare recommendation scoring inputs, selected output, and
explanation facts to ensure every fact is supported by evidence and every major
score component can be explained.

### Acceptance criteria

- Regression tests fail when explanation facts reference missing evidence.
- Major scoring components have corresponding explanation coverage or an explicit
  documented reason they are internal-only.
- Explanation generation is deterministic for identical scoring outputs.
- Tests verify no selected song appears only because of an explanation fact.

### Restrictions

- Do not snapshot large full responses when targeted assertions are clearer.
- Do not generate explanation facts before scoring and eligibility have completed.
- Do not allow explanation tests to depend on network or LLM calls.
