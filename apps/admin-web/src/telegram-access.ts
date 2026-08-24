import type { AdminApiClient } from './generated/cadentia-api/client';

export type TelegramAccessRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export type TelegramAccessRequest = {
    requestId: string;
    churchInstanceId: string;
    status: TelegramAccessRequestStatus;
    requestedAt: string;
    decidedAt?: string;
    decidedBy?: string;
    decisionReason?: string | null;
    maskedReference: string;
};

export type TelegramAccessRequestListResponse = {
    churchInstanceId: string;
    items: TelegramAccessRequest[];
};

export const listTelegramAccessRequests = (apiClient: AdminApiClient, status: TelegramAccessRequestStatus = 'PENDING') =>
    apiClient.request<TelegramAccessRequestListResponse>(`/admin/telegram/access-requests?status=${encodeURIComponent(status)}`);

export const approveTelegramAccessRequest = (apiClient: AdminApiClient, requestId: string, actorId: string, reason?: string) =>
    apiClient.request<{ request: TelegramAccessRequest }>(
        `/admin/telegram/access-requests/${encodeURIComponent(requestId)}:approve`,
        { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ reason: reason || undefined }) },
        { actorId },
    );

export const rejectTelegramAccessRequest = (apiClient: AdminApiClient, requestId: string, actorId: string, reason?: string) =>
    apiClient.request<{ request: TelegramAccessRequest }>(
        `/admin/telegram/access-requests/${encodeURIComponent(requestId)}:reject`,
        { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ reason: reason || undefined }) },
        { actorId },
    );
