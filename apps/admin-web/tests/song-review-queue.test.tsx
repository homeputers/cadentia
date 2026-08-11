import React from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { act } from 'react-dom/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { AdminSession } from '../src/auth/session';
import type { AdminApiClient } from '../src/generated/cadentia-api/client';
import { SongReviewQueue } from '../src/routes/SongReviewQueue';

let container: HTMLDivElement;
let root: Root;

const session: AdminSession = {
    actorId: 'reviewer-1',
    displayName: 'Reviewer One',
    churchInstanceId: 'church-1',
    roles: ['CATALOG_EDITOR'],
    capabilities: ['REVIEW_CATALOG'],
};

const searchResponse = {
    items: [{
        songId: '55555555-5555-4555-8555-555555555555',
        canonicalTitle: 'Reviewed Song',
        normalizedTitle: 'reviewed song',
        primaryLanguage: 'en',
        originalArtistDisplay: 'Reviewer Band',
        composerCredits: null,
        ccliNumber: null,
        yearWritten: null,
        songStatus: 'APPROVED',
        updatedAt: '2026-08-11T17:00:00Z',
        arrangementCount: 1,
    }],
    page: 1,
    pageSize: 25,
    totalItems: 1,
    totalPages: 1,
    sort: 'TITLE',
};

const render = async (apiClient: AdminApiClient, initialSearch = '') => {
    container = document.createElement('div');
    document.body.appendChild(container);
    await act(async () => {
        root = createRoot(container);
        root.render(<SongReviewQueue session={session} apiClient={apiClient} initialSearch={initialSearch} />);
    });
    await act(async () => { await Promise.resolve(); });
    return container;
};

const typeValue = (input: HTMLInputElement, value: string) => {
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(input, value);
    input.dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'insertText', data: value.slice(-1) }));
};

afterEach(() => {
    act(() => { root?.unmount(); });
    container?.remove();
    vi.restoreAllMocks();
});

describe('song review queue', () => {
    it('loads all statuses by default and does not reload on each search keystroke', async () => {
        const request = vi.fn().mockResolvedValue(searchResponse);
        const node = await render({ getAdminSession: vi.fn(), request } as unknown as AdminApiClient);
        const search = [...node.querySelectorAll('input')].find((input) => input.name === 'query')!;
        const status = [...node.querySelectorAll('select')].find((select) => select.id.includes('status') || select.value === 'ALL')!;

        expect(status.textContent).toContain('All');
        expect(request).toHaveBeenCalledTimes(1);
        expect(request.mock.calls[0][0]).toBe('/admin/songs?sort=TITLE&page=1&pageSize=25');

        await act(async () => {
            typeValue(search, 'A');
            typeValue(search, 'Am');
            typeValue(search, 'Ama');
            await Promise.resolve();
        });

        expect(request).toHaveBeenCalledTimes(1);

        await act(async () => {
            node.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
        });

        expect(request).toHaveBeenCalledTimes(2);
        expect(request.mock.calls[1][0]).toBe('/admin/songs?sort=TITLE&page=1&pageSize=25&query=Ama');
    });
});
