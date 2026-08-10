import type { AdminApiClient } from './generated/cadentia-api/client';

export type SongReviewFilterState = {
    query?: string;
    tag?: string;
    contributor?: string;
    key?: string;
    scriptureReference?: string;
    sort: string;
    page: number;
    pageSize: number;
};

export type CatalogSearchResult = {
    id: string;
    songId?: string;
    arrangementId?: string;
    resultType: string;
    title: string;
    subtitle?: string;
    score: number;
    matchedFields?: Array<{ field?: string; value?: string }>;
    rankingFactors?: Array<{ code?: string; contribution?: number; visibility?: string }>;
    hydration?: { href?: string; available?: boolean };
};

export type SongReviewQueueResponse = {
    items: CatalogSearchResult[];
    page: number;
    pageSize: number;
    totalItems: number;
    totalPages: number;
    sort: string;
    nextCursor?: string;
    hasMore: boolean;
};

export type AssetAttachment = {
    attachmentId: string;
    targetType: string;
    targetId: string;
    servicePlanId?: string | null;
    assetVersionId: string;
    attachmentType: string;
    displayLabel: string;
    sortOrder: number;
    purpose: string;
    requiredForUse: boolean;
    visibilityPolicy: string;
    createdAt: string;
    archivedAt?: string | null;
};

export type SongReviewDetail = {
    songId: string;
    arrangementId?: string | null;
    title?: string | null;
    subtitle?: string | null;
    hydrationHref?: string | null;
    songAttachments: AssetAttachment[];
    arrangementAttachments: AssetAttachment[];
};

export const defaultSongReviewFilters: SongReviewFilterState = {
    sort: 'RELEVANCE',
    page: 1,
    pageSize: 20,
};

const allowedSorts = new Set(['RELEVANCE', 'TITLE', 'UPDATED_AT']);
const legacySortMap: Record<string, string> = {
    'updatedAt:desc': 'UPDATED_AT',
    'updatedAt:asc': 'UPDATED_AT',
    'title:asc': 'TITLE',
    'title:desc': 'TITLE',
    'lastReviewedAt:desc': 'UPDATED_AT',
    'approvalStatus:asc': 'RELEVANCE',
};

const allowedKeys = new Set<keyof SongReviewFilterState>([
    'query',
    'tag',
    'contributor',
    'key',
    'scriptureReference',
    'sort',
    'page',
    'pageSize',
]);

export const parseSongReviewFilters = (search: string): SongReviewFilterState => {
    const params = new URLSearchParams(search);
    const filters: SongReviewFilterState = { ...defaultSongReviewFilters };
    for (const [key, value] of params.entries()) {
        if (!allowedKeys.has(key as keyof SongReviewFilterState) || value.trim() === '') continue;
        if (key === 'page' || key === 'pageSize') {
            const parsed = Number.parseInt(value, 10);
            if (Number.isFinite(parsed) && parsed > 0) filters[key] = parsed;
        } else if (key === 'sort') {
            const normalizedSort = legacySortMap[value] ?? value.toUpperCase();
            filters.sort = allowedSorts.has(normalizedSort) ? normalizedSort : defaultSongReviewFilters.sort;
        } else {
            filters[key as Exclude<keyof SongReviewFilterState, 'page' | 'pageSize'>] = value.slice(0, 160);
        }
    }
    return filters;
};

export const serializeSongReviewFilters = (filters: SongReviewFilterState): string => {
    const params = new URLSearchParams();
    for (const key of allowedKeys) {
        const value = filters[key];
        if (value === undefined || value === '' || value === defaultSongReviewFilters[key as 'sort']) continue;
        if (key === 'page' && value === 1) continue;
        if (key === 'pageSize' && value === defaultSongReviewFilters.pageSize) continue;
        params.set(key, String(value));
    }
    return params.toString();
};

export const buildSongReviewQueuePath = (filters: SongReviewFilterState): string => {
    const query = serializeSongReviewFilters(filters);
    return `/admin/songs${query ? `?${query}` : ''}`;
};

const cursorForPage = (page: number, pageSize: number): string | undefined => {
    const offset = Math.max(0, page - 1) * pageSize;
    return offset > 0 ? String(offset) : undefined;
};

export const listReviewSongs = async (client: AdminApiClient, filters: SongReviewFilterState): Promise<SongReviewQueueResponse> => {
    const sort = allowedSorts.has(filters.sort) ? filters.sort : defaultSongReviewFilters.sort;
    const body = {
        query: filters.query || undefined,
        filters: {
            tags: filters.tag ? [filters.tag] : undefined,
            contributors: filters.contributor ? [filters.contributor] : undefined,
            keys: filters.key ? [filters.key] : undefined,
            scriptureReferences: filters.scriptureReference ? [filters.scriptureReference] : undefined,
        },
        pagination: {
            pageSize: filters.pageSize,
            cursor: cursorForPage(filters.page, filters.pageSize),
        },
        sort,
        includeExplanations: true,
        includeFacets: true,
        includeDiagnostics: false,
    };
    const response = await client.request<{
        results: CatalogSearchResult[];
        pagination?: { pageSize?: number; nextCursor?: string | null; hasMore?: boolean };
    }>('/catalog/search', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
    });
    const items = response.results ?? [];
    return {
        items,
        page: filters.page,
        pageSize: response.pagination?.pageSize ?? filters.pageSize,
        totalItems: items.length,
        totalPages: response.pagination?.hasMore ? filters.page + 1 : filters.page,
        sort,
        nextCursor: response.pagination?.nextCursor ?? undefined,
        hasMore: Boolean(response.pagination?.hasMore),
    };
};

export const listAssetAttachments = (client: AdminApiClient, targetType: 'song' | 'arrangement', targetId: string) => {
    const params = new URLSearchParams({ targetType, targetId });
    return client.request<AssetAttachment[]>(`/asset-attachments?${params.toString()}`);
};

export const getReviewSong = async (
    client: AdminApiClient,
    songId: string,
    context: { title?: string | null; subtitle?: string | null; arrangementId?: string | null; hydrationHref?: string | null } = {},
): Promise<SongReviewDetail> => {
    const songAttachments = await listAssetAttachments(client, 'song', songId);
    const arrangementAttachments = context.arrangementId
        ? await listAssetAttachments(client, 'arrangement', context.arrangementId)
        : [];
    return {
        songId,
        arrangementId: context.arrangementId,
        title: context.title,
        subtitle: context.subtitle,
        hydrationHref: context.hydrationHref,
        songAttachments,
        arrangementAttachments,
    };
};
