import { type ChangeEvent, useEffect, useMemo, useState } from 'react';
import { hasCapability } from '../auth/permissions';
import type { AdminSession } from '../auth/session';
import { adminEnvironment } from '../config/environment';
import { createAdminApiClient, type AdminApiClient, type AdminApiError } from '../generated/cadentia-api/client';
import { buildSongReviewQueuePath, listReviewSongs, parseSongReviewFilters, serializeSongReviewFilters, type CatalogSearchResult, type SongReviewFilterState, type SongReviewQueueResponse } from '../song-review';
import { ActionBadge, Badge, Breadcrumbs, DataTable, Field, FilterPanel, PageHeader, StatePanel, redactSensitiveError } from './admin-ui';

const options = {
    sort: ['RELEVANCE', 'TITLE', 'UPDATED_AT'],
};

const label = (value?: string | null) => value ? value.replaceAll('_', ' ').toLowerCase().replace(/^./, (c) => c.toUpperCase()) : 'None';
const severityFor = (value?: string | null) => value === 'SONG' || value === 'ARRANGEMENT' ? 'success' : 'neutral';
const hasCriteria = (filters: SongReviewFilterState) =>
    Boolean(filters.query || filters.tag || filters.contributor || filters.key || filters.scriptureReference);

const SelectField = ({ name, labelText, value, onChange }: { name: keyof typeof options; labelText: string; value: string; onChange: (name: string, value: string) => void }) => (
    <Field label={labelText}>{({ inputId }) => <select id={inputId} value={value} onChange={(event) => onChange(name, event.target.value)}>{options[name].map((option) => <option key={option} value={option}>{option ? label(option) : 'Any'}</option>)}</select>}</Field>
);

export const SongReviewQueue = ({ session, apiClient = createAdminApiClient({ environment: adminEnvironment, getAccessToken: async () => null }), initialSearch = window.location.search }: { session: AdminSession; apiClient?: AdminApiClient; initialSearch?: string }) => {
    const [draftFilters, setDraftFilters] = useState<SongReviewFilterState>(() => parseSongReviewFilters(initialSearch));
    const [appliedFilters, setAppliedFilters] = useState<SongReviewFilterState>(() => parseSongReviewFilters(initialSearch));
    const [queue, setQueue] = useState<SongReviewQueueResponse | null>(null);
    const [state, setState] = useState<'idle' | 'loading' | 'empty' | 'ready' | 'unauthorized' | 'forbidden' | 'stale' | 'error'>('idle');
    const [error, setError] = useState('');
    const queuePath = useMemo(() => buildSongReviewQueuePath(appliedFilters), [appliedFilters]);
    const canReview = hasCapability(session, 'REVIEW_CATALOG');

    const load = async (filtersToLoad = appliedFilters, showStale = false) => {
        if (!hasCriteria(filtersToLoad)) {
            setQueue(null);
            setState('idle');
            return;
        }
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
        <main className="admin-shell" aria-labelledby="song-review-title">
            <Breadcrumbs items={[{ label: 'Admin', href: '/admin' }, { label: 'Songs' }]} />
            <PageHeader eyebrow="Catalog review" title="Reviewed songs" titleId="song-review-title" description="Read-only approved catalog search using the existing CatalogSearch API. Open a result to inspect song and arrangement asset attachments exposed by the existing Assets API." />
            <FilterPanel title="Catalog search" onSubmit={applyUrl}>
                <Field label="Search">{({ inputId }) => <input id={inputId} name="query" value={draftFilters.query ?? ''} onChange={updateInput} />}</Field>
                <Field label="Tag">{({ inputId }) => <input id={inputId} name="tag" value={draftFilters.tag ?? ''} onChange={updateInput} />}</Field>
                <Field label="Contributor">{({ inputId }) => <input id={inputId} name="contributor" value={draftFilters.contributor ?? ''} onChange={updateInput} />}</Field>
                <Field label="Key">{({ inputId }) => <input id={inputId} name="key" value={draftFilters.key ?? ''} onChange={updateInput} />}</Field>
                <Field label="Scripture">{({ inputId }) => <input id={inputId} name="scriptureReference" value={draftFilters.scriptureReference ?? ''} onChange={updateInput} />}</Field>
                <SelectField name="sort" labelText="Sort" value={draftFilters.sort} onChange={updateFilter} />
            </FilterPanel>
            <p role="status">Shareable song review URL state: <code>{`/admin/songs${serializeSongReviewFilters(appliedFilters) ? `?${serializeSongReviewFilters(appliedFilters)}` : ''}`}</code></p>
            {state === 'idle'
                ? <section className="admin-shell__panel" aria-labelledby="song-search-prompt-title"><h2 id="song-search-prompt-title">Enter catalog search criteria</h2><p className="admin-shell__muted">The current backend exposes catalog search, not a list-all songs endpoint. Enter a search term, tag, contributor, key, or scripture reference, then apply filters to load approved catalog results.</p></section>
                : <StatePanel state={state} title="Reviewed songs" onRetry={() => void load(appliedFilters, true)}>{error && <p>{error}</p>}</StatePanel>}
            {queue && rows.length > 0 && <><p>{queue.items.length} approved catalog results. Page {queue.page}{queue.hasMore ? ' with more results available' : ''}.</p><DataTable caption="Approved catalog search results" columns={['Result', 'Type', 'Matched fields', 'Ranking', 'Access']} rows={rows} /></>}
            {queue && (appliedFilters.page > 1 || queue.hasMore) && <nav aria-label="Pagination"><button disabled={appliedFilters.page <= 1} onClick={() => changePage(appliedFilters.page - 1)}>Previous</button><button disabled={!queue.hasMore} onClick={() => changePage(appliedFilters.page + 1)}>Next</button></nav>}
        </main>
    );
};

const detailHref = (result: CatalogSearchResult) => {
    const params = new URLSearchParams();
    if (result.title) params.set('title', result.title);
    if (result.subtitle) params.set('subtitle', result.subtitle);
    if (result.arrangementId) params.set('arrangementId', result.arrangementId);
    if (result.hydration?.href) params.set('hydrationHref', result.hydration.href);
    const query = params.toString();
    return `/admin/songs/${encodeURIComponent(result.songId ?? result.id)}${query ? `?${query}` : ''}`;
};

const renderRow = (result: CatalogSearchResult, canReview: boolean) => [
    <><a href={detailHref(result)}>{result.title}</a><br /><small>{result.subtitle ?? result.hydration?.href ?? 'No subtitle returned'}</small></>,
    <Badge severity={severityFor(result.resultType)}>{label(result.resultType)}</Badge>,
    (result.matchedFields ?? []).length ? <span className="admin-chip-list">{result.matchedFields!.map((field, index) => <code key={`${field.field}-${field.value}-${index}`}>{label(field.field)}: {field.value}</code>)}</span> : 'No matched fields returned',
    <><small>Score {Math.round(result.score * 100) / 100}</small><br />{(result.rankingFactors ?? []).map((factor, index) => <Badge key={`${factor.code}-${index}`}>{factor.code ?? 'factor'} {factor.contribution ?? 0}</Badge>)}</>,
    canReview ? <ActionBadge capability="REVIEW_CATALOG" /> : <small>Detail view only</small>,
];
