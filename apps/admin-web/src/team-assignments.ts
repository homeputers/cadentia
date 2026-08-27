import type { AdminApiClient } from './generated/cadentia-api/client';

export type TeamMusicianRoleCode = 'WORSHIP_LEADER' | 'VOCALIST' | 'INSTRUMENTALIST' | 'MUSIC_DIRECTOR' | 'TECH';
export type TeamInstrumentCode =
    | 'ACOUSTIC_GUITAR' | 'ELECTRIC_GUITAR' | 'PIANO' | 'KEYS' | 'BASS'
    | 'DRUMS' | 'PERCUSSION' | 'BRASS' | 'WINDS' | 'OTHER';
export type TeamVocalPartCode = 'LEAD' | 'ALTO' | 'TENOR' | 'BARITONE' | 'SOPRANO' | 'BACKGROUND';
export type TeamAssignmentStatusCode = 'REQUESTED' | 'TENTATIVE' | 'ACCEPTED' | 'DECLINED' | 'UNAVAILABLE' | 'SUBSTITUTE';
export type TeamAssignmentType = 'SERVICE' | 'REHEARSAL' | 'SONG_OVERRIDE';
export type TeamVocalRangeCode = 'LOW' | 'MEDIUM' | 'HIGH' | 'UNKNOWN';
export type TeamServingPreferenceCode = 'PREFERRED' | 'AVAILABLE' | 'LIMITED' | 'DO_NOT_SCHEDULE';
export type TeamSkillLevelCode = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED' | 'DIRECTOR';
export type TeamSkillAssignmentDomain = 'ROLE' | 'INSTRUMENT' | 'VOCAL_PART';

export const teamMusicianRoleCodes: TeamMusicianRoleCode[] = ['WORSHIP_LEADER', 'VOCALIST', 'INSTRUMENTALIST', 'MUSIC_DIRECTOR', 'TECH'];
export const teamInstrumentCodes: TeamInstrumentCode[] = ['ACOUSTIC_GUITAR', 'ELECTRIC_GUITAR', 'PIANO', 'KEYS', 'BASS', 'DRUMS', 'PERCUSSION', 'BRASS', 'WINDS', 'OTHER'];
export const teamVocalPartCodes: TeamVocalPartCode[] = ['LEAD', 'ALTO', 'TENOR', 'BARITONE', 'SOPRANO', 'BACKGROUND'];
export const teamAssignmentStatusCodes: TeamAssignmentStatusCode[] = ['REQUESTED', 'TENTATIVE', 'ACCEPTED', 'DECLINED', 'UNAVAILABLE', 'SUBSTITUTE'];
export const teamVocalRangeCodes: TeamVocalRangeCode[] = ['LOW', 'MEDIUM', 'HIGH', 'UNKNOWN'];
export const teamServingPreferenceCodes: TeamServingPreferenceCode[] = ['PREFERRED', 'AVAILABLE', 'LIMITED', 'DO_NOT_SCHEDULE'];
export const teamSkillLevelCodes: TeamSkillLevelCode[] = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'DIRECTOR'];

export type TeamMusician = {
    musicianId: string;
    displayName: string;
    accountPrincipal?: string | null;
    email?: string | null;
    phone?: string | null;
    primaryVocalRangeCode?: TeamVocalRangeCode | null;
    comfortableLowMidiNote?: number | null;
    comfortableHighMidiNote?: number | null;
    servingPreferenceCode?: TeamServingPreferenceCode | null;
    active: boolean;
};

export type CreateTeamMusicianPayload = {
    displayName: string;
    accountPrincipal?: string;
    email?: string;
    phone?: string;
    primaryVocalRangeCode?: TeamVocalRangeCode;
    comfortableLowMidiNote?: number;
    comfortableHighMidiNote?: number;
    servingPreferenceCode?: TeamServingPreferenceCode;
    reasonCode?: string;
    reference?: string;
};

export type TeamServiceAssignment = {
    assignmentId: string;
    servicePlanId: string;
    musicianId: string;
    roleCode: TeamMusicianRoleCode;
    instrumentCode?: TeamInstrumentCode;
    vocalPartCode?: TeamVocalPartCode;
    statusCode: TeamAssignmentStatusCode;
    assignmentOrder?: number | null;
    substituteForAssignmentId?: string | null;
};

export type TeamServiceAssignmentPayload = {
    musicianId: string;
    roleCode: TeamMusicianRoleCode;
    instrumentCode?: TeamInstrumentCode;
    vocalPartCode?: TeamVocalPartCode;
    statusCode: TeamAssignmentStatusCode;
    assignmentOrder?: number;
    overrideUnavailable?: boolean;
    reasonCode?: string;
    reference?: string;
};

export type TeamServiceRoster = {
    servicePlanId: string;
    assignments: TeamServiceAssignment[];
    staffingGaps: string[];
    availabilityConflicts: string[];
};

export type TeamAssignmentHistoryEntry = {
    historyId: string;
    assignmentType: TeamAssignmentType;
    assignmentId: string;
    servicePlanId: string;
    rehearsalEventId?: string | null;
    musicianId?: string | null;
    roleCode?: TeamMusicianRoleCode;
    instrumentCode?: TeamInstrumentCode;
    vocalPartCode?: TeamVocalPartCode;
    statusCode?: TeamAssignmentStatusCode;
    assignmentOrder?: number | null;
    substituteForAssignmentId?: string | null;
    serviceAssignmentId?: string | null;
    changeAction: string;
    changedBy: string;
    reasonCode: string;
    reference?: string | null;
    changedAt: string;
};

export type TeamRehearsalEvent = {
    rehearsalEventId: string;
    servicePlanId: string;
    startsAt: string;
    endsAt: string;
    location?: string | null;
};

export type TeamRehearsalAssignment = {
    assignmentId: string;
    rehearsalEventId: string;
    servicePlanId: string;
    musicianId: string;
    roleCode: TeamMusicianRoleCode;
    instrumentCode?: TeamInstrumentCode;
    vocalPartCode?: TeamVocalPartCode;
    statusCode: TeamAssignmentStatusCode;
    serviceAssignmentId?: string | null;
    substituteForAssignmentId?: string | null;
};

export type TeamSongAssignmentOverride = {
    overrideId: string;
    servicePlanId: string;
    servicePlanBlockId: string;
    baseServiceAssignmentId: string;
    musicianId: string;
    roleCode: TeamMusicianRoleCode;
    instrumentCode?: TeamInstrumentCode;
    vocalPartCode?: TeamVocalPartCode;
    statusCode: TeamAssignmentStatusCode;
};

export type TeamMusicianSkillAssignment = {
    assignmentId: string;
    musicianId: string;
    domain: TeamSkillAssignmentDomain;
    code: string;
    skillLevelCode?: TeamSkillLevelCode | null;
};

export type TeamAvailabilityWindow = {
    availabilityWindowId: string;
    musicianId: string;
    startsAt: string;
    endsAt: string;
    statusCode: TeamAssignmentStatusCode;
    servicePlanId?: string | null;
};

export type ServicePlanSummary = {
    servicePlanId: string;
    title: string;
    serviceDateTime: string;
    status: 'draft' | 'published' | 'finalized';
};

export type ServicePlanBlock = {
    blockId: string;
    orderIndex: number;
    type: 'praise' | 'worship' | 'offering' | 'altar_call' | 'communion' | 'special';
    title: string;
    notes?: string | null;
};

export type ServicePlanDetail = {
    servicePlanId: string;
    title: string;
    serviceDateTime: string;
    status: 'draft' | 'published' | 'finalized';
    blocks?: ServicePlanBlock[];
};

const jsonHeaders = { 'Content-Type': 'application/json' };
const post = (body: unknown): RequestInit => ({ method: 'POST', headers: jsonHeaders, body: JSON.stringify(body) });
const put = (body: unknown): RequestInit => ({ method: 'PUT', headers: jsonHeaders, body: JSON.stringify(body) });
const clean = (payload: Record<string, unknown>): Record<string, unknown> =>
    Object.fromEntries(Object.entries(payload).filter(([, value]) => value !== undefined && value !== ''));

export const listServicePlans = (apiClient: AdminApiClient) =>
    apiClient.request<ServicePlanSummary[]>('/service-plans');

export const getServicePlan = (apiClient: AdminApiClient, servicePlanId: string) =>
    apiClient.request<ServicePlanDetail>(`/service-plans/${encodeURIComponent(servicePlanId)}`);

export const listTeamMusicians = (apiClient: AdminApiClient) =>
    apiClient.request<TeamMusician[]>('/team-assignments/musicians');

export const getTeamMusician = (apiClient: AdminApiClient, musicianId: string) =>
    apiClient.request<TeamMusician>(`/team-assignments/musicians/${encodeURIComponent(musicianId)}`);

export const createTeamMusician = (apiClient: AdminApiClient, payload: CreateTeamMusicianPayload, actorId: string) =>
    apiClient.request<TeamMusician>('/team-assignments/musicians', post(clean(payload as Record<string, unknown>)), { actorId });

export const getTeamMusicianSkills = (apiClient: AdminApiClient, musicianId: string) =>
    apiClient.request<{ musicianId: string; assignments: TeamMusicianSkillAssignment[] }>(
        `/team-assignments/musicians/${encodeURIComponent(musicianId)}/skills`,
    );

const assignSkill = <TCode extends string>(
    apiClient: AdminApiClient,
    musicianId: string,
    resource: 'roles' | 'instruments' | 'vocal-parts',
    codeField: 'roleCode' | 'instrumentCode' | 'vocalPartCode',
    code: TCode,
    skillLevelCode: TeamSkillLevelCode | undefined,
    actorId: string,
    reasonCode?: string,
) =>
    apiClient.request<TeamMusicianSkillAssignment>(
        `/team-assignments/musicians/${encodeURIComponent(musicianId)}/${resource}`,
        post(clean({ [codeField]: code, skillLevelCode, reasonCode })),
        { actorId },
    );

export const assignTeamMusicianRole = (
    apiClient: AdminApiClient,
    musicianId: string,
    roleCode: TeamMusicianRoleCode,
    skillLevelCode: TeamSkillLevelCode | undefined,
    actorId: string,
    reasonCode?: string,
) => assignSkill(apiClient, musicianId, 'roles', 'roleCode', roleCode, skillLevelCode, actorId, reasonCode);

export const assignTeamMusicianInstrument = (
    apiClient: AdminApiClient,
    musicianId: string,
    instrumentCode: TeamInstrumentCode,
    skillLevelCode: TeamSkillLevelCode | undefined,
    actorId: string,
    reasonCode?: string,
) => assignSkill(apiClient, musicianId, 'instruments', 'instrumentCode', instrumentCode, skillLevelCode, actorId, reasonCode);

export const assignTeamMusicianVocalPart = (
    apiClient: AdminApiClient,
    musicianId: string,
    vocalPartCode: TeamVocalPartCode,
    skillLevelCode: TeamSkillLevelCode | undefined,
    actorId: string,
    reasonCode?: string,
) => assignSkill(apiClient, musicianId, 'vocal-parts', 'vocalPartCode', vocalPartCode, skillLevelCode, actorId, reasonCode);

export const createTeamAvailabilityWindow = (
    apiClient: AdminApiClient,
    musicianId: string,
    payload: { startsAt: string; endsAt: string; statusCode: TeamAssignmentStatusCode; servicePlanId?: string; reasonCode?: string; reference?: string },
    actorId: string,
) =>
    apiClient.request<TeamAvailabilityWindow>(
        `/team-assignments/musicians/${encodeURIComponent(musicianId)}/availability-windows`,
        post(clean(payload as Record<string, unknown>)),
        { actorId },
    );

export const getServiceTeamRoster = (apiClient: AdminApiClient, servicePlanId: string) =>
    apiClient.request<TeamServiceRoster>(`/team-assignments/services/${encodeURIComponent(servicePlanId)}/roster`);

export const listTeamAssignmentHistory = (apiClient: AdminApiClient, servicePlanId: string) =>
    apiClient.request<TeamAssignmentHistoryEntry[]>(`/team-assignments/services/${encodeURIComponent(servicePlanId)}/history`);

export const createServiceTeamAssignment = (apiClient: AdminApiClient, servicePlanId: string, payload: TeamServiceAssignmentPayload, actorId: string) =>
    apiClient.request<TeamServiceAssignment>(`/team-assignments/services/${encodeURIComponent(servicePlanId)}`, post(clean(payload as Record<string, unknown>)), { actorId });

export const updateServiceTeamAssignment = (
    apiClient: AdminApiClient,
    servicePlanId: string,
    assignmentId: string,
    payload: TeamServiceAssignmentPayload,
    actorId: string,
) =>
    apiClient.request<TeamServiceAssignment>(
        `/team-assignments/services/${encodeURIComponent(servicePlanId)}/assignments/${encodeURIComponent(assignmentId)}`,
        put(clean(payload as Record<string, unknown>)),
        { actorId },
    );

export const removeServiceTeamAssignment = (
    apiClient: AdminApiClient,
    servicePlanId: string,
    assignmentId: string,
    actorId: string,
    reasonCode?: string,
    reference?: string,
) => {
    const query = new URLSearchParams();
    if (reasonCode) query.set('reasonCode', reasonCode);
    if (reference) query.set('reference', reference);
    const suffix = query.size ? `?${query.toString()}` : '';
    return apiClient.request<void>(
        `/team-assignments/services/${encodeURIComponent(servicePlanId)}/assignments/${encodeURIComponent(assignmentId)}${suffix}`,
        { method: 'DELETE' },
        { actorId },
    );
};

export const substituteServiceTeamAssignment = (
    apiClient: AdminApiClient,
    servicePlanId: string,
    assignmentId: string,
    payload: { substituteMusicianId: string; statusCode?: TeamAssignmentStatusCode; overrideUnavailable?: boolean; reasonCode?: string; reference?: string },
    actorId: string,
) =>
    apiClient.request<TeamServiceAssignment>(
        `/team-assignments/services/${encodeURIComponent(servicePlanId)}/assignments/${encodeURIComponent(assignmentId)}/substitute`,
        post(clean(payload as Record<string, unknown>)),
        { actorId },
    );

export const reorderServiceTeamAssignments = (
    apiClient: AdminApiClient,
    servicePlanId: string,
    orderedAssignmentIds: string[],
    actorId: string,
    reasonCode?: string,
) =>
    apiClient.request<TeamServiceAssignment[]>(
        `/team-assignments/services/${encodeURIComponent(servicePlanId)}/reorder`,
        post(clean({ orderedAssignmentIds, reasonCode })),
        { actorId },
    );

export const createSongTeamAssignmentOverride = (
    apiClient: AdminApiClient,
    servicePlanId: string,
    payload: {
        servicePlanBlockId: string;
        baseServiceAssignmentId: string;
        musicianId: string;
        roleCode: TeamMusicianRoleCode;
        instrumentCode?: TeamInstrumentCode;
        vocalPartCode?: TeamVocalPartCode;
        statusCode: TeamAssignmentStatusCode;
        reasonCode?: string;
        reference?: string;
    },
    actorId: string,
) =>
    apiClient.request<TeamSongAssignmentOverride>(
        `/team-assignments/services/${encodeURIComponent(servicePlanId)}/song-overrides`,
        post(clean(payload as Record<string, unknown>)),
        { actorId },
    );

export const listTeamRehearsalEvents = (apiClient: AdminApiClient, servicePlanId: string) =>
    apiClient.request<TeamRehearsalEvent[]>(`/team-assignments/services/${encodeURIComponent(servicePlanId)}/rehearsal-events`);

export const createTeamRehearsalEvent = (
    apiClient: AdminApiClient,
    payload: { servicePlanId: string; startsAt: string; endsAt: string; location?: string },
    actorId: string,
) =>
    apiClient.request<TeamRehearsalEvent>('/team-assignments/rehearsal-events', post(clean(payload as Record<string, unknown>)), { actorId });

export const createRehearsalTeamAssignment = (
    apiClient: AdminApiClient,
    rehearsalEventId: string,
    payload: {
        servicePlanId: string;
        serviceAssignmentId?: string;
        musicianId: string;
        roleCode: TeamMusicianRoleCode;
        instrumentCode?: TeamInstrumentCode;
        vocalPartCode?: TeamVocalPartCode;
        statusCode: TeamAssignmentStatusCode;
        overrideUnavailable?: boolean;
        reasonCode?: string;
        reference?: string;
    },
    actorId: string,
) =>
    apiClient.request<TeamRehearsalAssignment>(
        `/team-assignments/rehearsals/${encodeURIComponent(rehearsalEventId)}`,
        post(clean(payload as Record<string, unknown>)),
        { actorId },
    );

export const listUpcomingTeamAssignmentsForMusician = (apiClient: AdminApiClient, musicianId: string, fromInclusive: string) =>
    apiClient.request<TeamServiceAssignment[]>(
        `/team-assignments/musicians/${encodeURIComponent(musicianId)}/upcoming?fromInclusive=${encodeURIComponent(fromInclusive)}`,
    );
