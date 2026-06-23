import { type FormEvent, type ReactNode, useEffect, useId, useRef } from 'react';
import type { AdminCapability, AdminRole } from '../auth/session';
import type { AdminEnvironment } from '../config/environment';

export type LoadState = 'loading' | 'empty' | 'ready' | 'partial-failure' | 'stale' | 'unauthorized' | 'forbidden' | 'error';
export type Severity = 'neutral' | 'success' | 'warning' | 'danger';

export const redactSensitiveError = (message?: string): string => {
    if (!message) {
        return 'The request could not be completed. Retry or contact support with the reference code.';
    }
    return message
        .replace(/Bearer\s+[A-Za-z0-9._~+/=-]+/gi, 'Bearer [redacted]')
        .replace(/(token|secret|password|rawPayload|connectorPayload)=([^\s&]+)/gi, '$1=[redacted]')
        .replace(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/g, '[redacted-email]');
};

export const ScreenReaderText = ({ children }: { children: ReactNode }) => <span className="sr-only">{children}</span>;

export const Breadcrumbs = ({ items }: { items: Array<{ label: string; href?: string }> }) => (
    <nav aria-label="Breadcrumb" className="admin-breadcrumbs">
        <ol>
            {items.map((item, index) => (
                <li key={`${item.label}-${index}`}>
                    {item.href && index < items.length - 1 ? <a href={item.href}>{item.label}</a> : <span aria-current="page">{item.label}</span>}
                </li>
            ))}
        </ol>
    </nav>
);

export const PageHeader = ({ eyebrow, title, description, actions, titleId }: { eyebrow?: string; title: string; description?: string; actions?: ReactNode; titleId?: string }) => (
    <header className="admin-page-header">
        {eyebrow && <p className="admin-shell__eyebrow">{eyebrow}</p>}
        <div>
            <h1 id={titleId}>{title}</h1>
            {description && <p>{description}</p>}
        </div>
        {actions && <div className="admin-page-header__actions">{actions}</div>}
    </header>
);

export const FilterPanel = ({ title, children, onSubmit }: { title: string; children: ReactNode; onSubmit?: () => void }) => {
    const headingId = useId();
    const handleSubmit = (event: FormEvent) => {
        event.preventDefault();
        onSubmit?.();
    };
    return (
        <form className="admin-filter-panel" aria-labelledby={headingId} onSubmit={handleSubmit}>
            <h2 id={headingId}>{title}</h2>
            <div className="admin-filter-panel__fields">{children}</div>
            <button type="submit">Apply filters</button>
        </form>
    );
};

export const Field = ({ label, error, children }: { label: string; error?: string; children: (ids: { inputId: string; errorId?: string }) => ReactNode }) => {
    const inputId = useId();
    const errorId = error ? `${inputId}-error` : undefined;
    return (
        <div className="admin-field">
            <label htmlFor={inputId}>{label}</label>
            {children({ inputId, errorId })}
            {error && <p id={errorId} role="alert" className="admin-field__error">{error}</p>}
        </div>
    );
};

export const DataTable = ({ caption, columns, rows }: { caption: string; columns: string[]; rows: Array<Array<ReactNode>> }) => (
    <div className="admin-table-wrap">
        <table className="admin-table">
            <caption>{caption}</caption>
            <thead>
                <tr>{columns.map((column) => <th key={column} scope="col">{column}</th>)}</tr>
            </thead>
            <tbody>{rows.map((row, rowIndex) => <tr key={rowIndex}>{row.map((cell, cellIndex) => <td key={cellIndex}>{cell}</td>)}</tr>)}</tbody>
        </table>
    </div>
);

export const Badge = ({ children, severity = 'neutral' }: { children: ReactNode; severity?: Severity }) => (
    <span className={`admin-badge admin-badge--${severity}`}>{children}</span>
);

export const RoleBadge = ({ role }: { role: AdminRole }) => <Badge severity="neutral"><ScreenReaderText>Role: </ScreenReaderText>{role.replaceAll('_', ' ')}</Badge>;
export const ActionBadge = ({ capability }: { capability: AdminCapability }) => <Badge severity="success"><ScreenReaderText>Allowed action: </ScreenReaderText>{capability.replaceAll('_', ' ')}</Badge>;

export const StatePanel = ({ state, title, children, onRetry }: { state: LoadState; title: string; children?: ReactNode; onRetry?: () => void }) => {
    const copy: Record<LoadState, string> = {
        loading: 'Loading administrative data…',
        empty: 'No records match the current filters.',
        ready: '',
        'partial-failure': 'Some data could not be loaded. Available rows may be incomplete.',
        stale: 'Showing cached data while fresh data loads.',
        unauthorized: 'Sign in is required before protected details can be loaded.',
        forbidden: 'You do not have access to this administrative resource. No protected details were loaded.',
        error: 'The request failed with a redacted error message.',
    };
    if (state === 'ready') {
        return <>{children}</>;
    }
    return (
        <section className={`admin-state admin-state--${state}`} aria-labelledby={`${title}-state-title`} role={state === 'loading' ? 'status' : 'alert'}>
            <h2 id={`${title}-state-title`}>{title}</h2>
            {state === 'loading' && <div className="admin-skeleton" aria-hidden="true" />}
            <p>{copy[state]}</p>
            {onRetry && ['partial-failure', 'stale', 'error'].includes(state) && <button type="button" onClick={onRetry}>Retry</button>}
        </section>
    );
};

export const ConfirmationDialog = ({ open, title, acknowledgement, facts, auditActor, versionContext, onCancel, onConfirm }: {
    open: boolean; title: string; acknowledgement: string; facts: string[]; auditActor: string; versionContext: string; onCancel: () => void; onConfirm: () => void;
}) => {
    const headingRef = useRef<HTMLHeadingElement>(null);
    useEffect(() => { if (open) headingRef.current?.focus(); }, [open]);
    if (!open) return null;
    return (
        <div className="admin-dialog-backdrop" role="presentation">
            <section className="admin-dialog" role="dialog" aria-modal="true" aria-labelledby="confirm-title">
                <h2 id="confirm-title" tabIndex={-1} ref={headingRef}>{title}</h2>
                <p><strong>Required acknowledgement:</strong> {acknowledgement}</p>
                <ul>{facts.map((fact) => <li key={fact}>{fact}</li>)}</ul>
                <p>Audit actor: {auditActor}</p>
                <p>Concurrency/version: {versionContext}</p>
                <div className="admin-dialog__actions"><button type="button" onClick={onCancel}>Cancel</button><button type="button" className="danger" onClick={onConfirm}>I understand, continue</button></div>
            </section>
        </div>
    );
};

export const DiffPanel = ({ before, after }: { before: string[]; after: string[] }) => (
    <section className="admin-diff" aria-labelledby="diff-title">
        <h2 id="diff-title">Previewed changes</h2>
        <div><h3>Before</h3><ul>{before.map((line) => <li key={line}>− {line}</li>)}</ul></div>
        <div><h3>After</h3><ul>{after.map((line) => <li key={line}>+ {line}</li>)}</ul></div>
    </section>
);

export const AuditReferenceLink = ({ auditId }: { auditId: string }) => <a href={`/admin/audit?event=${encodeURIComponent(auditId)}`}>Audit reference {auditId}</a>;

export const SupportDebugPanel = ({ environment }: { environment: AdminEnvironment }) => (
    <details className="admin-debug">
        <summary>Support/debug metadata</summary>
        <dl>
            <dt>Build version</dt><dd>{environment.buildVersion}</dd>
            <dt>Build commit</dt><dd>{environment.buildCommit}</dd>
            <dt>Build timestamp</dt><dd>{environment.buildTimestamp}</dd>
            <dt>Diagnostics</dt><dd>{environment.diagnosticsEnabled ? 'enabled' : 'disabled'}</dd>
            <dt>API base URL</dt><dd>{environment.apiBaseUrl ? 'configured' : 'missing'}</dd>
        </dl>
        <p>No secrets, tokens, raw payloads, copyrighted lyrics, or sensitive diagnostics are rendered here.</p>
    </details>
);
