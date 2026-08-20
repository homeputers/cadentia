import { type FormEvent, useEffect, useState } from 'react';
import { hasCapability } from '../auth/permissions';
import type { AdminSession } from '../auth/session';
import { adminEnvironment } from '../config/environment';
import { createAdminApiClient, type AdminApiClient, type AdminApiError } from '../generated/cadentia-api/client';
import { assignModerationFlag, commitCandidateMerge, createCandidateReviewNote, escalateModerationFlag, getCandidateAuditHistory, getCandidateDetail, hasVisibleWarnings, openModerationFlag, resolveModerationFlag, safeParserEvidence, submitApprovalAction, submitMergeDecision, type AuditHistoryItem, type CandidateDetail, type ModerationFlag } from '../candidate-detail';
import { LocalizedView } from '../i18n';
import { ActionBadge, AuditReferenceLink, Badge, Breadcrumbs, ConfirmationDialog, DataTable, DiffPanel, Field, PageHeader, StatePanel, redactSensitiveError } from './admin-ui';

const label = (value?: string | null) => value ? value.replaceAll('_', ' ').toLowerCase().replace(/^./, (c) => c.toUpperCase()) : 'None';
const severityFor = (value?: string | null) => value === 'BLOCKED' || value === 'ERROR' || value === 'HIGH' || value === 'REJECTED' ? 'danger' : value === 'READY' || value === 'VERIFIED' || value === 'CLEAR' ? 'success' : value === 'NONE' ? 'neutral' : 'warning';
const pct = (value?: number | null) => typeof value === 'number' && Number.isFinite(value) ? `${Math.round(value * 100)}%` : 'n/a';
const summarizeStateKeys = (state?: Record<string, unknown> | null) => {
    const keys = Object.keys(state ?? {});
    return keys.length ? keys.sort().join(', ') : 'No state fields returned';
};
const pendingApprovalTypes = (detail: CandidateDetail) => (detail.approvalState?.requiredTypes ?? [])
    .filter((type) => !detail.approvalState?.statuses.some((status) => status.type === type && status.status === 'APPROVED'));

export const ImportCandidateDetail = ({ session, candidateId, apiClient = createAdminApiClient({ environment: adminEnvironment, getAccessToken: async () => null }) }: { session: AdminSession; candidateId: string; apiClient?: AdminApiClient }) => {
    const [detail, setDetail] = useState<CandidateDetail | null>(null);
    const [auditHistory, setAuditHistory] = useState<AuditHistoryItem[]>([]);
    const [auditState, setAuditState] = useState<'loading' | 'ready' | 'empty' | 'error'>('loading');
    const [auditError, setAuditError] = useState('');
    const [state, setState] = useState<'loading' | 'ready' | 'unauthorized' | 'forbidden' | 'stale' | 'error'>('loading');
    const [error, setError] = useState('');
    const [noteBody, setNoteBody] = useState('');
    const [noteCategory, setNoteCategory] = useState('GENERAL');
    const [noteState, setNoteState] = useState<'idle' | 'saving' | 'stale' | 'error'>('idle');
    const [actionState, setActionState] = useState<'idle' | 'saving' | 'validation' | 'stale' | 'forbidden' | 'error'>('idle');
    const [pendingAction, setPendingAction] = useState<null | { kind: 'merge' | 'approval' | 'moderation'; label: string; consequences: string[]; run: () => Promise<void> }>(null);
    const [moderationDrafts, setModerationDrafts] = useState<Record<string, { assignedTo: string; assignReason: string; resolutionNotes: string; escalateReason: string }>>({});
    const [mergeDecision, setMergeDecision] = useState('CREATE_NEW');
    const [mergeRationale, setMergeRationale] = useState('');
    const [commitRationale, setCommitRationale] = useState('');
    const [approvalType, setApprovalType] = useState('');
    const [approvalAction, setApprovalAction] = useState('APPROVE');
    const [approvalRationale, setApprovalRationale] = useState('');
    const [flagType, setFlagType] = useState('METADATA_CONFLICT');
    const [flagScope, setFlagScope] = useState('IMPORT_CANDIDATE');
    const [flagReason, setFlagReason] = useState('');
    const [flagPolicy, setFlagPolicy] = useState('BLOCK_UNTIL_RESOLVED');
    const hasReviewCapability = hasCapability(session, 'REVIEW_CATALOG');

    const load = async () => {
        setState(detail ? 'stale' : 'loading');
        try {
            const response = await getCandidateDetail(apiClient, candidateId);
            setDetail(response);
            setState('ready');
        } catch (caught) {
            const apiError = caught as AdminApiError;
            setError(redactSensitiveError(apiError.message));
            if (apiError.status === 401) setState('unauthorized');
            else if (apiError.status === 403) setState('forbidden');
            else setState('error');
        }
    };

    const loadAuditHistory = async () => {
        setAuditState('loading');
        setAuditError('');
        try {
            const response = await getCandidateAuditHistory(apiClient, candidateId);
            const items = Array.isArray(response) ? response : [];
            setAuditHistory(items);
            setAuditState(items.length ? 'ready' : 'empty');
        } catch (caught) {
            const apiError = caught as AdminApiError;
            setAuditError(redactSensitiveError(apiError.message));
            setAuditState('error');
        }
    };

    useEffect(() => { void load(); void loadAuditHistory(); }, [candidateId]);

    const addNote = async (event: FormEvent) => {
        event.preventDefault();
        if (!detail || !noteBody.trim()) return;
        setNoteState('saving');
        try {
            const note = await createCandidateReviewNote(apiClient, detail.candidateId, { actor: session.actorId, category: noteCategory, body: noteBody.trim() }, session.actorId, detail.etag);
            setDetail({ ...detail, reviewNotes: [note, ...detail.reviewNotes], version: detail.version + 1 });
            setNoteBody('');
            setNoteState('idle');
        } catch (caught) {
            const apiError = caught as AdminApiError;
            setNoteState(apiError.status === 409 || apiError.status === 412 ? 'stale' : 'error');
        }
    };

    const canAddNotes = detail?.allowedActions.includes('ADD_REVIEW_NOTE') || hasReviewCapability;
    const parser = detail ? safeParserEvidence(detail) : null;
    const mergeDecisionOptions = [
        { value: 'CREATE_NEW', label: 'Create new song' },
        { value: 'MERGE_EXISTING', label: 'Merge into existing song' },
        { value: 'REJECT_DUPLICATE', label: 'Reject as duplicate' },
        { value: 'REJECT_NOT_PERMITTED', label: 'Reject, not permitted' },
        { value: 'DEFER', label: 'Defer review' },
    ];
    const canMerge = (decision: string) => Boolean(detail?.allowedActions.includes(decision) || detail?.allowedActions.includes(`MERGE_DECISION_${decision}`));
    const canSubmitAnyCandidateDecision = Boolean(detail && mergeDecisionOptions.some((option) => canMerge(option.value)));
    const canCommitMerge = Boolean(detail?.allowedActions.includes('COMMIT_MERGE'));
    const canSubmitApproval = detail?.allowedActions.includes('SUBMIT_APPROVAL_ACTION') || detail?.allowedActions.includes(approvalAction);
    const canOpenFlag = detail?.allowedActions.includes('OPEN_MODERATION_FLAG') && hasReviewCapability;
    const canManageModeration = hasCapability(session, 'MANAGE_MODERATION');
    const handleActionError = (caught: unknown) => { const apiError = caught as AdminApiError; setActionState(apiError.status === 400 || apiError.status === 422 ? 'validation' : apiError.status === 409 || apiError.status === 412 ? 'stale' : apiError.status === 403 ? 'forbidden' : 'error'); };
    const confirmAndRun = async () => { if (!pendingAction) return; setActionState('saving'); try { await pendingAction.run(); setPendingAction(null); setActionState('idle'); await load(); await loadAuditHistory(); } catch (caught) { handleActionError(caught); setPendingAction(null); } };
    const replaceModerationFlag = (flag: ModerationFlag) => {
        if (!detail) return;
        setDetail({ ...detail, moderationFlags: (detail.moderationFlags ?? []).map((current) => current.id === flag.id ? flag : current) });
    };
    const moderationDraft = (flagId: string) => moderationDrafts[flagId] ?? { assignedTo: '', assignReason: '', resolutionNotes: '', escalateReason: '' };
    const updateModerationDraft = (flagId: string, patch: Partial<ReturnType<typeof moderationDraft>>) => setModerationDrafts((current) => ({ ...current, [flagId]: { ...(current[flagId] ?? moderationDraft(flagId)), ...patch } }));
    const submitMergeReview = (event: FormEvent) => {
        event.preventDefault();
        if (!detail || !canMerge(mergeDecision) || !mergeRationale.trim()) return;
        setPendingAction({
            kind: 'merge',
            label: `Submit ${label(mergeDecision)} merge decision`,
            consequences: detail.duplicateComparison?.eligibilityEffects ?? detail.eligibilityBlockers,
            run: async () => {
                const refreshed = await submitMergeDecision(apiClient, detail.candidateId, {
                    actor: session.actorId,
                    decision: mergeDecision,
                    duplicateMatchId: detail.duplicateMatches[0]?.id,
                    rationale: mergeRationale.trim(),
                }, session.actorId, detail.etag);
                setDetail(refreshed);
                setMergeRationale('');
            },
        });
    };
    const submitApprovalReview = (event: FormEvent) => {
        event.preventDefault();
        if (!detail || !canSubmitApproval || !approvalType || !approvalRationale.trim()) return;
        setPendingAction({
            kind: 'approval',
            label: `${label(approvalAction)} ${approvalType}`,
            consequences: [detail.approvalState?.eligibilityImpact ?? 'Backend will recompute eligibility impact'],
            run: async () => {
                const refreshed = await submitApprovalAction(apiClient, detail.candidateId, {
                    actor: session.actorId,
                    approvalType,
                    action: approvalAction,
                    rationale: approvalRationale.trim(),
                }, session.actorId, detail.etag);
                setDetail(refreshed);
                setApprovalRationale('');
            },
        });
    };
    const latestCatalogDecision = detail ? [...detail.reviewHistory].reverse().find((item) => item.decision === 'CREATE_NEW_SONG' || item.decision === 'CONFIRM_MATCH') : null;
    const commitAction = latestCatalogDecision?.decision === 'CONFIRM_MATCH' || mergeDecision === 'MERGE_EXISTING' ? 'MERGE_EXISTING' : 'CREATE_NEW';
    const commitTargetSongId = latestCatalogDecision?.proposedDuplicateMatchId
        ? detail?.duplicateMatches.find((match) => match.id === latestCatalogDecision.proposedDuplicateMatchId)?.candidateSongId
        : detail?.duplicateMatches[0]?.candidateSongId;
    const commitLabel = commitAction === 'MERGE_EXISTING' ? 'Commit merge into existing song' : 'Commit create new song';
    const submitCommitMerge = (event: FormEvent) => {
        event.preventDefault();
        if (!detail || !canCommitMerge || !commitRationale.trim()) return;
        if (commitAction === 'MERGE_EXISTING' && !commitTargetSongId) return;
        setPendingAction({
            kind: 'merge',
            label: commitLabel,
            consequences: [
                commitAction === 'MERGE_EXISTING'
                    ? `Merge candidate into canonical song ${commitTargetSongId}`
                    : 'Create a canonical song in review from this candidate',
                'Catalog approval actions unlock after a successful commit.',
            ],
            run: async () => {
                const refreshed = await commitCandidateMerge(apiClient, detail.candidateId, {
                    actor: session.actorId,
                    action: commitAction,
                    targetSongId: commitAction === 'MERGE_EXISTING' ? commitTargetSongId : undefined,
                    selectedFields: [],
                    rationale: commitRationale.trim(),
                }, session.actorId, detail.etag);
                setDetail(refreshed);
                setCommitRationale('');
            },
        });
    };
    const approvalTypes = detail ? pendingApprovalTypes(detail) : [];
    const candidateDecisionDisabledReason = !canMerge(mergeDecision)
        ? 'This decision is not currently allowed by the backend for this candidate.'
        : !mergeRationale.trim()
            ? 'Decision rationale is required before this action can be submitted.'
            : '';
    const commitDisabledReason = !canCommitMerge
        ? 'Commit is available after a create-new or merge-existing candidate decision puts this candidate in ready-to-merge status.'
        : commitAction === 'MERGE_EXISTING' && !commitTargetSongId
            ? 'A target song is required before this candidate can be merged into an existing song.'
            : !commitRationale.trim()
                ? 'Commit rationale is required before this action can be submitted.'
                : '';
    const approvalDisabledReason = !canSubmitApproval
        ? canCommitMerge
            ? 'Approval actions unlock after this candidate is committed into the canonical catalog.'
            : 'This approval action is not currently allowed by the backend for this candidate.'
        : !approvalType
            ? 'Approval type is required before this action can be submitted.'
            : !approvalRationale.trim()
                ? 'Approval rationale is required before this action can be submitted.'
                : '';

    return (
        <LocalizedView><main className="admin-shell" aria-labelledby="candidate-detail-title">
            <Breadcrumbs items={[{ label: 'Admin', href: '/admin' }, { label: 'Import review', href: '/admin/imports' }, { label: detail?.normalizedTitle ?? detail?.rawTitle ?? 'Candidate detail' }]} />
            <PageHeader eyebrow="Candidate review" title={detail?.normalizedTitle ?? detail?.rawTitle ?? 'Import candidate detail'} titleId="candidate-detail-title" description="Backend facts, imported metadata, parser evidence, reviewer notes, and audit history are separated so notes never become approved song metadata." />
            <StatePanel state={state} title="Candidate detail" onRetry={() => void load()}>{error && <p>{error}</p>}</StatePanel>
            {detail && <>
                {hasVisibleWarnings(detail) && <section className="admin-state admin-state--error" role="alert"><h2>Visible review warnings</h2><p>Eligibility blockers, low-confidence parser evidence, and duplicate signals are backend-provided and require review before approval.</p></section>}
                <section className="admin-shell__panel" aria-labelledby="identity-title"><h2 id="identity-title">Backend facts and identity</h2><dl><dt>Candidate ID</dt><dd>{detail.candidateId}</dd><dt>Connector</dt><dd>{detail.connectorKey}</dd><dt>Import batch</dt><dd>{detail.importBatchId}</dd><dt>Status</dt><dd><Badge severity={severityFor(detail.status)}>{label(detail.status)}</Badge></dd><dt>Version / ETag</dt><dd>{detail.version} / <code>{detail.etag}</code></dd><dt>Allowed actions</dt><dd>{detail.allowedActions.length ? detail.allowedActions.map((action) => <Badge key={action}>{label(action)}</Badge>) : 'None returned'}</dd></dl></section>
                <section className="admin-shell__panel" aria-labelledby="review-actions-title">
                    <h2 id="review-actions-title">Review actions</h2>
                    {hasReviewCapability ? <>
                        {canSubmitAnyCandidateDecision ? <form onSubmit={submitMergeReview}>
                            <h3>Candidate decision</h3>
                            <p>Use this first for newly imported songs. Creating or merging a candidate moves it into the backend-reviewed catalog flow; approval actions become available after that step.</p>
                            <Field label="Decision" required>{({ inputId }) => <select id={inputId} value={mergeDecision} onChange={(e) => setMergeDecision(e.target.value)} required>{mergeDecisionOptions.map((option) => <option key={option.value} value={option.value} disabled={!canMerge(option.value)}>{option.label}</option>)}</select>}</Field>
                            <Field label="Decision rationale" required description="Required before submitting. Example: imported manually from verified source; no duplicate match returned.">{({ inputId, descriptionId }) => <textarea id={inputId} value={mergeRationale} onChange={(e) => setMergeRationale(e.target.value)} required aria-describedby={descriptionId} />}</Field>
                            <button type="submit" disabled={Boolean(candidateDecisionDisabledReason)} aria-describedby={candidateDecisionDisabledReason ? 'candidate-decision-disabled-reason' : undefined}>Submit candidate decision</button>
                            {candidateDecisionDisabledReason && <p id="candidate-decision-disabled-reason" className="admin-action-hint">{candidateDecisionDisabledReason}</p>}
                        </form> : <p>Candidate decision is complete for this status.</p>}
                        {canCommitMerge && <form onSubmit={submitCommitMerge}>
                            <h3>Commit merge</h3>
                            <p>The review decision is recorded. Commit it to create or merge the canonical song; catalog approval unlocks after this step.</p>
                            <Field label="Commit action">{({ inputId }) => <input id={inputId} value={label(commitAction)} readOnly />}</Field>
                            {commitAction === 'MERGE_EXISTING' && <Field label="Target song">{({ inputId }) => <input id={inputId} value={commitTargetSongId ?? ''} readOnly />}</Field>}
                            <Field label="Commit rationale" required>{({ inputId }) => <textarea id={inputId} value={commitRationale} onChange={(e) => setCommitRationale(e.target.value)} required />}</Field>
                            <button type="submit" disabled={Boolean(commitDisabledReason)} aria-describedby={commitDisabledReason ? 'commit-disabled-reason' : undefined}>{commitLabel}</button>
                            {commitDisabledReason && <p id="commit-disabled-reason" className="admin-action-hint">{commitDisabledReason}</p>}
                        </form>}
                        {detail.approvalState && canSubmitApproval ? <form onSubmit={submitApprovalReview}>
                            <h3>Catalog approval</h3>
                            <Field label="Approval type" required>{({ inputId }) => <select id={inputId} value={approvalType} onChange={(e) => setApprovalType(e.target.value)} required><option value="">Select pending type</option>{approvalTypes.map((type) => <option key={type}>{type}</option>)}</select>}</Field>
                            <Field label="Approval action" required>{({ inputId }) => <select id={inputId} value={approvalAction} onChange={(e) => setApprovalAction(e.target.value)} required><option value="APPROVE">Approve</option><option value="REVERSE_APPROVAL">Reverse approval</option><option value="REJECT_APPROVAL">Reject approval</option></select>}</Field>
                            <Field label="Approval rationale" required>{({ inputId }) => <textarea id={inputId} value={approvalRationale} onChange={(e) => setApprovalRationale(e.target.value)} required />}</Field>
                            <button type="submit" disabled={Boolean(approvalDisabledReason)} aria-describedby={approvalDisabledReason ? 'approval-disabled-reason' : undefined}>Submit approval action</button>
                            {approvalDisabledReason && <p id="approval-disabled-reason" className="admin-action-hint">{approvalDisabledReason}</p>}
                        </form> : detail.approvalState ? <p>Catalog approval is complete or no approval action is currently available for this candidate.</p> : <p>Approval actions are not available until the backend has a merged song for this candidate.</p>}
                        {canAddNotes && <p><a href="#notes-title">Add a reviewer note</a></p>}
                        {canOpenFlag && <p><a href="#moderation-title">Open a moderation flag</a></p>}
                    </> : <p>Read-only access. Backend review actions require catalog review capability.</p>}
                </section>
                <section className="admin-shell__panel" aria-labelledby="metadata-title"><h2 id="metadata-title">Imported candidate metadata (not approved catalog data)</h2><dl><dt>Raw title</dt><dd>{detail.rawTitle}</dd><dt>Normalized title</dt><dd>{detail.normalizedTitle ?? 'Not normalized'}</dd><dt>Source artist</dt><dd>{detail.sourceArtistName ?? 'Unknown'}</dd><dt>Raw connector payload</dt><dd>Redacted/omitted. Use only a documented safe endpoint if one is later authorized.</dd><dt>Full lyrics</dt><dd>Redacted/omitted from this review screen.</dd></dl></section>
                <section className="admin-shell__panel" aria-labelledby="provenance-title"><h2 id="provenance-title">Source and provenance references</h2><DataTable caption="Provenance references" columns={['Label', 'Reference', 'Fingerprint', 'Status']} rows={detail.provenanceReferences.map((ref) => [ref.label, ref.sourceReference, ref.fingerprint ? <code>{ref.fingerprint}</code> : 'Not returned', <Badge severity={severityFor(ref.status)}>{label(ref.status)}</Badge>])} /></section>
                <section className="admin-shell__panel" aria-labelledby="parser-title"><h2 id="parser-title">Parser evidence</h2><dl><dt>Parser</dt><dd>{parser?.parserName ?? 'Unknown'} {parser?.parserVersion ?? ''}</dd><dt>Confidence</dt><dd><Badge severity={(parser?.confidence ?? 1) < 0.75 ? 'warning' : 'success'}>{pct(parser?.confidence ?? null)}</Badge></dd></dl>{(parser?.warnings ?? []).length > 0 && <ul>{parser!.warnings!.map((warning) => <li key={warning}><Badge severity="warning">Parser warning</Badge> {warning}</li>)}</ul>}{(parser?.evidenceReferences ?? []).map((ref) => <p key={ref}><code>{ref}</code></p>)}</section>
                <section className="admin-shell__panel" aria-labelledby="eligibility-title"><h2 id="eligibility-title">Backend eligibility impact</h2>{detail.eligibilityBlockers.length ? <ul>{detail.eligibilityBlockers.map((blocker) => <li key={blocker}><Badge severity="danger">Blocker</Badge> {blocker}</li>)}</ul> : <Badge severity="success">No blockers returned</Badge>}</section>
                <section className="admin-shell__panel" aria-labelledby="duplicates-title"><h2 id="duplicates-title">Duplicate signals</h2><p><Badge severity={severityFor(detail.duplicateSummary.confidence)}>{label(detail.duplicateSummary.confidence)}</Badge> {detail.duplicateSummary.matchCount ?? detail.duplicateMatches.length} matches · top {pct(detail.duplicateSummary.topScore ?? null)} · {detail.duplicateSummary.summary ?? 'No summary returned'}</p><DataTable caption="Duplicate matches" columns={['Match ID', 'Candidate song', 'Score', 'Status']} rows={detail.duplicateMatches.map((match) => [match.id, match.candidateSongId, pct(match.matchScore), label(match.status)])} /></section>

                <section className="admin-shell__panel" aria-labelledby="duplicate-comparison-title"><h2 id="duplicate-comparison-title">Duplicate comparison and field-level merge review</h2>{detail.duplicateComparison ? <><DiffPanel before={Object.entries(detail.duplicateComparison.existing).map(([key, value]) => `${key}: ${value ?? 'Not returned'}`)} after={Object.entries(detail.duplicateComparison.candidate).map(([key, value]) => `${key}: ${value ?? 'Not returned'}`)} /><DataTable caption="Backend duplicate confidence features and conflicts" columns={['Matching features', 'Conflicts', 'Eligibility effects']} rows={[[detail.duplicateComparison.matchingFeatures.join(', ') || 'None returned', detail.duplicateComparison.conflicts.join(', ') || 'None returned', detail.duplicateComparison.eligibilityEffects.join(', ') || 'None returned']]} /><p>Current approved catalog state: <Badge severity={severityFor(detail.duplicateComparison.currentApprovedCatalogState)}>{label(detail.duplicateComparison.currentApprovedCatalogState)}</Badge> {detail.duplicateComparison.auditReferenceId && <AuditReferenceLink auditId={detail.duplicateComparison.auditReferenceId} />}</p></> : <p>No duplicate comparison returned. Use the candidate decision above to create a new song, reject, or defer based on backend-allowed actions.</p>}<DataTable caption="Duplicate matches" columns={['Match ID', 'Existing song', 'Score', 'Features', 'Conflicts', 'Catalog state']} rows={detail.duplicateMatches.map((match) => [match.id, match.candidateSongId, pct(match.matchScore), match.matchingFeatures?.join(', ') ?? 'Use backend comparison', match.conflicts?.join(', ') ?? 'None returned', label(match.existingCatalogState ?? match.status)])} /></section>
                <section className="admin-shell__panel" aria-labelledby="approval-title"><h2 id="approval-title">Approval actions and eligibility impact</h2>{detail.approvalState ? <><dl><dt>Required approval types</dt><dd>{detail.approvalState.requiredTypes.join(', ') || 'None returned'}</dd><dt>Allowed transitions</dt><dd>{detail.approvalState.allowedTransitions.map((transition) => <Badge key={transition}>{label(transition)}</Badge>)}</dd><dt>Eligibility impact</dt><dd>{detail.approvalState.eligibilityImpact}</dd></dl><DataTable caption="Current approval statuses" columns={['Type', 'Status', 'Actor', 'Audit']} rows={detail.approvalState.statuses.map((item) => [item.type, <Badge severity={severityFor(item.status)}>{label(item.status)}</Badge>, item.actor ?? 'Not returned', item.auditReferenceId ? <AuditReferenceLink auditId={item.auditReferenceId} /> : 'Not returned'])} />{detail.approvalState.blockers.length ? <ul>{detail.approvalState.blockers.map((blocker) => <li key={blocker}><Badge severity="danger">Blocker</Badge> {blocker}</li>)}</ul> : <Badge severity="success">No approval blockers returned</Badge>}</> : <p>No approval state returned by backend.</p>}</section>
                <section className="admin-shell__panel" aria-labelledby="moderation-title"><h2 id="moderation-title">Moderation flags</h2><DataTable caption="Moderation flags" columns={['Scope', 'Reason', 'Policy', 'Status', 'Owner', 'Audit']} rows={(detail.moderationFlags ?? []).map((flag) => [flag.scope ?? flag.type ?? 'Not returned', flag.reason ?? flag.type ?? 'Not returned', flag.eligibilityImpactPolicy ?? 'Not returned', <Badge severity={severityFor(flag.status)}>{label(flag.status)}</Badge>, flag.assignedTo ?? 'Unassigned', flag.auditReferenceId ? <AuditReferenceLink auditId={flag.auditReferenceId} /> : 'Not returned'])} />{canManageModeration && (detail.moderationFlags ?? []).map((flag) => {
                    const draft = moderationDraft(flag.id);
                    return <article key={flag.id} className="admin-shell__panel" aria-labelledby={`moderation-${flag.id}`}><h3 id={`moderation-${flag.id}`}>{label(flag.status)} flag {flag.type ? label(flag.type) : flag.id}</h3>{flag.resolutionNotes && <p>Resolution: {flag.resolutionNotes}</p>}<form onSubmit={(event) => { event.preventDefault(); if (!draft.assignedTo.trim() || !draft.assignReason.trim()) return; setPendingAction({ kind: 'moderation', label: 'Assign moderation flag', consequences: [`Assign to ${draft.assignedTo.trim()}`, draft.assignReason.trim()], run: async () => replaceModerationFlag(await assignModerationFlag(apiClient, flag.id, { actor: session.actorId, assignedTo: draft.assignedTo.trim(), reason: draft.assignReason.trim() }, session.actorId)) }); }}><Field label="Assign to reviewer">{({ inputId }) => <input id={inputId} value={draft.assignedTo} onChange={(e) => updateModerationDraft(flag.id, { assignedTo: e.target.value })} />}</Field><Field label="Assignment reason">{({ inputId }) => <input id={inputId} value={draft.assignReason} onChange={(e) => updateModerationDraft(flag.id, { assignReason: e.target.value })} />}</Field><button type="submit" disabled={flag.status === 'RESOLVED'}>Assign flag</button></form><form onSubmit={(event) => { event.preventDefault(); if (!draft.resolutionNotes.trim()) return; setPendingAction({ kind: 'moderation', label: 'Resolve moderation flag', consequences: [draft.resolutionNotes.trim(), 'Backend will recompute eligibility impact after resolution.'], run: async () => replaceModerationFlag(await resolveModerationFlag(apiClient, flag.id, { actor: session.actorId, resolutionNotes: draft.resolutionNotes.trim() }, session.actorId)) }); }}><Field label="Resolution notes">{({ inputId }) => <textarea id={inputId} value={draft.resolutionNotes} onChange={(e) => updateModerationDraft(flag.id, { resolutionNotes: e.target.value })} />}</Field><button type="submit" disabled={flag.status === 'RESOLVED'}>Resolve flag</button></form>{session.roles.includes('ADMIN') && <form onSubmit={(event) => { event.preventDefault(); if (!draft.escalateReason.trim()) return; setPendingAction({ kind: 'moderation', label: 'Escalate moderation flag', consequences: [draft.escalateReason.trim(), 'Admin escalation is audit-visible and may block downstream publication.'], run: async () => replaceModerationFlag(await escalateModerationFlag(apiClient, flag.id, { actor: session.actorId, reason: draft.escalateReason.trim() }, session.actorId)) }); }}><Field label="Escalation reason">{({ inputId }) => <textarea id={inputId} value={draft.escalateReason} onChange={(e) => updateModerationDraft(flag.id, { escalateReason: e.target.value })} />}</Field><button type="submit" disabled={flag.status === 'RESOLVED'}>Escalate flag</button></form>}</article>;
                })}{canOpenFlag && <form onSubmit={(event) => { event.preventDefault(); if (!detail || !flagReason.trim()) return; setPendingAction({ kind: 'moderation', label: 'Open moderation flag', consequences: [`Policy: ${flagPolicy}`, 'Backend will enforce recommendation eligibility impact'], run: async () => { const flag = await openModerationFlag(apiClient, detail.candidateId, { openedBy: session.actorId, scope: flagScope, type: flagType, reason: flagReason.trim(), eligibilityImpactPolicy: flagPolicy, excludeFromRecommendation: flagPolicy !== 'REVIEW_ONLY' }, session.actorId, detail.etag); setDetail({ ...detail, moderationFlags: [flag, ...(detail.moderationFlags ?? [])] }); } }); }}><Field label="Flag scope">{({ inputId }) => <input id={inputId} value={flagScope} onChange={(e) => setFlagScope(e.target.value)} />}</Field><Field label="Flag type">{({ inputId }) => <select id={inputId} value={flagType} onChange={(e) => setFlagType(e.target.value)}><option>BAD_SOURCE</option><option>LICENSING_CONCERN</option><option>INCORRECT_LYRICS</option><option>METADATA_CONFLICT</option><option>DOCTRINAL_CONCERN</option><option>PARSER_ISSUE</option></select>}</Field><Field label="Flag reason">{({ inputId }) => <textarea id={inputId} value={flagReason} onChange={(e) => setFlagReason(e.target.value)} />}</Field><Field label="Eligibility impact policy">{({ inputId }) => <select id={inputId} value={flagPolicy} onChange={(e) => setFlagPolicy(e.target.value)}><option>BLOCK_UNTIL_RESOLVED</option><option>REVIEW_ONLY</option><option>SUPPRESS_RECOMMENDATION</option></select>}</Field><button type="submit">Open flag with audit attribution</button></form>}</section>
                {actionState === 'validation' && <p role="alert">Backend validation rejected this action. Review the selected action, required rationale, and eligibility blockers before retrying.</p>}{actionState === 'stale' && <p role="alert">Version is stale. Reload before retrying this eligibility-impacting action.</p>}{actionState === 'forbidden' && <p role="alert">Action is forbidden by backend policy.</p>}{actionState === 'error' && <p role="alert">Action could not be completed. No protected details were exposed.</p>}

                <section className="admin-shell__panel" aria-labelledby="notes-title"><h2 id="notes-title">Reviewer notes (not approved metadata)</h2>{canAddNotes && <form onSubmit={addNote}><Field label="Note category">{({ inputId }) => <select id={inputId} value={noteCategory} onChange={(e) => setNoteCategory(e.target.value)}><option>GENERAL</option><option>DOCTRINAL</option><option>MUSICAL</option><option>PROVENANCE</option></select>}</Field><Field label="Structured review note">{({ inputId }) => <textarea id={inputId} value={noteBody} onChange={(e) => setNoteBody(e.target.value)} maxLength={2000} />}</Field><button type="submit" disabled={noteState === 'saving'}>Add reviewer note</button>{noteState === 'stale' && <p role="alert">Candidate version is stale. Reload before adding the note.</p>}{noteState === 'error' && <p role="alert">Note could not be saved.</p>}</form>}{detail.reviewNotes.map((note) => <article key={note.noteId}><h3>{note.category ?? 'GENERAL'} note by {note.authorDisplayName ?? note.authorId}</h3><p>{note.body}</p><small>{note.createdAt} {note.auditReferenceId && <AuditReferenceLink auditId={note.auditReferenceId} />}</small></article>)}</section>
                <section className="admin-shell__panel" aria-labelledby="history-title"><h2 id="history-title">Review history and audit references</h2><DataTable caption="Review history" columns={['Decision', 'Reviewer', 'Reviewed', 'Notes']} rows={detail.reviewHistory.map((item) => [label(item.decision), item.reviewer, item.reviewedAt, item.reviewNotes ?? 'No note'])} />{detail.relatedAuditReferences?.map((auditId) => <p key={auditId}><AuditReferenceLink auditId={auditId} /></p>)}</section>
                <section className="admin-shell__panel" aria-labelledby="candidate-audit-title"><h2 id="candidate-audit-title">Candidate audit history</h2><StatePanel state={auditState} title="Candidate audit history" onRetry={() => void loadAuditHistory()}>{auditError && <p>{auditError}</p>}<DataTable caption="Candidate audit events" columns={['Audit reference', 'Action', 'Actor', 'Occurred', 'Reason', 'State fields']} rows={auditHistory.map((item) => [<AuditReferenceLink auditId={item.id} />, label(item.action), item.actor, item.occurredAt, item.reason ?? 'No reason returned', `${summarizeStateKeys(item.beforeState)} → ${summarizeStateKeys(item.afterState)}`])} /></StatePanel></section>
            </>}
            <ConfirmationDialog open={Boolean(pendingAction)} title={pendingAction?.label ?? 'Confirm action'} acknowledgement="This action can affect publication or recommendation eligibility and will be audited." facts={pendingAction?.consequences.length ? pendingAction.consequences : ['Backend will validate allowed actions, blockers, actor attribution, and stale versions.']} auditActor={session.actorId} versionContext={detail ? `If-Match: ${detail.etag}; version ${detail.version}` : 'No version loaded'} onCancel={() => setPendingAction(null)} onConfirm={() => void confirmAndRun()} />
        </main></LocalizedView>
    );
};
