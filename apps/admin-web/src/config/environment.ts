export type AdminEnvironment = {
    apiBaseUrl: string;
    authIssuerUrl: string;
    authMode?: 'first-party' | 'oidc';
    identityProviderClientId: string;
    churchInstanceId: string;
    locale?: string;
    featureFlags: string[];
    diagnosticsEnabled: boolean;
    buildVersion: string;
    buildCommit: string;
    buildTimestamp: string;
};

const readEnv = (key: string): string => import.meta.env[key] ?? '';

const readCsv = (key: string): string[] =>
    readEnv(key)
        .split(',')
        .map((value) => value.trim())
        .filter(Boolean);

export const adminEnvironment: AdminEnvironment = {
    apiBaseUrl: readEnv('VITE_CADENTIA_API_BASE_URL'),
    authIssuerUrl: readEnv('VITE_CADENTIA_AUTH_ISSUER_URL'),
    authMode: (readEnv('VITE_CADENTIA_AUTH_MODE') || 'first-party') as 'first-party' | 'oidc',
    identityProviderClientId: readEnv('VITE_CADENTIA_IDP_CLIENT_ID'),
    churchInstanceId: readEnv('VITE_CADENTIA_CHURCH_INSTANCE_ID'),
    locale: readEnv('VITE_CADENTIA_LOCALE'),
    featureFlags: readCsv('VITE_CADENTIA_FEATURE_FLAGS'),
    diagnosticsEnabled: readEnv('VITE_CADENTIA_DIAGNOSTICS_ENABLED') === 'true',
    buildVersion: readEnv('VITE_CADENTIA_ADMIN_BUILD_VERSION') || '0.0.0-local',
    buildCommit: readEnv('VITE_CADENTIA_ADMIN_BUILD_COMMIT') || 'local',
    buildTimestamp: readEnv('VITE_CADENTIA_ADMIN_BUILD_TIMESTAMP') || 'local',
};

export const missingRequiredEnvironment = (environment: AdminEnvironment): string[] => {
    const requiredEntries: Array<[keyof AdminEnvironment, string]> = [
        ['apiBaseUrl', 'VITE_CADENTIA_API_BASE_URL'],
    ];
    if (environment.authMode === 'first-party') {
        requiredEntries.push(['authIssuerUrl', 'VITE_CADENTIA_AUTH_ISSUER_URL']);
    } else {
        requiredEntries.push(
            ['authIssuerUrl', 'VITE_CADENTIA_AUTH_ISSUER_URL'],
            ['identityProviderClientId', 'VITE_CADENTIA_IDP_CLIENT_ID']);
    }
    requiredEntries.push(['churchInstanceId', 'VITE_CADENTIA_CHURCH_INSTANCE_ID']);

    return requiredEntries
        .filter(([property]) => !environment[property])
        .map(([, envName]) => envName);
};
