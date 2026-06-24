import { useEffect, useMemo, useState } from 'react';
import { adminEnvironment, missingRequiredEnvironment } from '../config/environment';
import { bootstrapAdminSession, type PermissionState } from '../auth/session';
import { canRenderAction, visibleRoutes } from '../auth/permissions';
import { ImportCandidateQueue } from './ImportCandidateQueue';
import { ImportCandidateDetail } from './ImportCandidateDetail';
import { AuditRollback } from './AuditRollback';
import { Diagnostics } from './Diagnostics';
import { InstanceSettings } from './InstanceSettings';
import { ActionBadge, AuditReferenceLink, Badge, Breadcrumbs, DataTable, DiffPanel, Field, FilterPanel, PageHeader, RoleBadge, StatePanel, SupportDebugPanel } from './admin-ui';
import './admin-shell.css';

const renderPermissionState = (state: PermissionState) => {
    switch (state.kind) {
        case 'loading':
            return <StatePanel state="loading" title="Session loading" />;
        case 'missing-church-instance':
            return <StatePanel state="error" title="Missing church instance">Admin console is missing required church-instance context.</StatePanel>;
        case 'unauthenticated':
            return <a className="admin-shell__button" href={state.signInUrl}>Sign in to Cadentia admin</a>;
        case 'expired-session':
            return <a className="admin-shell__button" href={state.signInUrl}>Session expired. Sign in again.</a>;
        case 'forbidden':
            return <StatePanel state="forbidden" title="Access denied" />;
        case 'disabled-feature':
            return <StatePanel state="empty" title="Feature disabled" />;
        case 'failure':
            return <StatePanel state="error" title="Admin shell unavailable" />;
        case 'authenticated':
            return null;
    }
};

export const AdminShell = () => {
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
    if (session && window.location.pathname === '/admin/imports') {
        return <ImportCandidateQueue session={session} />;
    }
    if (session && window.location.pathname === '/admin/audit') {
        return <AuditRollback session={session} />;
    }
    if (session && window.location.pathname === '/admin/diagnostics') {
        return <Diagnostics session={session} />;
    }
    if (session && window.location.pathname === '/admin/settings') {
        return <InstanceSettings session={session} />;
    }
    const detailMatch = window.location.pathname.match(/^\/admin\/imports\/([^/]+)$/);
    if (session && detailMatch) {
        return <ImportCandidateDetail session={session} candidateId={decodeURIComponent(detailMatch[1])} />;
    }
    const routes = useMemo(() => (session ? visibleRoutes(session, adminEnvironment.featureFlags) : []), [session]);

    return (
        <main className="admin-shell" aria-labelledby="admin-shell-title">
            <Breadcrumbs items={[{ label: 'Admin', href: '/admin' }, { label: 'Shell foundations' }]} />
            <PageHeader
                eyebrow="Cadentia administrative console"
                title="Shared admin route shell"
                titleId="admin-shell-title"
                description="Reusable layout, data state, confirmation, audit, validation, table, and safe diagnostics patterns for later ADR-036 screens."
            />

            {permissionState.kind !== 'authenticated' ? (
                <section aria-labelledby="admin-permission-title" className="admin-shell__panel">
                    <h2 id="admin-permission-title">Permission state</h2>
                    {renderPermissionState(permissionState)}
                </section>
            ) : (
                <>
                    <section aria-labelledby="admin-nav-title" className="admin-shell__panel">
                        <h2 id="admin-nav-title">Protected route groups</h2>
                        <p>Signed in as {session!.displayName}. Only routes matching current capabilities are shown.</p>
                        <nav aria-label="Admin sections"><ul>{routes.map((route) => <li key={route.href}><a href={route.href}>{route.label}</a></li>)}</ul></nav>
                        <p>{session!.roles.map((role) => <RoleBadge key={role} role={role} />)}</p>
                    </section>

                    <section aria-labelledby="admin-actions-title" className="admin-shell__panel">
                        <h2 id="admin-actions-title">Available actions</h2>
                        {canRenderAction(session!, 'REVIEW_CATALOG') && <ActionBadge capability="REVIEW_CATALOG" />}
                        {canRenderAction(session!, 'EXECUTE_ROLLBACK') && <ActionBadge capability="EXECUTE_ROLLBACK" />}
                        {!canRenderAction(session!, 'REVIEW_CATALOG') && !canRenderAction(session!, 'EXECUTE_ROLLBACK') && <p>Read-only access. Mutating action controls are hidden as a usability aid.</p>}
                    </section>
                </>
            )}

            <section aria-labelledby="foundation-title" className="admin-shell__panel">
                <h2 id="foundation-title">Reusable component foundation preview</h2>
                <FilterPanel title="Accessible filter form"><Field label="Status" error="Choose a backend-provided status before applying.">{({ inputId, errorId }) => <select id={inputId} aria-describedby={errorId} aria-invalid={Boolean(errorId)}><option value="">Select status</option><option>Needs review</option></select>}</Field></FilterPanel>
                <DataTable caption="Import review state examples" columns={['Candidate', 'Status', 'Audit']} rows={[[ 'candidate-42', <Badge severity="warning">Needs review</Badge>, <AuditReferenceLink auditId="audit-100" /> ]]} />
                <StatePanel state="partial-failure" title="Partial data failure" onRetry={() => undefined} />
                <DiffPanel before={['Feature flag disabled']} after={['Feature flag enabled after backend preview']} />
            </section>

            <section aria-labelledby="admin-config-title" className="admin-shell__panel">
                <h2 id="admin-config-title">Deployment smoke metadata</h2>
                <SupportDebugPanel environment={adminEnvironment} />
                {missingEnvironment.length > 0 && <p role="status" className="admin-shell__warning">Missing runtime configuration: {missingEnvironment.join(', ')}</p>}
            </section>
        </main>
    );
};
