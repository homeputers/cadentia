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
    | 'MANAGE_TELEGRAM_ACCESS'
    | 'VIEW_TEAM_ROSTER'
    | 'MANAGE_TEAM_ASSIGNMENTS'
    | 'MANAGE_USERS';

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

const ACCESS_TOKEN_KEY = 'cadentia.admin.access-token';
const PKCE_VERIFIER_KEY = 'cadentia.admin.pkce-verifier';
const OIDC_STATE_KEY = 'cadentia.admin.oidc-state';
const OIDC_REDIRECT_URI_KEY = 'cadentia.admin.redirect-uri';

const defaultAccessTokenProvider: AccessTokenProvider = async () =>
    typeof window === 'undefined' ? null : window.sessionStorage.getItem(ACCESS_TOKEN_KEY);

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

const base64Url = (bytes: Uint8Array): string =>
    btoa(String.fromCharCode(...bytes)).replaceAll('+', '-').replaceAll('/', '_').replaceAll('=', '');

const createPkceVerifier = (): string => base64Url(crypto.getRandomValues(new Uint8Array(32)));

const sha256Base64Url = async (value: string): Promise<string> =>
    base64Url(new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value))));

export const buildSecureSignInUrl = async (environment: AdminEnvironment, returnTo = window.location.href): Promise<string> => {
    if (!environment.authIssuerUrl || !environment.identityProviderClientId) {
        return '#admin-auth-not-configured';
    }
    const verifier = createPkceVerifier();
    const state = base64Url(crypto.getRandomValues(new Uint8Array(24)));
    window.sessionStorage.setItem(PKCE_VERIFIER_KEY, verifier);
    window.sessionStorage.setItem(OIDC_STATE_KEY, state);
    window.sessionStorage.setItem(OIDC_REDIRECT_URI_KEY, returnTo);
    const signInUrl = new URL('/oauth2/authorize', environment.authIssuerUrl);
    signInUrl.searchParams.set('client_id', environment.identityProviderClientId);
    signInUrl.searchParams.set('response_type', 'code');
    signInUrl.searchParams.set('scope', 'openid profile email');
    signInUrl.searchParams.set('redirect_uri', returnTo);
    signInUrl.searchParams.set('state', state);
    signInUrl.searchParams.set('code_challenge', await sha256Base64Url(verifier));
    signInUrl.searchParams.set('code_challenge_method', 'S256');
    return signInUrl.toString();
};

export const completeAuthorizationCodeLogin = async (environment: AdminEnvironment): Promise<void> => {
    if (typeof window === 'undefined') return;
    const params = new URLSearchParams(window.location.search);
    const code = params.get('code');
    if (!code) return;
    const expectedState = window.sessionStorage.getItem(OIDC_STATE_KEY);
    if (!expectedState || params.get('state') !== expectedState) {
        throw new Error('OIDC login state validation failed.');
    }
    const verifier = window.sessionStorage.getItem(PKCE_VERIFIER_KEY);
    if (!verifier) throw new Error('OIDC login verifier is missing.');
    const response = await fetch(new URL('/oauth2/token', environment.authIssuerUrl), {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded', Accept: 'application/json' },
        body: new URLSearchParams({
            grant_type: 'authorization_code',
            client_id: environment.identityProviderClientId,
            code,
            redirect_uri: window.sessionStorage.getItem(OIDC_REDIRECT_URI_KEY) ?? window.location.origin + window.location.pathname,
            code_verifier: verifier,
        }),
    });
    if (!response.ok) throw new Error('OIDC authorization could not be completed.');
    const token = await response.json() as { access_token?: string };
    if (!token.access_token) throw new Error('OIDC token response did not contain an access token.');
    window.sessionStorage.setItem(ACCESS_TOKEN_KEY, token.access_token);
    window.sessionStorage.removeItem(PKCE_VERIFIER_KEY);
    window.sessionStorage.removeItem(OIDC_STATE_KEY);
    window.sessionStorage.removeItem(OIDC_REDIRECT_URI_KEY);
    window.history.replaceState({}, document.title, window.location.pathname + window.location.hash);
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
        await completeAuthorizationCodeLogin(environment);
        return { kind: 'authenticated', session: await apiClient.getAdminSession() };
    } catch (error) {
        const apiError = error as AdminApiError;
        const signInUrl = await buildSecureSignInUrl(environment);
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
