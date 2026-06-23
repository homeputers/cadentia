import type { AdminApiClient } from './generated/cadentia-api/client';

export type CandidateDetail = {
    candidateId: string;
    importBatchId: string;
    connectorKey: string;
    rawTitle: string;
    normalizedTitle?: string | null;
    sourceArtistName?: string | null;
    status: string;
    allowedActions: string[];
    version: number;
    etag: string;
    rawSourceReference?: string | null;
    sourcePayloadJson?: string | null;
    sourcePayloadRedacted?: boolean;
    parserName?: string | null;
    parserVersion?: string | null;
    parserConfidence?: string | number | null;
    parserWarnings?: string[];
    parserEvidence?: ParserEvidence | null;
    eligibilityBlockers: string[];
    duplicateSummary: DuplicateSummary;
    provenanceReferences: ProvenanceReference[];
    duplicateMatches: DuplicateMatch[];
    reviewNotes: ReviewNote[];
    reviewHistory: ReviewHistoryItem[];
    relatedAuditReferences?: string[];
};

export type ProvenanceReference = { label: string; sourceReference: string; fingerprint?: string | null; status?: string | null };
export type ParserEvidence = { parserName?: string | null; parserVersion?: string | null; confidence?: number | null; severity?: string | null; warnings?: string[]; evidenceReferences?: string[] };
export type DuplicateSummary = { confidence?: string | null; matchCount?: number; topScore?: number | null; summary?: string | null };
export type DuplicateMatch = { id: string; candidateSongId: string; matchScore: number; matchSignalsJson?: string | null; status: string };
export type ReviewNote = { noteId: string; authorId: string; authorDisplayName?: string | null; category?: string | null; body: string; createdAt: string; auditReferenceId?: string | null };
export type ReviewHistoryItem = { id: string; proposedDuplicateMatchId?: string | null; decision: string; reviewer: string; reviewNotes?: string | null; reviewedAt: string };
export type CreateReviewNoteRequest = { actor: string; category?: string; body: string };

const candidatePath = (candidateId: string, suffix = '') => `/admin/import-candidates/${encodeURIComponent(candidateId)}${suffix}`;

export const getCandidateDetail = (client: AdminApiClient, candidateId: string) =>
    client.request<CandidateDetail>(candidatePath(candidateId));

export const createCandidateReviewNote = (client: AdminApiClient, candidateId: string, request: CreateReviewNoteRequest, actorId: string, etag: string) =>
    client.request<ReviewNote>(candidatePath(candidateId, '/notes'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(request),
    }, { actorId, etag });

export const safeParserEvidence = (detail: CandidateDetail): ParserEvidence => ({
    parserName: detail.parserEvidence?.parserName ?? detail.parserName,
    parserVersion: detail.parserEvidence?.parserVersion ?? detail.parserVersion,
    confidence: detail.parserEvidence?.confidence ?? (typeof detail.parserConfidence === 'number' ? detail.parserConfidence : Number.parseFloat(detail.parserConfidence ?? '')),
    severity: detail.parserEvidence?.severity,
    warnings: detail.parserEvidence?.warnings ?? detail.parserWarnings ?? [],
    evidenceReferences: detail.parserEvidence?.evidenceReferences ?? [],
});

export const hasVisibleWarnings = (detail: CandidateDetail) => (safeParserEvidence(detail).warnings ?? []).length > 0 || detail.eligibilityBlockers.length > 0 || detail.duplicateSummary.confidence === 'HIGH';
