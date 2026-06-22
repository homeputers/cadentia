import { existsSync } from 'node:fs';

const openApiEntrypoint = '../../apps/api/src/main/openapi/cadentia-api.yaml';
if (!existsSync(openApiEntrypoint)) {
    console.error(`OpenAPI entrypoint not found: ${openApiEntrypoint}`);
    process.exit(1);
}

console.log('Generated client workflow placeholder verified. Subtask 2 will wire the OpenAPI generator output into src/generated/cadentia-api/.');
