import React from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { act } from 'react-dom/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ImportCandidateQueue } from '../src/routes/ImportCandidateQueue';
import { buildImportCandidateQueuePath, parseImportCandidateFilters, serializeImportCandidateFilters, type BulkActionResponse, type ImportCandidateQueueResponse } from '../src/import-candidates';
import type { AdminApiClient } from '../src/generated/cadentia-api/client';
import type { AdminSession } from '../src/auth/session';

let container: HTMLDivElement;
let root: Root;
const session: AdminSession = { actorId: 'reviewer-1', displayName: 'Reviewer One', churchInstanceId: 'church-1', roles: ['CATALOG_EDITOR'], capabilities: ['VIEW_IMPORT_QUEUE', 'REVIEW_CATALOG'] };
const auditReferenceId = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';

const queue: ImportCandidateQueueResponse = {
    items: [{
        candidateId: '11111111-1111-1111-1111-111111111111', importBatchId: '22222222-2222-2222-2222-222222222222', connectorKey: 'songselect', rawTitle: 'Safe title only', normalizedTitle: 'Safe Title Only', sourceArtistName: 'Artist', status: 'DEDUPLICATION_REVIEW', submittedAt: '2026-06-22T00:00:00Z', updatedAt: '2026-06-22T01:00:00Z', assignedReviewerId: 'reviewer-1', assignedReviewerName: 'Reviewer One', parserSeverity: 'WARNING', parserConfidence: 0.82, parserWarningCount: 2, provenanceStatus: 'VERIFIED', provenanceSummary: 'CCLI source reference verified', duplicateConfidence: 'HIGH', duplicateMatchCount: 3, duplicateTopScore: 0.93, moderationState: 'FLAGGED', reviewPriority: 'URGENT', approvalReadiness: 'BLOCKED', readinessSummary: 'Needs duplicate adjudication', allowedActions: ['VIEW_DETAIL', 'OPEN_MODERATION_FLAG'], auditReferenceId
    }],
    page: 1,
    pageSize: 25,
    totalItems: 1,
    totalPages: 1,
    sort: 'submittedAt:desc',
};

const render = async (apiClient: AdminApiClient, initialSearch = '') => {
    container = document.createElement('div');
    document.body.appendChild(container);
    await act(async () => { root = createRoot(container); root.render(<ImportCandidateQueue session={session} apiClient={apiClient} initialSearch={initialSearch} />); });
    return container;
};

afterEach(() => { act(() => { root?.unmount(); }); container?.remove(); vi.restoreAllMocks(); });

describe('import candidate queue', () => {
    it('serializes URL-safe server filters and generated-client request parameters', () => {
        const filters = parseImportCandidateFilters('?status=STAGED&connectorKey=songselect&rawPayload=secret&reviewNotes=sensitive&page=2&pageSize=50&sort=reviewPriority:desc');
        expect(filters).toMatchObject({ status: 'STAGED', connectorKey: 'songselect', page: 2, pageSize: 50, sort: 'reviewPriority:desc' });
        expect(serializeImportCandidateFilters(filters)).not.toContain('rawPayload');
        expect(serializeImportCandidateFilters(filters)).not.toContain('reviewNotes');
        expect(buildImportCandidateQueuePath(filters)).toBe('/admin/import-candidates?status=STAGED&connectorKey=songselect&sort=reviewPriority%3Adesc&page=2&pageSize=50');
    });

    it('renders safe row summaries, blocked readiness, audit links, and no raw content', async () => {
        const request = vi.fn().mockResolvedValue(queue);
        const node = await render({ getAdminSession: vi.fn(), request }, '?duplicateConfidence=HIGH&provenanceStatus=VERIFIED');
        expect(request).toHaveBeenCalledWith('/admin/import-candidates?provenanceStatus=VERIFIED&duplicateConfidence=HIGH');
        expect(node.textContent).toContain('Safe Title Only');
        expect(node.textContent).toContain('Warning parser · 82% · 2 warnings');
        expect(node.textContent).toContain('High duplicate · 3 matches · top 93%');
        expect(node.textContent).toContain('Blocked from row-level approval');
        expect(node.querySelector(`a[href="/admin/audit?event=${auditReferenceId}"]`)).not.toBeNull();
        expect(node.textContent).not.toContain('full lyrics');
        expect(node.textContent).not.toContain('rawPayload');
    });

    it('uses shared unauthorized, forbidden, empty, and retryable error states', async () => {
        const unauthorized = Object.assign(new Error('Bearer secret token=abc'), { status: 401 });
        const node = await render({ getAdminSession: vi.fn(), request: vi.fn().mockRejectedValue(unauthorized) });
        expect(node.querySelector('.admin-state--unauthorized')?.textContent).toContain('Sign in is required');
        const empty = await render({ getAdminSession: vi.fn(), request: vi.fn().mockResolvedValue({ ...queue, items: [], totalItems: 0, totalPages: 0 }) });
        expect(empty.querySelector('.admin-state--empty')?.textContent).toContain('No records match');
    });

    it('keeps reviewers without catalog review capability in detail-only visibility', async () => {
        const viewer: AdminSession = { ...session, capabilities: ['VIEW_IMPORT_QUEUE'] };
        container = document.createElement('div'); document.body.appendChild(container);
        await act(async () => { root = createRoot(container); root.render(<ImportCandidateQueue session={viewer} apiClient={{ getAdminSession: vi.fn(), request: vi.fn().mockResolvedValue(queue) }} />); });
        expect(container.textContent).toContain('Detail view only');
    });

    it('renders bulk action section only when user has REVIEW_CATALOG capability', async () => {
        const node = await render({ getAdminSession: vi.fn(), request: vi.fn().mockResolvedValue(queue) });
        expect(node.textContent).toContain('Bulk actions');
        const viewer: AdminSession = { ...session, capabilities: ['VIEW_IMPORT_QUEUE'] };
        container = document.createElement('div'); document.body.appendChild(container);
        await act(async () => { root = createRoot(container); root.render(<ImportCandidateQueue session={viewer} apiClient={{ getAdminSession: vi.fn(), request: vi.fn().mockResolvedValue(queue) }} />); });
        expect(container.textContent).not.toContain('Bulk actions');
    });

    it('shows bulk action disabled when no candidates are selected', async () => {
        const node = await render({ getAdminSession: vi.fn(), request: vi.fn().mockResolvedValue(queue) });
        expect(node.textContent).toContain('Select at least one candidate');
        const button = node.querySelector('button[aria-describedby="bulk-disabled-reason"]') as HTMLButtonElement;
        expect(button).not.toBeNull();
        expect(button.disabled).toBe(true);
    });

    it('allows selecting candidates and submitting a bulk action', async () => {
        const bulkResponse: BulkActionResponse = {
            actionType: 'REJECT_DUPLICATE',
            processedCount: 1,
            successCount: 1,
            failureCount: 0,
            results: [{ candidateId: '11111111-1111-1111-1111-111111111111', success: true }],
            auditReferenceId: 'bulk-audit-1',
        };
        const request = vi.fn()
            .mockResolvedValueOnce(queue)
            .mockResolvedValueOnce(bulkResponse)
            .mockResolvedValueOnce(queue);
        const node = await render({ getAdminSession: vi.fn(), request });
        const checkbox = node.querySelector('input[type="checkbox"][aria-label*="Select candidate"]') as HTMLInputElement;
        expect(checkbox).not.toBeNull();
        await act(async () => { checkbox.click(); });
        expect(node.textContent).toContain('1 selected');
        const openBulkButton = Array.from(node.querySelectorAll('button')).find((b) => b.textContent?.includes('Open bulk action')) as HTMLButtonElement;
        expect(openBulkButton).not.toBeUndefined();
        await act(async () => { openBulkButton.click(); });
        expect(node.textContent).toContain('Configure bulk action');
        const dialog = node.querySelector('[aria-labelledby="bulk-dialog-title"]') as HTMLElement;
        expect(dialog).not.toBeNull();
        const select = dialog.querySelector('select') as HTMLSelectElement;
        await act(async () => { select.value = 'REJECT_DUPLICATE'; select.dispatchEvent(new Event('change', { bubbles: true })); });
        const rationale = dialog.querySelector('textarea') as HTMLTextAreaElement;
        await act(async () => { rationale.value = 'Bulk reject for duplicate review'; rationale.dispatchEvent(new Event('change', { bubbles: true })); });
        const submitButton = Array.from(dialog.querySelectorAll('button')).find((b) => b.textContent?.includes('Submit bulk action'));
        expect(submitButton).not.toBeUndefined();
        await act(async () => { submitButton!.click(); });
        expect(request).toHaveBeenCalledWith('/admin/import-candidates:bulk-actions', expect.objectContaining({ method: 'POST' }));
        expect(node.textContent).toContain('1 succeeded');
    });

    it('disables bulk actions and shows error when backend rejects', async () => {
        const request = vi.fn()
            .mockResolvedValueOnce(queue)
            .mockRejectedValueOnce(new Error('Backend validation failed'));
        const node = await render({ getAdminSession: vi.fn(), request });
        const checkbox = node.querySelector('input[type="checkbox"]') as HTMLInputElement;
        expect(checkbox).not.toBeNull();
        await act(async () => { checkbox.click(); });
        const openBulkButton = Array.from(node.querySelectorAll('button')).find((b) => b.textContent?.includes('Open bulk action'));
        await act(async () => { openBulkButton?.click(); });
        const submitButton = Array.from(node.querySelectorAll('button')).find((b) => b.textContent?.includes('Submit bulk action'));
        await act(async () => { submitButton?.click(); });
        expect(node.textContent).toContain('Bulk action failed');
    });

    it('resets selection when queue reloads after bulk action', async () => {
        const bulkResponse: BulkActionResponse = {
            actionType: 'DEFER',
            processedCount: 1,
            successCount: 1,
            failureCount: 0,
            results: [{ candidateId: '11111111-1111-1111-1111-111111111111', success: true }],
        };
        const request = vi.fn()
            .mockResolvedValueOnce(queue)
            .mockResolvedValueOnce(bulkResponse)
            .mockResolvedValueOnce(queue);
        const node = await render({ getAdminSession: vi.fn(), request });
        const checkbox = node.querySelector('input[type="checkbox"]') as HTMLInputElement;
        await act(async () => { checkbox.click(); });
        expect(node.textContent).toContain('1 selected');
        const openBulkButton = Array.from(node.querySelectorAll('button')).find((b) => b.textContent?.includes('Open bulk action'));
        await act(async () => { openBulkButton?.click(); });
        const submitButton = Array.from(node.querySelectorAll('button')).find((b) => b.textContent?.includes('Submit bulk action'));
        await act(async () => { submitButton?.click(); });
        expect(node.textContent).not.toContain('1 selected');
    });
});
