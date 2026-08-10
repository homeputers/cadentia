import { useEffect, useMemo, useState } from 'react';
import { hasCapability } from '../auth/permissions';
import type { AdminSession } from '../auth/session';
import { adminEnvironment } from '../config/environment';
import { createAdminApiClient, type AdminApiClient, type AdminApiError } from '../generated/cadentia-api/client';
import { getReviewSong, type AssetAttachment, type SongReviewDetail as SongReviewDetailModel } from '../song-review';
import { ActionBadge, Badge, Breadcrumbs, DataTable, PageHeader, StatePanel, redactSensitiveError } from './admin-ui';

const label = (value?: string | null) => value ? value.replaceAll('_', ' ').toLowerCase().replace(/^./, (c) => c.toUpperCase()) : 'None';
const severityFor = (value?: string | null) => value === 'archived' || value === 'deleted' ? 'danger' : value === 'primary_chart' || value === 'performance' ? 'success' : 'neutral';

const paramsFromSearch = (search: string) => {
    const params = new URLSearchParams(search);
    return {
        title: params.get('title'),
        subtitle: params.get('subtitle'),
        arrangementId: params.get('arrangementId'),
        hydrationHref: params.get('hydrationHref'),
    };
};

export const SongReviewDetail = ({
    session,
    songId,
    apiClient = createAdminApiClient({ environment: adminEnvironment, getAccessToken: async () => null }),
    initialSearch = window.location.search,
}: {
    session: AdminSession;
    songId: string;
    apiClient?: AdminApiClient;
    initialSearch?: string;
}) => {
    const context = useMemo(() => paramsFromSearch(initialSearch), [initialSearch]);
    const [detail, setDetail] = useState<SongReviewDetailModel | null>(null);
    const [state, setState] = useState<'loading' | 'ready' | 'unauthorized' | 'forbidden' | 'stale' | 'error'>('loading');
    const [error, setError] = useState('');
    const canReview = hasCapability(session, 'REVIEW_CATALOG');

    const load = async () => {
        setState(detail ? 'stale' : 'loading');
        try {
            setDetail(await getReviewSong(apiClient, songId, context));
            setState('ready');
        } catch (caught) {
            const apiError = caught as AdminApiError;
            setError(redactSensitiveError(apiError.message));
            if (apiError.status === 401) setState('unauthorized');
            else if (apiError.status === 403) setState('forbidden');
            else setState('error');
        }
    };

    useEffect(() => { void load(); }, [songId, initialSearch]);

    return (
        <main className="admin-shell" aria-labelledby="song-detail-title">
            <Breadcrumbs items={[{ label: 'Admin', href: '/admin' }, { label: 'Songs', href: '/admin/songs' }, { label: detail?.title ?? 'Song resources' }]} />
            <PageHeader eyebrow="Catalog song review" title={detail?.title ?? 'Song resources'} titleId="song-detail-title" description="Read-only catalog resource view backed by existing asset attachment endpoints. Metadata here is limited to the selected catalog search result." actions={canReview && <ActionBadge capability="REVIEW_CATALOG" />} />
            <StatePanel state={state} title="Song resources" onRetry={() => void load()}>{error && <p>{error}</p>}</StatePanel>
            {detail && <>
                <section className="admin-shell__panel" aria-labelledby="song-identity-title">
                    <h2 id="song-identity-title">Catalog search context</h2>
                    <dl>
                        <dt>Song ID</dt><dd>{detail.songId}</dd>
                        <dt>Title</dt><dd>{detail.title ?? 'Open from search results to carry title context.'}</dd>
                        <dt>Arrangement</dt><dd>{detail.arrangementId ?? 'No arrangement selected'}</dd>
                        <dt>Subtitle</dt><dd>{detail.subtitle ?? 'Not returned'}</dd>
                        <dt>Hydration link</dt><dd>{detail.hydrationHref ?? 'Not returned by selected result'}</dd>
                    </dl>
                </section>
                <AttachmentSection title="Song attachments" caption="Song asset attachments" attachments={detail.songAttachments} />
                <AttachmentSection title="Arrangement attachments" caption="Arrangement asset attachments" attachments={detail.arrangementAttachments} emptyCopy={detail.arrangementId ? 'No arrangement attachments returned.' : 'Open a catalog search result with an arrangement ID to inspect arrangement attachments.'} />
                <section className="admin-shell__panel" aria-labelledby="edit-boundary-title">
                    <h2 id="edit-boundary-title">Editing boundary</h2>
                    <p>Song metadata, lyrics document editing, and canonical arrangement editing are not exposed by the current OpenAPI contract. This screen intentionally stays read-only until those admin endpoints are added.</p>
                </section>
            </>}
        </main>
    );
};

const AttachmentSection = ({ title, caption, attachments, emptyCopy = 'No attachments returned.' }: { title: string; caption: string; attachments: AssetAttachment[]; emptyCopy?: string }) => (
    <section className="admin-shell__panel" aria-labelledby={`${caption.replace(/\W+/g, '-').toLowerCase()}-title`}>
        <h2 id={`${caption.replace(/\W+/g, '-').toLowerCase()}-title`}>{title}</h2>
        {attachments.length
            ? <DataTable caption={caption} columns={['Label', 'Type', 'Purpose', 'Asset version', 'Required', 'Visibility', 'Created']} rows={attachments.map((attachment) => [
                attachment.displayLabel,
                label(attachment.attachmentType),
                <Badge severity={severityFor(attachment.purpose)}>{label(attachment.purpose)}</Badge>,
                <code>{attachment.assetVersionId}</code>,
                attachment.requiredForUse ? 'Required' : 'Optional',
                label(attachment.visibilityPolicy),
                attachment.createdAt,
            ])} />
            : <p>{emptyCopy}</p>}
    </section>
);
