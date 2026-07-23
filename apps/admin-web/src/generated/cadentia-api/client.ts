import type { AdminEnvironment } from '../../config/environment';
import type { AccessTokenProvider, AdminSession } from '../../auth/session';

export type AdminApiError = Error & {
    status?: number;
    code?: string;
};

export type MutationContext = {
    actorId: string;
    etag?: string;
};

export type AdminApiClient = {
    getAdminSession: () => Promise<AdminSession>;
    request: <T>(path: string, init?: RequestInit, mutation?: MutationContext) => Promise<T>;
};

const toApiError = async (response: Response): Promise<AdminApiError> => {
    const error = new Error('Admin API request failed') as AdminApiError;
    error.status = response.status;
    error.code = response.headers.get('X-Cadentia-Error-Code') ?? undefined;
    return error;
};

const resolveApiUrl = (path: string, apiBaseUrl: string): string => {
    const normalizedBase = apiBaseUrl.endsWith('/') ? apiBaseUrl.slice(0, -1) : apiBaseUrl;
    const normalizedPath = path.startsWith('/') ? path : `/${path}`;

    if (normalizedBase.startsWith('/')) {
        return `${normalizedBase}${normalizedPath}`;
    }

    return new URL(normalizedPath, normalizedBase).toString();
};

export const createAdminApiClient = ({
    environment,
    getAccessToken,
    fetchImpl = fetch,
}: {
    environment: AdminEnvironment;
    getAccessToken: AccessTokenProvider;
    fetchImpl?: typeof fetch;
}): AdminApiClient => {
    const request = async <T>(path: string, init: RequestInit = {}, mutation?: MutationContext): Promise<T> => {
        const token = await getAccessToken();
        const headers = new Headers(init.headers);
        headers.set('Accept', 'application/json');
        headers.set('X-Church-Instance-Id', environment.churchInstanceId);
        if (token) {
            headers.set('Authorization', `Bearer ${token}`);
        }
        if (mutation?.actorId) {
            headers.set('X-Cadentia-Actor-Id', mutation.actorId);
        }
        if (mutation?.etag) {
            headers.set('If-Match', mutation.etag);
        }

        const response = await fetchImpl(resolveApiUrl(path, environment.apiBaseUrl), {
            ...init,
            credentials: 'include',
            headers,
        });

        if (!response.ok) {
            throw await toApiError(response);
        }
        if (response.status === 204) {
            return undefined as T;
        }
        return (await response.json()) as T;
    };

    return {
        getAdminSession: () => request<AdminSession>('/admin/session'),
        request,
    };
};
