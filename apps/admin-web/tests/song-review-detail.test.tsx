import React from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { act } from 'react-dom/test-utils';
import { afterEach, describe, expect, it } from 'vitest';
import type { AdminSession } from '../src/auth/session';
import type { AdminApiClient } from '../src/generated/cadentia-api/client';
import { I18nProvider } from '../src/i18n';
import { SongReviewDetail } from '../src/routes/SongReviewDetail';

let container: HTMLDivElement;
let root: Root;

const session: AdminSession = {
    actorId: 'catalog-editor-1',
    displayName: 'Editor',
    churchInstanceId: 'church-1',
    locale: 'en',
    roles: ['CATALOG_EDITOR'],
    capabilities: ['REVIEW_CATALOG'],
};

const detail = {
    song: {
        songId: 'song-1',
        canonicalTitle: 'Abba padre',
        normalizedTitle: 'abba-padre',
        primaryLanguage: 'es',
        originalArtistDisplay: null,
        composerCredits: null,
        ccliNumber: null,
        yearWritten: 2020,
        songStatus: 'APPROVED',
        songRole: null,
        updatedAt: '2026-09-07T00:00:00Z',
        arrangementCount: 0,
    },
    doctrinalNotes: null,
    arrangements: [],
    provenance: [],
    approvals: [],
    tags: [],
};

afterEach(() => {
    act(() => { root?.unmount(); });
    container?.remove();
});

describe('song review detail metadata save', () => {
    it('sends the selected song role when saving song metadata', async () => {
        let putBody: Record<string, unknown> | null = null;
        const apiClient = {
            getAdminSession: async () => session,
            request: async (path: string, init?: RequestInit) => {
                if (init?.method === 'PUT') {
                    putBody = JSON.parse(String(init.body));
                    return detail;
                }
                if (path.startsWith('/asset-attachments')) return [];
                return detail;
            },
        } as unknown as AdminApiClient;

        container = document.createElement('div');
        document.body.appendChild(container);
        await act(async () => {
            root = createRoot(container);
            root.render(<I18nProvider locale="en"><SongReviewDetail session={session} songId="song-1" apiClient={apiClient} /></I18nProvider>);
        });

        const roleSelect = container.querySelectorAll('select')[1];
        expect([...roleSelect.options].map((option) => option.textContent)).toEqual(['—', 'Praise', 'Worship', 'Both']);
        await act(async () => {
            Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, 'value')!.set!.call(roleSelect, 'WORSHIP');
            roleSelect.dispatchEvent(new Event('change', { bubbles: true }));
        });

        const saveButton = [...container.querySelectorAll('button')].find((button) => button.textContent === 'Save song metadata')!;
        await act(async () => {
            saveButton.dispatchEvent(new MouseEvent('click', { bubbles: true }));
        });

        expect(putBody).not.toBeNull();
        expect(putBody!.songRole).toBe('WORSHIP');
        expect(putBody!.songStatus).toBe('APPROVED');
    });

    it('keeps an unsaved song role selection when saving arrangements', async () => {
        const apiClient = {
            getAdminSession: async () => session,
            request: async (path: string) => {
                if (path.startsWith('/asset-attachments')) return [];
                return detail;
            },
        } as unknown as AdminApiClient;

        container = document.createElement('div');
        document.body.appendChild(container);
        await act(async () => {
            root = createRoot(container);
            root.render(<I18nProvider locale="en"><SongReviewDetail session={session} songId="song-1" apiClient={apiClient} /></I18nProvider>);
        });

        const roleSelect = container.querySelectorAll('select')[1] as HTMLSelectElement;
        await act(async () => {
            Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, 'value')!.set!.call(roleSelect, 'BOTH');
            roleSelect.dispatchEvent(new Event('change', { bubbles: true }));
        });

        const saveArrangementsButton = [...container.querySelectorAll('button')].find((button) => button.textContent === 'Save arrangements')!;
        await act(async () => {
            saveArrangementsButton.dispatchEvent(new MouseEvent('click', { bubbles: true }));
        });

        const reloadedRoleSelect = container.querySelectorAll('select')[1] as HTMLSelectElement;
        expect(reloadedRoleSelect.value).toBe('BOTH');
    });
});
