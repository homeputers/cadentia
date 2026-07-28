import { type ChangeEvent, type FormEvent, useState } from 'react';
import type { AdminSession } from '../auth/session';
import { adminEnvironment } from '../config/environment';
import { createAdminApiClient, type AdminApiClient, type AdminApiError } from '../generated/cadentia-api/client';
import { createCsvSongImport, createManualSongImport, splitEntryList, type ManualSongImportDraft, type SongImportResponse, type SongResourceDraft } from '../song-imports';
import { Badge, Breadcrumbs, DataTable, Field, PageHeader, StatePanel, redactSensitiveError } from './admin-ui';

const licenseTypes = ['PUBLIC_DOMAIN', 'CCLI', 'DIRECT_PERMISSION', 'NOT_APPLICABLE'];
const resourceTypes = ['CHORD_CHART', 'LEAD_SHEET', 'SHEET_MUSIC', 'AUDIO_REFERENCE', 'VIDEO_REFERENCE', 'PLANNING_CENTER_RESOURCE', 'OTHER'];
const csvColumns = [
    'title',
    'author',
    'artist',
    'ccliNumber',
    'key',
    'bpm',
    'timeSignature',
    'energy',
    'difficulty',
    'themes',
    'scriptureReferences',
    'lyrics',
    'chordChart',
    'resources',
    'licenseType',
    'licenseEvidence',
];

const emptyManualDraft: ManualSongImportDraft = {
    title: '',
    licenseType: 'CCLI',
    themes: [],
    scriptureReferences: [],
    resources: [],
};

const emptyResource: SongResourceDraft = {
    resourceType: 'CHORD_CHART',
    title: '',
};

const label = (value?: string | null) => value ? value.replaceAll('_', ' ').toLowerCase().replace(/^./, (c) => c.toUpperCase()) : 'None';

export const SongImport = ({ session, apiClient = createAdminApiClient({ environment: adminEnvironment, getAccessToken: async () => null }) }: { session: AdminSession; apiClient?: AdminApiClient }) => {
    const [manualDraft, setManualDraft] = useState<ManualSongImportDraft>(emptyManualDraft);
    const [themeInput, setThemeInput] = useState('');
    const [scriptureInput, setScriptureInput] = useState('');
    const [resourceDraft, setResourceDraft] = useState<SongResourceDraft>(emptyResource);
    const [csvFile, setCsvFile] = useState<File | null>(null);
    const [csvLicenseType, setCsvLicenseType] = useState('CCLI');
    const [csvLicenseEvidence, setCsvLicenseEvidence] = useState('');
    const [state, setState] = useState<'ready' | 'loading' | 'error'>('ready');
    const [error, setError] = useState('');
    const [result, setResult] = useState<SongImportResponse | null>(null);

    const updateManual = (event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
        const { name, value } = event.target;
        setManualDraft((current) => ({
            ...current,
            [name]: ['bpm', 'energy', 'difficulty'].includes(name) && value ? Number(value) : value || undefined,
        }));
    };

    const updateResource = (event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
        const { name, value } = event.target;
        setResourceDraft((current) => ({ ...current, [name]: value || undefined }));
    };

    const addResource = () => {
        if (!resourceDraft.title.trim()) return;
        setManualDraft((current) => ({ ...current, resources: [...current.resources, resourceDraft] }));
        setResourceDraft(emptyResource);
    };

    const submitManual = async (event: FormEvent) => {
        event.preventDefault();
        setState('loading');
        setError('');
        try {
            const response = await createManualSongImport(apiClient, session.actorId, {
                ...manualDraft,
                themes: splitEntryList(themeInput),
                scriptureReferences: splitEntryList(scriptureInput),
            });
            setResult(response);
            setState('ready');
        } catch (caught) {
            setError(redactSensitiveError((caught as AdminApiError).message));
            setState('error');
        }
    };

    const submitCsv = async (event: FormEvent) => {
        event.preventDefault();
        setState('loading');
        setError('');
        if (!csvFile) {
            setError('Choose a CSV file before staging rows.');
            setState('error');
            return;
        }
        try {
            const response = await createCsvSongImport(apiClient, session.actorId, {
                file: csvFile,
                licenseType: csvLicenseType,
                licenseEvidence: csvLicenseEvidence || undefined,
            });
            setResult(response);
            setState('ready');
        } catch (caught) {
            setError(redactSensitiveError((caught as AdminApiError).message));
            setState('error');
        }
    };

    return (
        <main className="admin-shell" aria-labelledby="song-import-title">
            <Breadcrumbs items={[{ label: 'Admin', href: '/admin' }, { label: 'Song import' }]} />
            <PageHeader eyebrow="Catalog ingestion" title="Song import" titleId="song-import-title" description="Stage manually entered songs or CSV rows for duplicate review, provenance review, and approval workflows." />
            <StatePanel state={state} title="Song import request">{error && <p>{error}</p>}</StatePanel>
            <section className="admin-shell__panel" aria-labelledby="manual-song-import-title">
                <h2 id="manual-song-import-title">Manual entry</h2>
                <form className="admin-form-grid" onSubmit={submitManual}>
                    <Field label="Title">{({ inputId }) => <input id={inputId} name="title" required value={manualDraft.title} onChange={updateManual} />}</Field>
                    <Field label="Author">{({ inputId }) => <input id={inputId} name="author" value={manualDraft.author ?? ''} onChange={updateManual} />}</Field>
                    <Field label="Artist">{({ inputId }) => <input id={inputId} name="artist" value={manualDraft.artist ?? ''} onChange={updateManual} />}</Field>
                    <Field label="CCLI number">{({ inputId }) => <input id={inputId} name="ccliNumber" value={manualDraft.ccliNumber ?? ''} onChange={updateManual} />}</Field>
                    <Field label="Key">{({ inputId }) => <input id={inputId} name="key" value={manualDraft.key ?? ''} onChange={updateManual} />}</Field>
                    <Field label="Tempo">{({ inputId }) => <input id={inputId} type="number" min="30" max="260" name="bpm" value={manualDraft.bpm ?? ''} onChange={updateManual} />}</Field>
                    <Field label="Time signature">{({ inputId }) => <input id={inputId} name="timeSignature" value={manualDraft.timeSignature ?? ''} onChange={updateManual} />}</Field>
                    <Field label="Energy">{({ inputId }) => <input id={inputId} type="number" min="1" max="5" name="energy" value={manualDraft.energy ?? ''} onChange={updateManual} />}</Field>
                    <Field label="Difficulty">{({ inputId }) => <input id={inputId} type="number" min="1" max="5" name="difficulty" value={manualDraft.difficulty ?? ''} onChange={updateManual} />}</Field>
                    <Field label="Language">{({ inputId }) => <input id={inputId} name="language" value={manualDraft.language ?? ''} onChange={updateManual} />}</Field>
                    <Field label="Themes">{({ inputId }) => <input id={inputId} value={themeInput} onChange={(event) => setThemeInput(event.target.value)} placeholder="praise; grace; advent" />}</Field>
                    <Field label="Scripture references">{({ inputId }) => <input id={inputId} value={scriptureInput} onChange={(event) => setScriptureInput(event.target.value)} placeholder="Psalm 23; Romans 8" />}</Field>
                    <Field label="Copyright">{({ inputId }) => <input id={inputId} name="copyright" value={manualDraft.copyright ?? ''} onChange={updateManual} />}</Field>
                    <Field label="Publisher">{({ inputId }) => <input id={inputId} name="publisher" value={manualDraft.publisher ?? ''} onChange={updateManual} />}</Field>
                    <Field label="License">{({ inputId }) => <select id={inputId} name="licenseType" value={manualDraft.licenseType} onChange={updateManual}>{licenseTypes.map((type) => <option key={type} value={type}>{label(type)}</option>)}</select>}</Field>
                    <Field label="License evidence">{({ inputId }) => <input id={inputId} name="licenseEvidence" value={manualDraft.licenseEvidence ?? ''} onChange={updateManual} />}</Field>
                    <Field label="Source reference">{({ inputId }) => <input id={inputId} name="sourceReference" value={manualDraft.sourceReference ?? ''} onChange={updateManual} />}</Field>
                    <Field label="Arrangement notes">{({ inputId }) => <textarea id={inputId} name="arrangementNotes" value={manualDraft.arrangementNotes ?? ''} onChange={updateManual} />}</Field>
                    <Field label="Lyrics">{({ inputId }) => <textarea id={inputId} name="lyrics" value={manualDraft.lyrics ?? ''} onChange={updateManual} />}</Field>
                    <Field label="Chord chart">{({ inputId }) => <textarea id={inputId} name="chordChart" value={manualDraft.chordChart ?? ''} onChange={updateManual} />}</Field>
                    <section className="admin-form-grid__wide" aria-labelledby="resource-title">
                        <h3 id="resource-title">Resources</h3>
                        <div className="admin-filter-panel__fields">
                            <Field label="Resource type">{({ inputId }) => <select id={inputId} name="resourceType" value={resourceDraft.resourceType} onChange={updateResource}>{resourceTypes.map((type) => <option key={type} value={type}>{label(type)}</option>)}</select>}</Field>
                            <Field label="Resource title">{({ inputId }) => <input id={inputId} name="title" value={resourceDraft.title} onChange={updateResource} />}</Field>
                            <Field label="Resource URL">{({ inputId }) => <input id={inputId} name="url" value={resourceDraft.url ?? ''} onChange={updateResource} />}</Field>
                            <Field label="Asset ID">{({ inputId }) => <input id={inputId} name="assetId" value={resourceDraft.assetId ?? ''} onChange={updateResource} />}</Field>
                        </div>
                        <button type="button" onClick={addResource}>Add resource</button>
                        {manualDraft.resources.length > 0 && <p>{manualDraft.resources.length} resources staged with this song.</p>}
                    </section>
                    <button type="submit">Stage manual song</button>
                </form>
            </section>

            <section className="admin-shell__panel" aria-labelledby="csv-song-import-title">
                <h2 id="csv-song-import-title">CSV import</h2>
                <form className="admin-form-grid" onSubmit={submitCsv}>
                    <Field label="CSV file">{({ inputId }) => <input id={inputId} type="file" accept=".csv,text/csv" required onChange={(event) => setCsvFile(event.target.files?.[0] ?? null)} />}</Field>
                    <Field label="Default license">{({ inputId }) => <select id={inputId} value={csvLicenseType} onChange={(event) => setCsvLicenseType(event.target.value)}>{licenseTypes.map((type) => <option key={type} value={type}>{label(type)}</option>)}</select>}</Field>
                    <Field label="Default license evidence">{({ inputId }) => <input id={inputId} value={csvLicenseEvidence} onChange={(event) => setCsvLicenseEvidence(event.target.value)} />}</Field>
                    <section className="admin-form-grid__wide" aria-labelledby="csv-columns-title">
                        <h3 id="csv-columns-title">Supported columns</h3>
                        <p className="admin-shell__muted">The first row must be headers. Use semicolons or pipes inside list columns such as themes, scriptureReferences, and resources.</p>
                        <div className="admin-chip-list">
                            {csvColumns.map((column) => <code key={column}>{column}</code>)}
                        </div>
                    </section>
                    <button type="submit">Stage CSV rows</button>
                </form>
            </section>

            {result && <ImportResult result={result} />}
        </main>
    );
};

export const ImportResult = ({ result }: { result: SongImportResponse }) => {
    const candidates = result.candidates.length > 0
        ? result.candidates
        : result.candidateIds.map((candidateId) => ({
            candidateId,
            rawTitle: 'Imported song',
            normalizedTitle: null,
            sourceArtistName: null,
            status: 'DEDUPLICATION_REVIEW',
        }));
    const rows = candidates.map((candidate) => [
        <a href={`/admin/imports/${encodeURIComponent(candidate.candidateId)}`}>{candidate.normalizedTitle || candidate.rawTitle}</a>,
        candidate.sourceArtistName ?? 'Unknown',
        <Badge severity="warning">{label(candidate.status)}</Badge>,
    ]);
    const errorRows = result.validationErrors.map((error) => [error.rowIdentifier, error.field, error.message]);
    return (
        <section className="admin-shell__panel" aria-labelledby="song-import-result-title">
            <h2 id="song-import-result-title">Import result</h2>
            <p>Batch <a href={`/admin/imports?batchId=${encodeURIComponent(result.importBatchId)}`}>{result.importBatchId}</a> finished as {label(result.status)} with {result.acceptedCount} accepted candidates and {result.validationErrorCount} validation errors.</p>
            {rows.length > 0 && <DataTable caption="Staged candidates" columns={['Title', 'Artist', 'Review state']} rows={rows} />}
            {errorRows.length > 0 && <DataTable caption="Validation errors" columns={['Row', 'Field', 'Message']} rows={errorRows} />}
        </section>
    );
};
