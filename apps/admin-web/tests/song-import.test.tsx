import React from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { act } from 'react-dom/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { AdminSession } from '../src/auth/session';
import type { AdminApiClient } from '../src/generated/cadentia-api/client';
import { ImportResult, SongImport } from '../src/routes/SongImport';
import { createCsvSongImport, createManualSongImport, splitEntryList } from '../src/song-imports';

let container: HTMLDivElement;
let root: Root;

const session: AdminSession = {
    actorId: 'catalog-editor-1',
    displayName: 'Catalog Editor',
    churchInstanceId: 'church-1',
    roles: ['CATALOG_EDITOR'],
    capabilities: ['VIEW_IMPORT_QUEUE', 'REVIEW_CATALOG'],
};

const importResponse = {
    importBatchId: '22222222-2222-4222-8222-222222222222',
    status: 'COMPLETED',
    method: 'MANUAL_ENTRY',
    acceptedCount: 1,
    validationErrorCount: 0,
    candidateIds: ['11111111-1111-4111-8111-111111111111'],
    candidates: [{
        candidateId: '11111111-1111-4111-8111-111111111111',
        rawTitle: 'Raw Import Title',
        normalizedTitle: 'Import Title',
        sourceArtistName: 'Import Artist',
        status: 'DEDUPLICATION_REVIEW',
    }],
    validationErrors: [],
};

const render = async (apiClient: AdminApiClient) => {
    container = document.createElement('div');
    document.body.appendChild(container);
    await act(async () => {
        root = createRoot(container);
        root.render(<SongImport session={session} apiClient={apiClient} />);
    });
    return container;
};

const inputByLabel = (label: string) => {
    const labels = [...container.querySelectorAll('label')];
    const match = labels.find((candidate) => candidate.textContent === label);
    if (!match) throw new Error(`Missing label: ${label}`);
    return document.getElementById(match.getAttribute('for') ?? '') as HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement;
};

afterEach(() => {
    act(() => { root?.unmount(); });
    container?.remove();
    vi.restoreAllMocks();
});

describe('song import', () => {
    it('builds manual import requests against the documented admin endpoint', async () => {
        const request = vi.fn().mockResolvedValue(importResponse);
        await createManualSongImport({ getAdminSession: vi.fn(), request }, 'actor-1', {
            title: 'Safe Title',
            key: 'G',
            bpm: 72,
            licenseType: 'CCLI',
            themes: ['grace'],
            scriptureReferences: ['Romans 8'],
            resources: [{ resourceType: 'CHORD_CHART', title: 'Chord chart', url: 'https://example.test/chart' }],
        });
        expect(request).toHaveBeenCalledWith('/admin/song-imports/manual', expect.objectContaining({
            method: 'POST',
            body: expect.stringContaining('"title":"Safe Title"'),
        }));
        expect(JSON.parse(request.mock.calls[0][1].body)).toMatchObject({
            actor: 'actor-1',
            title: 'Safe Title',
            key: 'G',
            bpm: 72,
            resources: [{ resourceType: 'CHORD_CHART', title: 'Chord chart', url: 'https://example.test/chart' }],
        });
        expect(splitEntryList('grace; advent|hope')).toEqual(['grace', 'advent', 'hope']);
    });

    it('builds CSV import requests against the documented admin endpoint', async () => {
        const request = vi.fn().mockResolvedValue({ ...importResponse, method: 'CSV_IMPORT' });
        const file = new File(['title,author,key,bpm\nSafe CSV Song,Writer,G,96'], 'songs.csv', { type: 'text/csv' });
        await createCsvSongImport({ getAdminSession: vi.fn(), request }, 'actor-1', {
            file,
            licenseType: 'CCLI',
            licenseEvidence: 'Church CCLI export',
        });
        expect(request).toHaveBeenCalledWith('/admin/song-imports/csv', expect.objectContaining({
            method: 'POST',
        }));
        const body = request.mock.calls[0][1].body as FormData;
        expect(body.get('actor')).toBe('actor-1');
        expect(body.get('file')).toBe(file);
        expect(body.get('licenseType')).toBe('CCLI');
        expect(body.get('licenseEvidence')).toBe('Church CCLI export');
    });

    it('renders manual, CSV, and resource controls for catalog editors', async () => {
        const node = await render({ getAdminSession: vi.fn(), request: vi.fn().mockResolvedValue(importResponse) });
        expect(node.textContent).toContain('Manual entry');
        expect(node.textContent).toContain('CSV import');
        expect(inputByLabel('Title')).not.toBeNull();
        expect(inputByLabel('Key')).not.toBeNull();
        expect(inputByLabel('Tempo')).not.toBeNull();
        expect(inputByLabel('Lyrics')).not.toBeNull();
        expect(inputByLabel('Resource URL')).not.toBeNull();
        expect(inputByLabel('CSV file')).not.toBeNull();
        expect(node.textContent).toContain('Supported columns');
        expect(node.textContent).toContain('scriptureReferences');
        expect(node.textContent).not.toContain('CSV content');
        expect(node.textContent).not.toContain('approved catalog');
    });

    it('renders review queue links for staged import responses', async () => {
        container = document.createElement('div');
        document.body.appendChild(container);
        await act(async () => {
            root = createRoot(container);
            root.render(<ImportResult result={importResponse} />);
        });
        expect(container.textContent).toContain('Import result');
        expect(container.textContent).toContain('Import Title');
        expect(container.textContent).toContain('Import Artist');
        expect(container.textContent).toContain('Deduplication review');
        expect(container.textContent).not.toContain('11111111-1111-4111-8111-111111111111Needs review');
        expect(container.querySelector('a[href="/admin/imports?batchId=22222222-2222-4222-8222-222222222222"]')).not.toBeNull();
        expect(container.querySelector('a[href="/admin/imports/11111111-1111-4111-8111-111111111111"]')).not.toBeNull();
    });
});
