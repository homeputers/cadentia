import type { AdminEnvironment } from '../config/environment';

export const ACCESS_TOKEN_KEY = 'cadentia.admin.access-token';
export const REFRESH_TOKEN_KEY = 'cadentia.admin.refresh-token';

type FirstPartyTokenResponse = { accessToken?: string; refreshToken?: string };

const resolveAuthUrl = (environment: AdminEnvironment, path: string): string =>
    new URL(path, environment.authIssuerUrl.endsWith('/') ? environment.authIssuerUrl : `${environment.authIssuerUrl}/`).toString();

export const refreshFirstPartyAccessToken = async (environment: AdminEnvironment, fetchImpl: typeof fetch = fetch): Promise<string | null> => {
    if (environment.authMode !== 'first-party' || typeof window === 'undefined') return null;
    const refreshToken = window.sessionStorage.getItem(REFRESH_TOKEN_KEY);
    if (!refreshToken) return null;
    const response = await fetchImpl(resolveAuthUrl(environment, 'auth/refresh'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
        body: JSON.stringify({ refreshToken }),
    });
    if (!response.ok) {
        window.sessionStorage.removeItem(ACCESS_TOKEN_KEY);
        window.sessionStorage.removeItem(REFRESH_TOKEN_KEY);
        return null;
    }
    const tokens = await response.json() as FirstPartyTokenResponse;
    if (!tokens.accessToken || !tokens.refreshToken) return null;
    window.sessionStorage.setItem(ACCESS_TOKEN_KEY, tokens.accessToken);
    window.sessionStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken);
    return tokens.accessToken;
};
