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
    results: [{
        id: '66666666-6666-4666-8666-666666666666',
        songId: '55555555-5555-4555-8555-555555555555',
        arrangementId: '77777777-7777-4777-8777-777777777777',
        resultType: 'ARRANGEMENT',
        title: 'Reviewed Song',
        subtitle: 'Arrangement',
        score: 30,
        matchedFields: [{ field: 'TITLE', value: 'Reviewed Song' }],
    }],
    pagination: { pageSize: 20, hasMore: false },
    emptyState: { empty: false },
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
    it('does not post catalog search requests on each search keystroke', async () => {
        const request = vi.fn().mockResolvedValue(searchResponse);
        const node = await render({ getAdminSession: vi.fn(), request } as unknown as AdminApiClient);
        const search = [...node.querySelectorAll('input')].find((input) => input.name === 'query')!;

        expect(request).not.toHaveBeenCalled();
        expect(node.textContent).toContain('Enter catalog search criteria');

        await act(async () => {
            typeValue(search, 'A');
            typeValue(search, 'Am');
            typeValue(search, 'Ama');
            await Promise.resolve();
        });

        expect(request).not.toHaveBeenCalled();

        await act(async () => {
            node.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
        });

        expect(request).toHaveBeenCalledTimes(1);
        expect(request.mock.calls[0][0]).toBe('/catalog/search');
        expect(request.mock.calls[0][1].body).toContain('"query":"Ama"');
    });
});
