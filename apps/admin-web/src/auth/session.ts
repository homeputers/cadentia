import { adminEnvironment, type AdminEnvironment } from '../config/environment';
import { createAdminApiClient, type AdminApiClient, type AdminApiError } from '../generated/cadentia-api/client';

export type AdminRole =
    | 'VIEWER'
    | 'WORSHIP_LEADER'
    | 'CATALOG_EDITOR'
    | 'DOCTRINAL_REVIEWER'
    | 'MUSICAL_REVIEWER'
    | 'ADMIN'
    | (string & {});

export type AdminCapability =
    | 'VIEW_IMPORT_QUEUE'
    | 'REVIEW_CATALOG'
    | 'MANAGE_MODERATION'
    | 'PREVIEW_ROLLBACK'
    | 'EXECUTE_ROLLBACK'
    | 'VIEW_AUDIT'
    | 'VIEW_DIAGNOSTICS'
    | 'MANAGE_INSTANCE_CONFIGURATION'
    | 'MANAGE_FEATURE_FLAGS'
    | 'MANAGE_BOT_CHANNELS'
    | 'MANAGE_TELEGRAM_ACCESS';

export type AdminSession = {
    actorId: string;
    displayName: string;
    churchInstanceId: string;
    locale?: string;
    roles: AdminRole[];
    capabilities: AdminCapability[];
};

export type PermissionState =
    | { kind: 'loading' }
    | { kind: 'missing-church-instance'; missing: string[] }
    | { kind: 'unauthenticated'; signInUrl: string }
    | { kind: 'expired-session'; signInUrl: string }
    | { kind: 'forbidden' }
    | { kind: 'disabled-feature'; feature: string }
    | { kind: 'failure'; message: string }
    | { kind: 'authenticated'; session: AdminSession };

export type AccessTokenProvider = () => Promise<string | null>;

const defaultAccessTokenProvider: AccessTokenProvider = async () => null;

export const buildSignInUrl = (environment: AdminEnvironment, returnTo = window.location.href): string => {
    if (!environment.authIssuerUrl || !environment.identityProviderClientId) {
        return '#admin-auth-not-configured';
    }

    const signInUrl = new URL('/oauth2/authorize', environment.authIssuerUrl);
    signInUrl.searchParams.set('client_id', environment.identityProviderClientId);
    signInUrl.searchParams.set('response_type', 'code');
    signInUrl.searchParams.set('scope', 'openid profile');
    signInUrl.searchParams.set('redirect_uri', returnTo);
    return signInUrl.toString();
};

export const isFeatureEnabled = (environment: AdminEnvironment, feature: string): boolean =>
    environment.featureFlags.includes(feature);

export const bootstrapAdminSession = async ({
    environment = adminEnvironment,
    apiClient = createAdminApiClient({ environment, getAccessToken: defaultAccessTokenProvider }),
    requiredFeature,
}: {
    environment?: AdminEnvironment;
    apiClient?: AdminApiClient;
    requiredFeature?: string;
}): Promise<PermissionState> => {
    const missing = environment.churchInstanceId ? [] : ['VITE_CADENTIA_CHURCH_INSTANCE_ID'];
    if (missing.length > 0) {
        return { kind: 'missing-church-instance', missing };
    }

    if (requiredFeature && !isFeatureEnabled(environment, requiredFeature)) {
        return { kind: 'disabled-feature', feature: requiredFeature };
    }

    try {
        return { kind: 'authenticated', session: await apiClient.getAdminSession() };
    } catch (error) {
        const apiError = error as AdminApiError;
        const signInUrl = buildSignInUrl(environment);
        if (apiError.status === 401) {
            return apiError.code === 'SESSION_EXPIRED'
                ? { kind: 'expired-session', signInUrl }
                : { kind: 'unauthenticated', signInUrl };
        }
        if (apiError.status === 403) {
            return { kind: 'forbidden' };
        }
        return { kind: 'failure', message: 'The admin console could not be loaded.' };
    }
};
