import React from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { act } from 'react-dom/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { Diagnostics } from '../src/routes/Diagnostics';
import { InstanceSettings } from '../src/routes/InstanceSettings';
import type { AdminApiClient } from '../src/generated/cadentia-api/client';
import type { AdminSession } from '../src/auth/session';
import { adminEnvironment } from '../src/config/environment';

let container: HTMLDivElement;
let root: Root;
const admin: AdminSession = { actorId: 'admin-1', displayName: 'Admin One', churchInstanceId: 'church-1', roles: ['ADMIN'], capabilities: ['VIEW_DIAGNOSTICS', 'MANAGE_INSTANCE_CONFIGURATION', 'MANAGE_FEATURE_FLAGS'] };
const config = { churchInstanceId: 'church-1', displayName: 'Cadentia Church', defaultLocale: 'en-US', timeZone: 'America/New_York', diagnosticsEnabled: true, botChannelsEnabled: true, allowedActions: ['VIEW', 'UPDATE'], concurrency: { version: 3, etag: 'cfg-3' }, connectors: [{ key: 'songselect', label: 'SongSelect', enabled: true, status: 'CONNECTED', credentialState: 'Configured; secret redacted' }], scoringProfiles: [{ profileKey: 'default', label: 'Default scoring', active: true, policyVersion: 'reng-v4' }], operationalSettings: [{ key: 'cacheTtl', label: 'Cache TTL', value: '300s', editable: false }] };
const flags = { churchInstanceId: 'church-1', flags: [{ flagKey: 'admin-diagnostics', description: 'Admin diagnostics', enabled: true, allowedActions: ['VIEW', 'PREVIEW', 'CONFIRM'], concurrency: { version: 1, etag: 'flag-1' } }] };

const render = async (ui: React.ReactNode) => { container = document.createElement('div'); document.body.appendChild(container); await act(async () => { root = createRoot(container); root.render(<>{ui}</>); }); return container; };
afterEach(() => { act(() => { root?.unmount(); }); container?.remove(); vi.restoreAllMocks(); adminEnvironment.featureFlags = []; });

describe('operational diagnostics and settings screens', () => {
    it('gates diagnostics by role and diagnostics feature flag', async () => {
        adminEnvironment.featureFlags = [];
        const request = vi.fn();
        const node = await render(<Diagnostics session={admin} apiClient={{ getAdminSession: vi.fn(), request }} />);
        expect(request).not.toHaveBeenCalled();
        expect(node.textContent).toContain('not available for this instance or release');
    });

    it('renders backend-provided diagnostics with redacted role-specific references only', async () => {
        adminEnvironment.featureFlags = ['admin-diagnostics'];
        const request = vi.fn().mockResolvedValue({ churchInstanceId: 'church-1', generatedAt: '2026-06-24T00:00:00Z', capabilityEnabled: true, recommendations: [{ recommendationId: 'rec-1', generatedAt: '2026-06-24T00:00:00Z', scoringInputs: [{ name: 'themeMatch', value: 0.91 }], reasonCodes: ['SCRIPTURE_THEME_MATCH'], eligibilityBlockers: ['KEY_POLICY_BLOCKED'], policyVersion: 'reng-v4', readModelFreshness: { readModel: 'recommendation-read-model', updatedAt: '2026-06-24T00:00:00Z', lagSeconds: 4 }, cacheStatus: 'HIT', correlationId: 'corr-1234567890abcdef', traceId: 'trace-abcdef1234567890', auditReference: { auditEventId: 'audit-1', occurredAt: '2026-06-24T00:00:01Z' } }] });
        const node = await render(<Diagnostics session={admin} apiClient={{ getAdminSession: vi.fn(), request }} />);
        expect(request).toHaveBeenCalledWith('/admin/diagnostics');
        expect(node.textContent).toContain('themeMatch: 0.91');
        expect(node.textContent).toContain('SCRIPTURE_THEME_MATCH');
        expect(node.textContent).toContain('KEY_POLICY_BLOCKED');
        expect(node.textContent).toContain('reng-v4');
        expect(node.textContent).toContain('Cache: HIT');
        expect(node.textContent).toContain('corr-123…cdef');
        expect(node.textContent).not.toContain('corr-1234567890abcdef');
    });

    it('renders read-only configuration and deferred placeholders safely', async () => {
        const readOnly = { ...config, allowedActions: ['VIEW'], botChannels: undefined };
        const request = vi.fn().mockImplementation((path: string) => path === '/admin/instance-configuration' ? Promise.resolve(readOnly) : Promise.resolve(flags));
        const node = await render(<InstanceSettings session={admin} apiClient={{ getAdminSession: vi.fn(), request }} />);
        expect(node.querySelector('input[name="displayName"]')?.hasAttribute('readonly')).toBe(true);
        expect(node.textContent).toContain('SongSelect');
        expect(node.textContent).toContain('secret redacted');
        expect(node.textContent).toContain('Bot channels');
        expect(node.textContent).toContain('not yet available for the current instance or release');
    });

    it('submits editable configuration with optimistic concurrency and audit attribution', async () => {
        const request = vi.fn().mockImplementation((path: string, init?: RequestInit) => {
            if (path === '/admin/instance-configuration' && init?.method === 'PUT') return Promise.resolve({ ...config, displayName: 'Updated Church', concurrency: { version: 4, etag: 'cfg-4' } });
            if (path === '/admin/instance-configuration') return Promise.resolve(config);
            return Promise.resolve(flags);
        });
        const node = await render(<InstanceSettings session={admin} apiClient={{ getAdminSession: vi.fn(), request }} />);
        (node.querySelector('input[name="displayName"]') as HTMLInputElement).value = 'Updated Church';
        await act(async () => { node.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); });
        const putCall = request.mock.calls.find((call) => call[1]?.method === 'PUT');
        expect(putCall).toBeDefined();
        const [, putInit, putMutation] = putCall!;
        expect(JSON.parse(String(putInit?.body))).toMatchObject({ displayName: 'Updated Church', expectedVersion: 3, actorId: 'admin-1' });
        expect(putMutation).toMatchObject({ actorId: 'admin-1', etag: 'cfg-3' });
        expect(node.textContent).toContain('Settings updated with backend validation');
    });

    it('reports stale configuration updates without leaking backend details', async () => {
        const request = vi.fn().mockImplementation((path: string, init?: RequestInit) => {
            if (path === '/admin/instance-configuration' && init?.method === 'PUT') return Promise.reject(Object.assign(new Error('rawPayload=secret stale config'), { status: 409 }));
            if (path === '/admin/instance-configuration') return Promise.resolve(config);
            return Promise.resolve(flags);
        });
        const node = await render(<InstanceSettings session={admin} apiClient={{ getAdminSession: vi.fn(), request }} />);
        await act(async () => { node.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); });
        expect(node.textContent).toContain('Operations state changed. Reload settings and request a fresh backend preview before retrying.');
        expect(node.textContent).not.toContain('rawPayload=secret');
        expect(node.textContent).not.toContain('stale config');
    });

    it('previews and confirms feature flag changes through documented endpoints', async () => {
        const updatedFlag = { ...flags.flags[0], enabled: false, concurrency: { version: 2, etag: 'flag-2' } };
        const request = vi.fn().mockImplementation((path: string, init?: RequestInit) => {
            if (path === '/admin/feature-flags/admin-diagnostics:preview' && init?.method === 'POST') {
                return Promise.resolve({ previewId: '11111111-1111-4111-8111-111111111111', flagKey: 'admin-diagnostics', requestedEnabled: false, confirmationRequired: true, impactSummary: 'Diagnostics route will be disabled.', blockers: [] });
            }
            if (path === '/admin/feature-flags/admin-diagnostics:confirm' && init?.method === 'POST') return Promise.resolve(updatedFlag);
            if (path === '/admin/instance-configuration') return Promise.resolve(config);
            return Promise.resolve(flags);
        });
        const node = await render(<InstanceSettings session={admin} apiClient={{ getAdminSession: vi.fn(), request }} />);
        const flagForm = [...node.querySelectorAll('form')][1];

        await act(async () => { flagForm.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); });
        expect(node.textContent).toContain('Diagnostics route will be disabled.');
        const previewCall = request.mock.calls.find((call) => call[0] === '/admin/feature-flags/admin-diagnostics:preview');
        expect(JSON.parse(String(previewCall?.[1]?.body))).toMatchObject({ enabled: false, expectedVersion: 1, actorId: 'admin-1' });
        expect(previewCall?.[2]).toMatchObject({ actorId: 'admin-1', etag: 'flag-1' });

        const confirmation = [...node.querySelectorAll('input')].at(-1)!;
        await act(async () => { confirmation.value = '11111111-1111-4111-8111-111111111111'; confirmation.dispatchEvent(new Event('input', { bubbles: true })); });
        await act(async () => { [...node.querySelectorAll('button')].find((button) => button.textContent === 'Confirm backend feature-flag change')!.click(); });

        const confirmCall = request.mock.calls.find((call) => call[0] === '/admin/feature-flags/admin-diagnostics:confirm');
        expect(JSON.parse(String(confirmCall?.[1]?.body))).toMatchObject({ previewId: '11111111-1111-4111-8111-111111111111', actorId: 'admin-1', confirmationText: '11111111-1111-4111-8111-111111111111' });
        expect(node.textContent).toContain('admin-diagnostics updated with backend confirmation');
        expect(node.textContent).toContain('Disabled');
    });

    it('reports feature flag preview and confirmation failures safely', async () => {
        const previewFailure = vi.fn().mockImplementation((path: string, init?: RequestInit) => {
            if (path === '/admin/feature-flags/admin-diagnostics:preview' && init?.method === 'POST') return Promise.reject(Object.assign(new Error('connectorPayload=secret no store'), { status: 501 }));
            if (path === '/admin/instance-configuration') return Promise.resolve(config);
            return Promise.resolve(flags);
        });
        const previewNode = await render(<InstanceSettings session={admin} apiClient={{ getAdminSession: vi.fn(), request: previewFailure }} />);
        await act(async () => { [...previewNode.querySelectorAll('form')][1].dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); });
        expect(previewNode.textContent).toContain('persistent backend support is not configured');
        expect(previewNode.textContent).not.toContain('connectorPayload=secret');
        expect(previewNode.textContent).not.toContain('no store');

        act(() => { root.unmount(); }); container.remove();
        const confirmFailure = vi.fn().mockImplementation((path: string, init?: RequestInit) => {
            if (path === '/admin/feature-flags/admin-diagnostics:preview' && init?.method === 'POST') return Promise.resolve({ previewId: '11111111-1111-4111-8111-111111111111', flagKey: 'admin-diagnostics', requestedEnabled: false, confirmationRequired: true, impactSummary: 'Diagnostics route will be disabled.', blockers: [] });
            if (path === '/admin/feature-flags/admin-diagnostics:confirm' && init?.method === 'POST') return Promise.reject(Object.assign(new Error('token=secret bad confirmation'), { status: 400 }));
            if (path === '/admin/instance-configuration') return Promise.resolve(config);
            return Promise.resolve(flags);
        });
        const confirmNode = await render(<InstanceSettings session={admin} apiClient={{ getAdminSession: vi.fn(), request: confirmFailure }} />);
        await act(async () => { [...confirmNode.querySelectorAll('form')][1].dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); });
        const confirmation = [...confirmNode.querySelectorAll('input')].at(-1)!;
        await act(async () => { confirmation.value = '11111111-1111-4111-8111-111111111111'; confirmation.dispatchEvent(new Event('input', { bubbles: true })); });
        await act(async () => { [...confirmNode.querySelectorAll('button')].find((button) => button.textContent === 'Confirm backend feature-flag change')!.click(); });
        expect(confirmNode.textContent).toContain('Backend validation rejected this operations change');
        expect(confirmNode.textContent).not.toContain('token=secret');
    });
});
