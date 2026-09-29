import type { AdminApiClient } from './generated/cadentia-api/client';

export type MusicBrainzField = 'CANONICAL_TITLE' | 'ORIGINAL_ARTIST_DISPLAY' | 'ARRANGEMENT_NAME' | 'MUSICBRAINZ_LINKAGE';

export type MusicBrainzCandidate = {
    proposalId: string;
    status: string;
    score: number;
    artist?: { mbid: string; name: string; sortName?: string | null; type?: string | null; country?: string | null } | null;
    releaseGroup?: { mbid: string; title: string; primaryType?: string | null; firstReleaseDate?: string | null } | null;
    release?: { mbid: string; title: string; status?: string | null; date?: string | null; country?: string | null } | null;
    recording: { mbid: string; title: string; lengthMilliseconds?: number | null; discNumber?: number | null; trackNumber?: number | null; isrcs: string[]; sourceUri: string };
    warnings: string[];
    sourceReferences: string[];
};

export type MusicBrainzState = {
    enabled: boolean;
    acceptedProposalId?: string | null;
    proposals: MusicBrainzCandidate[];
};

export type MusicBrainzSearchResponse = { enabled: boolean; cacheHit: boolean; proposals: MusicBrainzCandidate[] };
export type MusicBrainzPreview = {
    proposalId: string;
    arrangementId?: string | null;
    selectedFields: MusicBrainzField[];
    fields: Array<{ field: MusicBrainzField; current?: string | null; proposed?: string | null; conflict: boolean }>;
    warnings: string[];
    etag: string;
};

const songPath = (songId: string, suffix = '') => `/admin/songs/${encodeURIComponent(songId)}/musicbrainz${suffix}`;

export const getMusicBrainzState = (client: AdminApiClient, songId: string) =>
    client.request<MusicBrainzState>(songPath(songId));

export const searchMusicBrainz = (
    client: AdminApiClient,
    songId: string,
    request: { title: string; artist?: string; album?: string; recordingMbid?: string; limit?: number },
) => client.request<MusicBrainzSearchResponse>(songPath(songId, '/search'), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
});

export const previewMusicBrainz = (
    client: AdminApiClient,
    songId: string,
    proposalId: string,
    request: { arrangementId?: string; selectedFields: MusicBrainzField[] },
) => client.request<MusicBrainzPreview>(`${songPath(songId)}/proposals/${encodeURIComponent(proposalId)}:preview`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
});

export const acceptMusicBrainz = (
    client: AdminApiClient,
    songId: string,
    proposalId: string,
    request: { actor: string; arrangementId?: string; selectedFields: MusicBrainzField[]; rationale: string },
    etag: string,
) => client.request<MusicBrainzState>(`${songPath(songId)}/proposals/${encodeURIComponent(proposalId)}:accept`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
}, { actorId: request.actor, etag });

export const rejectMusicBrainz = (
    client: AdminApiClient,
    songId: string,
    proposalId: string,
    request: { actor: string; rationale: string },
    etag: string,
) => client.request<MusicBrainzState>(`${songPath(songId)}/proposals/${encodeURIComponent(proposalId)}:reject`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
}, { actorId: request.actor, etag });
