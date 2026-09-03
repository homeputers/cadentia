import React from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { act } from 'react-dom/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { TeamAssignments } from '../src/routes/TeamAssignments';
import { TeamAssignmentDetail } from '../src/routes/TeamAssignmentDetail';
import type { AdminSession } from '../src/auth/session';
import type { AdminApiClient } from '../src/generated/cadentia-api/client';

let container: HTMLDivElement;
let root: Root;

const scheduler: AdminSession = {
    actorId: 'scheduler-1',
    displayName: 'Scheduler One',
    churchInstanceId: 'church-1',
    roles: ['TEAM_SCHEDULER'],
    capabilities: ['VIEW_TEAM_ROSTER', 'MANAGE_TEAM_ASSIGNMENTS'],
};
const reportingViewer: AdminSession = { ...scheduler, actorId: 'viewer-1', roles: ['REPORTING_VIEWER'], capabilities: ['VIEW_TEAM_ROSTER'] };
const viewer: AdminSession = { ...scheduler, actorId: 'viewer-2', roles: ['VIEWER'], capabilities: [] };

const musicianAvery = {
    musicianId: 'musician-1',
    displayName: 'Avery Rivera',
    active: true,
    email: null,
    primaryVocalRangeCode: null,
    servingPreferenceCode: null,
};
const musicianJordan = {
    musicianId: 'musician-2',
    displayName: 'Jordan Lee',
    active: true,
    email: 'jordan@example.test',
    primaryVocalRangeCode: 'MEDIUM',
    servingPreferenceCode: 'AVAILABLE',
};
const assignment = {
    assignmentId: 'assignment-1',
    servicePlanId: 'plan-1',
    musicianId: 'musician-1',
    roleCode: 'INSTRUMENTALIST',
    instrumentCode: 'PIANO',
    statusCode: 'ACCEPTED',
    assignmentOrder: 0,
};

const render = async (ui: React.ReactNode) => {
    container = document.createElement('div');
    document.body.appendChild(container);
    await act(async () => { root = createRoot(container); root.render(<>{ui}</>); });
    return container;
};

const setInputValue = (input: HTMLInputElement, value: string) => {
    const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value')!.set!;
    setter.call(input, value);
    input.dispatchEvent(new Event('input', { bubbles: true }));
};

const setSelectValue = (select: HTMLSelectElement, value: string) => {
    const setter = Object.getOwnPropertyDescriptor(window.HTMLSelectElement.prototype, 'value')!.set!;
    setter.call(select, value);
    select.dispatchEvent(new Event('change', { bubbles: true }));
};

const detailClient = (overrides?: (path: string, init?: RequestInit) => unknown): AdminApiClient => ({
    getAdminSession: vi.fn(),
    request: vi.fn().mockImplementation((path: string, init?: RequestInit) => {
        if (overrides) {
            const result = overrides(path, init);
            if (result !== undefined) return Promise.resolve(result);
        }
        if (path.endsWith('/roster')) {
            return Promise.resolve({
                servicePlanId: 'plan-1',
                assignments: [assignment],
                staffingGaps: ['DRUMS'],
                availabilityConflicts: [],
            });
        }
        if (path === '/team-assignments/musicians') return Promise.resolve([musicianAvery, musicianJordan]);
        if (path.endsWith('/history')) return Promise.resolve([]);
        if (path.endsWith('/rehearsal-events')) return Promise.resolve([]);
        if (path === '/service-plans/plan-1') {
            return Promise.resolve({
                servicePlanId: 'plan-1',
                title: 'Sunday Service',
                serviceDateTime: '2026-06-07T10:00:00Z',
                status: 'draft',
                blocks: [{ blockId: 'block-1', orderIndex: 0, type: 'worship', title: 'Worship set' }],
            });
        }
        return Promise.resolve({});
    }),
}) as unknown as AdminApiClient;

afterEach(() => { act(() => { root?.unmount(); }); container?.remove(); vi.restoreAllMocks(); });

describe('Team assignments service picker', () => {
    it('lists service plans with links to roster management', async () => {
        const request = vi.fn().mockResolvedValue([
            { servicePlanId: 'plan-1', title: 'Sunday Service', serviceDateTime: '2026-06-07T10:00:00Z', status: 'draft' },
        ]);
        const node = await render(<TeamAssignments session={scheduler} apiClient={{ getAdminSession: vi.fn(), request } as unknown as AdminApiClient} />);

        expect(request).toHaveBeenCalledWith('/service-plans');
        const link = node.querySelector('a[href="/admin/team-assignments/plan-1"]');
        expect(link?.textContent).toBe('Sunday Service');
    });

    it('renders forbidden state without roster capability', async () => {
        const request = vi.fn();
        const node = await render(<TeamAssignments session={viewer} apiClient={{ getAdminSession: vi.fn(), request } as unknown as AdminApiClient} />);

        expect(request).not.toHaveBeenCalled();
        expect(node.textContent).toContain('You do not have access');
    });
});

describe('Team assignment detail', () => {
    it('renders roster with directory names, gaps, and management actions', async () => {
        const node = await render(<TeamAssignmentDetail session={scheduler} servicePlanId="plan-1" apiClient={detailClient()} />);

        expect(node.textContent).toContain('Avery Rivera');
        expect(node.textContent).toContain('DRUMS');
        expect(node.textContent).toContain('Active roster');
        expect(node.textContent).toContain('Assignment history');
        expect(node.textContent).toContain('Rehearsal events');
        expect([...node.querySelectorAll('button')].some((button) => button.textContent === 'Add assignment')).toBe(true);
    });

    it('hides mutation controls for read-only roster viewers', async () => {
        const node = await render(<TeamAssignmentDetail session={reportingViewer} servicePlanId="plan-1" apiClient={detailClient()} />);

        expect(node.textContent).toContain('Avery Rivera');
        expect(node.textContent).toContain('Read-only');
        expect([...node.querySelectorAll('button')].some((button) => button.textContent === 'Add assignment')).toBe(false);
        expect([...node.querySelectorAll('button')].some((button) => button.textContent === 'Remove')).toBe(false);
    });

    it('creates a service assignment with actor attribution', async () => {
        const client = detailClient((path, init) => {
            if (path === '/team-assignments/services/plan-1' && init?.method === 'POST') {
                return { ...assignment, assignmentId: 'assignment-2', musicianId: 'musician-2' };
            }
            return undefined;
        });
        const node = await render(<TeamAssignmentDetail session={scheduler} servicePlanId="plan-1" apiClient={client} />);

        setSelectValue(node.querySelector('#assignment-musician') as HTMLSelectElement, 'musician-2');
        setSelectValue(node.querySelector('#assignment-role') as HTMLSelectElement, 'VOCALIST');
        setSelectValue(node.querySelector('#assignment-vocal-part') as HTMLSelectElement, 'LEAD');
        await act(async () => {
            (node.querySelector('#assignment-form-title')!.closest('section')!.querySelector('form') as HTMLFormElement)
                .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
        });

        const createCall = (client.request as ReturnType<typeof vi.fn>).mock.calls
            .find(([path, init]) => path === '/team-assignments/services/plan-1' && (init as RequestInit)?.method === 'POST');
        expect(createCall).toBeDefined();
        expect(JSON.parse(String(createCall![1]?.body))).toMatchObject({
            musicianId: 'musician-2',
            roleCode: 'VOCALIST',
            vocalPartCode: 'LEAD',
            statusCode: 'REQUESTED',
        });
        expect(createCall![2]).toMatchObject({ actorId: 'scheduler-1' });
    });

    it('removes an assignment with the provided reason code', async () => {
        const client = detailClient();
        const node = await render(<TeamAssignmentDetail session={scheduler} servicePlanId="plan-1" apiClient={client} />);

        const reasonInput = node.querySelector('input[aria-label="Removal reason for Avery Rivera"]') as HTMLInputElement;
        await act(async () => { setInputValue(reasonInput, 'schedule_conflict'); });
        const removeButton = [...node.querySelectorAll('button')].find((button) => button.textContent === 'Remove')!;
        await act(async () => { removeButton.dispatchEvent(new MouseEvent('click', { bubbles: true })); });

        const deleteCall = (client.request as ReturnType<typeof vi.fn>).mock.calls
            .find(([path, init]) => String(path).includes('/assignments/assignment-1') && (init as RequestInit)?.method === 'DELETE');
        expect(deleteCall).toBeDefined();
        expect(String(deleteCall![0])).toContain('reasonCode=schedule_conflict');
        expect(deleteCall![2]).toMatchObject({ actorId: 'scheduler-1' });
    });

    it('creates a substitute linked to the original assignment', async () => {
        const client = detailClient((path, init) => {
            if (String(path).endsWith('/substitute') && init?.method === 'POST') {
                return { ...assignment, assignmentId: 'assignment-sub', substituteForAssignmentId: 'assignment-1', statusCode: 'SUBSTITUTE' };
            }
            return undefined;
        });
        const node = await render(<TeamAssignmentDetail session={scheduler} servicePlanId="plan-1" apiClient={client} />);

        const substituteButton = [...node.querySelectorAll('button')].find((button) => button.textContent === 'Substitute')!;
        await act(async () => { substituteButton.dispatchEvent(new MouseEvent('click', { bubbles: true })); });
        setSelectValue(node.querySelector('#substitute-musician') as HTMLSelectElement, 'musician-2');
        await act(async () => {
            (node.querySelector('#substitute-title')!.closest('section')!.querySelector('form') as HTMLFormElement)
                .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
        });

        const substituteCall = (client.request as ReturnType<typeof vi.fn>).mock.calls
            .find(([path, init]) => String(path).endsWith('/substitute') && (init as RequestInit)?.method === 'POST');
        expect(substituteCall).toBeDefined();
        expect(String(substituteCall![0])).toBe('/team-assignments/services/plan-1/assignments/assignment-1/substitute');
        expect(JSON.parse(String(substituteCall![1]?.body))).toMatchObject({ substituteMusicianId: 'musician-2' });
    });

    it('reorders assignments when moving a row down', async () => {
        const client = detailClient((path) => {
            if (path.endsWith('/roster')) {
                return {
                    servicePlanId: 'plan-1',
                    assignments: [assignment, { ...assignment, assignmentId: 'assignment-2', musicianId: 'musician-2', assignmentOrder: 1 }],
                    staffingGaps: [],
                    availabilityConflicts: [],
                };
            }
            return undefined;
        });
        const node = await render(<TeamAssignmentDetail session={scheduler} servicePlanId="plan-1" apiClient={client} />);

        const downButton = [...node.querySelectorAll('button')].find((button) => button.getAttribute('aria-label') === 'Move Avery Rivera down')!;
        await act(async () => { downButton.dispatchEvent(new MouseEvent('click', { bubbles: true })); });

        const reorderCall = (client.request as ReturnType<typeof vi.fn>).mock.calls
            .find(([path]) => String(path).endsWith('/reorder'));
        expect(reorderCall).toBeDefined();
        expect(JSON.parse(String(reorderCall![1]?.body))).toEqual({ orderedAssignmentIds: ['assignment-2', 'assignment-1'] });
    });

    it('creates a rehearsal event for the service plan', async () => {
        const client = detailClient((path, init) => {
            if (path === '/team-assignments/rehearsal-events' && init?.method === 'POST') {
                return {
                    rehearsalEventId: 'event-1',
                    servicePlanId: 'plan-1',
                    startsAt: '2026-06-04T23:00:00.000Z',
                    endsAt: '2026-06-05T01:00:00.000Z',
                    location: 'Sanctuary',
                };
            }
            return undefined;
        });
        const node = await render(<TeamAssignmentDetail session={scheduler} servicePlanId="plan-1" apiClient={client} />);

        await act(async () => {
            setInputValue(node.querySelector('#event-starts') as HTMLInputElement, '2026-06-04T23:00');
            setInputValue(node.querySelector('#event-ends') as HTMLInputElement, '2026-06-05T01:00');
            setInputValue(node.querySelector('#event-location') as HTMLInputElement, 'Sanctuary');
        });
        await act(async () => {
            (node.querySelector('#event-starts')!.closest('form') as HTMLFormElement)
                .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
        });

        const createCall = (client.request as ReturnType<typeof vi.fn>).mock.calls
            .find(([path, init]) => path === '/team-assignments/rehearsal-events' && (init as RequestInit)?.method === 'POST');
        expect(createCall).toBeDefined();
        expect(JSON.parse(String(createCall![1]?.body))).toMatchObject({
            servicePlanId: 'plan-1',
            location: 'Sanctuary',
        });
        expect(createCall![2]).toMatchObject({ actorId: 'scheduler-1' });
    });

    it('renders forbidden state without roster capability', async () => {
        const request = vi.fn();
        const node = await render(<TeamAssignmentDetail session={viewer} servicePlanId="plan-1" apiClient={{ getAdminSession: vi.fn(), request } as unknown as AdminApiClient} />);

        expect(request).not.toHaveBeenCalled();
        expect(node.textContent).toContain('You do not have access');
    });
});
