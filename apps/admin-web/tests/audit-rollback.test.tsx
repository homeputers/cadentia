import React from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { act } from 'react-dom/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuditRollback } from '../src/routes/AuditRollback';
import type { AdminApiClient } from '../src/generated/cadentia-api/client';
import type { AdminSession } from '../src/auth/session';
import { buildAuditHistoryPath, parseAuditFilters, redactAuditSummary } from '../src/audit-rollback';

let container: HTMLDivElement;
let root: Root;
const session: AdminSession = { actorId: 'admin-1', displayName: 'Admin', churchInstanceId: 'church-1', roles: ['ADMIN'], capabilities: ['VIEW_AUDIT', 'PREVIEW_ROLLBACK', 'EXECUTE_ROLLBACK', 'VIEW_IMPORT_QUEUE', 'REVIEW_CATALOG', 'MANAGE_MODERATION', 'VIEW_DIAGNOSTICS', 'MANAGE_INSTANCE_CONFIGURATION', 'MANAGE_FEATURE_FLAGS', 'MANAGE_BOT_CHANNELS'] };
const render = async (ui: React.ReactNode) => {
    container = document.createElement('div'); document.body.appendChild(container);
    await act(async () => { root = createRoot(container); root.render(<>{ui}</>); });
    return container;
};
afterEach(() => { act(() => root?.unmount()); container?.remove(); vi.restoreAllMocks(); });

const clickButton = async (node: HTMLElement, label: string) => {
    await act(async () => { [...node.querySelectorAll('button')].find((button) => button.textContent === label)!.click(); });
};

const enterConfirmation = async (node: HTMLElement, value: string) => {
    const confirmation = [...node.querySelectorAll('input')].at(-1)!;
    await act(async () => { confirmation.value = value; confirmation.dispatchEvent(new Event('input', { bubbles: true })); });
};

describe('audit history and rollback workflows', () => {
    it('serializes supported audit filters and deep links', () => {
        const filters = parseAuditFilters('?entityType=SONG&actor=a1&from=2026-01-01&importBatchId=11111111-1111-4111-8111-111111111111&candidateId=22222222-2222-4222-8222-222222222222&songId=33333333-3333-4333-8333-333333333333&arrangementId=44444444-4444-4444-8444-444444444444&moderationFlagId=55555555-5555-4555-8555-555555555555&rollbackRequestId=66666666-6666-4666-8666-666666666666&event=77777777-7777-4777-8777-777777777777');
        expect(buildAuditHistoryPath(filters)).toContain('entityType=SONG');
        expect(buildAuditHistoryPath(filters)).toContain('rollbackRequestId=66666666-6666-4666-8666-666666666666');
    });

    it('drops invalid UUID filters before calling backend audit search', () => {
        const filters = parseAuditFilters('?event=audit-100&entityId=song-1&actor=a1');
        expect(buildAuditHistoryPath(filters)).not.toContain('audit-100');
        expect(buildAuditHistoryPath(filters)).not.toContain('song-1');
        expect(buildAuditHistoryPath(filters)).toContain('actor=a1');
    });

    it('renders redacted audit events without leaking raw payload data', async () => {
        const request = vi.fn().mockResolvedValue({ items: [{ id: 'audit-1', auditReferenceId: 'audit-1', entityType: 'SONG', entityId: 'song-1', action: 'APPROVED', actor: 'reviewer-1', occurredAt: '2026-06-24T00:00:00Z', correlationId: 'corr-1', payloadSummary: 'token=abc lyrics=full connectorPayload=secret safe summary', relatedLinks: [{ label: 'Song', href: '/admin/songs/song-1' }] }], totalItems: 1, page: 1, totalPages: 1 });
        const node = await render(<AuditRollback session={session} apiClient={{ request } as unknown as AdminApiClient} initialSearch="?songId=song-1" />);
        expect(request.mock.calls[0][0]).toContain('/admin/audit-events?');
        expect(node.textContent).toContain('Audit reference audit-1');
        expect(node.textContent).toContain('reviewer-1');
        expect(node.textContent).toContain('Correlation corr-1');
        expect(node.textContent).not.toContain('abc');
        expect(redactAuditSummary('secret:x password=y')).toContain('[redacted]');
    });

    it('creates backend rollback previews and gates execution by exact rollback request id', async () => {
        const request = vi.fn()
            .mockResolvedValueOnce({ items: [], totalItems: 0, page: 1, totalPages: 1 })
            .mockResolvedValueOnce({ rollbackRequestId: 'rollback-123', targetType: 'IMPORT_BATCH', targetId: 'batch-1', eligibilityImpacted: true, impactedRecords: [{ entityType: 'SONG', entityId: 'song-1', beforeStatus: 'APPROVED', afterStatus: 'STAGED', eligibilityChange: 'removed from recommendations' }], blockers: [], conflicts: [], irreversibleWarnings: ['Connector delete cannot be undone'], requiredPermissions: ['EXECUTE_ROLLBACK'], expiresAt: '2026-06-24T01:00:00Z', versionContext: 'preview-version-7' })
            .mockResolvedValueOnce({ rollbackRequestId: 'rollback-123', action: 'EXECUTED', auditEventId: 'audit-2' })
            .mockResolvedValueOnce({ items: [], totalItems: 0, page: 1, totalPages: 1 });
        const node = await render(<AuditRollback session={session} apiClient={{ request } as unknown as AdminApiClient} initialSearch="" />);
        await act(async () => { (node.querySelector('input') as HTMLInputElement).value = 'x'; node.querySelector('input')!.dispatchEvent(new Event('change', { bubbles: true })); });
        await act(async () => { [...node.querySelectorAll('button')].find((b) => b.textContent === 'Create backend rollback preview')!.click(); });
        expect(node.textContent).toContain('Backend rollback preview rollback-123');
        expect(node.textContent).toContain('removed from recommendations');
        const executeButton = [...node.querySelectorAll('button')].find((b) => b.textContent === 'Execute high-risk rollback from backend preview') as HTMLButtonElement;
        expect(executeButton.disabled).toBe(true);
        const confirmation = [...node.querySelectorAll('input')].at(-1)!;
        await act(async () => { confirmation.value = 'rollback-123'; confirmation.dispatchEvent(new Event('input', { bubbles: true })); });
        await act(async () => { executeButton.click(); });
        expect(request.mock.calls[2][1].body).toContain('rollback-123');
        expect(node.textContent).toContain('audit-2');
    });

    it('handles stale preview and role boundaries safely', async () => {
        const viewer = { ...session, capabilities: ['VIEW_AUDIT', 'PREVIEW_ROLLBACK'] as AdminSession['capabilities'] };
        const request = vi.fn().mockResolvedValueOnce({ items: [], totalItems: 0, page: 1, totalPages: 1 }).mockResolvedValueOnce({ rollbackRequestId: 'stale-1', targetType: 'SONG', targetId: 'song-1', blockers: [] });
        const node = await render(<AuditRollback session={viewer} apiClient={{ request } as unknown as AdminApiClient} />);
        await clickButton(node, 'Create backend rollback preview');
        expect((([...node.querySelectorAll('button')].find((b) => b.textContent === 'Execute high-risk rollback from backend preview')) as HTMLButtonElement).disabled).toBe(true);
    });

    it('clears stale rollback previews and reports preview authorization failures', async () => {
        const request = vi.fn()
            .mockResolvedValueOnce({ items: [], totalItems: 0, page: 1, totalPages: 1 })
            .mockResolvedValueOnce({ rollbackRequestId: 'preview-1', targetType: 'SONG', targetId: 'song-1', blockers: [] })
            .mockRejectedValueOnce({ status: 403, message: 'secret=raw' });
        const node = await render(<AuditRollback session={session} apiClient={{ request } as unknown as AdminApiClient} />);
        await clickButton(node, 'Create backend rollback preview');
        expect(node.textContent).toContain('Backend rollback preview preview-1');

        await clickButton(node, 'Create backend rollback preview');

        expect(node.textContent).not.toContain('Backend rollback preview preview-1');
        expect(node.textContent).toContain('You are not authorized to perform this rollback operation.');
        expect(node.textContent).not.toContain('secret=raw');
    });

    it.each([
        [400, 'Rollback validation failed. Review the target, blockers, and request a fresh backend preview.'],
        [409, 'Preview is stale or conflicted. Create a fresh backend preview before retrying.'],
        [412, 'Preview is stale or conflicted. Create a fresh backend preview before retrying.'],
        [500, 'Rollback failed safely before execution could be confirmed. Retry only after verifying the backend preview is still valid.'],
    ])('reports rollback execution failure status %s safely', async (status, message) => {
        const request = vi.fn()
            .mockResolvedValueOnce({ items: [], totalItems: 0, page: 1, totalPages: 1 })
            .mockResolvedValueOnce({ rollbackRequestId: 'rollback-123', targetType: 'SONG', targetId: 'song-1', blockers: [] })
            .mockRejectedValueOnce({ status, message: 'rawPayload=secret' });
        const node = await render(<AuditRollback session={session} apiClient={{ request } as unknown as AdminApiClient} />);
        await clickButton(node, 'Create backend rollback preview');
        await enterConfirmation(node, 'rollback-123');
        await clickButton(node, 'Execute high-risk rollback from backend preview');

        expect(node.textContent).toContain(message);
        expect(node.textContent).not.toContain('rawPayload=secret');
    });
});
