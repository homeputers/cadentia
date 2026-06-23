import { type ChangeEvent, useEffect, useMemo, useState } from 'react';
import { hasCapability } from '../auth/permissions';
import type { AdminSession } from '../auth/session';
import { adminEnvironment } from '../config/environment';
import { createAdminApiClient, type AdminApiClient, type AdminApiError } from '../generated/cadentia-api/client';
import { buildImportCandidateQueuePath, isBlockedCandidate, listImportCandidates, parseImportCandidateFilters, serializeImportCandidateFilters, type ImportCandidateFilterState, type ImportCandidateQueueItem, type ImportCandidateQueueResponse } from '../import-candidates';
import { ActionBadge, AuditReferenceLink, Badge, Breadcrumbs, DataTable, Field, FilterPanel, PageHeader, StatePanel, redactSensitiveError } from './admin-ui';

const options = {
    status: ['', 'STAGED', 'DEDUPLICATION_REVIEW', 'READY_TO_MERGE', 'MERGED', 'REJECTED', 'FAILED'],
    parserSeverity: ['', 'NONE', 'INFO', 'WARNING', 'ERROR'],
    provenanceStatus: ['', 'VERIFIED', 'NEEDS_REVIEW', 'MISSING', 'BLOCKED'],
    duplicateConfidence: ['', 'NONE', 'LOW', 'MEDIUM', 'HIGH'],
    moderationState: ['', 'CLEAR', 'FLAGGED', 'ESCALATED', 'BLOCKED'],
    reviewPriority: ['', 'LOW', 'NORMAL', 'HIGH', 'URGENT'],
    sort: ['submittedAt:desc', 'submittedAt:asc', 'updatedAt:desc', 'reviewPriority:desc', 'duplicateConfidence:desc', 'parserSeverity:desc'],
};

const label = (value?: string | null) => value ? value.replaceAll('_', ' ').toLowerCase().replace(/^./, (c) => c.toUpperCase()) : 'None';
const severityFor = (value: string) => value === 'BLOCKED' || value === 'ERROR' || value === 'HIGH' || value === 'URGENT' ? 'danger' : value === 'READY' || value === 'VERIFIED' || value === 'CLEAR' ? 'success' : value === 'NONE' ? 'neutral' : 'warning';
const pct = (value?: number | null) => typeof value === 'number' ? `${Math.round(value * 100)}%` : 'n/a';

const SelectField = ({ name, labelText, value, onChange }: { name: keyof typeof options; labelText: string; value: string; onChange: (name: string, value: string) => void }) => (
    <Field label={labelText}>{({ inputId }) => <select id={inputId} value={value} onChange={(event) => onChange(name, event.target.value)}>{options[name].map((option) => <option key={option} value={option}>{option ? label(option) : 'Any'}</option>)}</select>}</Field>
);

export const ImportCandidateQueue = ({ session, apiClient = createAdminApiClient({ environment: adminEnvironment, getAccessToken: async () => null }), initialSearch = window.location.search }: { session: AdminSession; apiClient?: AdminApiClient; initialSearch?: string }) => {
    const [filters, setFilters] = useState<ImportCandidateFilterState>(() => parseImportCandidateFilters(initialSearch));
    const [queue, setQueue] = useState<ImportCandidateQueueResponse | null>(null);
    const [state, setState] = useState<'loading' | 'empty' | 'ready' | 'unauthorized' | 'forbidden' | 'stale' | 'error'>('loading');
    const [error, setError] = useState('');

    const canReview = hasCapability(session, 'REVIEW_CATALOG');
    const queuePath = useMemo(() => buildImportCandidateQueuePath(filters), [filters]);

    const load = async (showStale = false) => {
        setState(showStale && queue ? 'stale' : 'loading');
        try {
            const response = await listImportCandidates(apiClient, filters);
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

    useEffect(() => { void load(false); }, [queuePath]);

    const updateFilter = (name: string, value: string) => setFilters((current) => ({ ...current, [name]: value || undefined, page: 1 }));
    const updateInput = (event: ChangeEvent<HTMLInputElement>) => updateFilter(event.target.name, event.target.value.trim());
    const applyUrl = () => {
        const query = serializeImportCandidateFilters(filters);
        window.history.replaceState(null, '', `/admin/imports${query ? `?${query}` : ''}`);
        void load(true);
    };

    const rows = (queue?.items ?? []).map((candidate) => renderRow(candidate, canReview));

    return (
        <main className="admin-shell" aria-labelledby="import-queue-title">
            <Breadcrumbs items={[{ label: 'Admin', href: '/admin' }, { label: 'Import review' }]} />
            <PageHeader eyebrow="Admin review" title="Import candidate queue" titleId="import-queue-title" description="Triage candidates with server-backed filters, safe summaries, duplicate and provenance signals, and audit links without exposing lyrics or raw connector payloads." />
            <FilterPanel title="Queue filters" onSubmit={applyUrl}>
                <SelectField name="status" labelText="Status" value={filters.status ?? ''} onChange={updateFilter} />
                <Field label="Connector">{({ inputId }) => <input id={inputId} name="connectorKey" value={filters.connectorKey ?? ''} onChange={updateInput} />}</Field>
                <Field label="Batch ID">{({ inputId }) => <input id={inputId} name="batchId" value={filters.batchId ?? ''} onChange={updateInput} />}</Field>
                <Field label="Assigned reviewer">{({ inputId }) => <input id={inputId} name="assignedReviewerId" value={filters.assignedReviewerId ?? ''} onChange={updateInput} />}</Field>
                <Field label="Submitted from">{({ inputId }) => <input id={inputId} type="date" name="submittedFrom" value={filters.submittedFrom ?? ''} onChange={updateInput} />}</Field>
                <Field label="Submitted to">{({ inputId }) => <input id={inputId} type="date" name="submittedTo" value={filters.submittedTo ?? ''} onChange={updateInput} />}</Field>
                <SelectField name="parserSeverity" labelText="Parser severity" value={filters.parserSeverity ?? ''} onChange={updateFilter} />
                <SelectField name="provenanceStatus" labelText="Provenance" value={filters.provenanceStatus ?? ''} onChange={updateFilter} />
                <SelectField name="duplicateConfidence" labelText="Duplicate confidence" value={filters.duplicateConfidence ?? ''} onChange={updateFilter} />
                <SelectField name="moderationState" labelText="Moderation" value={filters.moderationState ?? ''} onChange={updateFilter} />
                <SelectField name="reviewPriority" labelText="Priority" value={filters.reviewPriority ?? ''} onChange={updateFilter} />
                <SelectField name="sort" labelText="Sort" value={filters.sort} onChange={updateFilter} />
            </FilterPanel>
            <p role="status">Shareable queue URL state: <code>{`/admin/imports${serializeImportCandidateFilters(filters) ? `?${serializeImportCandidateFilters(filters)}` : ''}`}</code></p>
            <StatePanel state={state} title="Import candidates" onRetry={() => void load(true)}>{error && <p>{error}</p>}</StatePanel>
            {queue && rows.length > 0 && <><p>{queue.totalItems} server-matched candidates. Page {queue.page} of {queue.totalPages}.</p><DataTable caption="Import candidate triage queue" columns={['Candidate', 'Status', 'Signals', 'Reviewer', 'Readiness', 'Actions', 'Audit']} rows={rows} /></>}
            {queue && queue.totalPages > 1 && <nav aria-label="Pagination"><button disabled={filters.page <= 1} onClick={() => setFilters({ ...filters, page: filters.page - 1 })}>Previous</button><button disabled={filters.page >= queue.totalPages} onClick={() => setFilters({ ...filters, page: filters.page + 1 })}>Next</button></nav>}
        </main>
    );
};

const renderRow = (candidate: ImportCandidateQueueItem, canReview: boolean) => [
    <><a href={`/admin/imports/${encodeURIComponent(candidate.candidateId)}`}>{candidate.normalizedTitle || candidate.rawTitle}</a><br /><small>{candidate.connectorKey} · batch {candidate.importBatchId}</small></>,
    <Badge severity={severityFor(candidate.status)}>{label(candidate.status)}</Badge>,
    <><Badge severity={severityFor(candidate.parserSeverity)}>{label(candidate.parserSeverity)} parser · {pct(candidate.parserConfidence)} · {candidate.parserWarningCount ?? 0} warnings</Badge><br /><Badge severity={severityFor(candidate.duplicateConfidence)}>{label(candidate.duplicateConfidence)} duplicate · {candidate.duplicateMatchCount ?? 0} matches · top {pct(candidate.duplicateTopScore)}</Badge><br /><Badge severity={severityFor(candidate.provenanceStatus)}>{label(candidate.provenanceStatus)} provenance</Badge> <small>{candidate.provenanceSummary ?? 'Summary unavailable'}</small></>,
    candidate.assignedReviewerName ?? candidate.assignedReviewerId ?? 'Unassigned',
    <><Badge severity={severityFor(candidate.approvalReadiness)}>{label(candidate.approvalReadiness)}</Badge><br /><small>{isBlockedCandidate(candidate) ? 'Blocked from row-level approval; open detail for review.' : candidate.readinessSummary ?? 'Open detail before approval.'}</small></>,
    <>{candidate.allowedActions.filter((action) => action !== 'APPROVE' && action !== 'ROLLBACK').map((action) => <ActionBadge key={action} capability={canReview ? 'REVIEW_CATALOG' : 'VIEW_IMPORT_QUEUE'} />)}{!canReview && <small> Detail view only</small>}</>,
    candidate.auditReferenceId ? <AuditReferenceLink auditId={candidate.auditReferenceId} /> : 'Pending audit event',
];

export { parseImportCandidateFilters, serializeImportCandidateFilters };
