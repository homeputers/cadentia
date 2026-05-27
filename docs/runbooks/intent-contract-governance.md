# Runbook: Intent Contract Governance Drift Detection

## Purpose

Detect and remediate drift where implementation work starts treating rejected duplicate ADR-014 as an active contract instead of ADR-012.

## Signals to monitor

- CI failure in `ADR Governance` step from `.github/workflows/build.yml`.
- Pull request checklist misses in `.github/pull_request_template.md`.
- Documentation/planning changes that introduce ADR-014 references without duplicate/rejected context.

## Automated check

Run:

```bash
npm run check:adr-governance
```

The check scans `docs/**/*.md` and fails if rejected duplicate ADR-014 appears with active implementation semantics (for example: source-of-truth, normative, schema, validation, retry, fallback) outside ADR-014 archival records.

## Failure case demonstration

1. Edit any non-ADR-014 markdown file and add text such as:
   - `ADR-014 is the normative schema reference for intent validation.` (intentionally incorrect example)
2. Run `npm run check:adr-governance`.
3. Expected: command exits non-zero and reports file:line violations.

## Triage workflow

1. Identify violation line(s) from command output.
2. Decide whether the reference should:
   - point to ADR-012 for active behavior, or
   - be retained only as historical duplicate-governance context.
3. Update language accordingly.
4. Re-run `npm run check:adr-governance` and ensure pass.

## Remediation policy

- Active intent-contract behavior references must cite ADR-012.
- ADR-014 may be cited only for lifecycle history (duplicate/rejected/superseded status).
- If new requirements are needed, amend ADR-012 or introduce a formally superseding ADR.
