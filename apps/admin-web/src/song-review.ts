import type { AdminApiClient } from './generated/cadentia-api/client';

export type SongReviewFilterState = {
    query?: string;
    status: string;
    sort: string;
    page: number;
    pageSize: number;
};

export type CatalogSongSummary = {
    songId: string;
    canonicalTitle: string;
    normalizedTitle?: string | null;
    primaryLanguage: string;
    originalArtistDisplay?: string | null;
    composerCredits?: string | null;
    ccliNumber?: string | null;
    yearWritten?: number | null;
    songStatus: string;
    updatedAt: string;
    arrangementCount: number;
};

export type CatalogArrangement = {
    arrangementId: string;
    songId: string;
    name: string;
    normalizedName: string;
    sourceType: string;
    language: string;
    musicalKey?: string | null;
    keyMode?: string | null;
    tempoBpm?: number | null;
    timeSignature?: string | null;
    durationSeconds?: number | null;
    energyLevel?: number | null;
    difficultyLevel?: number | null;
    defaultForSong: boolean;
    active: boolean;
    updatedAt: string;
    lyricsDocuments: CatalogLyricsDocument[];
};

export type CatalogLyricsDocument = {
    lyricsDocumentId: string;
    arrangementId: string;
    format: string;
    content?: string | null;
    contentHash: string;
    versionNumber: number;
    current: boolean;
    containsChords: boolean;
    containsSections: boolean;
    sourceReference: string;
    createdBy: string;
    createdAt: string;
    parseStatus: string;
    parseError?: string | null;
};

export type CatalogProvenanceRecord = {
    provenanceId: string;
    arrangementId?: string | null;
    lyricsDocumentId?: string | null;
    sourceSystem: string;
    sourceUri?: string | null;
    sourceLabel: string;
    licenseType: string;
    licenseNotes?: string | null;
    importMethod: string;
    confidenceScore?: number | null;
    capturedAt: string;
};

export type CatalogApprovalRecord = {
    approvalId: string;
    arrangementId?: string | null;
    lyricsDocumentId?: string | null;
    approvalType: string;
    status: string;
    reviewer?: string | null;
    reviewNotes?: string | null;
    reviewedAt?: string | null;
    createdAt: string;
};

export type CatalogTag = {
    tagId: string;
    tagType: string;
    name: string;
    slug: string;
    description?: string | null;
    active: boolean;
};

export type SongReviewQueueResponse = {
    items: CatalogSongSummary[];
    page: number;
    pageSize: number;
    totalItems: number;
    totalPages: number;
    sort: string;
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
    song: CatalogSongSummary;
    doctrinalNotes?: string | null;
    arrangements: CatalogArrangement[];
    provenance: CatalogProvenanceRecord[];
    approvals: CatalogApprovalRecord[];
    tags: CatalogTag[];
    songAttachments: AssetAttachment[];
    arrangementAttachments: Record<string, AssetAttachment[]>;
};

export type SongMetadataDraft = {
    actor: string;
    canonicalTitle: string;
    normalizedTitle?: string | null;
    primaryLanguage: string;
    originalArtistDisplay?: string | null;
    composerCredits?: string | null;
    ccliNumber?: string | null;
    yearWritten?: number | null;
    songStatus: string;
    doctrinalNotes?: string | null;
    arrangements: ArrangementMetadataDraft[];
    lyricsDocuments: LyricsMetadataDraft[];
};

export type ArrangementMetadataDraft = Omit<CatalogArrangement, 'arrangementId' | 'songId' | 'updatedAt' | 'lyricsDocuments'> & {
    arrangementId?: string | null;
};
export type LyricsMetadataDraft = Pick<CatalogLyricsDocument, 'lyricsDocumentId' | 'format' | 'content' | 'containsChords' | 'containsSections' | 'sourceReference' | 'arrangementId'>;

export type AttachmentDraft = {
    targetType: 'song' | 'arrangement';
    targetId: string;
    assetVersionId: string;
    attachmentType: string;
    displayLabel: string;
    sortOrder: number;
    purpose: string;
    requiredForUse: boolean;
    visibilityPolicy: string;
};

export type AssetUploadInstructions = {
    uploadId: string;
    storageKey: string;
    uploadMethod: string;
    uploadUrl: string;
    expiresAt: string;
    expectedMimeType: string;
    expectedByteSize: number;
    checksumAlgorithm: string;
    checksumValue: string;
};

export type AssetVersion = {
    assetVersionId: string;
    assetId: string;
    mimeType: string;
    byteSize: number;
    checksumAlgorithm: string;
    checksumValue: string;
};

export const defaultSongReviewFilters: SongReviewFilterState = {
    status: 'ALL',
    sort: 'TITLE',
    page: 1,
    pageSize: 25,
};

const allowedSorts = new Set(['TITLE', 'UPDATED_AT']);
const allowedStatuses = new Set(['ALL', 'APPROVED', 'IN_REVIEW']);
const allowedKeys = new Set<keyof SongReviewFilterState>(['query', 'status', 'sort', 'page', 'pageSize']);

export const parseSongReviewFilters = (search: string): SongReviewFilterState => {
    const params = new URLSearchParams(search);
    const filters: SongReviewFilterState = { ...defaultSongReviewFilters };
    for (const [key, value] of params.entries()) {
        if (!allowedKeys.has(key as keyof SongReviewFilterState) || value.trim() === '') continue;
        if (key === 'page' || key === 'pageSize') {
            const parsed = Number.parseInt(value, 10);
            if (Number.isFinite(parsed) && parsed > 0) filters[key] = parsed;
        } else if (key === 'sort') {
            const normalized = value.toUpperCase();
            filters.sort = allowedSorts.has(normalized) ? normalized : defaultSongReviewFilters.sort;
        } else if (key === 'status') {
            const normalized = value.toUpperCase();
            filters.status = allowedStatuses.has(normalized) ? normalized : defaultSongReviewFilters.status;
        } else {
            filters.query = value.slice(0, 160);
        }
    }
    return filters;
};

export const serializeSongReviewFilters = (filters: SongReviewFilterState): string => {
    const params = new URLSearchParams();
    if (filters.query) params.set('query', filters.query);
    if (filters.status !== defaultSongReviewFilters.status) params.set('status', filters.status);
    if (filters.sort !== defaultSongReviewFilters.sort) params.set('sort', filters.sort);
    if (filters.page !== 1) params.set('page', String(filters.page));
    if (filters.pageSize !== defaultSongReviewFilters.pageSize) params.set('pageSize', String(filters.pageSize));
    return params.toString();
};

export const buildSongReviewQueuePath = (filters: SongReviewFilterState): string => {
    const query = serializeSongReviewFilters(filters);
    return `/admin/songs${query ? `?${query}` : ''}`;
};

export const listReviewSongs = async (client: AdminApiClient, filters: SongReviewFilterState): Promise<SongReviewQueueResponse> => {
    const params = new URLSearchParams({
        sort: filters.sort,
        page: String(filters.page),
        pageSize: String(filters.pageSize),
    });
    if (filters.status !== 'ALL') params.set('status', filters.status);
    if (filters.query) params.set('query', filters.query);
    return client.request<SongReviewQueueResponse>(`/admin/songs?${params.toString()}`);
};

export const listAssetAttachments = (client: AdminApiClient, targetType: 'song' | 'arrangement', targetId: string) => {
    const params = new URLSearchParams({ targetType, targetId });
    return client.request<AssetAttachment[]>(`/asset-attachments?${params.toString()}`);
};

export const createAssetAttachment = (client: AdminApiClient, draft: AttachmentDraft): Promise<AssetAttachment> => client.request<AssetAttachment>('/asset-attachments', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(draft),
});

export const uploadAndAttachResource = async (
    client: AdminApiClient,
    draft: AttachmentDraft,
    file: File,
): Promise<AssetAttachment> => {
    const mimeType = file.type || 'application/octet-stream';
    const checksumValue = await sha256Hex(file);
    const instructions = await client.request<AssetUploadInstructions>('/assets/uploads', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            assetType: draft.attachmentType,
            title: draft.displayLabel,
            expectedMimeType: mimeType,
            expectedByteSize: file.size,
            checksumAlgorithm: 'SHA-256',
            checksumValue,
            accessPolicy: draft.visibilityPolicy,
            licensing: {
                licenseStatus: 'unknown',
                privateFieldsVisible: false,
                visibilityPolicy: draft.visibilityPolicy,
            },
        }),
    });
    const uploadResponse = await fetch(instructions.uploadUrl, {
        method: instructions.uploadMethod,
        headers: { 'Content-Type': mimeType },
        body: file,
    });
    if (!uploadResponse.ok) {
        throw new Error(`Asset upload failed with status ${uploadResponse.status}`);
    }
    const version = await client.request<AssetVersion>(`/assets/uploads/${encodeURIComponent(instructions.uploadId)}/finalize`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            storageKey: instructions.storageKey,
            checksumAlgorithm: 'SHA-256',
            checksumValue,
            mimeType,
            byteSize: file.size,
        }),
    });
    return createAssetAttachment(client, { ...draft, assetVersionId: version.assetVersionId });
};

const sha256Hex = async (file: File): Promise<string> => {
    const digest = await crypto.subtle.digest('SHA-256', await fileToHashSource(file));
    return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, '0')).join('');
};

const fileToHashSource = async (file: File): Promise<BufferSource> => {
    const content = await fileToArrayBuffer(file);
    if (content instanceof ArrayBuffer) {
        return content;
    }
    if (ArrayBuffer.isView(content)) {
        return content;
    }
    return new Uint8Array(content);
};

const fileToArrayBuffer = (file: File): Promise<ArrayBuffer | ArrayBufferView> => {
    if ('arrayBuffer' in file && typeof file.arrayBuffer === 'function') {
        return file.arrayBuffer();
    }
    return new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(reader.result as ArrayBuffer);
        reader.onerror = () => reject(reader.error);
        reader.readAsArrayBuffer(file);
    });
};

export const getReviewSong = async (client: AdminApiClient, songId: string): Promise<SongReviewDetail> => {
    const detail = await client.request<Omit<SongReviewDetail, 'songAttachments' | 'arrangementAttachments'>>(`/admin/songs/${encodeURIComponent(songId)}`);
    const songAttachments = await listAssetAttachments(client, 'song', songId);
    const arrangementPairs = await Promise.all(detail.arrangements.map(async (arrangement) => [
        arrangement.arrangementId,
        await listAssetAttachments(client, 'arrangement', arrangement.arrangementId),
    ] as const));
    return {
        ...detail,
        songAttachments,
        arrangementAttachments: Object.fromEntries(arrangementPairs),
    };
};

export const toMetadataDraft = (detail: SongReviewDetail, actor: string): SongMetadataDraft => ({
    actor,
    canonicalTitle: detail.song.canonicalTitle,
    normalizedTitle: detail.song.normalizedTitle,
    primaryLanguage: detail.song.primaryLanguage,
    originalArtistDisplay: detail.song.originalArtistDisplay,
    composerCredits: detail.song.composerCredits,
    ccliNumber: detail.song.ccliNumber,
    yearWritten: detail.song.yearWritten,
    songStatus: detail.song.songStatus,
    doctrinalNotes: detail.doctrinalNotes,
    arrangements: detail.arrangements.map(({ arrangementId, name, normalizedName, sourceType, language, musicalKey, keyMode, tempoBpm, timeSignature, durationSeconds, energyLevel, difficultyLevel, defaultForSong, active }) => ({
        arrangementId,
        name,
        normalizedName,
        sourceType,
        language,
        musicalKey,
        keyMode,
        tempoBpm,
        timeSignature,
        durationSeconds,
        energyLevel,
        difficultyLevel,
        defaultForSong,
        active,
    })),
    lyricsDocuments: detail.arrangements.flatMap((arrangement) => arrangement.lyricsDocuments.filter((lyrics) => lyrics.current).map((lyrics) => ({
        lyricsDocumentId: lyrics.lyricsDocumentId,
        arrangementId: lyrics.arrangementId,
        format: lyrics.format,
        content: lyrics.content ?? '',
        containsChords: lyrics.containsChords,
        containsSections: lyrics.containsSections,
        sourceReference: lyrics.sourceReference,
    }))),
});

export type SongTagAssignmentDraft = {
    actor: string;
    tagType: string;
    name: string;
    description?: string | null;
};

export const CONTROLLED_TAG_TYPES = ['THEME', 'SCRIPTURE', 'MOOD', 'OCCASION', 'SEASON', 'MUSICAL_STYLE', 'AUDIENCE'] as const;

export const assignSongTag = (client: AdminApiClient, songId: string, draft: SongTagAssignmentDraft): Promise<CatalogTag> => client.request<CatalogTag>(`/admin/songs/${encodeURIComponent(songId)}/tags`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
        actor: draft.actor,
        tagType: draft.tagType,
        name: draft.name,
        description: draft.description ?? null,
    }),
});

export const removeSongTag = (client: AdminApiClient, songId: string, tagId: string, actor: string): Promise<void> => client.request<void>(`/admin/songs/${encodeURIComponent(songId)}/tags/${encodeURIComponent(tagId)}?${new URLSearchParams({ actor }).toString()}`, {
    method: 'DELETE',
});

export const updateReviewSong = (client: AdminApiClient, songId: string, draft: SongMetadataDraft): Promise<Omit<SongReviewDetail, 'songAttachments' | 'arrangementAttachments'>> => client.request<Omit<SongReviewDetail, 'songAttachments' | 'arrangementAttachments'>>(`/admin/songs/${encodeURIComponent(songId)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
        ...draft,
        arrangements: draft.arrangements.map((arrangement) => {
            if (!arrangement.arrangementId?.startsWith('new-')) {
                return arrangement;
            }
            const { arrangementId: _arrangementId, ...newArrangement } = arrangement;
            return newArrangement;
        }),
        lyricsDocuments: draft.lyricsDocuments.map((lyrics) => {
            if (!lyrics.lyricsDocumentId?.startsWith('new-')) {
                return lyrics;
            }
            return { ...lyrics, lyricsDocumentId: null };
        }),
    }),
});
