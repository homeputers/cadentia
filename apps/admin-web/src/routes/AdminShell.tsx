import { adminEnvironment, missingRequiredEnvironment } from '../config/environment';
import './admin-shell.css';

const adminRoutes = [
    { href: '/admin/imports', label: 'Import review' },
    { href: '/admin/audit', label: 'Audit history' },
    { href: '/admin/diagnostics', label: 'Diagnostics' },
    { href: '/admin/settings', label: 'Instance settings' },
];

export const AdminShell = () => {
    const missingEnvironment = missingRequiredEnvironment(adminEnvironment);

    return (
        <main className="admin-shell" aria-labelledby="admin-shell-title">
            <header className="admin-shell__header">
                <p className="admin-shell__eyebrow">Cadentia administrative console</p>
                <h1 id="admin-shell-title">Authenticated admin route shell</h1>
                <p>
                    This v1 shell reserves protected routes for catalog governance, audit,
                    diagnostics, and instance operations. Feature screens will be added only
                    after documented Cadentia API contracts exist.
                </p>
            </header>

            <section aria-labelledby="admin-nav-title" className="admin-shell__panel">
                <h2 id="admin-nav-title">Protected route groups</h2>
                <nav aria-label="Admin sections">
                    <ul>
                        {adminRoutes.map((route) => (
                            <li key={route.href}>
                                <a href={route.href}>{route.label}</a>
                            </li>
                        ))}
                    </ul>
                </nav>
            </section>

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
