import { describe, expect, it } from 'vitest';
import { bootstrapAdminSession, buildSignInUrl, type AdminSession } from '../src/auth/session';
import type { AdminEnvironment } from '../src/config/environment';

const environment: AdminEnvironment = {
    apiBaseUrl: 'https://api.example.test',
    authIssuerUrl: 'https://idp.example.test',
    identityProviderClientId: 'cadentia-admin',
    churchInstanceId: 'church-1',
    featureFlags: ['enabled-feature'],
    diagnosticsEnabled: true,
    buildVersion: 'test',
    buildCommit: 'test',
    buildTimestamp: 'test',
};

const adminSession: AdminSession = {
    actorId: 'operator-1',
    displayName: 'Operator One',
    churchInstanceId: 'church-1',
    roles: ['ADMIN'],
    capabilities: ['VIEW_IMPORT_QUEUE'],
};

describe('admin session bootstrap', () => {
    it('builds a documented sign-in URL without storing credentials', () => {
        expect(buildSignInUrl(environment, 'https://admin.example.test/admin')).toBe(
            'https://idp.example.test/oauth2/authorize?client_id=cadentia-admin&response_type=code&scope=openid+profile&redirect_uri=https%3A%2F%2Fadmin.example.test%2Fadmin',
        );
    });

    it('returns authenticated state from the backend session surface', async () => {
        await expect(
            bootstrapAdminSession({ environment, apiClient: { getAdminSession: async () => adminSession, request: async <T,>() => undefined as T } }),
        ).resolves.toEqual({ kind: 'authenticated', session: adminSession });
    });

    it('distinguishes missing church instance before loading protected data', async () => {
        await expect(
            bootstrapAdminSession({
                environment: { ...environment, churchInstanceId: '' },
                apiClient: { getAdminSession: async () => adminSession, request: async <T,>() => undefined as T },
            }),
        ).resolves.toEqual({ kind: 'missing-church-instance', missing: ['VITE_CADENTIA_CHURCH_INSTANCE_ID'] });
    });

    it('distinguishes unauthenticated, expired, forbidden, disabled feature, and general failure states', async () => {
        const failingClient = (status?: number, code?: string) => ({
            getAdminSession: async () => Promise.reject(Object.assign(new Error('failed'), { status, code })),
            request: async <T,>() => undefined as T,
        });

        await expect(bootstrapAdminSession({ environment, apiClient: failingClient(401) })).resolves.toMatchObject({ kind: 'unauthenticated' });
        await expect(bootstrapAdminSession({ environment, apiClient: failingClient(401, 'SESSION_EXPIRED') })).resolves.toMatchObject({ kind: 'expired-session' });
        await expect(bootstrapAdminSession({ environment, apiClient: failingClient(403) })).resolves.toEqual({ kind: 'forbidden' });
        await expect(bootstrapAdminSession({ environment, apiClient: failingClient(), requiredFeature: 'missing-feature' })).resolves.toEqual({ kind: 'disabled-feature', feature: 'missing-feature' });
        await expect(bootstrapAdminSession({ environment, apiClient: failingClient(500) })).resolves.toEqual({ kind: 'failure', message: 'The admin console could not be loaded.' });
    });
});
