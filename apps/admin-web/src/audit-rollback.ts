import type { AdminApiClient } from './generated/cadentia-api/client';

export type AuditFilters = {
    event?: string; entityType?: string; entityId?: string; actor?: string; action?: string; from?: string; to?: string; importBatchId?: string; candidateId?: string; songId?: string; arrangementId?: string; moderationFlagId?: string; rollbackRequestId?: string; page: number;
};

export type AuditEvent = {
    id: string; entityId: string; entityType: string; action: string; actor: string; occurredAt: string; targetLabel?: string; correlationId?: string; causationId?: string; auditReferenceId?: string; rollbackRequestId?: string; redactedSummary?: string; payloadSummary?: string; relatedLinks?: Array<{ label: string; href: string }>;
};
export type AuditHistoryResponse = { items: AuditEvent[]; totalItems: number; page: number; totalPages: number };

export type RollbackPreviewRequest = { targetType: string; targetId: string; importBatchId?: string; reason?: string };
export type RollbackImpactedRecord = { entityType: string; entityId: string; status?: string; beforeStatus?: string; afterStatus?: string; eligibilityChange?: string; summary?: string };
export type RollbackPreviewResponse = { rollbackRequestId: string; previewId?: string; targetType: string; targetId: string; importBatchId?: string; eligibilityImpacted?: boolean; impactedRecords?: RollbackImpactedRecord[]; blockers?: string[]; conflicts?: string[]; irreversibleWarnings?: string[]; requiredPermissions?: string[]; expiresAt?: string; versionContext?: string; auditReferenceId?: string; actor?: string };
export type RollbackExecutionResponse = { rollbackRequestId: string; action: string; auditEventId: string; status?: string };

const params = (filters: Partial<AuditFilters>) => {
    const search = new URLSearchParams();
    Object.entries(filters).forEach(([key, value]) => { if (value !== undefined && value !== '') search.set(key, String(value)); });
    return search.toString();
};

export const parseAuditFilters = (search: string): AuditFilters => {
    const p = new URLSearchParams(search);
    return { event: p.get('event') ?? undefined, entityType: p.get('entityType') ?? undefined, entityId: p.get('entityId') ?? undefined, actor: p.get('actor') ?? undefined, action: p.get('action') ?? undefined, from: p.get('from') ?? undefined, to: p.get('to') ?? undefined, importBatchId: p.get('importBatchId') ?? undefined, candidateId: p.get('candidateId') ?? undefined, songId: p.get('songId') ?? undefined, arrangementId: p.get('arrangementId') ?? undefined, moderationFlagId: p.get('moderationFlagId') ?? undefined, rollbackRequestId: p.get('rollbackRequestId') ?? undefined, page: Number(p.get('page') ?? '1') };
};
export const serializeAuditFilters = (filters: AuditFilters) => params(filters);
export const buildAuditHistoryPath = (filters: AuditFilters) => `/admin/audit-events?${serializeAuditFilters(filters)}`;
export const searchAuditEvents = (apiClient: AdminApiClient, filters: AuditFilters) => apiClient.request<AuditHistoryResponse>(buildAuditHistoryPath(filters));
export const createRollbackPreview = (apiClient: AdminApiClient, request: RollbackPreviewRequest, actorId: string) => apiClient.request<RollbackPreviewResponse>('/admin/rollback-previews', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request) }, { actorId });
export const executeRollback = (apiClient: AdminApiClient, rollbackRequestId: string, actorId: string, reason: string) => apiClient.request<RollbackExecutionResponse>('/admin/rollbacks', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ rollbackRequestId, actor: actorId, reason, confirmation: rollbackRequestId }) }, { actorId });

export const redactAuditSummary = (value?: string) => (value ?? 'Redacted summary unavailable').replace(/(secret|token|password|rawPayload|connectorPayload|lyrics)[:=][^\s,;]+/gi, '$1=[redacted]');
