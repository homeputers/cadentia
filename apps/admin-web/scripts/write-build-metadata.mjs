import { mkdirSync, writeFileSync } from 'node:fs';

const metadata = {
    name: '@cadentia/admin-web',
    version: process.env.VITE_CADENTIA_ADMIN_BUILD_VERSION ?? process.env.npm_package_version ?? '0.0.0-local',
    commit: process.env.VITE_CADENTIA_ADMIN_BUILD_COMMIT ?? process.env.GIT_COMMIT ?? 'local',
    builtAt: process.env.VITE_CADENTIA_ADMIN_BUILD_TIMESTAMP ?? new Date().toISOString(),
    apiBaseUrlConfigured: Boolean(process.env.VITE_CADENTIA_API_BASE_URL),
    diagnosticsEnabled: process.env.VITE_CADENTIA_DIAGNOSTICS_ENABLED === 'true',
};

mkdirSync('public', { recursive: true });
writeFileSync('public/admin-build.json', `${JSON.stringify(metadata, null, 2)}\n`);
writeFileSync('public/admin-health.json', `${JSON.stringify({ status: 'ok', artifact: 'admin-web-static' }, null, 2)}\n`);
