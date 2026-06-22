import { useEffect, useMemo, useState } from 'react';
import { adminEnvironment, missingRequiredEnvironment } from '../config/environment';
import { bootstrapAdminSession, type PermissionState } from '../auth/session';
import { canRenderAction, visibleRoutes } from '../auth/permissions';
import './admin-shell.css';

const renderPermissionState = (state: PermissionState) => {
    switch (state.kind) {
        case 'loading':
            return <p role="status">Loading secure admin session…</p>;
        case 'missing-church-instance':
            return <p role="alert">Admin console is missing required church-instance context.</p>;
        case 'unauthenticated':
            return <a className="admin-shell__button" href={state.signInUrl}>Sign in to Cadentia admin</a>;
        case 'expired-session':
            return <a className="admin-shell__button" href={state.signInUrl}>Session expired. Sign in again.</a>;
        case 'forbidden':
            return <p role="alert">You do not have access to this admin console. No protected resource details were loaded.</p>;
        case 'disabled-feature':
            return <p role="status">This admin feature is disabled for the current deployment.</p>;
        case 'failure':
            return <p role="alert">{state.message}</p>;
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
            if (isActive) {
                setPermissionState(state);
            }
        });
        return () => {
            isActive = false;
        };
    }, []);

    const session = permissionState.kind === 'authenticated' ? permissionState.session : null;
    const routes = useMemo(
        () => (session ? visibleRoutes(session, adminEnvironment.featureFlags) : []),
        [session],
    );

    return (
        <main className="admin-shell" aria-labelledby="admin-shell-title">
            <header className="admin-shell__header">
                <p className="admin-shell__eyebrow">Cadentia administrative console</p>
                <h1 id="admin-shell-title">Authenticated admin route shell</h1>
                <p>
                    Protected routes bootstrap identity from the documented admin session API,
                    pass authorization and church-instance context through the generated client,
                    and keep backend RBAC authoritative.
                </p>
            </header>

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
                        <nav aria-label="Admin sections">
                            <ul>
                                {routes.map((route) => (
                                    <li key={route.href}>
                                        <a href={route.href}>{route.label}</a>
                                    </li>
                                ))}
                            </ul>
                        </nav>
                    </section>

                    <section aria-labelledby="admin-actions-title" className="admin-shell__panel">
                        <h2 id="admin-actions-title">Available actions</h2>
                        {canRenderAction(session!, 'REVIEW_CATALOG') && <button type="button">Review catalog candidate</button>}
                        {canRenderAction(session!, 'EXECUTE_ROLLBACK') && <button type="button">Execute approved rollback</button>}
                        {!canRenderAction(session!, 'REVIEW_CATALOG') && !canRenderAction(session!, 'EXECUTE_ROLLBACK') && (
                            <p>Read-only access. Mutating action controls are hidden as a usability aid.</p>
                        )}
                    </section>
                </>
            )}

            <section aria-labelledby="admin-config-title" className="admin-shell__panel">
                <h2 id="admin-config-title">Deployment smoke metadata</h2>
                <dl>
                    <dt>API base URL configured</dt>
                    <dd>{adminEnvironment.apiBaseUrl ? 'yes' : 'missing'}</dd>
                    <dt>Church instance context configured</dt>
                    <dd>{adminEnvironment.churchInstanceId ? 'yes' : 'missing'}</dd>
                    <dt>Diagnostics enabled</dt>
                    <dd>{adminEnvironment.diagnosticsEnabled ? 'yes' : 'no'}</dd>
                    <dt>Bundle version</dt>
                    <dd>{adminEnvironment.buildVersion}</dd>
                </dl>
                {missingEnvironment.length > 0 && (
                    <p role="status" className="admin-shell__warning">
                        Missing runtime configuration: {missingEnvironment.join(', ')}
                    </p>
                )}
            </section>
        </main>
    );
};
