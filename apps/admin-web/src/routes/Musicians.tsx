import { useEffect, useMemo, useState } from 'react';
import { hasCapability } from '../auth/permissions';
import type { AdminSession } from '../auth/session';
import { adminEnvironment } from '../config/environment';
import { createAdminApiClient, type AdminApiClient, type AdminApiError } from '../generated/cadentia-api/client';
import {
    createTeamAvailabilityWindow,
    createTeamMusician,
    listTeamMusicians,
    listUpcomingTeamAssignmentsForMusician,
    teamAssignmentStatusCodes,
    teamServingPreferenceCodes,
    teamVocalRangeCodes,
    type TeamAssignmentStatusCode,
    type TeamMusician,
    type TeamServingPreferenceCode,
    type TeamVocalRangeCode,
} from '../team-assignments';
import { LocalizedView } from '../i18n';
import { Badge, Breadcrumbs, DataTable, PageHeader, StatePanel, redactSensitiveError } from './admin-ui';

const mutationFailureMessage = (status?: number) => {
    if (status === 400) return 'Backend validation rejected this change. Review the fields before retrying.';
    if (status === 401) return 'Your admin session expired. Sign in again before retrying.';
    if (status === 403) return 'You are not authorized to change musician records.';
    return 'The change failed safely. No protected details were exposed.';
};

const toIso = (value: string) => (value ? new Date(value).toISOString() : '');

export const Musicians = ({ session, apiClient: providedApiClient }: { session: AdminSession; apiClient?: AdminApiClient }) => {
    const apiClient = useMemo(() => providedApiClient ?? createAdminApiClient({ environment: adminEnvironment, getAccessToken: async () => null }), [providedApiClient]);
    const [musicians, setMusicians] = useState<TeamMusician[] | null>(null);
    const [state, setState] = useState<'loading' | 'ready' | 'empty' | 'forbidden' | 'unauthorized' | 'error'>('loading');
    const [message, setMessage] = useState('');
    const [pending, setPending] = useState(false);
    const allowed = hasCapability(session, 'MANAGE_TEAM_ASSIGNMENTS');

    const [displayName, setDisplayName] = useState('');
    const [email, setEmail] = useState('');
    const [phone, setPhone] = useState('');
    const [accountPrincipal, setAccountPrincipal] = useState('');
    const [vocalRange, setVocalRange] = useState<TeamVocalRangeCode | ''>('');
    const [servingPreference, setServingPreference] = useState<TeamServingPreferenceCode | ''>('');
    const [reasonCode, setReasonCode] = useState('');

    const [availabilityMusicianId, setAvailabilityMusicianId] = useState('');
    const [availabilityStatus, setAvailabilityStatus] = useState<TeamAssignmentStatusCode>('UNAVAILABLE');
    const [availabilityStartsAt, setAvailabilityStartsAt] = useState('');
    const [availabilityEndsAt, setAvailabilityEndsAt] = useState('');
    const [availabilityReason, setAvailabilityReason] = useState('');

    const [upcomingMusicianId, setUpcomingMusicianId] = useState('');
    const [upcomingFrom, setUpcomingFrom] = useState('');
    const [upcoming, setUpcoming] = useState<Array<{ assignmentId: string; servicePlanId: string; roleCode: string; statusCode: string }> | null>(null);

    const load = async () => {
        setState('loading');
        try {
            const response = await listTeamMusicians(apiClient);
            setMusicians(response);
            setState(response.length ? 'ready' : 'empty');
        } catch (caught) {
            const apiError = caught as AdminApiError;
            setMessage(redactSensitiveError(apiError.message));
            setState(apiError.status === 401 ? 'unauthorized' : apiError.status === 403 ? 'forbidden' : 'error');
        }
    };

    useEffect(() => {
        if (!allowed) { setState('forbidden'); return; }
        void load();
    }, [allowed, apiClient]);

    const submitMusician = async () => {
        setMessage('');
        setPending(true);
        try {
            await createTeamMusician(apiClient, {
                displayName,
                email: email || undefined,
                phone: phone || undefined,
                accountPrincipal: accountPrincipal || undefined,
                primaryVocalRangeCode: vocalRange || undefined,
                servingPreferenceCode: servingPreference || undefined,
                reasonCode: reasonCode || undefined,
            }, session.actorId);
            setDisplayName('');
            setEmail('');
            setPhone('');
            setAccountPrincipal('');
            setVocalRange('');
            setServingPreference('');
            setReasonCode('');
            setMessage('Musician created with audit attribution.');
            await load();
        } catch (caught) {
            setMessage(mutationFailureMessage((caught as AdminApiError).status));
        } finally {
            setPending(false);
        }
    };

    const submitAvailability = async () => {
        setMessage('');
        setPending(true);
        try {
            await createTeamAvailabilityWindow(apiClient, availabilityMusicianId, {
                startsAt: toIso(availabilityStartsAt),
                endsAt: toIso(availabilityEndsAt),
                statusCode: availabilityStatus,
                reasonCode: availabilityReason || undefined,
            }, session.actorId);
            setAvailabilityStartsAt('');
            setAvailabilityEndsAt('');
            setAvailabilityReason('');
            setMessage('Availability window recorded. Availability conflicts now reflect this window.');
        } catch (caught) {
            setMessage(mutationFailureMessage((caught as AdminApiError).status));
        } finally {
            setPending(false);
        }
    };

    const loadUpcoming = async () => {
        setMessage('');
        setPending(true);
        try {
            const response = await listUpcomingTeamAssignmentsForMusician(
                apiClient,
                upcomingMusicianId,
                toIso(upcomingFrom) || new Date().toISOString(),
            );
            setUpcoming(response.map((assignment) => ({
                assignmentId: assignment.assignmentId,
                servicePlanId: assignment.servicePlanId,
                roleCode: assignment.roleCode,
                statusCode: assignment.statusCode,
            })));
        } catch (caught) {
            setMessage(mutationFailureMessage((caught as AdminApiError).status));
            setUpcoming(null);
        } finally {
            setPending(false);
        }
    };

    const musicianName = (musicianId: string) =>
        (musicians ?? []).find((musician) => musician.musicianId === musicianId)?.displayName ?? musicianId;

    const rows = (musicians ?? []).map((musician) => [
        <>{musician.displayName}<br /><small>{musician.musicianId}</small></>,
        <Badge severity={musician.active ? 'success' : 'neutral'}>{musician.active ? 'Active' : 'Inactive'}</Badge>,
        musician.primaryVocalRangeCode ?? 'Not permitted',
        musician.servingPreferenceCode ?? 'Not permitted',
        musician.email ?? 'Redacted',
    ]);

    const musicianOptions = (musicians ?? []).map((musician) => (
        <option key={musician.musicianId} value={musician.musicianId}>{musician.displayName}</option>
    ));

    return <LocalizedView><main className="admin-shell" aria-labelledby="musicians-title">
        <Breadcrumbs items={[{ label: 'Admin', href: '/admin' }, { label: 'Musicians' }]} />
        <PageHeader
            eyebrow="Team planning"
            title="Musicians"
            titleId="musicians-title"
            description="Instance-scoped musician directory. Contact details, vocal ranges, and serving preferences appear only when your role is permitted to read them; hidden fields are never inferred."
        />
        {message && <p role="status" className="admin-shell__panel">{message}</p>}
        <StatePanel state={state} title="Musicians" onRetry={() => void load()} />
        {state === 'ready' && rows.length > 0 && (
            <section aria-labelledby="musicians-directory-title" className="admin-shell__panel">
                <h2 id="musicians-directory-title">Musician directory</h2>
                <DataTable caption="Instance musician directory" columns={['Musician', 'Status', 'Vocal range', 'Serving preference', 'Email']} rows={rows} />
            </section>
        )}

        {allowed && (
            <section aria-labelledby="musician-create-title" className="admin-shell__panel">
                <h2 id="musician-create-title">Add musician</h2>
                <form onSubmit={(event) => { event.preventDefault(); void submitMusician(); }}>
                    <div>
                        <label htmlFor="musician-display-name">Display name</label>{' '}
                        <input id="musician-display-name" required value={displayName} onChange={(event) => setDisplayName(event.target.value)} disabled={pending} />
                    </div>
                    <div>
                        <label htmlFor="musician-email">Email</label>{' '}
                        <input id="musician-email" type="email" value={email} onChange={(event) => setEmail(event.target.value)} disabled={pending} />
                    </div>
                    <div>
                        <label htmlFor="musician-phone">Phone</label>{' '}
                        <input id="musician-phone" value={phone} onChange={(event) => setPhone(event.target.value)} disabled={pending} />
                    </div>
                    <div>
                        <label htmlFor="musician-principal">Account principal</label>{' '}
                        <input id="musician-principal" value={accountPrincipal} onChange={(event) => setAccountPrincipal(event.target.value)} disabled={pending} />
                    </div>
                    <div>
                        <label htmlFor="musician-vocal-range">Primary vocal range</label>{' '}
                        <select id="musician-vocal-range" value={vocalRange} onChange={(event) => setVocalRange(event.target.value as TeamVocalRangeCode | '')} disabled={pending}>
                            <option value="">None</option>
                            {teamVocalRangeCodes.map((code) => <option key={code} value={code}>{code}</option>)}
                        </select>
                    </div>
                    <div>
                        <label htmlFor="musician-serving-preference">Serving preference</label>{' '}
                        <select id="musician-serving-preference" value={servingPreference} onChange={(event) => setServingPreference(event.target.value as TeamServingPreferenceCode | '')} disabled={pending}>
                            <option value="">None</option>
                            {teamServingPreferenceCodes.map((code) => <option key={code} value={code}>{code}</option>)}
                        </select>
                    </div>
                    <div>
                        <label htmlFor="musician-reason">Audit reason</label>{' '}
                        <input id="musician-reason" value={reasonCode} onChange={(event) => setReasonCode(event.target.value)} disabled={pending} />
                    </div>
                    <button type="submit" disabled={pending || !displayName.trim()}>Create musician</button>
                </form>
            </section>
        )}

        {allowed && state === 'ready' && (
            <section aria-labelledby="availability-title" className="admin-shell__panel">
                <h2 id="availability-title">Record availability window</h2>
                <form onSubmit={(event) => { event.preventDefault(); void submitAvailability(); }}>
                    <div>
                        <label htmlFor="availability-musician">Musician</label>{' '}
                        <select id="availability-musician" required value={availabilityMusicianId} onChange={(event) => setAvailabilityMusicianId(event.target.value)} disabled={pending}>
                            <option value="">Select musician...</option>
                            {musicianOptions}
                        </select>
                    </div>
                    <div>
                        <label htmlFor="availability-status">Status</label>{' '}
                        <select id="availability-status" value={availabilityStatus} onChange={(event) => setAvailabilityStatus(event.target.value as TeamAssignmentStatusCode)} disabled={pending}>
                            {teamAssignmentStatusCodes.map((code) => <option key={code} value={code}>{code}</option>)}
                        </select>
                    </div>
                    <div>
                        <label htmlFor="availability-starts">Starts at</label>{' '}
                        <input id="availability-starts" type="datetime-local" required value={availabilityStartsAt} onChange={(event) => setAvailabilityStartsAt(event.target.value)} disabled={pending} />
                    </div>
                    <div>
                        <label htmlFor="availability-ends">Ends at</label>{' '}
                        <input id="availability-ends" type="datetime-local" required value={availabilityEndsAt} onChange={(event) => setAvailabilityEndsAt(event.target.value)} disabled={pending} />
                    </div>
                    <div>
                        <label htmlFor="availability-reason">Audit reason</label>{' '}
                        <input id="availability-reason" value={availabilityReason} onChange={(event) => setAvailabilityReason(event.target.value)} disabled={pending} />
                    </div>
                    <button type="submit" disabled={pending || !availabilityMusicianId}>Record availability</button>
                </form>
            </section>
        )}

        {state === 'ready' && (
            <section aria-labelledby="upcoming-title" className="admin-shell__panel">
                <h2 id="upcoming-title">Upcoming assignments for a musician</h2>
                <form onSubmit={(event) => { event.preventDefault(); void loadUpcoming(); }}>
                    <div>
                        <label htmlFor="upcoming-musician">Musician</label>{' '}
                        <select id="upcoming-musician" required value={upcomingMusicianId} onChange={(event) => setUpcomingMusicianId(event.target.value)} disabled={pending}>
                            <option value="">Select musician...</option>
                            {musicianOptions}
                        </select>
                    </div>
                    <div>
                        <label htmlFor="upcoming-from">From</label>{' '}
                        <input id="upcoming-from" type="datetime-local" value={upcomingFrom} onChange={(event) => setUpcomingFrom(event.target.value)} disabled={pending} />
                    </div>
                    <button type="submit" disabled={pending || !upcomingMusicianId}>List upcoming assignments</button>
                </form>
                {upcoming && upcoming.length === 0 && <p>No upcoming assignments returned.</p>}
                {upcoming && upcoming.length > 0 && (
                    <DataTable
                        caption={`Upcoming assignments for ${musicianName(upcomingMusicianId)}`}
                        columns={['Assignment', 'Service plan', 'Role', 'Status']}
                        rows={upcoming.map((assignment) => [
                            <code>{assignment.assignmentId}</code>,
                            <a href={`/admin/team-assignments/${encodeURIComponent(assignment.servicePlanId)}`}><code>{assignment.servicePlanId}</code></a>,
                            assignment.roleCode,
                            <Badge severity={assignment.statusCode === 'ACCEPTED' ? 'success' : assignment.statusCode === 'DECLINED' || assignment.statusCode === 'UNAVAILABLE' ? 'danger' : 'warning'}>{assignment.statusCode}</Badge>,
                        ])}
                    />
                )}
            </section>
        )}
    </main></LocalizedView>;
};
