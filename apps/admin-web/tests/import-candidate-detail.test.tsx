import React from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { act, Simulate } from 'react-dom/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ImportCandidateDetail } from '../src/routes/ImportCandidateDetail';
import type { AuditHistoryItem, CandidateDetail } from '../src/candidate-detail';
import type { AdminApiClient } from '../src/generated/cadentia-api/client';
import type { AdminSession } from '../src/auth/session';

let container: HTMLDivElement;
let root: Root;
const session: AdminSession = { actorId: 'reviewer-1', displayName: 'Reviewer One', churchInstanceId: 'church-1', roles: ['CATALOG_EDITOR'], capabilities: ['VIEW_IMPORT_QUEUE', 'REVIEW_CATALOG'] };
const adminSession: AdminSession = { actorId: 'admin-1', displayName: 'Admin One', churchInstanceId: 'church-1', roles: ['ADMIN'], capabilities: ['VIEW_IMPORT_QUEUE', 'REVIEW_CATALOG', 'MANAGE_MODERATION'] };

const relatedAuditReferenceId = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
const duplicateAuditReferenceId = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
const candidateAuditReferenceId = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc';

const baseDetail: CandidateDetail = {
    candidateId: '11111111-1111-1111-1111-111111111111', importBatchId: '22222222-2222-2222-2222-222222222222', connectorKey: 'songselect', rawTitle: 'Raw Safe Title', normalizedTitle: 'Safe Title', sourceArtistName: 'Artist', status: 'READY_TO_MERGE', allowedActions: ['VIEW_DETAIL', 'ADD_REVIEW_NOTE', 'MERGE_DECISION_DEFER', 'SUBMIT_APPROVAL_ACTION', 'OPEN_MODERATION_FLAG'], version: 7, etag: 'W/"candidate-7"', rawSourceReference: 'CCLI-123', sourcePayloadJson: '{"rawPayload":"do-not-render"}', sourcePayloadRedacted: true, parserEvidence: { parserName: 'cadentia-parser', parserVersion: '1.2.3', confidence: 0.91, severity: 'NONE', warnings: [], evidenceReferences: ['section-map:abc'] }, eligibilityBlockers: [], duplicateSummary: { confidence: 'NONE', matchCount: 0, topScore: null, summary: 'No duplicate detected' }, provenanceReferences: [{ label: 'CCLI', sourceReference: 'CCLI-123', fingerprint: 'sha256:abc', status: 'VERIFIED' }], duplicateMatches: [], reviewNotes: [{ noteId: '33333333-3333-3333-3333-333333333333', authorId: 'reviewer-2', authorDisplayName: 'Second Reviewer', category: 'GENERAL', body: 'Looks safe, but this is only a review note.', createdAt: '2026-06-22T02:00:00Z', auditReferenceId: 'audit-note-1' }], reviewHistory: [{ id: '44444444-4444-4444-4444-444444444444', decision: 'READY', reviewer: 'Reviewer One', reviewedAt: '2026-06-22T03:00:00Z', reviewNotes: 'Backend decision note' }], relatedAuditReferences: [relatedAuditReferenceId],
    duplicateComparison: { candidate: { title: 'Safe Title', key: 'G' }, existing: { title: 'Safe Title', key: 'A' }, matchingFeatures: ['normalized title'], conflicts: ['key differs'], confidenceFeatures: ['title exact'], currentApprovedCatalogState: 'APPROVED', eligibilityEffects: ['Merge may publish approved arrangement'], auditReferenceId: duplicateAuditReferenceId },
    approvalState: { requiredTypes: ['DOCTRINAL', 'PROVENANCE'], statuses: [{ type: 'PROVENANCE', status: 'APPROVED', actor: 'reviewer-2', auditReferenceId: 'audit-approval-1' }], blockers: ['Needs second reviewer'], allowedTransitions: ['APPROVE', 'REVERSE_APPROVAL'], eligibilityImpact: 'Eligible only after all required approvals pass', auditReferenceId: 'audit-approval-state' },
    moderationFlags: [{ id: '66666666-6666-6666-6666-666666666666', scope: 'IMPORT_CANDIDATE', type: 'METADATA_CONFLICT', reason: 'Conflicting author metadata', status: 'OPEN', eligibilityImpactPolicy: 'BLOCK_UNTIL_RESOLVED', openedBy: 'reviewer-2', auditReferenceId: 'audit-flag-1' }],
};

const auditHistory: AuditHistoryItem[] = [{
    id: candidateAuditReferenceId,
    entityId: baseDetail.candidateId,
    entityType: 'IMPORT_CANDIDATE',
    action: 'MERGE_DECISION_DEFERRED',
    actor: 'reviewer-1',
    occurredAt: '2026-06-22T05:00:00Z',
    reason: 'Duplicate review deferred',
    beforeState: { status: 'READY_TO_MERGE', rawPayload: 'do-not-render-audit-payload' },
    afterState: { status: 'NEEDS_REVIEW', reviewerNote: 'safe key only' },
}];

const render = async (detail: CandidateDetail | Error, request = vi.fn(), activeSession = session) => {
    if (!request.getMockImplementation()) request.mockImplementation((path: string) => {
        if (path.endsWith('/audit-history')) return Promise.resolve(auditHistory);
        if (path.endsWith('/notes')) return Promise.resolve({ noteId: '55555555-5555-5555-5555-555555555555', authorId: 'reviewer-1', body: 'New note', createdAt: '2026-06-22T04:00:00Z' });
        return detail instanceof Error ? Promise.reject(detail) : Promise.resolve(detail);
    });
    const apiClient: AdminApiClient = { getAdminSession: vi.fn(), request };
    container = document.createElement('div'); document.body.appendChild(container);
    await act(async () => { root = createRoot(container); root.render(<ImportCandidateDetail session={activeSession} candidateId={baseDetail.candidateId} apiClient={apiClient} />); });
    return { node: container, request };
};

afterEach(() => { act(() => { root?.unmount(); }); container?.remove(); vi.restoreAllMocks(); });

describe('import candidate detail', () => {
    it('renders ready candidate facts, provenance fingerprints, notes, history, and redacts raw payloads and lyrics', async () => {
        const { node, request } = await render(baseDetail);
        expect(node.textContent).toContain('Safe Title');
        expect(node.textContent).toContain('songselect');
        expect(node.textContent).toContain('7 / W/"candidate-7"');
        expect(node.textContent).toContain('sha256:abc');
        expect(node.textContent).toContain('91%');
        expect(node.textContent).toContain('Looks safe, but this is only a review note.');
        expect(node.querySelector(`a[href="/admin/audit?event=${relatedAuditReferenceId}"]`)).not.toBeNull();
        expect(request).toHaveBeenCalledWith('/admin/import-candidates/11111111-1111-1111-1111-111111111111/audit-history');
        expect(node.textContent).toContain('Candidate audit history');
        expect(node.textContent).toContain('Merge decision deferred');
        expect(node.textContent).toContain('Duplicate review deferred');
        expect(node.textContent).toContain('reviewerNote, status');
        expect(node.querySelector(`a[href="/admin/audit?event=${candidateAuditReferenceId}"]`)).not.toBeNull();
        expect(node.textContent).not.toContain('do-not-render');
        expect(node.textContent).not.toContain('do-not-render-audit-payload');
        expect(node.textContent).not.toContain('full lyrics content');
    });

    it('shows visible warnings for blocked, parser-warning, duplicate-suspected, and rejected states', async () => {
        for (const status of ['FAILED', 'REJECTED']) {
            const blocked = { ...baseDetail, status, parserEvidence: { ...baseDetail.parserEvidence!, confidence: 0.52, severity: 'WARNING', warnings: ['Chord line could not be aligned'] }, eligibilityBlockers: ['Missing verified provenance'], duplicateSummary: { confidence: 'HIGH', matchCount: 2, topScore: 0.96, summary: 'Likely duplicate' }, duplicateMatches: [{ id: 'm1', candidateSongId: 'song-1', matchScore: 0.96, status: 'SUSPECTED' }] };
            const { node } = await render(blocked);
            expect(node.textContent).toContain('Visible review warnings');
            expect(node.textContent).toContain('Chord line could not be aligned');
            expect(node.textContent).toContain('Missing verified provenance');
            expect(node.textContent).toContain('Likely duplicate');
            act(() => { root.unmount(); }); container.remove();
        }
    });



    it('renders duplicate comparison, approval state, moderation flags, and safe excerpts only', async () => {
        const { node } = await render(baseDetail);
        expect(node.textContent).toContain('Duplicate comparison and field-level merge review');
        expect(node.textContent).toContain('normalized title');
        expect(node.textContent).toContain('key differs');
        expect(node.textContent).toContain('Required approval types');
        expect(node.textContent).toContain('Needs second reviewer');
        expect(node.textContent).toContain('Conflicting author metadata');
        expect(node.querySelector(`a[href="/admin/audit?event=${duplicateAuditReferenceId}"]`)).not.toBeNull();
    });

    it('submits merge decisions, approval reversals, and moderation flags only after confirmation with actor and If-Match context', async () => {
        const request = vi.fn().mockImplementation((path: string) => {
            if (path.endsWith('/audit-history')) return Promise.resolve(auditHistory);
            if (path.endsWith('/merge-decisions') || path.endsWith('/approval-actions')) return Promise.resolve(baseDetail);
            if (path.endsWith('/moderation-flags')) return Promise.resolve({ id: 'flag-new', scope: 'IMPORT_CANDIDATE', reason: 'Bad source', status: 'OPEN', eligibilityImpactPolicy: 'BLOCK_UNTIL_RESOLVED', auditReferenceId: 'audit-new-flag' });
            return Promise.resolve(baseDetail);
        });
        const { node } = await render(baseDetail, request);
        const textareas = node.querySelectorAll('textarea');
        await act(async () => { Simulate.change(textareas[0], { target: { value: 'Backend signals are inconclusive' } } as never); });
        await act(async () => { node.querySelector('form[aria-labelledby], form') });
        const forms = node.querySelectorAll('form');
        await act(async () => { forms[0].dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); });
        await act(async () => { (node.querySelector('.admin-dialog button.danger') as HTMLButtonElement).click(); });
        expect(request).toHaveBeenCalledWith('/admin/import-candidates/11111111-1111-1111-1111-111111111111/merge-decisions', expect.objectContaining({ method: 'POST', body: expect.stringContaining('DEFER') }), { actorId: 'reviewer-1', etag: 'W/"candidate-7"' });

        await act(async () => { Simulate.change(node.querySelectorAll('select')[1], { target: { value: 'DOCTRINAL' } } as never); });
        await act(async () => { Simulate.change(node.querySelectorAll('select')[2], { target: { value: 'REVERSE_APPROVAL' } } as never); });
        await act(async () => { Simulate.change(node.querySelectorAll('textarea')[1], { target: { value: 'Reverse after corrected provenance blocker' } } as never); });
        await act(async () => { forms[1].dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); });
        await act(async () => { (node.querySelector('.admin-dialog button.danger') as HTMLButtonElement).click(); });
        expect(request).toHaveBeenCalledWith('/admin/import-candidates/11111111-1111-1111-1111-111111111111/approval-actions', expect.objectContaining({ method: 'POST', body: expect.stringContaining('REVERSE_APPROVAL') }), { actorId: 'reviewer-1', etag: 'W/"candidate-7"' });

        await act(async () => { Simulate.change(node.querySelectorAll('textarea')[2], { target: { value: 'Bad source' } } as never); });
        await act(async () => { forms[2].dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); });
        await act(async () => { (node.querySelector('.admin-dialog button.danger') as HTMLButtonElement).click(); });
        expect(request).toHaveBeenCalledWith('/admin/import-candidates/11111111-1111-1111-1111-111111111111/moderation-flags', expect.objectContaining({ method: 'POST', body: expect.stringContaining('BLOCK_UNTIL_RESOLVED') }), { actorId: 'reviewer-1', etag: 'W/"candidate-7"' });
    });

    it('assigns, resolves, and escalates moderation flags through documented endpoints', async () => {
        const request = vi.fn().mockImplementation((path: string) => {
            if (path.endsWith('/audit-history')) return Promise.resolve(auditHistory);
            if (path.endsWith('/assign')) return Promise.resolve({ ...baseDetail.moderationFlags![0], assignedTo: 'reviewer-3', status: 'ASSIGNED' });
            if (path.endsWith('/resolve')) return Promise.resolve({ ...baseDetail.moderationFlags![0], status: 'RESOLVED', resolutionNotes: 'Cleared with source correction' });
            if (path.endsWith('/escalate')) return Promise.resolve({ ...baseDetail.moderationFlags![0], status: 'ESCALATED' });
            return Promise.resolve(baseDetail);
        });
        const { node } = await render(baseDetail, request, adminSession);
        let forms = node.querySelectorAll('form');

        await act(async () => { Simulate.change(forms[2].querySelectorAll('input')[0], { target: { value: 'reviewer-3' } } as never); });
        await act(async () => { Simulate.change(forms[2].querySelectorAll('input')[1], { target: { value: 'Needs provenance owner' } } as never); });
        await act(async () => { forms[2].dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); });
        await act(async () => { (node.querySelector('.admin-dialog button.danger') as HTMLButtonElement).click(); });
        expect(request).toHaveBeenCalledWith('/admin/moderation-flags/66666666-6666-6666-6666-666666666666/assign', expect.objectContaining({ method: 'POST', body: expect.stringContaining('reviewer-3') }), { actorId: 'admin-1' });

        forms = node.querySelectorAll('form');
        await act(async () => { Simulate.change(forms[3].querySelector('textarea')!, { target: { value: 'Cleared with source correction' } } as never); });
        await act(async () => { forms[3].dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); });
        await act(async () => { (node.querySelector('.admin-dialog button.danger') as HTMLButtonElement).click(); });
        expect(request).toHaveBeenCalledWith('/admin/moderation-flags/66666666-6666-6666-6666-666666666666/resolve', expect.objectContaining({ method: 'POST', body: expect.stringContaining('Cleared with source correction') }), { actorId: 'admin-1' });

        forms = node.querySelectorAll('form');
        await act(async () => { Simulate.change(forms[4].querySelector('textarea')!, { target: { value: 'Escalate to admin queue' } } as never); });
        await act(async () => { forms[4].dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); });
        await act(async () => { (node.querySelector('.admin-dialog button.danger') as HTMLButtonElement).click(); });
        expect(request).toHaveBeenCalledWith('/admin/moderation-flags/66666666-6666-6666-6666-666666666666/escalate', expect.objectContaining({ method: 'POST', body: expect.stringContaining('Escalate to admin queue') }), { actorId: 'admin-1' });
    });

    it('hides moderation management controls from reviewers without manage capability', async () => {
        const { node } = await render(baseDetail);
        expect(node.textContent).not.toContain('Assign flag');
        expect(node.textContent).not.toContain('Resolve flag');
        expect(node.textContent).not.toContain('Escalate flag');
    });

    it('handles candidate audit history load failures without blocking the rest of the detail page', async () => {
        const request = vi.fn().mockImplementation((path: string) => path.endsWith('/audit-history')
            ? Promise.reject(Object.assign(new Error('rawPayload=secret token=abc'), { status: 500 }))
            : Promise.resolve(baseDetail));
        const { node } = await render(baseDetail, request);
        expect(node.textContent).toContain('Safe Title');
        expect(node.textContent).toContain('Candidate audit history');
        expect(node.textContent).toContain('The request failed with a redacted error message.');
        expect(node.textContent).not.toContain('secret');
        expect(node.textContent).not.toContain('rawPayload=secret');
        expect(node.textContent).not.toContain('token=abc');
    });

    it('adds structured notes through documented endpoint with actor and If-Match concurrency context', async () => {
        const request = vi.fn();
        const { node } = await render(baseDetail, request);
        const textarea = node.querySelectorAll('textarea')[3]!;
        await act(async () => { Simulate.change(textarea, { target: { value: 'New structured note' } } as never); });
        await act(async () => { node.querySelectorAll('form')[3].dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); });
        expect(request).toHaveBeenCalledWith('/admin/import-candidates/11111111-1111-1111-1111-111111111111/notes', expect.objectContaining({ method: 'POST' }), { actorId: 'reviewer-1', etag: 'W/"candidate-7"' });
    });

    it('handles unauthorized and stale-version note states without leaking protected content', async () => {
        const unauthorized = Object.assign(new Error('rawPayload=secret Bearer token'), { status: 403 });
        const { node } = await render(unauthorized);
        expect(node.querySelector('.admin-state--forbidden')?.textContent).toContain('You do not have access');
        expect(node.textContent).not.toContain('secret');

        const request = vi.fn().mockImplementation((path: string) => {
            if (path.endsWith('/audit-history')) return Promise.resolve(auditHistory);
            return path.endsWith('/notes') ? Promise.reject(Object.assign(new Error('stale'), { status: 412 })) : Promise.resolve(baseDetail);
        });
        const stale = await render(baseDetail, request);
        const textarea = stale.node.querySelectorAll('textarea')[3]!;
        await act(async () => { Simulate.change(textarea, { target: { value: 'Stale note' } } as never); });
        await act(async () => { stale.node.querySelectorAll('form')[3].dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); });
        expect(stale.node.textContent).toContain('Candidate version is stale');
    });
});
