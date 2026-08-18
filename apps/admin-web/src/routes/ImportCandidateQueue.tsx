import { type ChangeEvent, useEffect, useMemo, useState } from 'react';
import { hasCapability } from '../auth/permissions';
import type { AdminSession } from '../auth/session';
import { adminEnvironment } from '../config/environment';
import { createAdminApiClient, type AdminApiClient, type AdminApiError } from '../generated/cadentia-api/client';
import { buildImportCandidateQueuePath, isBlockedCandidate, listImportCandidates, parseImportCandidateFilters, serializeImportCandidateFilters, submitBulkAction, type BulkActionRequest, type BulkActionResponse, type BulkActionType, type ImportCandidateFilterState, type ImportCandidateQueueItem, type ImportCandidateQueueResponse } from '../import-candidates';
import { ActionBadge, AuditReferenceLink, Badge, Breadcrumbs, ConfirmationDialog, DataTable, Field, FilterPanel, PageHeader, StatePanel, redactSensitiveError } from './admin-ui';

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
    const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
    const [bulkActionOpen, setBulkActionOpen] = useState(false);
    const [bulkActionType, setBulkActionType] = useState<BulkActionType>('ASSIGN_REVIEWER');
    const [bulkReviewerId, setBulkReviewerId] = useState('');
    const [bulkRationale, setBulkRationale] = useState('');
    const [bulkFlagType, setBulkFlagType] = useState('METADATA_CONFLICT');
    const [bulkFlagScope, setBulkFlagScope] = useState('IMPORT_CANDIDATE');
    const [bulkFlagReason, setBulkFlagReason] = useState('');
    const [bulkFlagPolicy, setBulkFlagPolicy] = useState('BLOCK_UNTIL_RESOLVED');
    const [bulkState, setBulkState] = useState<'idle' | 'saving' | 'error'>('idle');
    const [bulkResult, setBulkResult] = useState<BulkActionResponse | null>(null);

    const canReview = hasCapability(session, 'REVIEW_CATALOG');
    const queuePath = useMemo(() => buildImportCandidateQueuePath(filters), [filters]);
    const allSelected = queue !== null && queue.items.length > 0 && queue.items.every((item) => selectedIds.has(item.candidateId));
    const selectedCount = selectedIds.size;

    const toggleSelect = (candidateId: string) => {
        setSelectedIds((prev) => {
            const next = new Set(prev);
            if (next.has(candidateId)) next.delete(candidateId);
            else next.add(candidateId);
            return next;
        });
    };

    const toggleSelectAll = () => {
        if (!queue) return;
        if (allSelected) {
            setSelectedIds((prev) => {
                const next = new Set(prev);
                queue.items.forEach((item) => next.delete(item.candidateId));
                return next;
            });
        } else {
            setSelectedIds((prev) => {
                const next = new Set(prev);
                queue.items.forEach((item) => next.add(item.candidateId));
                return next;
            });
        }
    };

    const resetBulkForm = () => {
        setBulkActionType('ASSIGN_REVIEWER');
        setBulkReviewerId('');
        setBulkRationale('');
        setBulkFlagType('METADATA_CONFLICT');
        setBulkFlagScope('IMPORT_CANDIDATE');
        setBulkFlagReason('');
        setBulkFlagPolicy('BLOCK_UNTIL_RESOLVED');
        setBulkState('idle');
    };

    const openBulkAction = () => {
        resetBulkForm();
        setBulkActionOpen(true);
    };

    const submitBulk = async () => {
        setBulkState('saving');
        try {
            const request: BulkActionRequest = {
                actionType: bulkActionType,
                candidateIds: Array.from(selectedIds),
                actor: session.actorId,
                assignedReviewerId: bulkActionType === 'ASSIGN_REVIEWER' ? bulkReviewerId || null : null,
                flagType: bulkActionType === 'OPEN_MODERATION_FLAG' ? bulkFlagType : null,
                flagScope: bulkActionType === 'OPEN_MODERATION_FLAG' ? bulkFlagScope : null,
                flagReason: bulkActionType === 'OPEN_MODERATION_FLAG' ? bulkFlagReason || null : null,
                flagPolicy: bulkActionType === 'OPEN_MODERATION_FLAG' ? bulkFlagPolicy : null,
                rationale: ['REJECT_DUPLICATE', 'REJECT_NOT_PERMITTED', 'DEFER'].includes(bulkActionType) ? bulkRationale || null : null,
            };
            const response = await submitBulkAction(apiClient, request);
            setBulkResult(response);
            setBulkState('idle');
            setSelectedIds(new Set());
            void load(true);
        } catch (caught) {
            setBulkState('error');
        }
    };

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

    const rows = (queue?.items ?? []).map((candidate) => renderRow(candidate, canReview, selectedIds.has(candidate.candidateId), () => toggleSelect(candidate.candidateId)));

    const bulkDisabledReason = !canReview
        ? 'Catalog review capability is required for bulk actions.'
        : selectedCount === 0
            ? 'Select at least one candidate before choosing a bulk action.'
            : '';

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
            {canReview && (
                <section className="admin-shell__panel" aria-labelledby="bulk-actions-title">
                    <h2 id="bulk-actions-title">Bulk actions</h2>
                    <p>{selectedCount} selected on this page.</p>
                    <button type="button" disabled={Boolean(bulkDisabledReason)} onClick={openBulkAction} aria-describedby={bulkDisabledReason ? 'bulk-disabled-reason' : undefined}>Open bulk action</button>
                    {bulkDisabledReason && <p id="bulk-disabled-reason" className="admin-action-hint">{bulkDisabledReason}</p>}
                </section>
            )}
            {queue && rows.length > 0 && <><p>{queue.totalItems} server-matched candidates. Page {queue.page} of {queue.totalPages}.</p><DataTable caption="Import candidate triage queue" columns={[<input type="checkbox" aria-label="Select all candidates on this page" checked={allSelected} onChange={toggleSelectAll} />, 'Candidate', 'Status', 'Signals', 'Reviewer', 'Readiness', 'Actions', 'Audit']} rows={rows} /></>}
            {queue && queue.totalPages > 1 && <nav aria-label="Pagination"><button disabled={filters.page <= 1} onClick={() => setFilters({ ...filters, page: filters.page - 1 })}>Previous</button><button disabled={filters.page >= queue.totalPages} onClick={() => setFilters({ ...filters, page: filters.page + 1 })}>Next</button></nav>}
            <ConfirmationDialog open={bulkActionOpen} title="Confirm bulk action" acknowledgement="This action will be applied to all selected candidates and will create audit records." facts={[`Action type: ${label(bulkActionType)}`, `Selected candidates: ${selectedCount}`, ...(bulkActionType === 'ASSIGN_REVIEWER' ? [`Assigned reviewer: ${bulkReviewerId || 'Unchanged'}`] : []), ...(bulkActionType === 'OPEN_MODERATION_FLAG' ? [`Flag type: ${bulkFlagType}`, `Policy: ${bulkFlagPolicy}`] : []), ...(['REJECT_DUPLICATE', 'REJECT_NOT_PERMITTED', 'DEFER'].includes(bulkActionType) ? [`Rationale: ${bulkRationale || 'None provided'}`] : [])]} auditActor={session.actorId} versionContext={`Bulk action on ${selectedCount} candidate${selectedCount === 1 ? '' : 's'}`} onCancel={() => { setBulkActionOpen(false); setBulkResult(null); }} onConfirm={() => void submitBulk()} />
            {bulkActionOpen && (
                <div className="admin-dialog-backdrop" role="presentation">
                    <section className="admin-dialog" role="dialog" aria-modal="true" aria-labelledby="bulk-dialog-title">
                        <h2 id="bulk-dialog-title">Configure bulk action</h2>
                        <Field label="Action type">{({ inputId }) => <select id={inputId} value={bulkActionType} onChange={(e) => setBulkActionType(e.target.value as BulkActionType)}><option value="ASSIGN_REVIEWER">Assign reviewer</option><option value="REJECT_DUPLICATE">Reject duplicate</option><option value="REJECT_NOT_PERMITTED">Reject not permitted</option><option value="DEFER">Defer</option><option value="OPEN_MODERATION_FLAG">Open moderation flag</option></select>}</Field>
                        {bulkActionType === 'ASSIGN_REVIEWER' && <Field label="Reviewer ID">{({ inputId }) => <input id={inputId} value={bulkReviewerId} onChange={(e) => setBulkReviewerId(e.target.value)} />}</Field>}
                        {['REJECT_DUPLICATE', 'REJECT_NOT_PERMITTED', 'DEFER'].includes(bulkActionType) && <Field label="Rationale">{({ inputId }) => <textarea id={inputId} value={bulkRationale} onChange={(e) => setBulkRationale(e.target.value)} />}</Field>}
                        {bulkActionType === 'OPEN_MODERATION_FLAG' && <><Field label="Flag type">{({ inputId }) => <select id={inputId} value={bulkFlagType} onChange={(e) => setBulkFlagType(e.target.value)}><option>BAD_SOURCE</option><option>LICENSING_CONCERN</option><option>INCORRECT_LYRICS</option><option>METADATA_CONFLICT</option><option>DOCTRINAL_CONCERN</option><option>PARSER_ISSUE</option></select>}</Field><Field label="Flag scope">{({ inputId }) => <input id={inputId} value={bulkFlagScope} onChange={(e) => setBulkFlagScope(e.target.value)} />}</Field><Field label="Flag reason">{({ inputId }) => <textarea id={inputId} value={bulkFlagReason} onChange={(e) => setBulkFlagReason(e.target.value)} />}</Field><Field label="Eligibility impact policy">{({ inputId }) => <select id={inputId} value={bulkFlagPolicy} onChange={(e) => setBulkFlagPolicy(e.target.value)}><option>BLOCK_UNTIL_RESOLVED</option><option>REVIEW_ONLY</option><option>SUPPRESS_RECOMMENDATION</option></select>}</Field></>}
                        <div className="admin-dialog__actions"><button type="button" onClick={() => setBulkActionOpen(false)}>Cancel</button><button type="button" disabled={bulkState === 'saving'} onClick={() => void submitBulk()}>Submit bulk action</button></div>
                        {bulkState === 'error' && <p role="alert">Bulk action failed. No protected details were exposed.</p>}
                        {bulkResult && (
                            <div role="status">
                                <p>Processed {bulkResult.processedCount}: {bulkResult.successCount} succeeded, {bulkResult.failureCount} failed.</p>
                                {bulkResult.results.filter((r) => !r.success).map((r) => <p key={r.candidateId} role="alert"><Badge severity="danger">Failed</Badge> {r.candidateId}: {r.errorMessage}</p>)}
                            </div>
                        )}
                    </section>
                </div>
            )}
        </main>
    );
};

const renderRow = (candidate: ImportCandidateQueueItem, canReview: boolean, isSelected: boolean, onToggle: () => void) => [
    <input type="checkbox" aria-label={`Select candidate ${candidate.normalizedTitle || candidate.rawTitle}`} checked={isSelected} onChange={onToggle} />,
    <><a href={`/admin/imports/${encodeURIComponent(candidate.candidateId)}`}>{candidate.normalizedTitle || candidate.rawTitle}</a><br /><small>{candidate.connectorKey} · batch {candidate.importBatchId}</small></>,
    <Badge severity={severityFor(candidate.status)}>{label(candidate.status)}</Badge>,
    <><Badge severity={severityFor(candidate.parserSeverity)}>{label(candidate.parserSeverity)} parser · {pct(candidate.parserConfidence)} · {candidate.parserWarningCount ?? 0} warnings</Badge><br /><Badge severity={severityFor(candidate.duplicateConfidence)}>{label(candidate.duplicateConfidence)} duplicate · {candidate.duplicateMatchCount ?? 0} matches · top {pct(candidate.duplicateTopScore)}</Badge><br /><Badge severity={severityFor(candidate.provenanceStatus)}>{label(candidate.provenanceStatus)} provenance</Badge> <small>{candidate.provenanceSummary ?? 'Summary unavailable'}</small></>,
    candidate.assignedReviewerName ?? candidate.assignedReviewerId ?? 'Unassigned',
    <><Badge severity={severityFor(candidate.approvalReadiness)}>{label(candidate.approvalReadiness)}</Badge><br /><small>{isBlockedCandidate(candidate) ? 'Blocked from row-level approval; open detail for review.' : candidate.readinessSummary ?? 'Open detail before approval.'}</small></>,
    <>{candidate.allowedActions.filter((action) => action !== 'APPROVE' && action !== 'ROLLBACK').map((action) => <ActionBadge key={action} capability={canReview ? 'REVIEW_CATALOG' : 'VIEW_IMPORT_QUEUE'} />)}{!canReview && <small> Detail view only</small>}</>,
    candidate.auditReferenceId ? <AuditReferenceLink auditId={candidate.auditReferenceId} /> : 'Pending audit event',
];

export { parseImportCandidateFilters, serializeImportCandidateFilters };
