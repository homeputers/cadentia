import React from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { act } from 'react-dom/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { TelegramAccessRequests } from '../src/routes/TelegramAccessRequests';
import type { AdminSession } from '../src/auth/session';

let container: HTMLDivElement;
let root: Root;
const admin: AdminSession = {
    actorId: 'admin-1',
    displayName: 'Admin One',
    churchInstanceId: 'church-1',
    roles: ['ADMIN'],
    capabilities: ['MANAGE_TELEGRAM_ACCESS'],
};
const viewer: AdminSession = { ...admin, actorId: 'viewer-1', roles: ['VIEWER'], capabilities: [] };
const pendingRequest = {
    requestId: 'req-1',
    churchInstanceId: 'church-1',
    status: 'PENDING',
    requestedAt: '2026-08-24T12:00:00Z',
    maskedReference: '32d4e9594294',
};

const render = async (ui: React.ReactNode) => {
    container = document.createElement('div');
    document.body.appendChild(container);
    await act(async () => { root = createRoot(container); root.render(<>{ui}</>); });
    return container;
};
afterEach(() => { act(() => { root?.unmount(); }); container?.remove(); vi.restoreAllMocks(); });

describe('Telegram access request queue', () => {
    it('renders pending requests with masked references only', async () => {
        const request = vi.fn().mockResolvedValue({ churchInstanceId: 'church-1', items: [pendingRequest] });
        const node = await render(<TelegramAccessRequests session={admin} apiClient={{ getAdminSession: vi.fn(), request }} />);

        expect(request).toHaveBeenCalledWith('/admin/telegram/access-requests?status=PENDING');
        expect(node.textContent).toContain('32d4e9594294');
        expect(node.textContent).toContain('Pending access requests');
    });

    it('approves a request with actor attribution and removes it from the queue', async () => {
        const request = vi.fn().mockImplementation((path: string, init?: RequestInit) => {
            if (path.endsWith(':approve')) return Promise.resolve({ request: { ...pendingRequest, status: 'APPROVED', decidedAt: '2026-08-24T12:05:00Z', decidedBy: 'admin-1' } });
            return Promise.resolve({ churchInstanceId: 'church-1', items: [pendingRequest] });
        });
        const node = await render(<TelegramAccessRequests session={admin} apiClient={{ getAdminSession: vi.fn(), request }} />);

        const approveButton = [...node.querySelectorAll('button')].find((button) => button.textContent === 'Approve')!;
        await act(async () => { approveButton.dispatchEvent(new MouseEvent('click', { bubbles: true })); });

        const approveCall = request.mock.calls.find((call) => String(call[0]).endsWith(':approve'));
        expect(String(approveCall![0])).toBe('/admin/telegram/access-requests/req-1:approve');
        expect(approveCall![2]).toMatchObject({ actorId: 'admin-1' });
        expect(node.textContent).toContain('Access request approved. The requester was notified on Telegram.');
        expect(node.textContent).not.toContain('32d4e9594294');
    });

    it('rejects a request with the provided reason', async () => {
        const request = vi.fn().mockImplementation((path: string) => {
            if (path.endsWith(':reject')) return Promise.resolve({ request: { ...pendingRequest, status: 'REJECTED', decidedAt: '2026-08-24T12:05:00Z' } });
            return Promise.resolve({ churchInstanceId: 'church-1', items: [pendingRequest] });
        });
        const node = await render(<TelegramAccessRequests session={admin} apiClient={{ getAdminSession: vi.fn(), request }} />);

        const reasonInput = node.querySelector('input') as HTMLInputElement;
        await act(async () => {
            const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value')!.set!;
            setter.call(reasonInput, 'Unknown person');
            reasonInput.dispatchEvent(new Event('input', { bubbles: true }));
        });
        const rejectButton = [...node.querySelectorAll('button')].find((button) => button.textContent === 'Reject')!;
        await act(async () => { rejectButton.dispatchEvent(new MouseEvent('click', { bubbles: true })); });

        const rejectCall = request.mock.calls.find((call) => String(call[0]).endsWith(':reject'));
        expect(JSON.parse(String(rejectCall![1]?.body))).toMatchObject({ reason: 'Unknown person' });
        expect(node.textContent).toContain('Access request rejected. The requester was notified on Telegram.');
    });

    it('surfaces already-decided conflicts without leaking backend details', async () => {
        const request = vi.fn().mockImplementation((path: string) => {
            if (path.endsWith(':approve')) return Promise.reject(Object.assign(new Error('chat_id=4242 conflict'), { status: 409 }));
            return Promise.resolve({ churchInstanceId: 'church-1', items: [pendingRequest] });
        });
        const node = await render(<TelegramAccessRequests session={admin} apiClient={{ getAdminSession: vi.fn(), request }} />);

        const approveButton = [...node.querySelectorAll('button')].find((button) => button.textContent === 'Approve')!;
        await act(async () => { approveButton.dispatchEvent(new MouseEvent('click', { bubbles: true })); });

        expect(node.textContent).toContain('This access request was already decided. Reload the queue.');
        expect(node.textContent).not.toContain('chat_id=4242');
    });

    it('hides the queue from sessions without the telegram access capability', async () => {
        const request = vi.fn();
        const node = await render(<TelegramAccessRequests session={viewer} apiClient={{ getAdminSession: vi.fn(), request }} />);

        expect(request).not.toHaveBeenCalled();
        expect(node.textContent).not.toContain('Pending access requests');
    });
});
