import type { AdminApiClient } from './generated/cadentia-api/client';

export type AdminUserStatus = 'ACTIVE' | 'SUSPENDED';
export type AdminUserRole = 'VIEWER' | 'WORSHIP_LEADER' | 'CATALOG_EDITOR' | 'DOCTRINAL_REVIEWER' | 'MUSICAL_REVIEWER' | 'ADMIN' | 'TEAM_SCHEDULER' | 'ASSIGNED_MUSICIAN' | 'REPORTING_VIEWER' | 'INTEGRATION_MANAGER';
export type AdminUser = {
    userId: string;
    churchInstanceId: string;
    externalSubject: string;
    displayName: string;
    email?: string | null;
    invitationUrl?: string | null;
    status: AdminUserStatus;
    roles: AdminUserRole[];
    version: number;
    createdAt: string;
    updatedAt: string;
};

export const adminUserRoles: AdminUserRole[] = ['VIEWER', 'WORSHIP_LEADER', 'CATALOG_EDITOR', 'DOCTRINAL_REVIEWER', 'MUSICAL_REVIEWER', 'ADMIN', 'TEAM_SCHEDULER', 'ASSIGNED_MUSICIAN', 'REPORTING_VIEWER', 'INTEGRATION_MANAGER'];

export const listAdminUsers = (apiClient: AdminApiClient, status?: AdminUserStatus, search?: string) => {
    const query = new URLSearchParams();
    if (status) query.set('status', status);
    if (search?.trim()) query.set('search', search.trim());
    const suffix = query.toString() ? `?${query.toString()}` : '';
    return apiClient.request<{ items: AdminUser[]; totalItems: number }>(`/admin/users${suffix}`);
};

export const createAdminUser = (apiClient: AdminApiClient, actorId: string, payload: { externalSubject?: string; email?: string; displayName: string; roles: AdminUserRole[] }) =>
    apiClient.request<AdminUser>('/admin/users', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) }, { actorId });

export const updateAdminUser = (apiClient: AdminApiClient, actorId: string, user: AdminUser, payload: { displayName: string; email?: string; status: AdminUserStatus }) =>
    apiClient.request<AdminUser>(`/admin/users/${encodeURIComponent(user.userId)}`, { method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ...payload, expectedVersion: user.version }) }, { actorId });

export const replaceAdminUserRoles = (apiClient: AdminApiClient, actorId: string, user: AdminUser, roles: AdminUserRole[], reason: string) =>
    apiClient.request<AdminUser>(`/admin/users/${encodeURIComponent(user.userId)}/roles`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ roles, expectedVersion: user.version, reason }) }, { actorId });
