import { readdir, readFile } from 'node:fs/promises';
import path from 'node:path';

const ROOT = process.cwd();
const DOCS_DIR = path.join(ROOT, 'docs');

const ALLOWED_ADR_014_PATHS = new Set([
  'docs/adr/ADR-014-llm-intent-extraction-contract.md',
  'docs/implementation-plans/ADR-014-llm-intent-extraction-contract-plan.md'
]);

const FLAG_TERMS = [
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

function hasContextFlag(line) {
  const normalized = line.toLowerCase();
  return FLAG_TERMS.some((term) => normalized.includes(term));
}

const files = await walk(DOCS_DIR);
const violations = [];

for (const file of files) {
  const relativePath = path.relative(ROOT, file).replaceAll(path.sep, '/');
  if (ALLOWED_ADR_014_PATHS.has(relativePath)) {
    continue;
  }

  const content = await readFile(file, 'utf8');
  const lines = content.split(/\r?\n/u);
  let inCodeBlock = false;
  for (let index = 0; index < lines.length; index += 1) {
    const line = lines[index];
    if (line.trimStart().startsWith('```')) {
      inCodeBlock = !inCodeBlock;
      continue;
    }
    if (inCodeBlock || line.includes('intentionally incorrect example') || !line.includes('ADR-014')) {
      continue;
    }

    const context = lines.slice(Math.max(0, index - 1), Math.min(lines.length, index + 2)).join(' ').toLowerCase();
    const mentionsRejectedContext = context.includes('duplicate') || context.includes('rejected') || context.includes('superseded') || context.includes('non-active');

    if (hasContextFlag(line) && !mentionsRejectedContext) {
      violations.push(`${relativePath}:${index + 1}`);
    }
  }
}

if (violations.length > 0) {
  console.error('ADR governance check failed. ADR-014 cannot be referenced as an active implementation contract.');
  console.error('Violations:');
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log('ADR governance check passed. ADR-014 references are non-normative outside ADR-014 records.');
