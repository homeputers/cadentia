import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterEach, describe, expect, it } from 'vitest';
// @ts-expect-error The smoke script is a Node CLI module intentionally kept as .mjs.
import { smokeAdminBundle } from '../scripts/smoke-admin-bundle.mjs';

const tempDirs: string[] = [];

const makeDist = (overrides: Record<string, unknown> = {}, healthOverrides: Record<string, unknown> = {}) => {
    const distDir = mkdtempSync(join(tmpdir(), 'cadentia-admin-smoke-'));
    tempDirs.push(distDir);
    writeFileSync(join(distDir, 'index.html'), '<!doctype html><html><body><div id="root"></div></body></html>');
    writeFileSync(join(distDir, 'admin-build.json'), `${JSON.stringify({
        name: '@cadentia/admin-web',
        version: '1.2.3',
        commit: 'abc123',
        builtAt: '2026-07-24T00:00:00Z',
        apiBaseUrlConfigured: true,
        diagnosticsEnabled: false,
        ...overrides,
    })}\n`);
    writeFileSync(join(distDir, 'admin-health.json'), `${JSON.stringify({
        status: 'ok',
        artifact: 'admin-web-static',
        ...healthOverrides,
    })}\n`);
    return distDir;
};

afterEach(() => {
    while (tempDirs.length > 0) {
        rmSync(tempDirs.pop()!, { recursive: true, force: true });
    }
});

describe('admin bundle smoke check', () => {
    it('accepts a complete static admin bundle', () => {
        const result = smokeAdminBundle({ distDir: makeDist(), log: false });

        expect(result.metadata).toMatchObject({ name: '@cadentia/admin-web', version: '1.2.3' });
        expect(result.health).toMatchObject({ status: 'ok', artifact: 'admin-web-static' });
    });

    it('fails when required artifact files are missing', () => {
        const distDir = makeDist();
        rmSync(join(distDir, 'admin-health.json'));

        expect(() => smokeAdminBundle({ distDir, log: false })).toThrow('Missing admin artifact files');
    });

    it('fails when metadata is malformed or incomplete', () => {
        expect(() => smokeAdminBundle({ distDir: makeDist({ builtAt: 'not-a-date' }), log: false }))
            .toThrow('builtAt must be an ISO-compatible timestamp');
        expect(() => smokeAdminBundle({ distDir: makeDist({ apiBaseUrlConfigured: 'yes' }), log: false }))
            .toThrow('API base URL configuration status');
    });

    it('fails when health metadata or SPA root are invalid', () => {
        expect(() => smokeAdminBundle({ distDir: makeDist({}, { status: 'down' }), log: false }))
            .toThrow('healthy admin-web static artifact');
        const distDir = makeDist();
        writeFileSync(join(distDir, 'index.html'), '<!doctype html><html><body></body></html>');

        expect(() => smokeAdminBundle({ distDir, log: false })).toThrow('SPA root mount node');
    });

    it('enforces promotion metadata only in strict mode', () => {
        const localDist = makeDist({ commit: 'local', apiBaseUrlConfigured: false });

        expect(() => smokeAdminBundle({ distDir: localDist, strictPromotion: false, log: false })).not.toThrow();
        expect(() => smokeAdminBundle({ distDir: localDist, strictPromotion: true, log: false }))
            .toThrow('Strict admin smoke requires API base URL configuration');
        expect(() => smokeAdminBundle({
            distDir: makeDist({ commit: 'local', apiBaseUrlConfigured: true }),
            strictPromotion: true,
            log: false,
        })).toThrow('CI-provided commit and build timestamp metadata');
    });
});
