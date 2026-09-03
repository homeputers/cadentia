import React from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { act } from 'react-dom/test-utils';
import { afterEach, describe, expect, it } from 'vitest';
import type { AdminSession } from '../src/auth/session';
import type { AdminApiClient } from '../src/generated/cadentia-api/client';
import { I18nProvider } from '../src/i18n';
import { SongImport } from '../src/routes/SongImport';
import { TagsSection } from '../src/routes/SongReviewDetail';
import { ActionBadge, RoleBadge } from '../src/routes/admin-ui';

let container: HTMLDivElement;
let root: Root;

const session: AdminSession = {
    actorId: 'catalog-editor-1',
    displayName: 'Editor',
    churchInstanceId: 'church-1',
    locale: 'es-GT',
    roles: ['CATALOG_EDITOR'],
    capabilities: ['REVIEW_CATALOG'],
};

const apiClient = { getAdminSession: async () => session, request: async () => ({}) } as unknown as AdminApiClient;

afterEach(() => {
    act(() => { root?.unmount(); });
    container?.remove();
});

describe('admin-web Spanish rendering', () => {
    it('localizes roles, capabilities, and a subpage when the church locale is Spanish', async () => {
        container = document.createElement('div');
        document.body.appendChild(container);
        await act(async () => {
            root = createRoot(container);
            root.render(<I18nProvider locale="es-GT"><RoleBadge role="CATALOG_EDITOR" /><ActionBadge capability="REVIEW_CATALOG" /><SongImport session={session} apiClient={apiClient} /></I18nProvider>);
        });

        expect(container.textContent).toContain('Editor del catálogo');
        expect(container.textContent).toContain('Revisar catálogo');
        expect(container.textContent).toContain('Ingreso manual');
        expect(container.textContent).toContain('Importación CSV');
        expect(container.textContent).toContain('Preparar canción manual');
        expect(container.textContent).not.toContain('Manual entry');
        expect(container.textContent).not.toContain('CSV import');
    });

    it('localizes tag type options and tag editing controls when the church locale is Spanish', async () => {
        container = document.createElement('div');
        document.body.appendChild(container);
        await act(async () => {
            root = createRoot(container);
            root.render(<I18nProvider locale="es-GT"><TagsSection
                tags={[{
                    tagId: 'tag-1',
                    tagType: 'SCRIPTURE',
                    name: 'Philippians 4:13',
                    slug: 'philippians-4-13',
                    active: true,
                }]}
                canEdit={true}
                tagDraft={{ tagType: 'THEME', name: '' }}
                onTagDraftChange={() => undefined}
                onAssignTag={(event) => event.preventDefault()}
                onRemoveTag={() => undefined}
            /></I18nProvider>);
        });

        const optionLabels = [...container.querySelectorAll('option')].map((option) => option.textContent);
        expect(optionLabels).toEqual([
            'Tema',
            'Escritura',
            'Ánimo',
            'Ocasión',
            'Temporada',
            'Estilo musical',
            'Audiencia',
        ]);
        expect(container.textContent).toContain('Tipo de etiqueta');
        expect(container.textContent).toContain('Nombre de etiqueta');
        expect(container.textContent).toContain('Asignar etiqueta');
        expect(container.textContent).toContain('Eliminar');
    });
});
