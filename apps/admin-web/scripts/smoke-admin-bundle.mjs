import { existsSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { join, resolve } from 'node:path';

const requiredFiles = ['index.html', 'admin-build.json', 'admin-health.json'];

const readJson = (file) => {
    try {
        return JSON.parse(readFileSync(file, 'utf8'));
    } catch (error) {
        throw new Error(`Invalid JSON in ${file}: ${error instanceof Error ? error.message : String(error)}`);
    }
};

const requireString = (metadata, key) => {
    if (typeof metadata[key] !== 'string' || metadata[key].trim() === '') {
        throw new Error(`Admin bundle metadata is missing ${key}.`);
    }
};

export const smokeAdminBundle = ({ distDir = 'dist', strictPromotion = process.env.CADENTIA_ADMIN_STRICT_SMOKE === 'true', log = true } = {}) => {
    const resolvedDistDir = resolve(distDir);
    const missingFiles = requiredFiles
        .map((file) => join(resolvedDistDir, file))
        .filter((file) => !existsSync(file));
    if (missingFiles.length > 0) {
        throw new Error(`Missing admin artifact files: ${missingFiles.join(', ')}`);
    }

    const indexHtml = readFileSync(join(resolvedDistDir, 'index.html'), 'utf8');
    if (!indexHtml.includes('id="root"')) {
        throw new Error('Admin bundle index.html is missing the SPA root mount node.');
    }

    const metadata = readJson(join(resolvedDistDir, 'admin-build.json'));
    requireString(metadata, 'name');
    requireString(metadata, 'version');
    requireString(metadata, 'commit');
    requireString(metadata, 'builtAt');
    if (metadata.name !== '@cadentia/admin-web') {
        throw new Error(`Unexpected admin bundle name: ${metadata.name}`);
    }
    if (metadata.version === '0.0.0' || metadata.version === '0.0.0-local') {
        throw new Error('Admin bundle metadata is missing a usable version.');
    }
    if (Number.isNaN(Date.parse(metadata.builtAt))) {
        throw new Error('Admin bundle metadata builtAt must be an ISO-compatible timestamp.');
    }
    if (typeof metadata.apiBaseUrlConfigured !== 'boolean') {
        throw new Error('Admin bundle metadata does not expose API base URL configuration status.');
    }
    if (typeof metadata.diagnosticsEnabled !== 'boolean') {
        throw new Error('Admin bundle metadata does not expose diagnostics configuration status.');
    }

    const health = readJson(join(resolvedDistDir, 'admin-health.json'));
    if (health.status !== 'ok' || health.artifact !== 'admin-web-static') {
        throw new Error('Admin health metadata does not identify a healthy admin-web static artifact.');
    }

    if (strictPromotion) {
        if (!metadata.apiBaseUrlConfigured) {
            throw new Error('Strict admin smoke requires API base URL configuration.');
        }
        if (metadata.commit === 'local' || metadata.builtAt === 'local') {
            throw new Error('Strict admin smoke requires CI-provided commit and build timestamp metadata.');
        }
    }

    if (log) console.log(`Admin bundle smoke check passed for ${metadata.name}@${metadata.version}.`);
    return { metadata, health };
};

if (process.argv[1] === fileURLToPath(import.meta.url)) {
    try {
        smokeAdminBundle({
            distDir: process.env.CADENTIA_ADMIN_SMOKE_DIST_DIR ?? 'dist',
            strictPromotion: process.env.CADENTIA_ADMIN_STRICT_SMOKE === 'true',
        });
    } catch (error) {
        console.error(error instanceof Error ? error.message : String(error));
        process.exit(1);
    }
}
