import type { AdminApiClient } from './generated/cadentia-api/client';

export type ImportCandidateFilterState = {
    status?: string;
    connectorKey?: string;
    batchId?: string;
    submittedFrom?: string;
    submittedTo?: string;
    assignedReviewerId?: string;
    parserSeverity?: string;
    provenanceStatus?: string;
    duplicateConfidence?: string;
    moderationState?: string;
    reviewPriority?: string;
    sort: string;
    page: number;
    pageSize: number;
};

export type ImportCandidateQueueItem = {
    candidateId: string;
    importBatchId: string;
    connectorKey: string;
    rawTitle: string;
    normalizedTitle?: string | null;
    sourceArtistName?: string | null;
    status: string;
    submittedAt: string;
    updatedAt: string;
    assignedReviewerId?: string | null;
    assignedReviewerName?: string | null;
    parserSeverity: string;
    parserConfidence?: number | null;
    parserWarningCount?: number;
    provenanceStatus: string;
    provenanceSummary?: string | null;
    duplicateConfidence: string;
    duplicateMatchCount?: number;
    duplicateTopScore?: number | null;
    moderationState: string;
    reviewPriority: string;
    approvalReadiness: string;
    readinessSummary?: string | null;
    allowedActions: string[];
    auditReferenceId?: string | null;
};

export type ImportCandidateQueueResponse = {
    items: ImportCandidateQueueItem[];
    page: number;
    pageSize: number;
    totalItems: number;
    totalPages: number;
    sort: string;
};

export const defaultImportCandidateFilters: ImportCandidateFilterState = {
    sort: 'submittedAt:desc',
    page: 1,
    pageSize: 25,
};

const allowedKeys = new Set<keyof ImportCandidateFilterState>([
    'status', 'connectorKey', 'batchId', 'submittedFrom', 'submittedTo', 'assignedReviewerId',
    'parserSeverity', 'provenanceStatus', 'duplicateConfidence', 'moderationState', 'reviewPriority',
    'sort', 'page', 'pageSize',
]);

export const parseImportCandidateFilters = (search: string): ImportCandidateFilterState => {
    const params = new URLSearchParams(search);
    const filters: ImportCandidateFilterState = { ...defaultImportCandidateFilters };
    for (const [key, value] of params.entries()) {
        if (!allowedKeys.has(key as keyof ImportCandidateFilterState) || value.trim() === '') continue;
        if (key === 'page' || key === 'pageSize') {
            const parsed = Number.parseInt(value, 10);
            if (Number.isFinite(parsed) && parsed > 0) filters[key] = parsed;
        } else {
            filters[key as Exclude<keyof ImportCandidateFilterState, 'page' | 'pageSize'>] = value.slice(0, 120);
        }
    }
    return filters;
};

export const serializeImportCandidateFilters = (filters: ImportCandidateFilterState): string => {
    const params = new URLSearchParams();
    for (const key of allowedKeys) {
        const value = filters[key];
        if (value === undefined || value === '' || value === defaultImportCandidateFilters[key as 'sort']) continue;
        if (key === 'page' && value === 1) continue;
        if (key === 'pageSize' && value === 25) continue;
        params.set(key, String(value));
    }
    return params.toString();
};

export const buildImportCandidateQueuePath = (filters: ImportCandidateFilterState): string => {
    const query = serializeImportCandidateFilters(filters);
    return `/admin/import-candidates${query ? `?${query}` : ''}`;
};

export const listImportCandidates = (client: AdminApiClient, filters: ImportCandidateFilterState) =>
    client.request<ImportCandidateQueueResponse>(buildImportCandidateQueuePath(filters));

export const isBlockedCandidate = (candidate: ImportCandidateQueueItem): boolean =>
    candidate.approvalReadiness === 'BLOCKED' || candidate.moderationState === 'BLOCKED' || candidate.provenanceStatus === 'BLOCKED';
