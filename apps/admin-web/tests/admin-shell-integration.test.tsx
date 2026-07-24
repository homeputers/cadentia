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

const candidateDetail = {
    candidateId: '11111111-1111-4111-8111-111111111111',
    importBatchId: '22222222-2222-4222-8222-222222222222',
    connectorKey: 'songselect',
    rawTitle: 'Raw Shell Title',
    normalizedTitle: 'Safe Shell Title',
    sourceArtistName: 'Artist',
    status: 'READY_TO_MERGE',
    allowedActions: ['VIEW_DETAIL', 'ADD_REVIEW_NOTE', 'MERGE_DECISION_DEFER', 'SUBMIT_APPROVAL_ACTION', 'OPEN_MODERATION_FLAG'],
    version: 7,
    etag: 'W/"candidate-7"',
    rawSourceReference: 'CCLI-123',
    sourcePayloadJson: '{"rawPayload":"do-not-render"}',
    sourcePayloadRedacted: true,
    parserEvidence: {
        parserName: 'cadentia-parser',
        parserVersion: '1.2.3',
        confidence: 0.91,
        severity: 'NONE',
        warnings: [],
        evidenceReferences: ['section-map:shell'],
    },
    eligibilityBlockers: [],
    duplicateSummary: {
        confidence: 'NONE',
        matchCount: 0,
        topScore: null,
        summary: 'No duplicate detected',
    },
    provenanceReferences: [{
        label: 'CCLI',
        sourceReference: 'CCLI-123',
        fingerprint: 'sha256:shell',
        status: 'VERIFIED',
    }],
    duplicateMatches: [],
    reviewNotes: [{
        noteId: '33333333-3333-4333-8333-333333333333',
        authorId: 'reviewer-2',
        authorDisplayName: 'Second Reviewer',
        category: 'GENERAL',
        body: 'Shell detail note',
        createdAt: '2026-06-22T02:00:00Z',
        auditReferenceId: 'audit-note-shell',
    }],
    reviewHistory: [{
        id: '44444444-4444-4444-8444-444444444444',
        decision: 'READY',
        reviewer: 'Reviewer One',
        reviewedAt: '2026-06-22T03:00:00Z',
        reviewNotes: 'Backend decision note',
    }],
    relatedAuditReferences: ['aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'],
    duplicateComparison: {
        candidate: { title: 'Safe Shell Title', key: 'G' },
        existing: { title: 'Safe Shell Title', key: 'A' },
        matchingFeatures: ['normalized title'],
        conflicts: ['key differs'],
        confidenceFeatures: ['title exact'],
        currentApprovedCatalogState: 'APPROVED',
        eligibilityEffects: ['Merge may publish approved arrangement'],
        auditReferenceId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
    },
    approvalState: {
        requiredTypes: ['DOCTRINAL', 'PROVENANCE'],
        statuses: [{ type: 'PROVENANCE', status: 'APPROVED', actor: 'reviewer-2', auditReferenceId: 'audit-approval-shell' }],
        blockers: ['Needs second reviewer'],
        allowedTransitions: ['APPROVE', 'REVERSE_APPROVAL'],
        eligibilityImpact: 'Eligible only after all required approvals pass',
        auditReferenceId: 'audit-approval-state-shell',
    },
    moderationFlags: [{
        id: '66666666-6666-4666-8666-666666666666',
        scope: 'IMPORT_CANDIDATE',
        type: 'METADATA_CONFLICT',
        reason: 'Conflicting author metadata',
        status: 'OPEN',
        eligibilityImpactPolicy: 'BLOCK_UNTIL_RESOLVED',
        openedBy: 'reviewer-2',
        auditReferenceId: 'audit-flag-shell',
    }],
};

const candidateAuditHistory = [{
    id: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
    entityId: candidateDetail.candidateId,
    entityType: 'IMPORT_CANDIDATE',
    action: 'MERGE_DECISION_DEFERRED',
    actor: 'reviewer-1',
    occurredAt: '2026-06-22T05:00:00Z',
    reason: 'Duplicate review deferred',
    beforeState: { status: 'READY_TO_MERGE', rawPayload: 'do-not-render-audit-payload' },
    afterState: { status: 'NEEDS_REVIEW', reviewerNote: 'safe key only' },
}];

const auditEventId = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
const auditResponse = {
    items: [{
        id: auditEventId,
        auditReferenceId: auditEventId,
        entityType: 'IMPORT_CANDIDATE',
        entityId: candidateDetail.candidateId,
        action: 'MERGE_DECISION_DEFERRED',
        actor: 'reviewer-1',
        occurredAt: '2026-06-22T05:00:00Z',
        correlationId: 'corr-1234567890abcdef',
        payloadSummary: 'token=abc rawPayload=secret safe summary',
        relatedLinks: [{ label: 'Candidate', href: `/admin/imports/${candidateDetail.candidateId}` }],
    }],
    page: 1,
    totalItems: 1,
    totalPages: 1,
};

const instanceConfiguration = {
    churchInstanceId: 'church-1',
    displayName: 'Cadentia Church',
    defaultLocale: 'en-US',
    timeZone: 'America/New_York',
    diagnosticsEnabled: true,
    botChannelsEnabled: true,
    allowedActions: ['VIEW', 'UPDATE'],
    concurrency: { version: 3, etag: 'cfg-3' },
    connectors: [{
        key: 'songselect',
        label: 'SongSelect',
        enabled: true,
        status: 'CONNECTED',
        credentialState: 'Configured; secret redacted',
    }],
    scoringProfiles: [{ profileKey: 'default', label: 'Default scoring', active: true, policyVersion: 'reng-v4' }],
    operationalSettings: [{ key: 'cacheTtl', label: 'Cache TTL', value: '300s', editable: false }],
};

const featureFlags = {
    churchInstanceId: 'church-1',
    flags: [{
        flagKey: 'admin-diagnostics',
        description: 'Admin diagnostics',
        enabled: true,
        allowedActions: ['VIEW', 'PREVIEW', 'CONFIRM'],
        concurrency: { version: 1, etag: 'flag-1' },
    }],
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

    it('loads a direct import-candidate detail route through the generated API client without exposing raw payloads', async () => {
        const fetchImpl = vi.fn().mockImplementation((input: RequestInfo | URL) => {
            const url = String(input);
            if (url === '/api/admin/session') return Promise.resolve(response(adminSession));
            if (url === `/api/admin/import-candidates/${candidateDetail.candidateId}`) return Promise.resolve(response(candidateDetail));
            if (url === `/api/admin/import-candidates/${candidateDetail.candidateId}/audit-history`) return Promise.resolve(response(candidateAuditHistory));
            return Promise.resolve(response({ error: 'unexpected' }, 404));
        });
        vi.stubGlobal('fetch', fetchImpl);

        const node = await renderShell(`/admin/imports/${candidateDetail.candidateId}`);

        expect(fetchImpl).toHaveBeenCalledWith(`/api/admin/import-candidates/${candidateDetail.candidateId}`, expect.any(Object));
        expect(fetchImpl).toHaveBeenCalledWith(`/api/admin/import-candidates/${candidateDetail.candidateId}/audit-history`, expect.any(Object));
        expect(node.textContent).toContain('Safe Shell Title');
        expect(node.textContent).toContain('sha256:shell');
        expect(node.textContent).toContain('Candidate audit history');
        expect(node.textContent).toContain('Merge decision deferred');
        expect(node.textContent).not.toContain('do-not-render');
        expect(node.textContent).not.toContain('do-not-render-audit-payload');
    });

    it('loads a direct audit deep link and redacts backend payload summaries', async () => {
        const fetchImpl = vi.fn().mockImplementation((input: RequestInfo | URL) => {
            const url = String(input);
            if (url === '/api/admin/session') return Promise.resolve(response(adminSession));
            if (url.startsWith('/api/admin/audit-events?')) return Promise.resolve(response(auditResponse));
            return Promise.resolve(response({ error: 'unexpected' }, 404));
        });
        vi.stubGlobal('fetch', fetchImpl);

        const node = await renderShell(`/admin/audit?event=${auditEventId}`);

        expect(fetchImpl).toHaveBeenCalledWith(`/api/admin/audit-events?event=${auditEventId}&page=1`, expect.any(Object));
        expect(node.textContent).toContain(`Audit reference ${auditEventId}`);
        expect(node.textContent).toContain('reviewer-1');
        expect(node.textContent).toContain('Correlation corr-123');
        expect(node.textContent).not.toContain('token=abc');
        expect(node.textContent).not.toContain('rawPayload=secret');
    });

    it('loads a direct settings route with instance configuration and feature flags from documented endpoints', async () => {
        const fetchImpl = vi.fn().mockImplementation((input: RequestInfo | URL) => {
            const url = String(input);
            if (url === '/api/admin/session') return Promise.resolve(response(adminSession));
            if (url === '/api/admin/instance-configuration') return Promise.resolve(response(instanceConfiguration));
            if (url === '/api/admin/feature-flags') return Promise.resolve(response(featureFlags));
            return Promise.resolve(response({ error: 'unexpected' }, 404));
        });
        vi.stubGlobal('fetch', fetchImpl);

        const node = await renderShell('/admin/settings');

        expect(fetchImpl).toHaveBeenCalledWith('/api/admin/instance-configuration', expect.any(Object));
        expect(fetchImpl).toHaveBeenCalledWith('/api/admin/feature-flags', expect.any(Object));
        expect((node.querySelector('input[name="displayName"]') as HTMLInputElement).value).toBe('Cadentia Church');
        expect(node.textContent).toContain('SongSelect');
        expect(node.textContent).toContain('secret redacted');
        expect(node.textContent).toContain('Admin diagnostics');
    });
});
