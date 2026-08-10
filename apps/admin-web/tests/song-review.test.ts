import { describe, expect, it, vi } from 'vitest';
import { listReviewSongs, parseSongReviewFilters, serializeSongReviewFilters } from '../src/song-review';
import type { AdminApiClient } from '../src/generated/cadentia-api/client';

describe('song review API adapter', () => {
    it('normalizes stale admin song sort params into CatalogSearch enum values', async () => {
        const filters = parseSongReviewFilters('?query=Amazing&sort=updatedAt:desc&pageSize=25');
        expect(filters).toMatchObject({ query: 'Amazing', sort: 'UPDATED_AT', pageSize: 25 });
        expect(serializeSongReviewFilters(filters)).toBe('query=Amazing&sort=UPDATED_AT&pageSize=25');

        const request = vi.fn().mockResolvedValue({
            results: [],
            pagination: { pageSize: 25, hasMore: false },
        });
        await listReviewSongs({ getAdminSession: vi.fn(), request } as unknown as AdminApiClient, filters);

        expect(request).toHaveBeenCalledWith('/catalog/search', expect.objectContaining({
            method: 'POST',
            body: expect.stringContaining('"sort":"UPDATED_AT"'),
        }));
        expect(request.mock.calls[0][1].body).not.toContain('updatedAt:desc');
    });

    it('falls back to the default CatalogSearch sort for unknown sort params', async () => {
        const filters = parseSongReviewFilters('?sort=reviewPriority:desc');

        expect(filters.sort).toBe('RELEVANCE');
    });
});
