import { type FormEvent, useEffect, useState } from 'react';
import { hasCapability } from '../auth/permissions';
import type { AdminSession } from '../auth/session';
import { adminEnvironment } from '../config/environment';
import { createAdminApiClient, type AdminApiClient, type AdminApiError } from '../generated/cadentia-api/client';
import { createCandidateReviewNote, getCandidateDetail, hasVisibleWarnings, safeParserEvidence, type CandidateDetail } from '../candidate-detail';
import { ActionBadge, AuditReferenceLink, Badge, Breadcrumbs, DataTable, Field, PageHeader, StatePanel, redactSensitiveError } from './admin-ui';

const label = (value?: string | null) => value ? value.replaceAll('_', ' ').toLowerCase().replace(/^./, (c) => c.toUpperCase()) : 'None';
const severityFor = (value?: string | null) => value === 'BLOCKED' || value === 'ERROR' || value === 'HIGH' || value === 'REJECTED' ? 'danger' : value === 'READY' || value === 'VERIFIED' || value === 'CLEAR' ? 'success' : value === 'NONE' ? 'neutral' : 'warning';
const pct = (value?: number | null) => typeof value === 'number' && Number.isFinite(value) ? `${Math.round(value * 100)}%` : 'n/a';

export const ImportCandidateDetail = ({ session, candidateId, apiClient = createAdminApiClient({ environment: adminEnvironment, getAccessToken: async () => null }) }: { session: AdminSession; candidateId: string; apiClient?: AdminApiClient }) => {
    const [detail, setDetail] = useState<CandidateDetail | null>(null);
    const [state, setState] = useState<'loading' | 'ready' | 'unauthorized' | 'forbidden' | 'stale' | 'error'>('loading');
    const [error, setError] = useState('');
    const [noteBody, setNoteBody] = useState('');
    const [noteCategory, setNoteCategory] = useState('GENERAL');
    const [noteState, setNoteState] = useState<'idle' | 'saving' | 'stale' | 'error'>('idle');

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

    useEffect(() => { void load(); }, [candidateId]);

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

    const canAddNotes = detail?.allowedActions.includes('ADD_REVIEW_NOTE') || hasCapability(session, 'REVIEW_CATALOG');
    const parser = detail ? safeParserEvidence(detail) : null;

    return (
        <main className="admin-shell" aria-labelledby="candidate-detail-title">
            <Breadcrumbs items={[{ label: 'Admin', href: '/admin' }, { label: 'Import review', href: '/admin/imports' }, { label: detail?.normalizedTitle ?? detail?.rawTitle ?? 'Candidate detail' }]} />
            <PageHeader eyebrow="Candidate review" title={detail?.normalizedTitle ?? detail?.rawTitle ?? 'Import candidate detail'} titleId="candidate-detail-title" description="Backend facts, imported metadata, parser evidence, reviewer notes, and audit history are separated so notes never become approved song metadata." />
            <StatePanel state={state} title="Candidate detail" onRetry={() => void load()}>{error && <p>{error}</p>}</StatePanel>
            {detail && <>
                {hasVisibleWarnings(detail) && <section className="admin-state admin-state--error" role="alert"><h2>Visible review warnings</h2><p>Eligibility blockers, low-confidence parser evidence, and duplicate signals are backend-provided and require review before approval.</p></section>}
                <section className="admin-shell__panel" aria-labelledby="identity-title"><h2 id="identity-title">Backend facts and identity</h2><dl><dt>Candidate ID</dt><dd>{detail.candidateId}</dd><dt>Connector</dt><dd>{detail.connectorKey}</dd><dt>Import batch</dt><dd>{detail.importBatchId}</dd><dt>Status</dt><dd><Badge severity={severityFor(detail.status)}>{label(detail.status)}</Badge></dd><dt>Version / ETag</dt><dd>{detail.version} / <code>{detail.etag}</code></dd><dt>Allowed actions</dt><dd>{detail.allowedActions.length ? detail.allowedActions.map((action) => <Badge key={action}>{label(action)}</Badge>) : 'None returned'}</dd></dl></section>
                <section className="admin-shell__panel" aria-labelledby="metadata-title"><h2 id="metadata-title">Imported candidate metadata (not approved catalog data)</h2><dl><dt>Raw title</dt><dd>{detail.rawTitle}</dd><dt>Normalized title</dt><dd>{detail.normalizedTitle ?? 'Not normalized'}</dd><dt>Source artist</dt><dd>{detail.sourceArtistName ?? 'Unknown'}</dd><dt>Raw connector payload</dt><dd>Redacted/omitted. Use only a documented safe endpoint if one is later authorized.</dd><dt>Full lyrics</dt><dd>Redacted/omitted from this review screen.</dd></dl></section>
                <section className="admin-shell__panel" aria-labelledby="provenance-title"><h2 id="provenance-title">Source and provenance references</h2><DataTable caption="Provenance references" columns={['Label', 'Reference', 'Fingerprint', 'Status']} rows={detail.provenanceReferences.map((ref) => [ref.label, ref.sourceReference, ref.fingerprint ? <code>{ref.fingerprint}</code> : 'Not returned', <Badge severity={severityFor(ref.status)}>{label(ref.status)}</Badge>])} /></section>
                <section className="admin-shell__panel" aria-labelledby="parser-title"><h2 id="parser-title">Parser evidence</h2><dl><dt>Parser</dt><dd>{parser?.parserName ?? 'Unknown'} {parser?.parserVersion ?? ''}</dd><dt>Confidence</dt><dd><Badge severity={(parser?.confidence ?? 1) < 0.75 ? 'warning' : 'success'}>{pct(parser?.confidence ?? null)}</Badge></dd></dl>{(parser?.warnings ?? []).length > 0 && <ul>{parser!.warnings!.map((warning) => <li key={warning}><Badge severity="warning">Parser warning</Badge> {warning}</li>)}</ul>}{(parser?.evidenceReferences ?? []).map((ref) => <p key={ref}><code>{ref}</code></p>)}</section>
                <section className="admin-shell__panel" aria-labelledby="eligibility-title"><h2 id="eligibility-title">Backend eligibility impact</h2>{detail.eligibilityBlockers.length ? <ul>{detail.eligibilityBlockers.map((blocker) => <li key={blocker}><Badge severity="danger">Blocker</Badge> {blocker}</li>)}</ul> : <Badge severity="success">No blockers returned</Badge>}</section>
                <section className="admin-shell__panel" aria-labelledby="duplicates-title"><h2 id="duplicates-title">Duplicate signals</h2><p><Badge severity={severityFor(detail.duplicateSummary.confidence)}>{label(detail.duplicateSummary.confidence)}</Badge> {detail.duplicateSummary.matchCount ?? detail.duplicateMatches.length} matches · top {pct(detail.duplicateSummary.topScore ?? null)} · {detail.duplicateSummary.summary ?? 'No summary returned'}</p><DataTable caption="Duplicate matches" columns={['Match ID', 'Candidate song', 'Score', 'Status']} rows={detail.duplicateMatches.map((match) => [match.id, match.candidateSongId, pct(match.matchScore), label(match.status)])} /></section>
                <section className="admin-shell__panel" aria-labelledby="notes-title"><h2 id="notes-title">Reviewer notes (not approved metadata)</h2>{canAddNotes && <form onSubmit={addNote}><Field label="Note category">{({ inputId }) => <select id={inputId} value={noteCategory} onChange={(e) => setNoteCategory(e.target.value)}><option>GENERAL</option><option>DOCTRINAL</option><option>MUSICAL</option><option>PROVENANCE</option></select>}</Field><Field label="Structured review note">{({ inputId }) => <textarea id={inputId} value={noteBody} onChange={(e) => setNoteBody(e.target.value)} maxLength={2000} />}</Field><button type="submit" disabled={noteState === 'saving'}>Add reviewer note</button>{noteState === 'stale' && <p role="alert">Candidate version is stale. Reload before adding the note.</p>}{noteState === 'error' && <p role="alert">Note could not be saved.</p>}</form>}{detail.reviewNotes.map((note) => <article key={note.noteId}><h3>{note.category ?? 'GENERAL'} note by {note.authorDisplayName ?? note.authorId}</h3><p>{note.body}</p><small>{note.createdAt} {note.auditReferenceId && <AuditReferenceLink auditId={note.auditReferenceId} />}</small></article>)}</section>
                <section className="admin-shell__panel" aria-labelledby="history-title"><h2 id="history-title">Review history and audit references</h2><DataTable caption="Review history" columns={['Decision', 'Reviewer', 'Reviewed', 'Notes']} rows={detail.reviewHistory.map((item) => [label(item.decision), item.reviewer, item.reviewedAt, item.reviewNotes ?? 'No note'])} />{detail.relatedAuditReferences?.map((auditId) => <p key={auditId}><AuditReferenceLink auditId={auditId} /></p>)}</section>
            </>}
        </main>
    );
};
