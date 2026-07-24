import type { AdminApiClient } from './generated/cadentia-api/client';

export type AllowedAction = 'VIEW' | 'CREATE' | 'UPDATE' | 'PREVIEW' | 'CONFIRM' | 'ASSIGN' | 'RESOLVE' | 'ESCALATE' | 'ROLLBACK';
export type AuditReference = { auditEventId: string; occurredAt: string; actorId?: string; reason?: string | null };
export type Concurrency = { version: number; etag: string };

export type RecommendationDiagnostic = {
    recommendationId: string;
    generatedAt: string;
    scoringInputs: Array<{ name: string; value: string | number | boolean }>;
    reasonCodes: string[];
    eligibilityBlockers: string[];
    policyVersion: string;
    readModelFreshness: { readModel: string; updatedAt: string; lagSeconds?: number };
    cacheStatus: string;
    correlationId: string;
    traceId?: string;
    auditReference?: AuditReference;
};
export type DiagnosticsResponse = {
    churchInstanceId: string;
    generatedAt: string;
    capabilityEnabled?: boolean;
    recommendations?: RecommendationDiagnostic[];
    components?: Array<{ name: string; status: string; summary?: string; redactionApplied: boolean; lastCheckedAt?: string }>;
};
export type InstanceConfiguration = {
    churchInstanceId: string;
    displayName: string;
    defaultLocale: string;
    timeZone: string;
    diagnosticsEnabled?: boolean;
    botChannelsEnabled?: boolean;
    connectors?: Array<{ key: string; label: string; enabled: boolean; status: string; credentialState?: string }>;
    botChannels?: Array<{ channelId: string; label: string; enabled: boolean; status: string }>;
    scoringProfiles?: Array<{ profileKey: string; label: string; active: boolean; policyVersion: string }>;
    operationalSettings?: Array<{ key: string; label: string; value: string; editable: boolean }>;
    allowedActions: AllowedAction[];
    concurrency: Concurrency;
    lastAuditReference?: AuditReference;
};
export type FeatureFlag = { flagKey: string; description?: string; enabled: boolean; allowedActions: AllowedAction[]; concurrency: Concurrency; lastAuditReference?: AuditReference };
export type FeatureFlagList = { churchInstanceId: string; flags: FeatureFlag[] };
export type FeatureFlagPreview = { previewId: string; flagKey: string; requestedEnabled: boolean; confirmationRequired: boolean; impactSummary?: string; blockers: string[] };
export type UpdateInstanceConfigurationRequest = { displayName: string; defaultLocale: string; timeZone: string; diagnosticsEnabled?: boolean; botChannelsEnabled?: boolean; expectedVersion: number; actorId: string; reason: string };

export const getDiagnostics = (apiClient: AdminApiClient) => apiClient.request<DiagnosticsResponse>('/admin/diagnostics');
export const getInstanceConfiguration = (apiClient: AdminApiClient) => apiClient.request<InstanceConfiguration>('/admin/instance-configuration');
export const updateInstanceConfiguration = (apiClient: AdminApiClient, payload: UpdateInstanceConfigurationRequest, etag: string) =>
    apiClient.request<InstanceConfiguration>('/admin/instance-configuration', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) }, { actorId: payload.actorId, etag });
export const listFeatureFlags = (apiClient: AdminApiClient) => apiClient.request<FeatureFlagList>('/admin/feature-flags');
export const previewFeatureFlagChange = (apiClient: AdminApiClient, flag: FeatureFlag, enabled: boolean, actorId: string, reason: string) =>
    apiClient.request<FeatureFlagPreview>(`/admin/feature-flags/${encodeURIComponent(flag.flagKey)}:preview`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ enabled, expectedVersion: flag.concurrency.version, actorId, reason }) }, { actorId, etag: flag.concurrency.etag });
export const confirmFeatureFlagChange = (apiClient: AdminApiClient, flagKey: string, previewId: string, actorId: string, confirmationText: string) =>
    apiClient.request<FeatureFlag>(`/admin/feature-flags/${encodeURIComponent(flagKey)}:confirm`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ previewId, actorId, confirmationText }) }, { actorId });
