import { useEffect, useMemo, useState } from 'react';
import { adminEnvironment, missingRequiredEnvironment } from '../config/environment';
import { bootstrapAdminSession, type AdminSession, type PermissionState } from '../auth/session';
import { canAccessRoute, canRenderAction, visibleRoutes } from '../auth/permissions';
import { defaultImportCandidateFilters, listImportCandidates, type ImportCandidateQueueResponse } from '../import-candidates';
import { ImportCandidateQueue } from './ImportCandidateQueue';
import { ImportCandidateDetail } from './ImportCandidateDetail';
import { SongImport } from './SongImport';
import { SongReviewDetail } from './SongReviewDetail';
import { SongReviewQueue } from './SongReviewQueue';
import { AuditRollback } from './AuditRollback';
import { Diagnostics } from './Diagnostics';
import { InstanceSettings } from './InstanceSettings';
import { TelegramAccessRequests } from './TelegramAccessRequests';
import { ActionBadge, AuditReferenceLink, Badge, Breadcrumbs, DataTable, PageHeader, RoleBadge, StatePanel, SupportDebugPanel, redactSensitiveError } from './admin-ui';
import { createAdminApiClient, type AdminApiClient, type AdminApiError } from '../generated/cadentia-api/client';
import { I18nProvider, LocalizedView, routeLabel, translate, useI18n, type TranslationKey } from '../i18n';
import './admin-shell.css';

const TopNav = ({ session, routes }: { session: AdminSession; routes: Array<{ href: string; label: string }> }) => {
    const { t } = useI18n();
    return <header className="admin-topnav">
        <a className="admin-topnav__brand" href="/admin">{t('brand')}</a>
        <nav aria-label={t('sections')}>
            <ul className="admin-topnav__links">
                {routes.map((route) => (
                    <li key={route.href}>
                        <a href={route.href} aria-current={window.location.pathname === route.href || window.location.pathname.startsWith(`${route.href}/`) ? 'page' : undefined}>{routeLabel(session.locale, route.label)}</a>
                    </li>
                ))}
            </ul>
        </nav>
        <details className="admin-topnav__menu">
            <summary className="admin-topnav__user-button" aria-label={`${t('signedInAs')} ${session.displayName}`}>
                <svg aria-hidden="true" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <circle cx="12" cy="8" r="4" />
                    <path d="M4 20c0-4 4-6 8-6s8 2 8 6" />
                </svg>
            </summary>
            <div className="admin-topnav__popover" role="group" aria-label={`${t('signedInAs')} ${session.displayName}`}>
                <span className="admin-topnav__name">{t('signedInAs')} {session.displayName}</span>
                <div className="admin-topnav__roles">
                    {session.roles.map((role) => <RoleBadge key={role} role={role} />)}
                </div>
            </div>
        </details>
    </header>;
};

const renderPermissionState = (state: PermissionState, t: (key: TranslationKey) => string) => {
    switch (state.kind) {
        case 'loading':
            return <StatePanel state="loading" title="Session loading" />;
        case 'missing-church-instance':
            return <StatePanel state="error" title={t('missingInstance')}>{t('missingInstanceCopy')}</StatePanel>;
        case 'unauthenticated':
            return <a className="admin-shell__button" href={state.signInUrl}>{t('signIn')}</a>;
        case 'expired-session':
            return <a className="admin-shell__button" href={state.signInUrl}>{t('sessionExpired')}</a>;
        case 'forbidden':
            return <StatePanel state="forbidden" title={t('accessDenied')} />;
        case 'disabled-feature':
            return <StatePanel state="empty" title={t('capabilityDenied')} />;
        case 'failure':
            return <StatePanel state="error" title={t('shellUnavailable')} />;
        case 'authenticated':
            return null;
    }
};

const label = (value?: string | null) => value ? value.replaceAll('_', ' ').toLowerCase().replace(/^./, (c) => c.toUpperCase()) : 'None';
const severityFor = (value: string) => value === 'BLOCKED' || value === 'ERROR' || value === 'HIGH' || value === 'URGENT' ? 'danger' : value === 'READY' || value === 'VERIFIED' || value === 'CLEAR' ? 'success' : value === 'NONE' ? 'neutral' : 'warning';

const ImportCandidateSummary = ({ apiClient = createAdminApiClient({ environment: adminEnvironment, getAccessToken: async () => null }) }: { apiClient?: AdminApiClient }) => {
    const [queue, setQueue] = useState<ImportCandidateQueueResponse | null>(null);
    const [state, setState] = useState<'loading' | 'empty' | 'ready' | 'unauthorized' | 'forbidden' | 'error'>('loading');
    const [error, setError] = useState('');

    const load = async () => {
        setState('loading');
        try {
            const response = await listImportCandidates(apiClient, {
                ...defaultImportCandidateFilters,
                pageSize: 5,
                sort: 'updatedAt:desc',
            });
            setQueue(response);
            setState(response.items.length ? 'ready' : 'empty');
        } catch (caught) {
            const apiError = caught as AdminApiError;
            setError(redactSensitiveError(apiError.message));
            setState(apiError.status === 401 ? 'unauthorized' : apiError.status === 403 ? 'forbidden' : 'error');
        }
    };

    useEffect(() => { void load(); }, []);

    const rows = (queue?.items ?? []).map((candidate) => [
        <><a href={`/admin/imports/${encodeURIComponent(candidate.candidateId)}`}>{candidate.normalizedTitle || candidate.rawTitle}</a><br /><small>{candidate.connectorKey} · batch {candidate.importBatchId}</small></>,
        <Badge severity={severityFor(candidate.status)}>{label(candidate.status)}</Badge>,
        candidate.assignedReviewerName ?? candidate.assignedReviewerId ?? 'Unassigned',
        candidate.auditReferenceId ? <AuditReferenceLink auditId={candidate.auditReferenceId} /> : 'Pending audit event',
    ]);

    return (
        <LocalizedView><section aria-labelledby="import-summary-title" className="admin-shell__panel">
            <h2 id="import-summary-title">Import review snapshot</h2>
            <StatePanel state={state} title="Import candidates" onRetry={() => void load()}>{error && <p>{error}</p>}</StatePanel>
            {queue && rows.length > 0 && <><p>{queue.totalItems} server-matched candidates. Showing up to 5.</p><DataTable caption="Latest import candidates" columns={['Candidate', 'Status', 'Reviewer', 'Audit']} rows={rows} /></>}
            <p><a href="/admin/imports">Open full import review queue</a></p>
        </section></LocalizedView>
    );
};

export const AdminShell = () => {
    const { t: defaultT } = useI18n();
    const [permissionState, setPermissionState] = useState<PermissionState>({ kind: 'loading' });
    const missingEnvironment = missingRequiredEnvironment(adminEnvironment);

    useEffect(() => {
        let isActive = true;
        void bootstrapAdminSession({ environment: adminEnvironment }).then((state) => {
            if (isActive) setPermissionState(state);
        });
        return () => { isActive = false; };
    }, []);

    const session = permissionState.kind === 'authenticated' ? permissionState.session : null;
    const routes = useMemo(() => (session ? visibleRoutes(session, adminEnvironment.featureFlags) : []), [session]);
    const blockedDirectRoute = session && !canAccessRoute(session, adminEnvironment.featureFlags, window.location.pathname);

    const t = (key: TranslationKey) => session ? translate(session.locale, key) : defaultT(key);
    const withNav = (page: JSX.Element) => session
        ? <I18nProvider locale={session.locale}><TopNav session={session} routes={routes} />{page}</I18nProvider>
        : page;

    if (blockedDirectRoute) {
        return withNav(<main className="admin-shell"><Breadcrumbs items={[{ label: t('admin'), href: '/admin' }, { label: t('accessDenied') }]} /><StatePanel state="forbidden" title={t('accessDenied')}>{t('forbidden')}</StatePanel></main>);
    }

    if (session && window.location.pathname === '/admin/imports') {
        return withNav(<ImportCandidateQueue session={session} />);
    }
    if (session && window.location.pathname === '/admin/song-imports') {
        return withNav(<SongImport session={session} />);
    }
    if (session && window.location.pathname === '/admin/songs') {
        return withNav(<SongReviewQueue session={session} />);
    }
    if (session && window.location.pathname === '/admin/audit') {
        return withNav(<AuditRollback session={session} />);
    }
    if (session && window.location.pathname === '/admin/diagnostics') {
        return withNav(<Diagnostics session={session} />);
    }
    if (session && window.location.pathname === '/admin/settings') {
        return withNav(<InstanceSettings session={session} />);
    }
    if (session && window.location.pathname === '/admin/telegram-access') {
        return withNav(<TelegramAccessRequests session={session} />);
    }
    const detailMatch = window.location.pathname.match(/^\/admin\/imports\/([^/]+)$/);
    if (session && detailMatch) {
        return withNav(<ImportCandidateDetail session={session} candidateId={decodeURIComponent(detailMatch[1])} />);
    }
    const songDetailMatch = window.location.pathname.match(/^\/admin\/songs\/([^/]+)$/);
    if (session && songDetailMatch) {
        return withNav(<SongReviewDetail session={session} songId={decodeURIComponent(songDetailMatch[1])} />);
    }

    return withNav(
        <LocalizedView><main className="admin-shell" aria-labelledby="admin-shell-title">
            <Breadcrumbs items={[{ label: t('admin'), href: '/admin' }, { label: t('shell') }]} />
            <PageHeader
                eyebrow={t('brand')}
                title={t('shell')}
                titleId="admin-shell-title"
                description={t('capabilityCopy')}
            />

            {permissionState.kind !== 'authenticated' ? (
                <section aria-labelledby="admin-permission-title" className="admin-shell__panel">
                    <h2 id="admin-permission-title">{t('accessDenied')}</h2>
                    {renderPermissionState(permissionState, t)}
                </section>
            ) : (
                <section aria-labelledby="admin-nav-title" className="admin-shell__panel">
                    <h2 id="admin-nav-title">{t('protectedRoutes')}</h2>
                    <p>{t('signedInAs')} {session!.displayName}. {t('routeCopy')}</p>
                    <p>{session!.roles.map((role) => <RoleBadge key={role} role={role} />)}</p>
                </section>
            )}

            {session && <ImportCandidateSummary />}

            <div className="admin-card-grid">
                {session && (
                    <section aria-labelledby="admin-capabilities-title" className="admin-shell__panel">
                        <h2 id="admin-capabilities-title">{t('grantedCapabilities')}</h2>
                        <p>{t('capabilityCopy')}</p>
                        {canRenderAction(session, 'REVIEW_CATALOG') && <ActionBadge capability="REVIEW_CATALOG" />}
                        {canRenderAction(session, 'EXECUTE_ROLLBACK') && <ActionBadge capability="EXECUTE_ROLLBACK" />}
                        {!canRenderAction(session, 'REVIEW_CATALOG') && !canRenderAction(session, 'EXECUTE_ROLLBACK') && <p>{t('readOnly')}</p>}
                    </section>
                )}

                <section aria-labelledby="admin-config-title" className="admin-shell__panel">
                    <h2 id="admin-config-title">{t('deploymentMetadata')}</h2>
                    <SupportDebugPanel environment={adminEnvironment} />
                    {missingEnvironment.length > 0 && <p role="status" className="admin-shell__warning">{t('missingRuntime')}: {missingEnvironment.join(', ')}</p>}
                </section>
            </div>
        </main></LocalizedView>
    );
};
