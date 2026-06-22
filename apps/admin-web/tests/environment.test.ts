import { describe, expect, it } from 'vitest';
import { missingRequiredEnvironment, type AdminEnvironment } from '../src/config/environment';

describe('admin environment contract', () => {
    it('reports required deployment configuration that is missing', () => {
        const environment: AdminEnvironment = {
            apiBaseUrl: '',
            authIssuerUrl: '',
            identityProviderClientId: '',
            churchInstanceId: '',
            featureFlags: [],
            diagnosticsEnabled: false,
            buildVersion: '0.1.0',
            buildCommit: 'local',
            buildTimestamp: 'local',
        };

        expect(missingRequiredEnvironment(environment)).toEqual([
            'VITE_CADENTIA_API_BASE_URL',
            'VITE_CADENTIA_AUTH_ISSUER_URL',
            'VITE_CADENTIA_IDP_CLIENT_ID',
            'VITE_CADENTIA_CHURCH_INSTANCE_ID',
        ]);
    });
});
