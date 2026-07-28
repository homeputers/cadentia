import type { AdminApiClient } from './generated/cadentia-api/client';

export type SongResourceDraft = {
    resourceType: string;
    title: string;
    url?: string;
    assetId?: string;
    notes?: string;
};

export type ManualSongImportDraft = {
    title: string;
    author?: string;
    artist?: string;
    ccliNumber?: string;
    copyright?: string;
    publisher?: string;
    language?: string;
    key?: string;
    bpm?: number;
    timeSignature?: string;
    energy?: number;
    difficulty?: number;
    themes: string[];
    scriptureReferences: string[];
    lyrics?: string;
    chordChart?: string;
    arrangementNotes?: string;
    sourceReference?: string;
    licenseType: string;
    licenseEvidence?: string;
    resources: SongResourceDraft[];
};

export type CsvSongImportDraft = {
    file: File;
    licenseType: string;
    licenseEvidence?: string;
};

export type SongImportValidationError = {
    rowIdentifier: string;
    field: string;
    message: string;
};

export type SongImportCandidateSummary = {
    candidateId: string;
    rawTitle: string;
    normalizedTitle?: string | null;
    sourceArtistName?: string | null;
    status: string;
};

export type SongImportResponse = {
    importBatchId: string;
    status: string;
    method: string;
    acceptedCount: number;
    validationErrorCount: number;
    candidateIds: string[];
    candidates: SongImportCandidateSummary[];
    validationErrors: SongImportValidationError[];
};

const jsonRequest = <T>(client: AdminApiClient, path: string, body: unknown) =>
    client.request<T>(path, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
    });

export const createManualSongImport = (client: AdminApiClient, actor: string, draft: ManualSongImportDraft) =>
    jsonRequest<SongImportResponse>(client, '/admin/song-imports/manual', { actor, ...draft });

export const createCsvSongImport = (client: AdminApiClient, actor: string, draft: CsvSongImportDraft) => {
    const body = new FormData();
    body.set('actor', actor);
    body.set('file', draft.file);
    body.set('licenseType', draft.licenseType);
    if (draft.licenseEvidence) {
        body.set('licenseEvidence', draft.licenseEvidence);
    }
    return client.request<SongImportResponse>('/admin/song-imports/csv', {
        method: 'POST',
        body,
    });
};

export const splitEntryList = (value: string): string[] =>
    value.split(/[;|]/).map((item) => item.trim()).filter(Boolean);
