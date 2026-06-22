import { existsSync, readFileSync } from 'node:fs';

const requiredFiles = ['dist/index.html', 'dist/admin-build.json', 'dist/admin-health.json'];
const missingFiles = requiredFiles.filter((file) => !existsSync(file));
if (missingFiles.length > 0) {
    console.error(`Missing admin artifact files: ${missingFiles.join(', ')}`);
    process.exit(1);
}

const metadata = JSON.parse(readFileSync('dist/admin-build.json', 'utf8'));
if (!metadata.version || metadata.version === '0.0.0') {
    console.error('Admin bundle metadata is missing a usable version.');
    process.exit(1);
}
if (typeof metadata.apiBaseUrlConfigured !== 'boolean') {
    console.error('Admin bundle metadata does not expose API base URL configuration status.');
    process.exit(1);
}

console.log(`Admin bundle smoke check passed for ${metadata.name}@${metadata.version}.`);
