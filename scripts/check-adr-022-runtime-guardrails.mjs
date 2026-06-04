import { readdir, readFile } from 'node:fs/promises';
import path from 'node:path';

const ROOT = process.cwd();
const SCAN_DIRS = ['apps/api/src/main/java', 'apps/api/src/main/resources/db/migration'];
const ALLOWED_FILES = new Set([
  'scripts/check-adr-022-runtime-guardrails.mjs'
]);

async function walk(dir) {
  const entries = await readdir(dir, { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      files.push(...(await walk(full)));
    } else if (entry.isFile() && /\.(java|sql)$/u.test(entry.name)) {
      files.push(full);
    }
  }
  return files;
}

const forbiddenPatterns = [
  {
    name: 'tenant column semantics',
    pattern: /\btenant_?id\b/iu
  },
  {
    name: 'shared tenant table',
    pattern: /\btenant(s)?\b.*\b(from|join|where|filter)\b|\b(from|join|where|filter)\b.*\btenant(s)?\b/iu
  },
  {
    name: 'shared catalog recommendation read',
    pattern: /\b(shared|global|denominational)_catalog\b.*\b(recommend|candidate|eligible|eligibility)\b|\b(recommend|candidate|eligible|eligibility)\b.*\b(shared|global|denominational)_catalog\b/iu
  },
  {
    name: 'cross instance runtime read',
    pattern: /\bcross[_-]?instance\b.*\b(read|query|candidate|recommend|workflow)\b|\b(read|query|candidate|recommend|workflow)\b.*\bcross[_-]?instance\b/iu
  }
];

const violations = [];
for (const scanDir of SCAN_DIRS) {
  for (const file of await walk(path.join(ROOT, scanDir))) {
    const relative = path.relative(ROOT, file).replaceAll(path.sep, '/');
    if (ALLOWED_FILES.has(relative)) {
      continue;
    }
    const lines = (await readFile(file, 'utf8')).split(/\r?\n/u);
    lines.forEach((line, index) => {
      const normalized = line.trim();
      if (normalized.startsWith('// ADR-022-AUDIT-INSTANCE-ID-ONLY:')) {
        return;
      }
      for (const rule of forbiddenPatterns) {
        if (rule.pattern.test(line)) {
          violations.push(`${relative}:${index + 1} ${rule.name}`);
        }
      }
    });
  }
}

if (violations.length > 0) {
  console.error('ADR-022 runtime guardrail check failed. Use isolated instance configuration for audit/support identifiers; do not introduce tenant-row recommendation eligibility or cross-instance data reads in normal workflows.');
  console.error('Violations:');
  violations.forEach((violation) => console.error(`- ${violation}`));
  process.exit(1);
}

console.log('ADR-022 runtime guardrail check passed. No tenant-row recommendation filters or cross-instance normal workflow reads were found.');
