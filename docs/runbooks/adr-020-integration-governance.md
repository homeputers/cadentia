# ADR-020 Duplicate Integration Governance

ADR-020 is a rejected duplicate decision record. Integration implementation work
must use canonical ADR authority:

- **ADR-008** for connector lifecycle, provenance, retries, and idempotency.
- **ADR-003** for staging and dedup boundaries.
- **ADR-011** for governance/review promotion gates.
- **ADR-004** for import-format compatibility and lyrics storage guarantees.

## Acceptable ADR-020 mention patterns

Use ADR-020 only as historical context when the same statement includes a
canonical ADR pointer.

- ✅ "Originally proposed in ADR-020; implemented per ADR-008 and ADR-003."
- ✅ "ADR-020 was superseded by ADR-008/003/011/004 for integration work."
- ❌ intentionally incorrect example: "Implement this connector requirement from ADR-020."
- ❌ intentionally incorrect example: "ADR-020 is the primary source for integration behavior."

## Local and CI validation

The repository enforces duplicate-governance checks by scanning markdown docs
for active-authority references to rejected ADRs.

Run locally:

```bash
npm run check:adr-governance
```

CI runs the same command in the **ADR Governance** workflow job when docs or
scripts change.

## PR checklist guidance for integration changes

When a change affects connectors, staging/dedup, governance review gates, or
format compatibility:

1. Include at least one canonical ADR reference (ADR-008/003/011/004).
2. If ADR-020 appears, keep the mention historical and non-normative.
3. Run `npm run check:adr-governance` before merge.
