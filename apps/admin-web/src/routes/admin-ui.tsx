import { type FormEvent, type KeyboardEvent, type ReactNode, useEffect, useId, useRef } from 'react';
import type { AdminCapability, AdminRole } from '../auth/session';
import type { AdminEnvironment } from '../config/environment';
import { localizedCapability, localizedRole, translateText, useI18n } from '../i18n';

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

export const Breadcrumbs = ({ items }: { items: Array<{ label: string; href?: string }> }) => {
    const { t, locale } = useI18n();
    return <nav aria-label={t('breadcrumb')} className="admin-breadcrumbs">
        <ol>
            {items.map((item, index) => (
                <li key={`${item.label}-${index}`}>
                    {item.href && index < items.length - 1 ? <a href={item.href}>{translateText(locale, item.label)}</a> : <span aria-current="page">{translateText(locale, item.label)}</span>}
                </li>
            ))}
        </ol>
    </nav>;
};

export const PageHeader = ({ eyebrow, title, description, actions, titleId }: { eyebrow?: string; title: string; description?: string; actions?: ReactNode; titleId?: string }) => {
    const { locale } = useI18n();
    return (
    <header className="admin-page-header">
        <div className="admin-page-header__body">
            {eyebrow && <p className="admin-shell__eyebrow">{translateText(locale, eyebrow)}</p>}
            <h1 id={titleId}>{translateText(locale, title)}</h1>
            {description && <p>{translateText(locale, description)}</p>}
        </div>
        {actions && <div className="admin-page-header__actions">{actions}</div>}
    </header>
    );
};

export const FilterPanel = ({ title, children, onSubmit }: { title: string; children: ReactNode; onSubmit?: () => void }) => {
    const { t } = useI18n();
    const headingId = useId();
    const handleSubmit = (event: FormEvent) => {
        event.preventDefault();
        onSubmit?.();
    };
    return (
        <form className="admin-filter-panel" aria-labelledby={headingId} onSubmit={handleSubmit}>
            <h2 id={headingId}>{title}</h2>
            <div className="admin-filter-panel__fields">{children}</div>
            <div className="admin-filter-panel__actions"><button type="submit">{t('applyFilters')}</button></div>
        </form>
    );
};

export const Field = ({ label, error, description, required = false, children }: { label: string; error?: string; description?: string; required?: boolean; children: (ids: { inputId: string; errorId?: string; descriptionId?: string }) => ReactNode }) => {
    const { t, locale } = useI18n();
    const inputId = useId();
    const errorId = error ? `${inputId}-error` : undefined;
    const descriptionId = description ? `${inputId}-description` : undefined;
    return (
        <div className="admin-field">
            <label htmlFor={inputId}>{translateText(locale, label)}{required && <span className="admin-field__required" aria-label={t('required')}> *</span>}</label>
            {description && <p id={descriptionId} className="admin-field__description">{translateText(locale, description)}</p>}
            {children({ inputId, errorId, descriptionId })}
            {error && <p id={errorId} role="alert" className="admin-field__error">{error}</p>}
        </div>
    );
};

export const DataTable = ({ caption, columns, rows }: { caption: string; columns: string[]; rows: Array<Array<ReactNode>> }) => {
    const { locale } = useI18n();
    return (
    <div className="admin-table-wrap">
        <table className="admin-table">
            <caption>{translateText(locale, caption)}</caption>
            <thead>
                <tr>{columns.map((column) => <th key={column} scope="col">{translateText(locale, column)}</th>)}</tr>
            </thead>
            <tbody>{rows.map((row, rowIndex) => <tr key={rowIndex}>{row.map((cell, cellIndex) => <td key={cellIndex}>{cell}</td>)}</tr>)}</tbody>
        </table>
    </div>
    );
};

export const Badge = ({ children, severity = 'neutral' }: { children: ReactNode; severity?: Severity }) => (
    <span className={`admin-badge admin-badge--${severity}`}>{children}</span>
);

export const RoleBadge = ({ role }: { role: AdminRole }) => { const { t, locale } = useI18n(); return <Badge severity="neutral"><ScreenReaderText>{t('role')}: </ScreenReaderText>{localizedRole(locale, role)}</Badge>; };
export const ActionBadge = ({ capability }: { capability: AdminCapability }) => { const { t, locale } = useI18n(); return <Badge severity="success"><ScreenReaderText>{t('allowedAction')}: </ScreenReaderText>{localizedCapability(locale, capability)}</Badge>; };

export const StatePanel = ({ state, title, children, onRetry }: { state: LoadState; title: string; children?: ReactNode; onRetry?: () => void }) => {
    const { t, locale } = useI18n();
    const copy: Record<LoadState, string> = {
        loading: t('loading'),
        empty: t('empty'),
        ready: '',
        'partial-failure': t('partialFailure'),
        stale: t('stale'),
        unauthorized: t('unauthorized'),
        forbidden: t('forbidden'),
        error: t('error'),
    };
    if (state === 'ready') {
        return <>{children}</>;
    }
    return (
        <section className={`admin-state admin-state--${state}`} aria-labelledby={`${title}-state-title`} role={state === 'loading' ? 'status' : 'alert'}>
            <h2 id={`${title}-state-title`}>{translateText(locale, title)}</h2>
            {state === 'loading' && <div className="admin-skeleton" aria-hidden="true" />}
            <p>{copy[state]}</p>
            {onRetry && ['partial-failure', 'stale', 'error'].includes(state) && <button type="button" className="secondary" onClick={onRetry}>{t('retry')}</button>}
        </section>
    );
};

export const ConfirmationDialog = ({ open, title, acknowledgement, facts, auditActor, versionContext, onCancel, onConfirm }: {
    open: boolean; title: string; acknowledgement: string; facts: string[]; auditActor: string; versionContext?: string; onCancel: () => void; onConfirm: () => void;
}) => {
    const { t, locale } = useI18n();
    const headingRef = useRef<HTMLHeadingElement>(null);
    useEffect(() => { if (open) headingRef.current?.focus(); }, [open]);
    if (!open) return null;
    const onKeyDown = (event: KeyboardEvent) => {
        if (event.key === 'Escape') {
            event.stopPropagation();
            onCancel();
        } else if (event.key === 'Enter' && !(event.target instanceof HTMLButtonElement)) {
            event.preventDefault();
            onConfirm();
        }
    };
    return (
        <div className="admin-dialog-backdrop" role="presentation" onKeyDown={onKeyDown}>
            <section className="admin-dialog" role="dialog" aria-modal="true" aria-labelledby="confirm-title">
                <h2 id="confirm-title" tabIndex={-1} ref={headingRef}>{translateText(locale, title)}</h2>
                <p><strong>{t('requiredAcknowledgement')}</strong> {translateText(locale, acknowledgement)}</p>
                <ul>{facts.map((fact) => <li key={fact}>{translateText(locale, fact)}</li>)}</ul>
                <p>{t('auditActor')} {auditActor}</p>
                {versionContext && <p>{t('concurrency')} {versionContext}</p>}
                <div className="admin-dialog__actions"><button type="button" className="secondary" onClick={onCancel}>{t('cancel')}</button><button type="button" className="danger" onClick={onConfirm}>{t('continue')}</button></div>
            </section>
        </div>
    );
};

export const DiffPanel = ({ before, after }: { before: string[]; after: string[] }) => {
    const { t } = useI18n();
    return (
    <section className="admin-diff" aria-labelledby="diff-title">
        <h2 id="diff-title">{t('previewedChanges')}</h2>
        <div><h3>{t('before')}</h3><ul>{before.map((line) => <li key={line}>− {line}</li>)}</ul></div>
        <div><h3>{t('after')}</h3><ul>{after.map((line) => <li key={line}>+ {line}</li>)}</ul></div>
    </section>
    );
};

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export const AuditReferenceLink = ({ auditId }: { auditId: string }) => {
    const { t } = useI18n();
    return UUID_PATTERN.test(auditId)
        ? <a href={`/admin/audit?event=${encodeURIComponent(auditId)}`}>{t('auditReference')} {auditId}</a>
        : <span>{t('auditReference')} <code>{auditId}</code></span>;
};

export const SupportDebugPanel = ({ environment }: { environment: AdminEnvironment }) => {
    const { t } = useI18n();
    return (
    <details className="admin-debug">
        <summary>{t('supportDebug')}</summary>
        <dl>
            <dt>{t('buildVersion')}</dt><dd>{environment.buildVersion}</dd>
            <dt>{t('buildCommit')}</dt><dd>{environment.buildCommit}</dd>
            <dt>{t('buildTimestamp')}</dt><dd>{environment.buildTimestamp}</dd>
            <dt>{t('diagnostics')}</dt><dd>{environment.diagnosticsEnabled ? t('enabled') : t('disabled')}</dd>
            <dt>API base URL</dt><dd>{environment.apiBaseUrl ? t('configured') : t('missing')}</dd>
        </dl>
        <p>{t('noSecrets')}</p>
    </details>
    );
};
