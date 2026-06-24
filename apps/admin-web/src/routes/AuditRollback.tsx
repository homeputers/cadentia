import { type ChangeEvent, useEffect, useMemo, useState } from 'react';
import { hasCapability } from '../auth/permissions';
import type { AdminSession } from '../auth/session';
import { adminEnvironment } from '../config/environment';
import { createAdminApiClient, type AdminApiClient, type AdminApiError } from '../generated/cadentia-api/client';
import { buildAuditHistoryPath, createRollbackPreview, executeRollback, parseAuditFilters, redactAuditSummary, searchAuditEvents, serializeAuditFilters, type AuditFilters, type RollbackPreviewResponse } from '../audit-rollback';
import { AuditReferenceLink, Badge, Breadcrumbs, DataTable, Field, FilterPanel, PageHeader, StatePanel, redactSensitiveError } from './admin-ui';

const update = (filters: AuditFilters, name: string, value: string): AuditFilters => ({ ...filters, [name]: value || undefined, page: 1 });

export const AuditRollback = ({ session, apiClient = createAdminApiClient({ environment: adminEnvironment, getAccessToken: async () => null }), initialSearch = window.location.search }: { session: AdminSession; apiClient?: AdminApiClient; initialSearch?: string }) => {
    const [filters, setFilters] = useState(() => parseAuditFilters(initialSearch));
    const [events, setEvents] = useState<Awaited<ReturnType<typeof searchAuditEvents>> | null>(null);
    const [state, setState] = useState<'loading' | 'ready' | 'empty' | 'forbidden' | 'unauthorized' | 'error' | 'stale'>('loading');
    const [error, setError] = useState('');
    const [preview, setPreview] = useState<RollbackPreviewResponse | null>(null);
    const [rollbackForm, setRollbackForm] = useState({ targetType: 'IMPORT_BATCH', targetId: '', importBatchId: '', reason: '', confirmation: '' });
    const [rollbackMessage, setRollbackMessage] = useState('');
    const path = useMemo(() => buildAuditHistoryPath(filters), [filters]);
    const canPreview = hasCapability(session, 'PREVIEW_ROLLBACK');
    const canExecute = hasCapability(session, 'EXECUTE_ROLLBACK');

    const load = async (stale = false) => {
        setState(stale && events ? 'stale' : 'loading');
        try {
            const result = await searchAuditEvents(apiClient, filters);
            setEvents(result);
            setState(result.items.length ? 'ready' : 'empty');
        } catch (caught) {
            const apiError = caught as AdminApiError;
            setError(redactSensitiveError(apiError.message));
            setState(apiError.status === 401 ? 'unauthorized' : apiError.status === 403 ? 'forbidden' : 'error');
        }
    };
    useEffect(() => { void load(false); }, [path]);
    const onInput = (event: ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setFilters((current) => update(current, event.target.name, event.target.value.trim()));
    const applyUrl = () => { const query = serializeAuditFilters(filters); window.history.replaceState(null, '', `/admin/audit${query ? `?${query}` : ''}`); void load(true); };
    const makePreview = async () => {
        setRollbackMessage('');
        const result = await createRollbackPreview(apiClient, { targetType: rollbackForm.targetType, targetId: rollbackForm.targetId, importBatchId: rollbackForm.importBatchId || undefined, reason: rollbackForm.reason || undefined }, session.actorId);
        setPreview(result);
        setRollbackForm((current) => ({ ...current, confirmation: '' }));
    };
    const runRollback = async () => {
        if (!preview || rollbackForm.confirmation !== preview.rollbackRequestId) return setRollbackMessage('Type the exact backend rollback request ID before execution.');
        try {
            const result = await executeRollback(apiClient, preview.rollbackRequestId, session.actorId, rollbackForm.reason);
            setRollbackMessage(`Rollback ${result.action} audited as ${result.auditEventId}.`);
            setFilters((current) => ({ ...current, rollbackRequestId: result.rollbackRequestId, page: 1 }));
            await load(true);
        } catch (caught) {
            const status = (caught as AdminApiError).status;
            setRollbackMessage(status === 409 || status === 412 ? 'Preview is stale or conflicted. Create a fresh backend preview before retrying.' : status === 403 ? 'You are not authorized to execute this rollback.' : status === 400 || status === 422 ? 'Rollback validation failed. Review blockers and request a new preview.' : 'Rollback failed safely before execution could be confirmed. Retry with the same backend preview only if it remains valid.');
        }
    };

    const rows = (events?.items ?? []).map((event) => [
        <><AuditReferenceLink auditId={event.auditReferenceId ?? event.id} /><br /><small>{event.correlationId && `Correlation ${event.correlationId}`} {event.causationId && `· Causation ${event.causationId}`}</small></>,
        <><Badge severity="neutral">{event.action}</Badge><br /><time dateTime={event.occurredAt}>{event.occurredAt}</time></>,
        event.actor,
        <>{event.entityType} <code>{event.entityId}</code><br />{event.relatedLinks?.map((link) => <a key={link.href} href={link.href}>{link.label}</a>)}</>,
        redactAuditSummary(event.redactedSummary ?? event.payloadSummary),
    ]);

    return <main className="admin-shell" aria-labelledby="audit-title">
        <Breadcrumbs items={[{ label: 'Admin', href: '/admin' }, { label: 'Audit history' }]} />
        <PageHeader eyebrow="Governance" title="Audit history and rollback" titleId="audit-title" description="Search append-only audit events and execute rollbacks only from backend previews with explicit request-ID confirmation." />
        <FilterPanel title="Audit filters" onSubmit={applyUrl}>{['event', 'entityType', 'entityId', 'actor', 'action', 'from', 'to', 'importBatchId', 'candidateId', 'songId', 'arrangementId', 'moderationFlagId', 'rollbackRequestId'].map((name) => <Field key={name} label={name}>{({ inputId }) => <input id={inputId} name={name} value={String(filters[name as keyof AuditFilters] ?? '')} onChange={onInput} />}</Field>)}</FilterPanel>
        <p role="status">Deep link: <code>{`/admin/audit${serializeAuditFilters(filters) ? `?${serializeAuditFilters(filters)}` : ''}`}</code></p>
        <StatePanel state={state} title="Audit events" onRetry={() => void load(true)}>{error && <p>{error}</p>}</StatePanel>
        {rows.length > 0 && <DataTable caption="Redacted audit event history" columns={['Audit reference', 'Action', 'Actor', 'Target', 'Redacted summary']} rows={rows} />}
        <section className="admin-shell__panel" aria-labelledby="rollback-title"><h2 id="rollback-title">High-risk rollback</h2>{!canPreview && <p>Rollback preview requires PREVIEW_ROLLBACK.</p>}{canPreview && <><Field label="Target type">{({ inputId }) => <select id={inputId} value={rollbackForm.targetType} onChange={(e) => setRollbackForm({ ...rollbackForm, targetType: e.target.value })}><option>IMPORT_BATCH</option><option>CANDIDATE</option><option>SONG</option><option>ARRANGEMENT</option><option>MODERATION_FLAG</option></select>}</Field><Field label="Target ID">{({ inputId }) => <input id={inputId} value={rollbackForm.targetId} onChange={(e) => setRollbackForm({ ...rollbackForm, targetId: e.target.value })} />}</Field><Field label="Import batch ID">{({ inputId }) => <input id={inputId} value={rollbackForm.importBatchId} onChange={(e) => setRollbackForm({ ...rollbackForm, importBatchId: e.target.value })} />}</Field><Field label="Reason">{({ inputId }) => <input id={inputId} value={rollbackForm.reason} onChange={(e) => setRollbackForm({ ...rollbackForm, reason: e.target.value })} />}</Field><button type="button" onClick={() => void makePreview()}>Create backend rollback preview</button></>}{preview && <section aria-labelledby="preview-title"><h3 id="preview-title">Backend rollback preview {preview.rollbackRequestId}</h3><p>Expires: {preview.expiresAt ?? 'not provided'} · Version: {preview.versionContext ?? 'server controlled'} · Audit: {preview.auditReferenceId ? <AuditReferenceLink auditId={preview.auditReferenceId} /> : 'pending execution'}</p><p>Eligibility impacted: {preview.eligibilityImpacted ? 'yes' : 'no'} · Required permissions: {(preview.requiredPermissions ?? ['EXECUTE_ROLLBACK']).join(', ')}</p><ul>{(preview.impactedRecords ?? []).map((record) => <li key={`${record.entityType}-${record.entityId}`}>{record.entityType} {record.entityId}: {record.summary ?? record.status ?? `${record.beforeStatus ?? 'current'} → ${record.afterStatus ?? 'rollback'}`} {record.eligibilityChange}</li>)}</ul>{[...(preview.blockers ?? []), ...(preview.conflicts ?? []), ...(preview.irreversibleWarnings ?? [])].map((warning) => <p key={warning} role="alert"><Badge severity="danger">{warning}</Badge></p>)}<Field label={`Type rollback request ID ${preview.rollbackRequestId}`}>{({ inputId }) => <input id={inputId} value={rollbackForm.confirmation} onInput={(e) => setRollbackForm({ ...rollbackForm, confirmation: e.currentTarget.value })} />}</Field><button type="button" className="danger" disabled={!canExecute || rollbackForm.confirmation !== preview.rollbackRequestId || Boolean(preview.blockers?.length)} onClick={() => void runRollback()}>Execute high-risk rollback from backend preview</button></section>}{rollbackMessage && <p role="alert">{rollbackMessage}</p>}</section>
    </main>;
};
