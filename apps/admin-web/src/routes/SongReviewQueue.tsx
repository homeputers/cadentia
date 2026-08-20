import { type ChangeEvent, useEffect, useMemo, useState } from 'react';
import { hasCapability } from '../auth/permissions';
import type { AdminSession } from '../auth/session';
import { adminEnvironment } from '../config/environment';
import { createAdminApiClient, type AdminApiClient, type AdminApiError } from '../generated/cadentia-api/client';
import { buildSongReviewQueuePath, listReviewSongs, parseSongReviewFilters, serializeSongReviewFilters, type CatalogSongSummary, type SongReviewFilterState, type SongReviewQueueResponse } from '../song-review';
import { LocalizedView } from '../i18n';
import { ActionBadge, Badge, Breadcrumbs, DataTable, Field, FilterPanel, PageHeader, StatePanel, redactSensitiveError } from './admin-ui';

const options = {
    status: ['ALL', 'APPROVED', 'IN_REVIEW'],
    sort: ['TITLE', 'UPDATED_AT'],
};

const label = (value?: string | null) => value ? value.replaceAll('_', ' ').toLowerCase().replace(/^./, (c) => c.toUpperCase()) : 'None';
const severityFor = (value?: string | null) => value === 'APPROVED' ? 'success' : value === 'REJECTED' || value === 'ARCHIVED' ? 'danger' : 'neutral';

const SelectField = ({ name, labelText, value, onChange }: { name: keyof typeof options; labelText: string; value: string; onChange: (name: string, value: string) => void }) => (
    <LocalizedView><Field label={labelText}>{({ inputId }) => <select id={inputId} value={value} onChange={(event) => onChange(name, event.target.value)}>{options[name].map((option) => <option key={option} value={option}>{option ? label(option) : 'Any'}</option>)}</select>}</Field></LocalizedView>
);

export const SongReviewQueue = ({ session, apiClient = createAdminApiClient({ environment: adminEnvironment, getAccessToken: async () => null }), initialSearch = window.location.search }: { session: AdminSession; apiClient?: AdminApiClient; initialSearch?: string }) => {
    const [draftFilters, setDraftFilters] = useState<SongReviewFilterState>(() => parseSongReviewFilters(initialSearch));
    const [appliedFilters, setAppliedFilters] = useState<SongReviewFilterState>(() => parseSongReviewFilters(initialSearch));
    const [queue, setQueue] = useState<SongReviewQueueResponse | null>(null);
    const [state, setState] = useState<'loading' | 'empty' | 'ready' | 'unauthorized' | 'forbidden' | 'stale' | 'error'>('loading');
    const [error, setError] = useState('');
    const queuePath = useMemo(() => buildSongReviewQueuePath(appliedFilters), [appliedFilters]);
    const canReview = hasCapability(session, 'REVIEW_CATALOG');

    const load = async (filtersToLoad = appliedFilters, showStale = false) => {
        setState(showStale && queue ? 'stale' : 'loading');
        try {
            const response = await listReviewSongs(apiClient, filtersToLoad);
            setQueue(response);
            setState(response.items.length === 0 ? 'empty' : 'ready');
        } catch (caught) {
            const apiError = caught as AdminApiError;
            setError(redactSensitiveError(apiError.message));
            if (apiError.status === 401) setState('unauthorized');
            else if (apiError.status === 403) setState('forbidden');
            else setState('error');
        }
    };

    useEffect(() => { void load(appliedFilters, false); }, [queuePath]);

    const updateFilter = (name: string, value: string) => setDraftFilters((current) => ({ ...current, [name]: value || undefined, page: 1 }));
    const updateInput = (event: ChangeEvent<HTMLInputElement>) => updateFilter(event.target.name, event.target.value.trim());
    const applyUrl = () => {
        const nextFilters = { ...draftFilters, page: 1 };
        const query = serializeSongReviewFilters(nextFilters);
        window.history.replaceState(null, '', `/admin/songs${query ? `?${query}` : ''}`);
        setAppliedFilters(nextFilters);
        if (buildSongReviewQueuePath(nextFilters) === queuePath) {
            void load(nextFilters, true);
        }
    };
    const changePage = (page: number) => {
        const nextFilters = { ...appliedFilters, page };
        setDraftFilters(nextFilters);
        setAppliedFilters(nextFilters);
    };

    const rows = (queue?.items ?? []).map((song) => renderRow(song, canReview));

    return (
        <LocalizedView><main className="admin-shell" aria-labelledby="song-review-title">
            <Breadcrumbs items={[{ label: 'Admin', href: '/admin' }, { label: 'Songs' }]} />
            <PageHeader eyebrow="Catalog review" title="Reviewed songs" titleId="song-review-title" description="Reviewed and approved catalog songs with editable metadata and resource attachment management." />
            <FilterPanel title="Catalog search" onSubmit={applyUrl}>
                <Field label="Search">{({ inputId }) => <input id={inputId} name="query" value={draftFilters.query ?? ''} onChange={updateInput} />}</Field>
                <SelectField name="status" labelText="Status" value={draftFilters.status} onChange={updateFilter} />
                <SelectField name="sort" labelText="Sort" value={draftFilters.sort} onChange={updateFilter} />
            </FilterPanel>
            <p role="status">Shareable song review URL state: <code>{`/admin/songs${serializeSongReviewFilters(appliedFilters) ? `?${serializeSongReviewFilters(appliedFilters)}` : ''}`}</code></p>
            <StatePanel state={state} title="Reviewed songs" onRetry={() => void load(appliedFilters, true)}>{error && <p>{error}</p>}</StatePanel>
            {queue && rows.length > 0 && <><p>{queue.totalItems} catalog songs. Page {queue.page} of {queue.totalPages || 1}.</p><DataTable caption="Reviewed catalog songs" columns={['Song', 'Status', 'Catalog metadata', 'Arrangements', 'Access']} rows={rows} /></>}
            {queue && (appliedFilters.page > 1 || appliedFilters.page < queue.totalPages) && <nav aria-label="Pagination"><button className="secondary" disabled={appliedFilters.page <= 1} onClick={() => changePage(appliedFilters.page - 1)}>Previous</button><button className="secondary" disabled={appliedFilters.page >= queue.totalPages} onClick={() => changePage(appliedFilters.page + 1)}>Next</button></nav>}
        </main></LocalizedView>
    );
};

const detailHref = (result: CatalogSongSummary) => `/admin/songs/${encodeURIComponent(result.songId)}`;

const renderRow = (result: CatalogSongSummary, canReview: boolean) => [
    <><a href={detailHref(result)}>{result.canonicalTitle}</a><br /><small>{result.originalArtistDisplay ?? result.composerCredits ?? 'No contributor metadata'}</small></>,
    <Badge severity={severityFor(result.songStatus)}>{label(result.songStatus)}</Badge>,
    <dl><dt>Language</dt><dd>{result.primaryLanguage}</dd><dt>CCLI</dt><dd>{result.ccliNumber ?? 'None'}</dd><dt>Updated</dt><dd>{result.updatedAt}</dd></dl>,
    result.arrangementCount,
    canReview ? <ActionBadge capability="REVIEW_CATALOG" /> : <small>Detail view only</small>,
];
