import { readdir, readFile } from 'node:fs/promises';
import path from 'node:path';

const ROOT = process.cwd();
const DOCS_DIR = path.join(ROOT, 'docs');

const RULES = [
  {
    adr: 'ADR-014',
    allowedPaths: new Set([
      'docs/adr/ADR-014-llm-intent-extraction-contract.md',
      'docs/implementation-plans/ADR-014-llm-intent-extraction-contract-plan.md'
    ]),
    canonicalReferences: ['ADR-012'],
    rejectionTerms: ['duplicate', 'rejected', 'superseded', 'non-active'],
    blockedTerms: [
      'source of truth',
      'authoritative',
      'normative',
      'implement',
      'implementation',
      'schema',
      'validation',
      'retry',
      'fallback',
      'active'
    ]
  },
  {
    adr: 'ADR-020',
    allowedPaths: new Set([
      'docs/adr/ADR-020-external-integration-boundaries.md',
      'docs/implementation-plans/ADR-020-external-integration-boundaries-plan.md'
    ]),
    canonicalReferences: ['ADR-008', 'ADR-003', 'ADR-011', 'ADR-004'],
    rejectionTerms: ['duplicate', 'rejected', 'superseded', 'historical'],
    blockedTerms: [
      'source of truth',
      'authoritative',
      'normative',
      'implement',
      'implementation',
      'active',
      'primary',
      'canonical'
    ]
  }
];

async function walk(dir) {
  const entries = await readdir(dir, { withFileTypes: true });
  const results = [];
  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      results.push(...(await walk(fullPath)));
    } else if (entry.isFile() && entry.name.endsWith('.md')) {
      results.push(fullPath);
    }
  }
  return results;
}

function hasTerm(text, terms) {
  const normalized = text.toLowerCase();
  return terms.some((term) => normalized.includes(term));
}

const files = await walk(DOCS_DIR);
const violations = [];

for (const file of files) {
  const relativePath = path.relative(ROOT, file).replaceAll(path.sep, '/');
  const content = await readFile(file, 'utf8');
  const lines = content.split(/\r?\n/u);

  let inCodeBlock = false;
  for (let index = 0; index < lines.length; index += 1) {
    const line = lines[index];
    if (line.trimStart().startsWith('```')) {
      inCodeBlock = !inCodeBlock;
      continue;
    }

    if (inCodeBlock || line.includes('intentionally incorrect example')) {
      continue;
    }

    for (const rule of RULES) {
      if (rule.allowedPaths.has(relativePath) || !line.includes(rule.adr)) {
        continue;
      }

      const context = lines
        .slice(Math.max(0, index - 1), Math.min(lines.length, index + 2))
        .join(' ')
        .toLowerCase();

      const hasRejectedContext = hasTerm(context, rule.rejectionTerms);
      const hasCanonicalReference = hasTerm(context, rule.canonicalReferences.map((reference) => reference.toLowerCase()));
      const hasBlockedTerm = hasTerm(line, rule.blockedTerms);

      if (hasBlockedTerm && !hasRejectedContext && !hasCanonicalReference) {
        violations.push(`${relativePath}:${index + 1} (${rule.adr})`);
      }
    }
  }
}

if (violations.length > 0) {
  console.error('ADR governance check failed. Rejected duplicate ADRs cannot be referenced as active implementation authority.');
  console.error('Violations:');
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log('ADR governance check passed. Rejected duplicate ADR references are non-normative outside their archival records.');
