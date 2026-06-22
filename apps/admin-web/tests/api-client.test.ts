import { describe, expect, it } from 'vitest';
import { createAdminApiClient } from '../src/generated/cadentia-api/client';
import type { AdminEnvironment } from '../src/config/environment';

const environment: AdminEnvironment = {
    apiBaseUrl: 'https://api.example.test',
    authIssuerUrl: 'https://idp.example.test',
    identityProviderClientId: 'cadentia-admin',
    churchInstanceId: 'church-1',
    featureFlags: [],
    diagnosticsEnabled: false,
    buildVersion: 'test',
    buildCommit: 'test',
    buildTimestamp: 'test',
};

describe('generated admin API client wrapper', () => {
    it('passes authorization, church-instance, actor attribution, and concurrency headers', async () => {
        const capturedRequests: Request[] = [];
        const fetchImpl = async (input: URL | RequestInfo, init?: RequestInit) => {
            capturedRequests.push(new Request(input, init));
            return new Response(JSON.stringify({ ok: true }), {
                status: 200,
                headers: { 'Content-Type': 'application/json' },
            });
        };
        const client = createAdminApiClient({
            environment,
            getAccessToken: async () => 'access-token',
            fetchImpl: fetchImpl as typeof fetch,
        });

        await client.request('/admin/instance-configuration', { method: 'PUT' }, { actorId: 'operator-1', etag: '"v7"' });

        const capturedRequest = capturedRequests[0];
        expect(capturedRequest.headers.get('Authorization')).toBe('Bearer access-token');
        expect(capturedRequest.headers.get('X-Church-Instance-Id')).toBe('church-1');
        expect(capturedRequest.headers.get('X-Cadentia-Actor-Id')).toBe('operator-1');
        expect(capturedRequest.headers.get('If-Match')).toBe('"v7"');
        expect(capturedRequest.credentials).toBe('include');
    });
});
