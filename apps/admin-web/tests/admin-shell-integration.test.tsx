import React from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { act } from 'react-dom/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { adminEnvironment, type AdminEnvironment } from '../src/config/environment';
import { AdminShell } from '../src/routes/AdminShell';

let container: HTMLDivElement;
let root: Root;
let originalEnvironment: AdminEnvironment;

const adminSession = {
    actorId: 'admin-1',
    displayName: 'Admin One',
    churchInstanceId: 'church-1',
    roles: ['ADMIN'],
    capabilities: [
        'VIEW_IMPORT_QUEUE',
        'REVIEW_CATALOG',
        'MANAGE_MODERATION',
        'PREVIEW_ROLLBACK',
        'EXECUTE_ROLLBACK',
        'VIEW_AUDIT',
        'VIEW_DIAGNOSTICS',
        'MANAGE_INSTANCE_CONFIGURATION',
        'MANAGE_FEATURE_FLAGS',
    ],
};

const viewerSession = {
    actorId: 'viewer-1',
    displayName: 'Viewer One',
    churchInstanceId: 'church-1',
    roles: ['VIEWER'],
    capabilities: ['VIEW_IMPORT_QUEUE'],
};

const queueResponse = {
    items: [{
        candidateId: '11111111-1111-4111-8111-111111111111',
        importBatchId: '22222222-2222-4222-8222-222222222222',
        connectorKey: 'songselect',
        rawTitle: 'Safe Shell Title',
        normalizedTitle: 'Safe Shell Title',
        sourceArtistName: 'Artist',
        status: 'DEDUPLICATION_REVIEW',
        submittedAt: '2026-06-22T00:00:00Z',
        updatedAt: '2026-06-22T01:00:00Z',
        parserSeverity: 'NONE',
        parserWarningCount: 0,
        provenanceStatus: 'VERIFIED',
        duplicateConfidence: 'NONE',
        duplicateMatchCount: 0,
        moderationState: 'CLEAR',
        reviewPriority: 'NORMAL',
        approvalReadiness: 'NEEDS_REVIEW',
        allowedActions: ['VIEW_DETAIL'],
        auditReferenceId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    }],
    page: 1,
    pageSize: 5,
    totalItems: 1,
    totalPages: 1,
    sort: 'updatedAt:desc',
};

const setEnvironment = (overrides: Partial<AdminEnvironment> = {}) => {
    Object.assign(adminEnvironment, {
        apiBaseUrl: '/api',
        authIssuerUrl: 'https://idp.example.test',
        identityProviderClientId: 'cadentia-admin',
        churchInstanceId: 'church-1',
        featureFlags: ['admin-diagnostics'],
        diagnosticsEnabled: true,
        buildVersion: 'test',
        buildCommit: 'test',
        buildTimestamp: '2026-06-22T00:00:00Z',
        ...overrides,
    });
};

const response = (body: unknown, status = 200) => new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
});

const renderShell = async (path = '/admin') => {
    window.history.pushState({}, '', path);
    container = document.createElement('div');
    document.body.appendChild(container);
    await act(async () => {
        root = createRoot(container);
        root.render(<AdminShell />);
    });
    await act(async () => {
        await Promise.resolve();
    });
    return container;
};

beforeEach(() => {
    originalEnvironment = { ...adminEnvironment, featureFlags: [...adminEnvironment.featureFlags] };
    setEnvironment();
});

afterEach(() => {
    act(() => { root?.unmount(); });
    container?.remove();
    Object.assign(adminEnvironment, originalEnvironment);
    vi.restoreAllMocks();
});

describe('admin shell integration smoke', () => {
    it('bootstraps the authenticated shell, renders role-aware navigation, and loads the import snapshot', async () => {
        const fetchImpl = vi.fn().mockImplementation((input: RequestInfo | URL) => {
            const url = String(input);
            if (url === '/api/admin/session') return Promise.resolve(response(adminSession));
            if (url.startsWith('/api/admin/import-candidates')) return Promise.resolve(response(queueResponse));
            return Promise.resolve(response({ error: 'unexpected' }, 404));
        });
        vi.stubGlobal('fetch', fetchImpl);

        const node = await renderShell('/admin');

        expect(fetchImpl).toHaveBeenCalledWith('/api/admin/session', expect.objectContaining({ credentials: 'include' }));
        expect(fetchImpl).toHaveBeenCalledWith('/api/admin/import-candidates?sort=updatedAt%3Adesc&pageSize=5', expect.any(Object));
        expect(node.textContent).toContain('Signed in as Admin One');
        expect(node.textContent).toContain('Import review');
        expect(node.textContent).toContain('Audit history');
        expect(node.textContent).toContain('Diagnostics');
        expect(node.textContent).toContain('Instance settings');
        expect(node.textContent).toContain('Safe Shell Title');
        expect(node.textContent).not.toContain('rawPayload');
    });

    it('blocks direct routes that the authenticated session cannot access', async () => {
        const fetchImpl = vi.fn().mockImplementation((input: RequestInfo | URL) => {
            if (String(input) === '/api/admin/session') return Promise.resolve(response(viewerSession));
            return Promise.resolve(response({ error: 'unexpected' }, 404));
        });
        vi.stubGlobal('fetch', fetchImpl);

        const node = await renderShell('/admin/settings');

        expect(node.textContent).toContain('Access denied');
        expect(node.textContent).toContain('No protected details were loaded');
        expect(fetchImpl).not.toHaveBeenCalledWith('/api/admin/instance-configuration', expect.any(Object));
    });

    it('stops before protected API calls when church-instance runtime configuration is missing', async () => {
        setEnvironment({ churchInstanceId: '' });
        const fetchImpl = vi.fn();
        vi.stubGlobal('fetch', fetchImpl);

        const node = await renderShell('/admin');

        expect(node.textContent).toContain('Missing church instance');
        expect(fetchImpl).not.toHaveBeenCalled();
    });

    it('keeps import snapshot failures retryable without leaking backend details', async () => {
        const fetchImpl = vi.fn().mockImplementation((input: RequestInfo | URL) => {
            const url = String(input);
            if (url === '/api/admin/session') return Promise.resolve(response(adminSession));
            if (url.startsWith('/api/admin/import-candidates')) return Promise.resolve(response({ message: 'token=secret rawPayload' }, 500));
            return Promise.resolve(response({ error: 'unexpected' }, 404));
        });
        vi.stubGlobal('fetch', fetchImpl);

        const node = await renderShell('/admin');

        expect(node.querySelector('.admin-state--error')?.textContent).toContain('The request failed');
        expect(node.textContent).not.toContain('token=secret');
        expect(node.textContent).not.toContain('rawPayload');
    });
});
