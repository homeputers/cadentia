import { afterEach, describe, expect, it, vi } from 'vitest';
import { listReviewSongs, parseSongReviewFilters, serializeSongReviewFilters, updateReviewSong, uploadAndAttachResource } from '../src/song-review';
import type { AdminApiClient } from '../src/generated/cadentia-api/client';

describe('song review API adapter', () => {
    afterEach(() => {
        vi.unstubAllGlobals();
        vi.restoreAllMocks();
    });

    it('uses all statuses by default and omits the backend status parameter', async () => {
        const filters = parseSongReviewFilters('?query=Amazing&pageSize=25');
        expect(filters).toMatchObject({ query: 'Amazing', status: 'ALL', sort: 'TITLE', pageSize: 25 });
        expect(serializeSongReviewFilters(filters)).toBe('query=Amazing');

        const request = vi.fn().mockResolvedValue({
            items: [],
            page: 1,
            pageSize: 25,
            totalItems: 0,
            totalPages: 0,
            sort: 'TITLE',
        });
        await listReviewSongs({ getAdminSession: vi.fn(), request } as unknown as AdminApiClient, filters);

        const requestPath = request.mock.calls[0][0] as string;
        expect(requestPath).toContain('/admin/songs?');
        expect(requestPath).toContain('query=Amazing');
        expect(requestPath).not.toContain('status=');
    });

    it('serializes concrete status filters', async () => {
        const filters = parseSongReviewFilters('?status=approved&sort=UPDATED_AT');

        expect(filters.status).toBe('APPROVED');
        expect(serializeSongReviewFilters(filters)).toBe('status=APPROVED&sort=UPDATED_AT');
    });

    it('falls back to all statuses for unsupported status params', async () => {
        const filters = parseSongReviewFilters('?status=REJECTED');

        expect(filters.status).toBe('ALL');
    });

    it('uploads, finalizes, and attaches a resource file', async () => {
        const request = vi.fn(async (path: string, init?: RequestInit) => {
            if (path === '/assets/uploads') {
                expect(init?.method).toBe('POST');
                expect(String(init?.body)).toContain('"assetType":"pdf"');
                return {
                    uploadId: '11111111-1111-4111-8111-111111111111',
                    storageKey: 'processing/test.pdf',
                    uploadMethod: 'PUT',
                    uploadUrl: 'https://uploads.example.test/test.pdf',
                    expiresAt: '2026-08-11T17:00:00Z',
                    expectedMimeType: 'application/pdf',
                    expectedByteSize: 5,
                    checksumAlgorithm: 'SHA-256',
                    checksumValue: 'ignored',
                };
            }
            if (path === '/assets/uploads/11111111-1111-4111-8111-111111111111/finalize') {
                expect(init?.method).toBe('POST');
                expect(String(init?.body)).toContain('"storageKey":"processing/test.pdf"');
                return {
                    assetVersionId: '22222222-2222-4222-8222-222222222222',
                    assetId: '33333333-3333-4333-8333-333333333333',
                    mimeType: 'application/pdf',
                    byteSize: 5,
                    checksumAlgorithm: 'SHA-256',
                    checksumValue: 'ignored',
                };
            }
            if (path === '/asset-attachments') {
                expect(init?.method).toBe('POST');
                expect(String(init?.body)).toContain('"assetVersionId":"22222222-2222-4222-8222-222222222222"');
                return { attachmentId: '44444444-4444-4444-8444-444444444444' };
            }
            throw new Error(`Unexpected request ${path}`);
        });
        const fetch = vi.fn().mockResolvedValue({ ok: true });
        vi.stubGlobal('fetch', fetch);

        const file = {
            name: 'lead-sheet.pdf',
            type: 'application/pdf',
            size: 5,
            arrayBuffer: async () => new Uint8Array([104, 101, 108, 108, 111]),
        } as unknown as File;

        await uploadAndAttachResource({ getAdminSession: vi.fn(), request } as unknown as AdminApiClient, {
            targetType: 'song',
            targetId: '55555555-5555-4555-8555-555555555555',
            assetVersionId: '',
            attachmentType: 'pdf',
            displayLabel: 'Lead sheet',
            sortOrder: 0,
            purpose: 'reference',
            requiredForUse: false,
            visibilityPolicy: 'catalog_reviewers',
        }, file);

        expect(fetch).toHaveBeenCalledWith('https://uploads.example.test/test.pdf', expect.objectContaining({
            method: 'PUT',
        }));
        expect(request).toHaveBeenCalledTimes(3);
    });

    it('omits temporary arrangement ids when saving new arrangements', async () => {
        const request = vi.fn().mockResolvedValue({});

        await updateReviewSong({ getAdminSession: vi.fn(), request } as unknown as AdminApiClient, 'song-1', {
            actor: 'editor-1',
            canonicalTitle: 'Song',
            primaryLanguage: 'en',
            songStatus: 'IN_REVIEW',
            arrangements: [{
                arrangementId: 'new-client-id',
                name: 'Acoustic',
                normalizedName: '',
                sourceType: 'ACOUSTIC',
                language: 'en',
                musicalKey: 'G',
                keyMode: null,
                tempoBpm: 92,
                timeSignature: null,
                durationSeconds: null,
                energyLevel: null,
                difficultyLevel: null,
                defaultForSong: false,
                active: true,
            }],
            lyricsDocuments: [],
        });

        const body = JSON.parse(String(request.mock.calls[0][1].body));
        expect(body.arrangements[0].arrangementId).toBeUndefined();
        expect(body.arrangements[0].name).toBe('Acoustic');
    });
});
