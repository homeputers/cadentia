import { useEffect, useMemo, useState } from 'react';
import { hasCapability } from '../auth/permissions';
import type { AdminSession } from '../auth/session';
import { adminEnvironment } from '../config/environment';
import { createAdminApiClient, type AdminApiClient, type AdminApiError } from '../generated/cadentia-api/client';
import {
    createRehearsalTeamAssignment,
    createServiceTeamAssignment,
    createSongTeamAssignmentOverride,
    createTeamRehearsalEvent,
    getServicePlan,
    getServiceTeamRoster,
    listTeamAssignmentHistory,
    listTeamMusicians,
    listTeamRehearsalEvents,
    removeServiceTeamAssignment,
    reorderServiceTeamAssignments,
    substituteServiceTeamAssignment,
    teamAssignmentStatusCodes,
    teamInstrumentCodes,
    teamMusicianRoleCodes,
    teamVocalPartCodes,
    updateServiceTeamAssignment,
    type ServicePlanBlock,
    type TeamAssignmentHistoryEntry,
    type TeamAssignmentStatusCode,
    type TeamInstrumentCode,
    type TeamMusician,
    type TeamMusicianRoleCode,
    type TeamRehearsalEvent,
    type TeamServiceAssignment,
    type TeamServiceRoster,
    type TeamVocalPartCode,
} from '../team-assignments';
import { LocalizedView } from '../i18n';
import { Badge, Breadcrumbs, DataTable, PageHeader, StatePanel, redactSensitiveError } from './admin-ui';

const failureMessage = (status?: number) => {
    if (status === 400) return 'Backend validation rejected this change. Review the assignment fields; an availability conflict requires the explicit override.';
    if (status === 401) return 'Your admin session expired. Sign in again before retrying.';
    if (status === 403) return 'You are not authorized to change team assignments.';
    if (status === 404) return 'This assignment or service plan is no longer available. Reload the roster.';
    return 'The change failed safely. No protected details were exposed.';
};

const toIso = (value: string) => (value ? new Date(value).toISOString() : '');

const statusSeverity = (status: TeamAssignmentStatusCode) =>
    status === 'ACCEPTED' ? 'success' : status === 'DECLINED' || status === 'UNAVAILABLE' ? 'danger' : status === 'SUBSTITUTE' ? 'neutral' : 'warning';

type AssignmentFormState = {
    musicianId: string;
    roleCode: TeamMusicianRoleCode | '';
    instrumentCode: TeamInstrumentCode | '';
    vocalPartCode: TeamVocalPartCode | '';
    statusCode: TeamAssignmentStatusCode;
    assignmentOrder: string;
    overrideUnavailable: boolean;
    reasonCode: string;
};

const emptyAssignmentForm: AssignmentFormState = {
    musicianId: '',
    roleCode: '',
    instrumentCode: '',
    vocalPartCode: '',
    statusCode: 'REQUESTED',
    assignmentOrder: '',
    overrideUnavailable: false,
    reasonCode: '',
};

export const TeamAssignmentDetail = ({ session, servicePlanId, apiClient: providedApiClient }: { session: AdminSession; servicePlanId: string; apiClient?: AdminApiClient }) => {
    const apiClient = useMemo(() => providedApiClient ?? createAdminApiClient({ environment: adminEnvironment, getAccessToken: async () => null }), [providedApiClient]);
    const canView = hasCapability(session, 'VIEW_TEAM_ROSTER');
    const canManage = hasCapability(session, 'MANAGE_TEAM_ASSIGNMENTS');

    const [roster, setRoster] = useState<TeamServiceRoster | null>(null);
    const [musicians, setMusicians] = useState<TeamMusician[]>([]);
    const [history, setHistory] = useState<TeamAssignmentHistoryEntry[]>([]);
    const [rehearsalEvents, setRehearsalEvents] = useState<TeamRehearsalEvent[]>([]);
    const [blocks, setBlocks] = useState<ServicePlanBlock[]>([]);
    const [planTitle, setPlanTitle] = useState('');
    const [state, setState] = useState<'loading' | 'ready' | 'empty' | 'forbidden' | 'unauthorized' | 'error'>('loading');
    const [message, setMessage] = useState('');
    const [pending, setPending] = useState(false);

    const [assignmentForm, setAssignmentForm] = useState<AssignmentFormState>(emptyAssignmentForm);
    const [editingAssignmentId, setEditingAssignmentId] = useState<string | null>(null);
    const [substitutingId, setSubstitutingId] = useState<string | null>(null);
    const [substituteMusicianId, setSubstituteMusicianId] = useState('');
    const [substituteOverride, setSubstituteOverride] = useState(false);
    const [rowReasons, setRowReasons] = useState<Record<string, string>>({});

    const [overrideBlockId, setOverrideBlockId] = useState('');
    const [overrideAssignmentId, setOverrideAssignmentId] = useState('');
    const [overrideMusicianId, setOverrideMusicianId] = useState('');
    const [overrideRole, setOverrideRole] = useState<TeamMusicianRoleCode | ''>('');
    const [overrideReason, setOverrideReason] = useState('');

    const [eventStartsAt, setEventStartsAt] = useState('');
    const [eventEndsAt, setEventEndsAt] = useState('');
    const [eventLocation, setEventLocation] = useState('');
    const [rehearsalEventId, setRehearsalEventId] = useState('');
    const [rehearsalMusicianId, setRehearsalMusicianId] = useState('');
    const [rehearsalRole, setRehearsalRole] = useState<TeamMusicianRoleCode | ''>('');
    const [rehearsalServiceAssignmentId, setRehearsalServiceAssignmentId] = useState('');

    const musicianName = (musicianId?: string | null) =>
        (musicianId && musicians.find((musician) => musician.musicianId === musicianId)?.displayName) || musicianId || 'Unassigned';

    const load = async () => {
        setState('loading');
        try {
            const rosterResponse = await getServiceTeamRoster(apiClient, servicePlanId);
            setRoster(rosterResponse);
            setState(rosterResponse.assignments.length ? 'ready' : 'empty');
        } catch (caught) {
            const apiError = caught as AdminApiError;
            setMessage(redactSensitiveError(apiError.message));
            setState(apiError.status === 401 ? 'unauthorized' : apiError.status === 403 ? 'forbidden' : 'error');
            return;
        }
        const [musiciansResult, historyResult, eventsResult, planResult] = await Promise.allSettled([
            listTeamMusicians(apiClient),
            listTeamAssignmentHistory(apiClient, servicePlanId),
            listTeamRehearsalEvents(apiClient, servicePlanId),
            getServicePlan(apiClient, servicePlanId),
        ]);
        if (musiciansResult.status === 'fulfilled') setMusicians(musiciansResult.value);
        if (historyResult.status === 'fulfilled') setHistory(historyResult.value);
        if (eventsResult.status === 'fulfilled') setRehearsalEvents(eventsResult.value);
        if (planResult.status === 'fulfilled') {
            setBlocks(planResult.value.blocks ?? []);
            setPlanTitle(planResult.value.title);
        }
    };

    useEffect(() => {
        if (!canView) { setState('forbidden'); return; }
        void load();
    }, [canView, apiClient, servicePlanId]);

    const runMutation = async (operation: () => Promise<unknown>, successMessage: string) => {
        setMessage('');
        setPending(true);
        try {
            await operation();
            setMessage(successMessage);
            await load();
            return true;
        } catch (caught) {
            setMessage(failureMessage((caught as AdminApiError).status));
            return false;
        } finally {
            setPending(false);
        }
    };

    const submitAssignment = async () => {
        if (!assignmentForm.musicianId || !assignmentForm.roleCode) return;
        const payload = {
            musicianId: assignmentForm.musicianId,
            roleCode: assignmentForm.roleCode as TeamMusicianRoleCode,
            instrumentCode: assignmentForm.instrumentCode || undefined,
            vocalPartCode: assignmentForm.vocalPartCode || undefined,
            statusCode: assignmentForm.statusCode,
            assignmentOrder: assignmentForm.assignmentOrder ? Number(assignmentForm.assignmentOrder) : undefined,
            overrideUnavailable: assignmentForm.overrideUnavailable || undefined,
            reasonCode: assignmentForm.reasonCode || undefined,
        };
        const succeeded = await runMutation(
            () => editingAssignmentId
                ? updateServiceTeamAssignment(apiClient, servicePlanId, editingAssignmentId, payload, session.actorId)
                : createServiceTeamAssignment(apiClient, servicePlanId, payload, session.actorId),
            editingAssignmentId ? 'Assignment updated with audit attribution.' : 'Assignment created with audit attribution.',
        );
        if (succeeded) {
            setAssignmentForm(emptyAssignmentForm);
            setEditingAssignmentId(null);
        }
    };

    const startEdit = (assignment: TeamServiceAssignment) => {
        setEditingAssignmentId(assignment.assignmentId);
        setAssignmentForm({
            musicianId: assignment.musicianId,
            roleCode: assignment.roleCode,
            instrumentCode: assignment.instrumentCode ?? '',
            vocalPartCode: assignment.vocalPartCode ?? '',
            statusCode: assignment.statusCode,
            assignmentOrder: assignment.assignmentOrder != null ? String(assignment.assignmentOrder) : '',
            overrideUnavailable: false,
            reasonCode: '',
        });
    };

    const removeAssignment = async (assignment: TeamServiceAssignment) => {
        await runMutation(
            () => removeServiceTeamAssignment(apiClient, servicePlanId, assignment.assignmentId, session.actorId, rowReasons[assignment.assignmentId] || undefined),
            'Assignment removed from the active roster. History is preserved.',
        );
    };

    const submitSubstitute = async () => {
        if (!substitutingId || !substituteMusicianId) return;
        const succeeded = await runMutation(
            () => substituteServiceTeamAssignment(apiClient, servicePlanId, substitutingId, {
                substituteMusicianId,
                overrideUnavailable: substituteOverride || undefined,
                reasonCode: rowReasons[substitutingId] || undefined,
            }, session.actorId),
            'Substitute assignment created. The original assignment remains in history.',
        );
        if (succeeded) {
            setSubstitutingId(null);
            setSubstituteMusicianId('');
            setSubstituteOverride(false);
        }
    };

    const moveAssignment = async (assignment: TeamServiceAssignment, direction: -1 | 1) => {
        const ordered = (roster?.assignments ?? []).map((entry) => entry.assignmentId);
        const index = ordered.indexOf(assignment.assignmentId);
        const target = index + direction;
        if (index < 0 || target < 0 || target >= ordered.length) return;
        [ordered[index], ordered[target]] = [ordered[target], ordered[index]];
        await runMutation(
            () => reorderServiceTeamAssignments(apiClient, servicePlanId, ordered, session.actorId),
            'Roster order updated with audit attribution.',
        );
    };

    const submitSongOverride = async () => {
        if (!overrideBlockId || !overrideAssignmentId || !overrideMusicianId || !overrideRole) return;
        const succeeded = await runMutation(
            () => createSongTeamAssignmentOverride(apiClient, servicePlanId, {
                servicePlanBlockId: overrideBlockId,
                baseServiceAssignmentId: overrideAssignmentId,
                musicianId: overrideMusicianId,
                roleCode: overrideRole as TeamMusicianRoleCode,
                statusCode: 'ACCEPTED',
                reasonCode: overrideReason || undefined,
            }, session.actorId),
            'Song-specific override recorded. Catalog approval gates are unchanged.',
        );
        if (succeeded) {
            setOverrideBlockId('');
            setOverrideAssignmentId('');
            setOverrideMusicianId('');
            setOverrideRole('');
            setOverrideReason('');
        }
    };

    const submitRehearsalEvent = async () => {
        const succeeded = await runMutation(
            () => createTeamRehearsalEvent(apiClient, {
                servicePlanId,
                startsAt: toIso(eventStartsAt),
                endsAt: toIso(eventEndsAt),
                location: eventLocation || undefined,
            }, session.actorId),
            'Rehearsal event created.',
        );
        if (succeeded) {
            setEventStartsAt('');
            setEventEndsAt('');
            setEventLocation('');
        }
    };

    const submitRehearsalAssignment = async () => {
        if (!rehearsalEventId || !rehearsalMusicianId || !rehearsalRole) return;
        await runMutation(
            () => createRehearsalTeamAssignment(apiClient, rehearsalEventId, {
                servicePlanId,
                serviceAssignmentId: rehearsalServiceAssignmentId || undefined,
                musicianId: rehearsalMusicianId,
                roleCode: rehearsalRole as TeamMusicianRoleCode,
                statusCode: 'REQUESTED',
            }, session.actorId),
            'Rehearsal assignment created with its own response status.',
        );
    };

    const musicianOptions = musicians.map((musician) => (
        <option key={musician.musicianId} value={musician.musicianId}>{musician.displayName}</option>
    ));

    const rosterRows = (roster?.assignments ?? []).map((assignment, index, all) => [
        assignment.assignmentOrder ?? index,
        musicianName(assignment.musicianId),
        assignment.roleCode,
        assignment.instrumentCode ?? 'None',
        assignment.vocalPartCode ?? 'None',
        <Badge severity={statusSeverity(assignment.statusCode)}>{assignment.statusCode}</Badge>,
        canManage ? (
            <div>
                <button type="button" disabled={pending} onClick={() => startEdit(assignment)}>Edit</button>{' '}
                <button type="button" disabled={pending} onClick={() => setSubstitutingId(assignment.assignmentId)}>Substitute</button>{' '}
                <button type="button" disabled={pending || index === 0} aria-label={`Move ${musicianName(assignment.musicianId)} up`} onClick={() => void moveAssignment(assignment, -1)}>Up</button>{' '}
                <button type="button" disabled={pending || index === all.length - 1} aria-label={`Move ${musicianName(assignment.musicianId)} down`} onClick={() => void moveAssignment(assignment, 1)}>Down</button>{' '}
                <input
                    aria-label={`Removal reason for ${musicianName(assignment.musicianId)}`}
                    placeholder="Optional reason"
                    value={rowReasons[assignment.assignmentId] ?? ''}
                    onChange={(event) => setRowReasons((current) => ({ ...current, [assignment.assignmentId]: event.target.value }))}
                    disabled={pending}
                />{' '}
                <button type="button" disabled={pending} onClick={() => void removeAssignment(assignment)}>Remove</button>
            </div>
        ) : 'Read-only',
    ]);

    const historyRows = history.map((entry) => [
        new Date(entry.changedAt).toLocaleString(),
        entry.assignmentType,
        entry.changeAction,
        musicianName(entry.musicianId),
        entry.roleCode ?? 'None',
        entry.statusCode ? <Badge severity={statusSeverity(entry.statusCode)}>{entry.statusCode}</Badge> : 'None',
        entry.changedBy,
        entry.reasonCode,
    ]);

    return <LocalizedView><main className="admin-shell" aria-labelledby="team-assignment-detail-title">
        <Breadcrumbs items={[{ label: 'Admin', href: '/admin' }, { label: 'Team assignments', href: '/admin/team-assignments' }, { label: planTitle || servicePlanId }]} />
        <PageHeader
            eyebrow="Team planning"
            title={planTitle ? `Service roster: ${planTitle}` : 'Service roster'}
            titleId="team-assignment-detail-title"
            description="Privacy-aware service roster with staffing gaps and availability conflicts. Substitutions and removals preserve assignment history; readiness notes never change catalog approval."
        />
        {message && <p role="status" className="admin-shell__panel">{message}</p>}
        <StatePanel state={state} title="Service roster" onRetry={() => void load()} />

        {roster && (
            <section aria-labelledby="roster-signals-title" className="admin-shell__panel">
                <h2 id="roster-signals-title">Staffing signals</h2>
                {roster.staffingGaps.length === 0 && roster.availabilityConflicts.length === 0 && <p>No staffing gaps or availability conflicts returned.</p>}
                {roster.staffingGaps.length > 0 && (
                    <p>Staffing gaps: {roster.staffingGaps.map((gap) => <Badge key={gap} severity="danger">{gap}</Badge>)}</p>
                )}
                {roster.availabilityConflicts.length > 0 && (
                    <p>Availability conflicts on assignments: {roster.availabilityConflicts.map((conflict) => <code key={conflict}>{conflict}</code>)}</p>
                )}
            </section>
        )}

        {roster && roster.assignments.length > 0 && (
            <section aria-labelledby="roster-title" className="admin-shell__panel">
                <h2 id="roster-title">Active roster</h2>
                <DataTable
                    caption="Active service team roster"
                    columns={['Order', 'Musician', 'Role', 'Instrument', 'Vocal part', 'Status', 'Actions']}
                    rows={rosterRows}
                />
            </section>
        )}

        {canManage && roster && (
            <section aria-labelledby="assignment-form-title" className="admin-shell__panel">
                <h2 id="assignment-form-title">{editingAssignmentId ? 'Edit assignment' : 'Add assignment'}</h2>
                <form onSubmit={(event) => { event.preventDefault(); void submitAssignment(); }}>
                    <div>
                        <label htmlFor="assignment-musician">Musician</label>{' '}
                        <select id="assignment-musician" required value={assignmentForm.musicianId} onChange={(event) => setAssignmentForm((current) => ({ ...current, musicianId: event.target.value }))} disabled={pending}>
                            <option value="">Select musician...</option>
                            {musicianOptions}
                        </select>
                    </div>
                    <div>
                        <label htmlFor="assignment-role">Role</label>{' '}
                        <select id="assignment-role" required value={assignmentForm.roleCode} onChange={(event) => setAssignmentForm((current) => ({ ...current, roleCode: event.target.value as TeamMusicianRoleCode }))} disabled={pending}>
                            <option value="">Select role...</option>
                            {teamMusicianRoleCodes.map((code) => <option key={code} value={code}>{code}</option>)}
                        </select>
                    </div>
                    <div>
                        <label htmlFor="assignment-instrument">Instrument</label>{' '}
                        <select id="assignment-instrument" value={assignmentForm.instrumentCode} onChange={(event) => setAssignmentForm((current) => ({ ...current, instrumentCode: event.target.value as TeamInstrumentCode | '' }))} disabled={pending}>
                            <option value="">None</option>
                            {teamInstrumentCodes.map((code) => <option key={code} value={code}>{code}</option>)}
                        </select>
                    </div>
                    <div>
                        <label htmlFor="assignment-vocal-part">Vocal part</label>{' '}
                        <select id="assignment-vocal-part" value={assignmentForm.vocalPartCode} onChange={(event) => setAssignmentForm((current) => ({ ...current, vocalPartCode: event.target.value as TeamVocalPartCode | '' }))} disabled={pending}>
                            <option value="">None</option>
                            {teamVocalPartCodes.map((code) => <option key={code} value={code}>{code}</option>)}
                        </select>
                    </div>
                    <div>
                        <label htmlFor="assignment-status">Status</label>{' '}
                        <select id="assignment-status" value={assignmentForm.statusCode} onChange={(event) => setAssignmentForm((current) => ({ ...current, statusCode: event.target.value as TeamAssignmentStatusCode }))} disabled={pending}>
                            {teamAssignmentStatusCodes.map((code) => <option key={code} value={code}>{code}</option>)}
                        </select>
                    </div>
                    <div>
                        <label htmlFor="assignment-order">Order</label>{' '}
                        <input id="assignment-order" type="number" min={0} value={assignmentForm.assignmentOrder} onChange={(event) => setAssignmentForm((current) => ({ ...current, assignmentOrder: event.target.value }))} disabled={pending} />
                    </div>
                    <div>
                        <label htmlFor="assignment-override">
                            <input id="assignment-override" type="checkbox" checked={assignmentForm.overrideUnavailable} onChange={(event) => setAssignmentForm((current) => ({ ...current, overrideUnavailable: event.target.checked }))} disabled={pending} />
                            Override unavailable musician
                        </label>{' '}
                        <small>Only enable after the backend reports an availability conflict for this musician and service.</small>
                    </div>
                    <div>
                        <label htmlFor="assignment-reason">Audit reason</label>{' '}
                        <input id="assignment-reason" value={assignmentForm.reasonCode} onChange={(event) => setAssignmentForm((current) => ({ ...current, reasonCode: event.target.value }))} disabled={pending} />
                    </div>
                    <button type="submit" disabled={pending || !assignmentForm.musicianId || !assignmentForm.roleCode}>{editingAssignmentId ? 'Save assignment' : 'Add assignment'}</button>{' '}
                    {editingAssignmentId && <button type="button" disabled={pending} onClick={() => { setEditingAssignmentId(null); setAssignmentForm(emptyAssignmentForm); }}>Cancel edit</button>}
                </form>
            </section>
        )}

        {canManage && substitutingId && (
            <section aria-labelledby="substitute-title" className="admin-shell__panel">
                <h2 id="substitute-title">Substitute for {musicianName((roster?.assignments ?? []).find((assignment) => assignment.assignmentId === substitutingId)?.musicianId)}</h2>
                <form onSubmit={(event) => { event.preventDefault(); void submitSubstitute(); }}>
                    <div>
                        <label htmlFor="substitute-musician">Substitute musician</label>{' '}
                        <select id="substitute-musician" required value={substituteMusicianId} onChange={(event) => setSubstituteMusicianId(event.target.value)} disabled={pending}>
                            <option value="">Select musician...</option>
                            {musicianOptions}
                        </select>
                    </div>
                    <div>
                        <label htmlFor="substitute-override">
                            <input id="substitute-override" type="checkbox" checked={substituteOverride} onChange={(event) => setSubstituteOverride(event.target.checked)} disabled={pending} />
                            Override unavailable musician
                        </label>
                    </div>
                    <button type="submit" disabled={pending || !substituteMusicianId}>Create substitute</button>{' '}
                    <button type="button" disabled={pending} onClick={() => setSubstitutingId(null)}>Cancel</button>
                </form>
            </section>
        )}

        {canManage && roster && roster.assignments.length > 0 && blocks.length > 0 && (
            <section aria-labelledby="song-override-title" className="admin-shell__panel">
                <h2 id="song-override-title">Song-specific override</h2>
                <p>Assigns a controlled responsibility for one song block without changing catalog approval gates.</p>
                <form onSubmit={(event) => { event.preventDefault(); void submitSongOverride(); }}>
                    <div>
                        <label htmlFor="override-block">Song block</label>{' '}
                        <select id="override-block" required value={overrideBlockId} onChange={(event) => setOverrideBlockId(event.target.value)} disabled={pending}>
                            <option value="">Select block...</option>
                            {blocks.map((block) => <option key={block.blockId} value={block.blockId}>{block.orderIndex + 1}. {block.title} ({block.type})</option>)}
                        </select>
                    </div>
                    <div>
                        <label htmlFor="override-assignment">Base assignment</label>{' '}
                        <select id="override-assignment" required value={overrideAssignmentId} onChange={(event) => setOverrideAssignmentId(event.target.value)} disabled={pending}>
                            <option value="">Select assignment...</option>
                            {(roster?.assignments ?? []).map((assignment) => (
                                <option key={assignment.assignmentId} value={assignment.assignmentId}>{musicianName(assignment.musicianId)} — {assignment.roleCode}</option>
                            ))}
                        </select>
                    </div>
                    <div>
                        <label htmlFor="override-musician">Musician</label>{' '}
                        <select id="override-musician" required value={overrideMusicianId} onChange={(event) => setOverrideMusicianId(event.target.value)} disabled={pending}>
                            <option value="">Select musician...</option>
                            {musicianOptions}
                        </select>
                    </div>
                    <div>
                        <label htmlFor="override-role">Role</label>{' '}
                        <select id="override-role" required value={overrideRole} onChange={(event) => setOverrideRole(event.target.value as TeamMusicianRoleCode)} disabled={pending}>
                            <option value="">Select role...</option>
                            {teamMusicianRoleCodes.map((code) => <option key={code} value={code}>{code}</option>)}
                        </select>
                    </div>
                    <div>
                        <label htmlFor="override-reason">Audit reason</label>{' '}
                        <input id="override-reason" value={overrideReason} onChange={(event) => setOverrideReason(event.target.value)} disabled={pending} />
                    </div>
                    <button type="submit" disabled={pending || !overrideBlockId || !overrideAssignmentId || !overrideMusicianId || !overrideRole}>Create song override</button>
                </form>
            </section>
        )}

        {roster && (
            <section aria-labelledby="rehearsal-events-title" className="admin-shell__panel">
                <h2 id="rehearsal-events-title">Rehearsal events</h2>
                {rehearsalEvents.length === 0 && <p>No rehearsal events returned for this service.</p>}
                {rehearsalEvents.length > 0 && (
                    <DataTable
                        caption="Team-planning rehearsal events"
                        columns={['Starts', 'Ends', 'Location']}
                        rows={rehearsalEvents.map((event) => [
                            <>{new Date(event.startsAt).toLocaleString()}<br /><small>{event.rehearsalEventId}</small></>,
                            new Date(event.endsAt).toLocaleString(),
                            event.location ?? 'Not recorded',
                        ])}
                    />
                )}
                {canManage && (
                    <form onSubmit={(event) => { event.preventDefault(); void submitRehearsalEvent(); }}>
                        <div>
                            <label htmlFor="event-starts">Starts at</label>{' '}
                            <input id="event-starts" type="datetime-local" required value={eventStartsAt} onChange={(event) => setEventStartsAt(event.target.value)} disabled={pending} />
                        </div>
                        <div>
                            <label htmlFor="event-ends">Ends at</label>{' '}
                            <input id="event-ends" type="datetime-local" required value={eventEndsAt} onChange={(event) => setEventEndsAt(event.target.value)} disabled={pending} />
                        </div>
                        <div>
                            <label htmlFor="event-location">Location</label>{' '}
                            <input id="event-location" value={eventLocation} onChange={(event) => setEventLocation(event.target.value)} disabled={pending} />
                        </div>
                        <button type="submit" disabled={pending || !eventStartsAt || !eventEndsAt}>Create rehearsal event</button>
                    </form>
                )}
                {canManage && rehearsalEvents.length > 0 && (
                    <form onSubmit={(event) => { event.preventDefault(); void submitRehearsalAssignment(); }}>
                        <h3>Assign musician to rehearsal</h3>
                        <div>
                            <label htmlFor="rehearsal-event">Rehearsal event</label>{' '}
                            <select id="rehearsal-event" required value={rehearsalEventId} onChange={(event) => setRehearsalEventId(event.target.value)} disabled={pending}>
                                <option value="">Select rehearsal event...</option>
                                {rehearsalEvents.map((event) => (
                                    <option key={event.rehearsalEventId} value={event.rehearsalEventId}>{new Date(event.startsAt).toLocaleString()}{event.location ? ` — ${event.location}` : ''}</option>
                                ))}
                            </select>
                        </div>
                        <div>
                            <label htmlFor="rehearsal-musician">Musician</label>{' '}
                            <select id="rehearsal-musician" required value={rehearsalMusicianId} onChange={(event) => setRehearsalMusicianId(event.target.value)} disabled={pending}>
                                <option value="">Select musician...</option>
                                {musicianOptions}
                            </select>
                        </div>
                        <div>
                            <label htmlFor="rehearsal-role">Role</label>{' '}
                            <select id="rehearsal-role" required value={rehearsalRole} onChange={(event) => setRehearsalRole(event.target.value as TeamMusicianRoleCode)} disabled={pending}>
                                <option value="">Select role...</option>
                                {teamMusicianRoleCodes.map((code) => <option key={code} value={code}>{code}</option>)}
                            </select>
                        </div>
                        <div>
                            <label htmlFor="rehearsal-service-assignment">Linked service assignment</label>{' '}
                            <select id="rehearsal-service-assignment" value={rehearsalServiceAssignmentId} onChange={(event) => setRehearsalServiceAssignmentId(event.target.value)} disabled={pending}>
                                <option value="">None</option>
                                {(roster?.assignments ?? []).map((assignment) => (
                                    <option key={assignment.assignmentId} value={assignment.assignmentId}>{musicianName(assignment.musicianId)} — {assignment.roleCode}</option>
                                ))}
                            </select>
                        </div>
                        <button type="submit" disabled={pending || !rehearsalEventId || !rehearsalMusicianId || !rehearsalRole}>Create rehearsal assignment</button>
                    </form>
                )}
            </section>
        )}

        {roster && (
            <section aria-labelledby="assignment-history-title" className="admin-shell__panel">
                <h2 id="assignment-history-title">Assignment history</h2>
                {history.length === 0 && <p>No assignment history returned.</p>}
                {history.length > 0 && (
                    <DataTable
                        caption="Assignment change history"
                        columns={['Changed', 'Type', 'Action', 'Musician', 'Role', 'Status', 'Changed by', 'Reason']}
                        rows={historyRows}
                    />
                )}
            </section>
        )}
    </main></LocalizedView>;
};
