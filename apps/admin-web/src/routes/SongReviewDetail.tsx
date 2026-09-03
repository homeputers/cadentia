import { type ChangeEvent, type FormEvent, useEffect, useMemo, useState } from 'react';
import { hasCapability } from '../auth/permissions';
import type { AdminSession } from '../auth/session';
import { adminEnvironment } from '../config/environment';
import { createAdminApiClient, type AdminApiClient, type AdminApiError } from '../generated/cadentia-api/client';
import { assignSongTag, CONTROLLED_TAG_TYPES, getReviewSong, removeSongTag, toMetadataDraft, updateReviewSong, uploadAndAttachResource, type AssetAttachment, type AttachmentDraft, type SongMetadataDraft, type SongReviewDetail as SongReviewDetailModel } from '../song-review';
import { LocalizedView } from '../i18n';
import { ActionBadge, Badge, Breadcrumbs, DataTable, Field, PageHeader, StatePanel, redactSensitiveError } from './admin-ui';

const label = (value?: string | null) => value ? value.replaceAll('_', ' ').toLowerCase().replace(/^./, (c) => c.toUpperCase()) : 'None';
const severityFor = (value?: string | null) => value === 'APPROVED' || value === 'primary_chart' || value === 'performance' ? 'success' : value === 'ARCHIVED' || value === 'REJECTED' ? 'danger' : 'neutral';

const emptyAttachmentDraft = (songId: string): AttachmentDraft => ({
    targetType: 'song',
    targetId: songId,
    assetVersionId: '',
    attachmentType: 'pdf',
    displayLabel: '',
    sortOrder: 0,
    purpose: 'reference',
    requiredForUse: false,
    visibilityPolicy: 'catalog_reviewers',
});

export const SongReviewDetail = ({
    session,
    songId,
    apiClient = createAdminApiClient({ environment: adminEnvironment, getAccessToken: async () => null }),
}: {
    session: AdminSession;
    songId: string;
    apiClient?: AdminApiClient;
    initialSearch?: string;
}) => {
    const [detail, setDetail] = useState<SongReviewDetailModel | null>(null);
    const [draft, setDraft] = useState<SongMetadataDraft | null>(null);
    const [attachmentDraft, setAttachmentDraft] = useState<AttachmentDraft>(() => emptyAttachmentDraft(songId));
    const [attachmentFile, setAttachmentFile] = useState<File | null>(null);
    const [tagDraft, setTagDraft] = useState({ tagType: 'THEME', name: '' });
    const [state, setState] = useState<'loading' | 'ready' | 'unauthorized' | 'forbidden' | 'stale' | 'error'>('loading');
    const [error, setError] = useState('');
    const [notice, setNotice] = useState('');
    const canReview = hasCapability(session, 'REVIEW_CATALOG');
    const canEdit = canReview;
    const arrangementOptions = useMemo(() => detail?.arrangements ?? [], [detail]);

    const load = async (preserveDraft?: (loadedDraft: SongMetadataDraft) => SongMetadataDraft) => {
        setState(detail ? 'stale' : 'loading');
        try {
            const loaded = await getReviewSong(apiClient, songId);
            const loadedDraft = toMetadataDraft(loaded, session.actorId);
            setDetail(loaded);
            setDraft(preserveDraft ? preserveDraft(loadedDraft) : loadedDraft);
            setAttachmentDraft(emptyAttachmentDraft(songId));
            setAttachmentFile(null);
            setState('ready');
        } catch (caught) {
            const apiError = caught as AdminApiError;
            setError(redactSensitiveError(apiError.message));
            if (apiError.status === 401) setState('unauthorized');
            else if (apiError.status === 403) setState('forbidden');
            else setState('error');
        }
    };

    useEffect(() => { void load(); }, [songId]);

    const updateDraft = (name: keyof SongMetadataDraft, value: string | number | null) => {
        setDraft((current) => current ? { ...current, [name]: value } : current);
    };

    const saveSongMetadata = async (event: FormEvent) => {
        event.preventDefault();
        if (!draft || !detail) return;
        const loadedDraft = toMetadataDraft(detail, session.actorId);
        const arrangements = draft.arrangements;
        const lyricsDocuments = draft.lyricsDocuments;
        setState('stale');
        try {
            await updateReviewSong(apiClient, songId, {
                ...loadedDraft,
                actor: draft.actor,
                canonicalTitle: draft.canonicalTitle,
                normalizedTitle: draft.normalizedTitle,
                primaryLanguage: draft.primaryLanguage,
                originalArtistDisplay: draft.originalArtistDisplay,
                composerCredits: draft.composerCredits,
                ccliNumber: draft.ccliNumber,
                yearWritten: draft.yearWritten,
                songStatus: draft.songStatus,
                doctrinalNotes: draft.doctrinalNotes,
            });
            setNotice('Song metadata saved.');
            await load((nextDraft) => ({ ...nextDraft, arrangements, lyricsDocuments }));
        } catch (caught) {
            const apiError = caught as AdminApiError;
            setError(redactSensitiveError(apiError.message));
            setState(apiError.status === 403 ? 'forbidden' : 'error');
        }
    };

    const saveArrangementMetadata = async (event: FormEvent) => {
        event.preventDefault();
        if (!draft || !detail) return;
        const loadedDraft = toMetadataDraft(detail, session.actorId);
        const songMetadata = {
            actor: draft.actor,
            canonicalTitle: draft.canonicalTitle,
            normalizedTitle: draft.normalizedTitle,
            primaryLanguage: draft.primaryLanguage,
            originalArtistDisplay: draft.originalArtistDisplay,
            composerCredits: draft.composerCredits,
            ccliNumber: draft.ccliNumber,
            yearWritten: draft.yearWritten,
            songStatus: draft.songStatus,
            doctrinalNotes: draft.doctrinalNotes,
        };
        setState('stale');
        try {
            await updateReviewSong(apiClient, songId, {
                ...loadedDraft,
                arrangements: draft.arrangements,
                lyricsDocuments: draft.lyricsDocuments,
            });
            setNotice('Arrangements saved.');
            await load((nextDraft) => ({ ...nextDraft, ...songMetadata }));
        } catch (caught) {
            const apiError = caught as AdminApiError;
            setError(redactSensitiveError(apiError.message));
            setState(apiError.status === 403 ? 'forbidden' : 'error');
        }
    };

    const updateArrangement = (arrangementId: string, name: string, value: string | number | boolean | null) => {
        setDraft((current) => current ? {
            ...current,
            arrangements: current.arrangements.map((arrangement) => arrangement.arrangementId === arrangementId ? { ...arrangement, [name]: value } : arrangement),
        } : current);
    };

    const removeArrangement = (arrangementId: string) => {
        const isNewArrangement = arrangementId.startsWith('new-');
        setDraft((current) => current ? {
            ...current,
            arrangements: current.arrangements
                    .filter((arrangement) => arrangement.arrangementId !== arrangementId || !isNewArrangement)
                    .map((arrangement) => arrangement.arrangementId === arrangementId ? { ...arrangement, active: false } : arrangement),
        } : current);
    };

    const addArrangement = () => {
        setDraft((current) => current ? {
            ...current,
            arrangements: [
                ...current.arrangements,
                {
                    arrangementId: `new-${crypto.randomUUID()}`,
                    name: `${current.canonicalTitle} arrangement ${current.arrangements.length + 1}`,
                    normalizedName: '',
                    sourceType: 'CUSTOM',
                    language: current.primaryLanguage,
                    musicalKey: null,
                    keyMode: null,
                    tempoBpm: null,
                    timeSignature: null,
                    durationSeconds: null,
                    energyLevel: null,
                    difficultyLevel: null,
                    defaultForSong: current.arrangements.length === 0,
                    active: true,
                },
            ],
        } : current);
    };

    const updateLyrics = (lyricsDocumentId: string, name: string, value: string | boolean) => {
        setDraft((current) => current ? {
            ...current,
            lyricsDocuments: current.lyricsDocuments.map((lyrics) => lyrics.lyricsDocumentId === lyricsDocumentId ? { ...lyrics, [name]: value } : lyrics),
        } : current);
    };

    const addLyricsDocument = (arrangementId: string) => {
        setDraft((current) => current ? {
            ...current,
            lyricsDocuments: [
                ...current.lyricsDocuments,
                {
                    lyricsDocumentId: `new-${crypto.randomUUID()}`,
                    arrangementId,
                    format: 'plain_text',
                    content: '',
                    containsChords: false,
                    containsSections: false,
                    sourceReference: '',
                },
            ],
        } : current);
    };

    const removeLyricsDocument = (lyricsDocumentId: string) => {
        setDraft((current) => current ? {
            ...current,
            lyricsDocuments: current.lyricsDocuments.filter((lyrics) => lyrics.lyricsDocumentId !== lyricsDocumentId),
        } : current);
    };

    const updateAttachmentTarget = (targetType: 'song' | 'arrangement', targetId: string) => {
        setAttachmentDraft((current) => ({ ...current, targetType, targetId }));
    };

    const updateAttachmentInput = (event: ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
        const { name, value, type } = event.target;
        setAttachmentDraft((current) => ({
            ...current,
            [name]: type === 'checkbox' ? (event.target as HTMLInputElement).checked : name === 'sortOrder' ? Number(value) : value,
        }));
    };

    const updateAttachmentFile = (event: ChangeEvent<HTMLInputElement>) => {
        const file = event.target.files?.[0] ?? null;
        setAttachmentFile(file);
        if (file) {
            setAttachmentDraft((current) => ({
                ...current,
                displayLabel: current.displayLabel || file.name.replace(/\.[^.]+$/, ''),
                attachmentType: assetTypeForFile(file, current.attachmentType),
            }));
        }
    };

    const assignTag = async (event: FormEvent) => {
        event.preventDefault();
        if (!tagDraft.name.trim()) return;
        setState('stale');
        try {
            await assignSongTag(apiClient, songId, { actor: session.actorId, tagType: tagDraft.tagType, name: tagDraft.name.trim() });
            setNotice('Tag assigned.');
            setTagDraft((current) => ({ ...current, name: '' }));
            await load();
        } catch (caught) {
            const apiError = caught as AdminApiError;
            setError(redactSensitiveError(apiError.message));
            setState(apiError.status === 403 ? 'forbidden' : 'error');
        }
    };

    const removeTag = async (tagId: string) => {
        setState('stale');
        try {
            await removeSongTag(apiClient, songId, tagId, session.actorId);
            setNotice('Tag removed.');
            await load();
        } catch (caught) {
            const apiError = caught as AdminApiError;
            setError(redactSensitiveError(apiError.message));
            setState(apiError.status === 403 ? 'forbidden' : 'error');
        }
    };

    const addAttachment = async (event: FormEvent) => {
        event.preventDefault();
        if (!attachmentFile) return;
        setState('stale');
        try {
            await uploadAndAttachResource(apiClient, attachmentDraft, attachmentFile);
            setNotice('Resource uploaded and attached.');
            await load();
        } catch (caught) {
            const apiError = caught as AdminApiError;
            setError(redactSensitiveError(apiError.message));
            setState(apiError.status === 403 ? 'forbidden' : 'error');
        }
    };

    return (
        <LocalizedView><main className="admin-shell" aria-labelledby="song-detail-title">
            <Breadcrumbs items={[{ label: 'Admin', href: '/admin' }, { label: 'Songs', href: '/admin/songs' }, { label: detail?.song.canonicalTitle ?? 'Song resources' }]} />
            <PageHeader eyebrow="Catalog song review" title={detail?.song.canonicalTitle ?? 'Song resources'} titleId="song-detail-title" description="Editable catalog metadata, current lyrics versions, provenance, approvals, and song or arrangement resources." actions={canReview && <ActionBadge capability="REVIEW_CATALOG" />} />
            {notice && <p role="status" className="admin-shell__warning">{notice}</p>}
            <StatePanel state={state} title="Song resources" onRetry={() => void load()}>{error && <p>{error}</p>}</StatePanel>
            {detail && draft && <>
                <SongMetadataForm draft={draft} canEdit={canEdit} onSaveSongMetadata={saveSongMetadata} onSaveArrangementMetadata={saveArrangementMetadata} onSongChange={updateDraft} onArrangementChange={updateArrangement} onAddArrangement={addArrangement} onRemoveArrangement={removeArrangement} onLyricsChange={updateLyrics} onAddLyricsDocument={addLyricsDocument} onRemoveLyricsDocument={removeLyricsDocument} />
                <TagsSection tags={detail.tags} canEdit={canEdit} tagDraft={tagDraft} onTagDraftChange={setTagDraft} onAssignTag={assignTag} onRemoveTag={removeTag} />
                <AttachmentCreateForm draft={attachmentDraft} selectedFile={attachmentFile} arrangements={arrangementOptions} canEdit={canEdit} onSubmit={addAttachment} onTargetChange={updateAttachmentTarget} onInputChange={updateAttachmentInput} onFileChange={updateAttachmentFile} />
                <AttachmentSection title="Song attachments" caption="Song asset attachments" attachments={detail.songAttachments} />
                {detail.arrangements.map((arrangement) => <AttachmentSection key={arrangement.arrangementId} title={`${arrangement.name} attachments`} caption={`${arrangement.name} asset attachments`} attachments={detail.arrangementAttachments[arrangement.arrangementId] ?? []} />)}
                <CatalogEvidence detail={detail} />
            </>}
        </main></LocalizedView>
    );
};

const assetTypeForFile = (file: File, fallback: string) => {
    if (file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf')) return 'pdf';
    if (file.type.startsWith('audio/')) return 'rehearsal_recording';
    if (file.name.toLowerCase().endsWith('.mid') || file.name.toLowerCase().endsWith('.midi')) return 'midi_cue';
    return fallback;
};

const SongMetadataForm = ({ draft, canEdit, onSaveSongMetadata, onSaveArrangementMetadata, onSongChange, onArrangementChange, onAddArrangement, onRemoveArrangement, onLyricsChange, onAddLyricsDocument, onRemoveLyricsDocument }: {
    draft: SongMetadataDraft;
    canEdit: boolean;
    onSaveSongMetadata: (event: FormEvent) => void;
    onSaveArrangementMetadata: (event: FormEvent) => void;
    onSongChange: (name: keyof SongMetadataDraft, value: string | number | null) => void;
    onArrangementChange: (arrangementId: string, name: string, value: string | number | boolean | null) => void;
    onAddArrangement: () => void;
    onRemoveArrangement: (arrangementId: string) => void;
    onLyricsChange: (lyricsDocumentId: string, name: string, value: string | boolean) => void;
    onAddLyricsDocument: (arrangementId: string) => void;
    onRemoveLyricsDocument: (lyricsDocumentId: string) => void;
}) => {
    const existingArrangements = draft.arrangements.filter((a) => a.arrangementId && !a.arrangementId.startsWith('new-'));
    return (
    <LocalizedView><form className="admin-shell__panel admin-form-grid" aria-labelledby="song-metadata-title" onSubmit={(event) => event.preventDefault()}>
        <h2 id="song-metadata-title" className="admin-form-grid__wide">Song metadata</h2>
        <Field label="Title" required>{({ inputId }) => <input id={inputId} value={draft.canonicalTitle} disabled={!canEdit} onChange={(event) => onSongChange('canonicalTitle', event.target.value)} />}</Field>
        <Field label="Language" required>{({ inputId }) => <input id={inputId} value={draft.primaryLanguage} disabled={!canEdit} onChange={(event) => onSongChange('primaryLanguage', event.target.value)} />}</Field>
        <Field label="Status">{({ inputId }) => <select id={inputId} value={draft.songStatus} disabled={!canEdit} onChange={(event) => onSongChange('songStatus', event.target.value)}>{['APPROVED', 'IN_REVIEW', 'DRAFT', 'REJECTED', 'ARCHIVED'].map((status) => <option key={status} value={status}>{label(status)}</option>)}</select>}</Field>
        <Field label="Artist">{({ inputId }) => <input id={inputId} value={draft.originalArtistDisplay ?? ''} disabled={!canEdit} onChange={(event) => onSongChange('originalArtistDisplay', event.target.value)} />}</Field>
        <Field label="Composers">{({ inputId }) => <input id={inputId} value={draft.composerCredits ?? ''} disabled={!canEdit} onChange={(event) => onSongChange('composerCredits', event.target.value)} />}</Field>
        <Field label="CCLI">{({ inputId }) => <input id={inputId} value={draft.ccliNumber ?? ''} disabled={!canEdit} onChange={(event) => onSongChange('ccliNumber', event.target.value)} />}</Field>
        <Field label="Year">{({ inputId }) => <input id={inputId} type="number" value={draft.yearWritten ?? ''} disabled={!canEdit} onChange={(event) => onSongChange('yearWritten', event.target.value ? Number(event.target.value) : null)} />}</Field>
        <Field label="Doctrinal notes">{({ inputId }) => <textarea id={inputId} value={draft.doctrinalNotes ?? ''} disabled={!canEdit} onChange={(event) => onSongChange('doctrinalNotes', event.target.value)} />}</Field>
        <button type="button" disabled={!canEdit} onClick={onSaveSongMetadata}>Save song metadata</button>
        <section className="admin-form-grid__wide admin-shell__subsection" aria-labelledby="arrangement-metadata-title">
            <div className="admin-arrangement-header">
                <h3 id="arrangement-metadata-title">Arrangements</h3>
                <button type="button" className="secondary" disabled={!canEdit} onClick={onAddArrangement}>Add arrangement</button>
            </div>
        </section>
        <div className="admin-form-grid__wide admin-arrangement-table" role="table" aria-label="Arrangement metadata">
            <div className="admin-arrangement-row admin-arrangement-row--head" role="row">
                <span role="columnheader">Arrangement</span>
                <span role="columnheader">Source</span>
                <span role="columnheader">Key</span>
                <span role="columnheader">Mode</span>
                <span role="columnheader">Tempo</span>
                <span role="columnheader">Time</span>
                <span role="columnheader">Duration</span>
                <span role="columnheader">Energy</span>
                <span role="columnheader">Difficulty</span>
                <span role="columnheader">Status</span>
                <span role="columnheader">Action</span>
            </div>
            {draft.arrangements.map((arrangement, index) => {
                const rowId = arrangement.arrangementId ?? `arrangement-${index}`;
                return <div key={rowId} className={`admin-arrangement-row${arrangement.active ? '' : ' admin-arrangement-row--inactive'}`} role="row">
                    <div role="cell">
                        <label className="sr-only" htmlFor={`${rowId}-name`}>{arrangementHeading(draft.canonicalTitle, arrangement.name, draft.arrangements.length, index)}</label>
                        <input id={`${rowId}-name`} value={arrangement.name} disabled={!canEdit} onChange={(event) => onArrangementChange(arrangement.arrangementId ?? '', 'name', event.target.value)} />
                    </div>
                    <div role="cell">
                        <label className="sr-only" htmlFor={`${rowId}-source`}>Arrangement source</label>
                        <select id={`${rowId}-source`} value={arrangement.sourceType} disabled={!canEdit} onChange={(event) => onArrangementChange(arrangement.arrangementId ?? '', 'sourceType', event.target.value)}>{['ORIGINAL', 'LIVE', 'ACOUSTIC', 'STUDIO', 'TRANSLATION', 'CUSTOM', 'UNKNOWN'].map((source) => <option key={source} value={source}>{label(source)}</option>)}</select>
                    </div>
                    <div role="cell">
                        <label className="sr-only" htmlFor={`${rowId}-key`}>Musical key</label>
                        <input id={`${rowId}-key`} value={arrangement.musicalKey ?? ''} disabled={!canEdit} onChange={(event) => onArrangementChange(arrangement.arrangementId ?? '', 'musicalKey', event.target.value)} />
                    </div>
                    <div role="cell">
                        <label className="sr-only" htmlFor={`${rowId}-keyMode`}>Key mode</label>
                        <select id={`${rowId}-keyMode`} value={arrangement.keyMode ?? ''} disabled={!canEdit} onChange={(event) => onArrangementChange(arrangement.arrangementId ?? '', 'keyMode', event.target.value || null)}><option value="">—</option>{['MAJOR', 'MINOR', 'MODAL', 'UNKNOWN'].map((mode) => <option key={mode} value={mode}>{label(mode)}</option>)}</select>
                    </div>
                    <div role="cell">
                        <label className="sr-only" htmlFor={`${rowId}-tempo`}>Tempo BPM</label>
                        <input id={`${rowId}-tempo`} type="number" min={30} max={260} value={arrangement.tempoBpm ?? ''} disabled={!canEdit} onChange={(event) => onArrangementChange(arrangement.arrangementId ?? '', 'tempoBpm', event.target.value ? Number(event.target.value) : null)} />
                    </div>
                    <div role="cell">
                        <label className="sr-only" htmlFor={`${rowId}-timeSignature`}>Time signature</label>
                        <input id={`${rowId}-timeSignature`} value={arrangement.timeSignature ?? ''} disabled={!canEdit} onChange={(event) => onArrangementChange(arrangement.arrangementId ?? '', 'timeSignature', event.target.value || null)} placeholder="4/4" />
                    </div>
                    <div role="cell">
                        <label className="sr-only" htmlFor={`${rowId}-duration`}>Duration seconds</label>
                        <input id={`${rowId}-duration`} type="number" min={1} value={arrangement.durationSeconds ?? ''} disabled={!canEdit} onChange={(event) => onArrangementChange(arrangement.arrangementId ?? '', 'durationSeconds', event.target.value ? Number(event.target.value) : null)} />
                    </div>
                    <div role="cell">
                        <label className="sr-only" htmlFor={`${rowId}-energy`}>Energy level</label>
                        <select id={`${rowId}-energy`} value={arrangement.energyLevel ?? ''} disabled={!canEdit} onChange={(event) => onArrangementChange(arrangement.arrangementId ?? '', 'energyLevel', event.target.value ? Number(event.target.value) : null)}><option value="">—</option>{[1, 2, 3, 4, 5].map((level) => <option key={level} value={level}>{level}</option>)}</select>
                    </div>
                    <div role="cell">
                        <label className="sr-only" htmlFor={`${rowId}-difficulty`}>Difficulty level</label>
                        <select id={`${rowId}-difficulty`} value={arrangement.difficultyLevel ?? ''} disabled={!canEdit} onChange={(event) => onArrangementChange(arrangement.arrangementId ?? '', 'difficultyLevel', event.target.value ? Number(event.target.value) : null)}><option value="">—</option>{[1, 2, 3, 4, 5].map((level) => <option key={level} value={level}>{level}</option>)}</select>
                    </div>
                    <div role="cell"><Badge severity={arrangement.active ? 'success' : 'neutral'}>{arrangement.active ? 'Active' : 'Inactive'}</Badge></div>
                    <div role="cell">
                        {arrangement.active
                            ? <button type="button" className="danger" disabled={!canEdit || !arrangement.arrangementId} onClick={() => onRemoveArrangement(arrangement.arrangementId ?? '')}>Remove</button>
                            : <button type="button" className="secondary" disabled={!canEdit || !arrangement.arrangementId} onClick={() => onArrangementChange(arrangement.arrangementId ?? '', 'active', true)}>Restore</button>}
                    </div>
                </div>;
            })}
        </div>
        {draft.lyricsDocuments.map((lyrics) => {
            const isNew = lyrics.lyricsDocumentId.startsWith('new-');
            return <section key={lyrics.lyricsDocumentId} className="admin-form-grid__wide admin-shell__subsection" aria-label={isNew ? 'New lyrics document' : 'Current lyrics metadata'}>
                <div className="admin-lyrics-header">
                    <h3>{isNew ? 'New lyrics document' : 'Current lyrics'}</h3>
                    {isNew && <button type="button" className="danger small" disabled={!canEdit} onClick={() => onRemoveLyricsDocument(lyrics.lyricsDocumentId)}>Remove</button>}
                </div>
                <div className="admin-form-grid">
                    {isNew && (
                        <Field label="Arrangement" required>{({ inputId }) => {
                            const arrangementValue = (lyrics.arrangementId ?? '') as string;
                            return (
                            <select id={inputId} value={arrangementValue} disabled={!canEdit} onChange={(event) => onLyricsChange(lyrics.lyricsDocumentId, 'arrangementId', event.target.value)}>
                                <option value="">Select arrangement...</option>
                                {existingArrangements.map((arr) => (
                                    <option key={arr.arrangementId!} value={arr.arrangementId!}>{arr.name}</option>
                                ))}
                            </select>
                            );
                        }}</Field>
                    )}
                    <Field label="Format">{({ inputId }) => <select id={inputId} value={lyrics.format} disabled={!canEdit} onChange={(event) => onLyricsChange(lyrics.lyricsDocumentId, 'format', event.target.value)}>{['plain_text', 'chordpro', 'onsong', 'markdown'].map((format) => <option key={format} value={format}>{label(format)}</option>)}</select>}</Field>
                    <Field label="Source reference">{({ inputId }) => <input id={inputId} value={lyrics.sourceReference} disabled={!canEdit} onChange={(event) => onLyricsChange(lyrics.lyricsDocumentId, 'sourceReference', event.target.value)} />}</Field>
                    <Field label="Contains chords">{({ inputId }) => <input id={inputId} type="checkbox" checked={lyrics.containsChords} disabled={!canEdit} onChange={(event) => onLyricsChange(lyrics.lyricsDocumentId, 'containsChords', event.target.checked)} />}</Field>
                    <Field label="Lyrics content">{({ inputId }) => <textarea id={inputId} className="admin-form-grid__monospace" value={lyrics.content ?? ''} disabled={!canEdit} onChange={(event) => onLyricsChange(lyrics.lyricsDocumentId, 'content', event.target.value)} />}</Field>
                </div>
            </section>;
        })}
        {existingArrangements.length > 0 && (
            <div className="admin-form-grid__wide admin-shell__subsection">
                <h3>Add lyrics document</h3>
                <div className="admin-add-lyrics-row">
                    <AddLyricsSelect canEdit={canEdit} arrangements={existingArrangements} onAdd={onAddLyricsDocument} />
                </div>
            </div>
        )}
        <button type="button" disabled={!canEdit} onClick={onSaveArrangementMetadata}>Save arrangements</button>
    </form></LocalizedView>
    );
};

const AddLyricsSelect = ({ canEdit, arrangements, onAdd }: { canEdit: boolean; arrangements: SongMetadataDraft['arrangements']; onAdd: (arrangementId: string) => void }) => {
    const [selected, setSelected] = useState('');
    return (
        <LocalizedView><select value={selected as string} disabled={!canEdit} onChange={(event) => {
            const value = event.target.value;
            setSelected(value);
            if (value) {
                onAdd(value);
                setSelected('');
            }
        }}>
            <option value="">Select arrangement to add lyrics...</option>
            {arrangements.map((arr) => (
                <option key={arr.arrangementId!} value={arr.arrangementId!}>{arr.name}</option>
            ))}
        </select></LocalizedView>
    );
};

const arrangementHeading = (songTitle: string, arrangementName: string, count: number, index: number) => {
    if (count === 1 && arrangementName.trim().toLowerCase() === songTitle.trim().toLowerCase()) {
        return 'Default arrangement';
    }
    return `Arrangement ${index + 1}: ${arrangementName}`;
};

const AttachmentCreateForm = ({ draft, selectedFile, arrangements, canEdit, onSubmit, onTargetChange, onInputChange, onFileChange }: {
    draft: AttachmentDraft;
    selectedFile: File | null;
    arrangements: SongReviewDetailModel['arrangements'];
    canEdit: boolean;
    onSubmit: (event: FormEvent) => void;
    onTargetChange: (targetType: 'song' | 'arrangement', targetId: string) => void;
    onInputChange: (event: ChangeEvent<HTMLInputElement | HTMLSelectElement>) => void;
    onFileChange: (event: ChangeEvent<HTMLInputElement>) => void;
}) => (
    <LocalizedView><form className="admin-shell__panel admin-form-grid" aria-labelledby="add-resource-title" onSubmit={onSubmit}>
        <h2 id="add-resource-title" className="admin-form-grid__wide">Upload resource</h2>
        <Field label="Target">{({ inputId }) => <select id={inputId} value={`${draft.targetType}:${draft.targetId}`} disabled={!canEdit} onChange={(event) => {
            const [targetType, targetId] = event.target.value.split(':') as ['song' | 'arrangement', string];
            onTargetChange(targetType, targetId);
        }}><option value={`song:${draft.targetType === 'song' ? draft.targetId : arrangements[0]?.songId ?? ''}`}>Song</option>{arrangements.map((arrangement) => <option key={arrangement.arrangementId} value={`arrangement:${arrangement.arrangementId}`}>{arrangement.name}</option>)}</select>}</Field>
        <Field label="File" required>{({ inputId }) => <input id={inputId} type="file" disabled={!canEdit} onChange={onFileChange} />}</Field>
        <Field label="Label" required>{({ inputId }) => <input id={inputId} name="displayLabel" value={draft.displayLabel} disabled={!canEdit} onChange={onInputChange} />}</Field>
        <Field label="Type">{({ inputId }) => <select id={inputId} name="attachmentType" value={draft.attachmentType} disabled={!canEdit} onChange={onInputChange}>{['pdf', 'chord_chart', 'stem', 'backing_track', 'click_track', 'midi_cue', 'rehearsal_recording', 'preview', 'local_extension'].map((type) => <option key={type} value={type}>{label(type)}</option>)}</select>}</Field>
        <Field label="Purpose">{({ inputId }) => <select id={inputId} name="purpose" value={draft.purpose} disabled={!canEdit} onChange={onInputChange}>{['primary_chart', 'reference', 'rehearsal', 'performance', 'evidence', 'follow_up', 'local_override'].map((purpose) => <option key={purpose} value={purpose}>{label(purpose)}</option>)}</select>}</Field>
        <Field label="Visibility">{({ inputId }) => <select id={inputId} name="visibilityPolicy" value={draft.visibilityPolicy} disabled={!canEdit} onChange={onInputChange}>{['public_metadata', 'catalog_reviewers', 'worship_team', 'service_participants', 'admins_only', 'local_policy'].map((policy) => <option key={policy} value={policy}>{label(policy)}</option>)}</select>}</Field>
        <Field label="Required">{({ inputId }) => <input id={inputId} name="requiredForUse" type="checkbox" checked={draft.requiredForUse} disabled={!canEdit} onChange={onInputChange} />}</Field>
        <button type="submit" disabled={!canEdit || !selectedFile || !draft.displayLabel}>Upload and attach resource</button>
    </form></LocalizedView>
);

const CatalogEvidence = ({ detail }: { detail: SongReviewDetailModel }) => (
    <LocalizedView><section className="admin-shell__panel" aria-labelledby="catalog-evidence-title">
        <h2 id="catalog-evidence-title">Review evidence</h2>
        <DataTable caption="Approvals" columns={['Type', 'Status', 'Reviewer', 'Reviewed']} rows={detail.approvals.map((approval) => [
            approval.approvalType,
            <Badge severity={severityFor(approval.status)}>{label(approval.status)}</Badge>,
            approval.reviewer ?? 'None',
            approval.reviewedAt ?? approval.createdAt,
        ])} />
        <DataTable caption="Provenance" columns={['Source', 'License', 'Method', 'Captured']} rows={detail.provenance.map((record) => [
            record.sourceUri ? <a href={record.sourceUri}>{record.sourceLabel}</a> : record.sourceLabel,
            record.licenseType,
            record.importMethod,
            record.capturedAt,
        ])} />
    </section></LocalizedView>
);

const TagsSection = ({ tags, canEdit, tagDraft, onTagDraftChange, onAssignTag, onRemoveTag }: {
    tags: SongReviewDetailModel['tags'];
    canEdit: boolean;
    tagDraft: { tagType: string; name: string };
    onTagDraftChange: (draft: { tagType: string; name: string }) => void;
    onAssignTag: (event: FormEvent) => void;
    onRemoveTag: (tagId: string) => void;
}) => {
    const byType = tags.reduce<Record<string, typeof tags>>((acc, tag) => {
        const group = acc[tag.tagType] ?? [];
        group.push(tag);
        acc[tag.tagType] = group;
        return acc;
    }, {});
    const tagTypes = Object.keys(byType).sort();
    return (
        <LocalizedView><section className="admin-shell__panel" aria-labelledby="tags-title">
            <h2 id="tags-title">Tags</h2>
            {tagTypes.length
                ? tagTypes.map((tagType) => (
                    <div key={tagType} className="admin-tag-group">
                        <h3>{label(tagType)}</h3>
                        <div className="admin-tag-list">
                            {byType[tagType].map((tag) => (
                                <span key={tag.tagId} className="admin-tag-item">
                                    <Badge severity="neutral">{tag.name}</Badge>
                                    {canEdit && <button type="button" className="secondary" aria-label="Remove tag" title={tag.name} onClick={() => onRemoveTag(tag.tagId)}>Remove</button>}
                                </span>
                            ))}
                        </div>
                    </div>
                ))
                : <p>No tags assigned.</p>}
            <form className="admin-form-grid admin-form-grid__wide" aria-label="Assign tag" onSubmit={onAssignTag}>
                <Field label="Tag type">{({ inputId }) => <select id={inputId} value={tagDraft.tagType} disabled={!canEdit} onChange={(event) => onTagDraftChange({ ...tagDraft, tagType: event.target.value })}>{CONTROLLED_TAG_TYPES.map((tagType) => <option key={tagType} value={tagType}>{label(tagType)}</option>)}</select>}</Field>
                <Field label="Tag name" required>{({ inputId }) => <input id={inputId} value={tagDraft.name} disabled={!canEdit} onChange={(event) => onTagDraftChange({ ...tagDraft, name: event.target.value })} />}</Field>
                <button type="submit" disabled={!canEdit || !tagDraft.name.trim()}>Assign tag</button>
            </form>
        </section></LocalizedView>
    );
};

const AttachmentSection = ({ title, caption, attachments, emptyCopy = 'No attachments returned.' }: { title: string; caption: string; attachments: AssetAttachment[]; emptyCopy?: string }) => (
    <LocalizedView><section className="admin-shell__panel" aria-labelledby={`${caption.replace(/\W+/g, '-').toLowerCase()}-title`}>
        <h2 id={`${caption.replace(/\W+/g, '-').toLowerCase()}-title`}>{title}</h2>
        {attachments.length
            ? <DataTable caption={caption} columns={['Label', 'Type', 'Purpose', 'Asset version', 'Required', 'Visibility', 'Created']} rows={attachments.map((attachment) => [
                attachment.displayLabel,
                label(attachment.attachmentType),
                <Badge severity={severityFor(attachment.purpose)}>{label(attachment.purpose)}</Badge>,
                <code>{attachment.assetVersionId}</code>,
                attachment.requiredForUse ? 'Required' : 'Optional',
                label(attachment.visibilityPolicy),
                attachment.createdAt,
            ])} />
            : <p>{emptyCopy}</p>}
    </section></LocalizedView>
);
