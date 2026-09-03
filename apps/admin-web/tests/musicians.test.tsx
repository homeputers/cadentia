import React from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { act } from 'react-dom/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { Musicians } from '../src/routes/Musicians';
import type { AdminSession } from '../src/auth/session';
import type { AdminApiClient } from '../src/generated/cadentia-api/client';

let container: HTMLDivElement;
let root: Root;

const worshipLeader: AdminSession = {
    actorId: 'leader-1',
    displayName: 'Leader One',
    churchInstanceId: 'church-1',
    roles: ['WORSHIP_LEADER'],
    capabilities: ['VIEW_TEAM_ROSTER', 'MANAGE_TEAM_ASSIGNMENTS'],
};
const viewer: AdminSession = { ...worshipLeader, actorId: 'viewer-1', roles: ['VIEWER'], capabilities: [] };

const directory = [
    { musicianId: 'musician-1', displayName: 'Avery Rivera', active: true, email: null, phone: null, primaryVocalRangeCode: null, servingPreferenceCode: null },
    { musicianId: 'musician-2', displayName: 'Jordan Lee', active: true, email: 'jordan@example.test', primaryVocalRangeCode: 'MEDIUM', servingPreferenceCode: 'AVAILABLE' },
];

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

afterEach(() => { act(() => { root?.unmount(); }); container?.remove(); vi.restoreAllMocks(); });

describe('Musicians directory', () => {
    it('renders the directory without inferring redacted fields', async () => {
        const request = vi.fn().mockResolvedValue(directory);
        const node = await render(<Musicians session={worshipLeader} apiClient={{ getAdminSession: vi.fn(), request } as unknown as AdminApiClient} />);

        expect(request).toHaveBeenCalledWith('/team-assignments/musicians');
        expect(node.textContent).toContain('Avery Rivera');
        expect(node.textContent).toContain('jordan@example.test');
        expect(node.textContent).toContain('Redacted');
        expect(node.textContent).toContain('Not permitted');
        expect(node.textContent).not.toContain('avery@example.test');
    });

    it('creates a musician with actor attribution and audit reason', async () => {
        const request = vi.fn().mockImplementation((path: string, init?: RequestInit) => {
            if (path === '/team-assignments/musicians' && init?.method === 'POST') {
                return Promise.resolve({ musicianId: 'musician-3', displayName: 'Casey Morgan', active: true });
            }
            return Promise.resolve(directory);
        });
        const node = await render(<Musicians session={worshipLeader} apiClient={{ getAdminSession: vi.fn(), request } as unknown as AdminApiClient} />);

        await act(async () => {
            setInputValue(node.querySelector('#musician-display-name') as HTMLInputElement, 'Casey Morgan');
            setInputValue(node.querySelector('#musician-email') as HTMLInputElement, 'casey@example.test');
            setInputValue(node.querySelector('#musician-reason') as HTMLInputElement, 'roster_onboarding');
            setSelectValue(node.querySelector('#musician-vocal-range') as HTMLSelectElement, 'HIGH');
        });
        const createButton = [...node.querySelectorAll('button')].find((button) => button.textContent === 'Create musician')!;
        await act(async () => { createButton.closest('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); });

        const createCall = request.mock.calls.find(([path, init]) => path === '/team-assignments/musicians' && (init as RequestInit)?.method === 'POST');
        expect(createCall).toBeDefined();
        expect(JSON.parse(String(createCall![1]?.body))).toEqual({
            displayName: 'Casey Morgan',
            email: 'casey@example.test',
            primaryVocalRangeCode: 'HIGH',
            reasonCode: 'roster_onboarding',
        });
        expect(createCall![2]).toMatchObject({ actorId: 'leader-1' });
        expect(node.textContent).toContain('Musician created with audit attribution.');
    });

    it('records an availability window with ISO timestamps', async () => {
        const request = vi.fn().mockImplementation((path: string, init?: RequestInit) => {
            if (String(path).endsWith('/availability-windows') && init?.method === 'POST') {
                return Promise.resolve({ availabilityWindowId: 'window-1', musicianId: 'musician-1' });
            }
            return Promise.resolve(directory);
        });
        const node = await render(<Musicians session={worshipLeader} apiClient={{ getAdminSession: vi.fn(), request } as unknown as AdminApiClient} />);

        await act(async () => {
            setSelectValue(node.querySelector('#availability-musician') as HTMLSelectElement, 'musician-1');
            setInputValue(node.querySelector('#availability-starts') as HTMLInputElement, '2026-06-07T08:00');
            setInputValue(node.querySelector('#availability-ends') as HTMLInputElement, '2026-06-07T12:00');
        });
        const submitButton = [...node.querySelectorAll('button')].find((button) => button.textContent === 'Record availability')!;
        await act(async () => { submitButton.closest('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); });

        const windowCall = request.mock.calls.find(([path, init]) => String(path).endsWith('/availability-windows') && (init as RequestInit)?.method === 'POST');
        expect(windowCall).toBeDefined();
        expect(String(windowCall![0])).toBe('/team-assignments/musicians/musician-1/availability-windows');
        const body = JSON.parse(String(windowCall![1]?.body));
        expect(body.statusCode).toBe('UNAVAILABLE');
        expect(body.startsAt).toMatch(/^2026-06-07T/);
        expect(body.endsAt).toMatch(/^2026-06-07T/);
        expect(windowCall![2]).toMatchObject({ actorId: 'leader-1' });
    });

    it('lists upcoming assignments for the selected musician', async () => {
        const request = vi.fn().mockImplementation((path: string) => {
            if (String(path).includes('/upcoming')) {
                return Promise.resolve([{ assignmentId: 'assignment-9', servicePlanId: 'plan-1', musicianId: 'musician-2', roleCode: 'VOCALIST', statusCode: 'ACCEPTED' }]);
            }
            return Promise.resolve(directory);
        });
        const node = await render(<Musicians session={worshipLeader} apiClient={{ getAdminSession: vi.fn(), request } as unknown as AdminApiClient} />);

        await act(async () => {
            setSelectValue(node.querySelector('#upcoming-musician') as HTMLSelectElement, 'musician-2');
        });
        const listButton = [...node.querySelectorAll('button')].find((button) => button.textContent === 'List upcoming assignments')!;
        await act(async () => { listButton.closest('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); });

        const upcomingCall = request.mock.calls.find(([path]) => String(path).includes('/upcoming'));
        expect(String(upcomingCall![0])).toContain('/team-assignments/musicians/musician-2/upcoming?fromInclusive=');
        expect(node.textContent).toContain('assignment-9');
    });

    it('loads and renders skill assignments for the selected musician', async () => {
        const request = vi.fn().mockImplementation((path: string) => {
            if (String(path).endsWith('/skills')) {
                return Promise.resolve({
                    musicianId: 'musician-2',
                    assignments: [
                        { assignmentId: 'skill-1', musicianId: 'musician-2', domain: 'INSTRUMENT', code: 'KEYS', skillLevelCode: 'INTERMEDIATE' },
                        { assignmentId: 'skill-2', musicianId: 'musician-2', domain: 'VOCAL_PART', code: 'ALTO', skillLevelCode: null },
                    ],
                });
            }
            return Promise.resolve(directory);
        });
        const node = await render(<Musicians session={worshipLeader} apiClient={{ getAdminSession: vi.fn(), request } as unknown as AdminApiClient} />);

        await act(async () => {
            setSelectValue(node.querySelector('#skills-musician') as HTMLSelectElement, 'musician-2');
        });

        expect(request.mock.calls.some(([path]) => String(path) === '/team-assignments/musicians/musician-2/skills')).toBe(true);
        expect(node.textContent).toContain('KEYS');
        expect(node.textContent).toContain('INTERMEDIATE');
        expect(node.textContent).toContain('ALTO');
    });

    it('assigns an instrument skill with actor attribution and reloads', async () => {
        const request = vi.fn().mockImplementation((path: string, init?: RequestInit) => {
            if (String(path).endsWith('/instruments') && init?.method === 'POST') {
                return Promise.resolve({ assignmentId: 'skill-3', musicianId: 'musician-1', domain: 'INSTRUMENT', code: 'ACOUSTIC_GUITAR', skillLevelCode: 'BEGINNER' });
            }
            if (String(path).endsWith('/skills')) {
                return Promise.resolve({
                    musicianId: 'musician-1',
                    assignments: [{ assignmentId: 'skill-3', musicianId: 'musician-1', domain: 'INSTRUMENT', code: 'ACOUSTIC_GUITAR', skillLevelCode: 'BEGINNER' }],
                });
            }
            return Promise.resolve(directory);
        });
        const node = await render(<Musicians session={worshipLeader} apiClient={{ getAdminSession: vi.fn(), request } as unknown as AdminApiClient} />);

        await act(async () => {
            setSelectValue(node.querySelector('#skills-musician') as HTMLSelectElement, 'musician-1');
        });
        await act(async () => {
            setSelectValue(node.querySelector('#skill-code') as HTMLSelectElement, 'ACOUSTIC_GUITAR');
            setSelectValue(node.querySelector('#skill-level') as HTMLSelectElement, 'BEGINNER');
        });
        const assignButton = [...node.querySelectorAll('button')].find((button) => button.textContent === 'Assign skill')!;
        await act(async () => { assignButton.closest('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); });

        const assignCall = request.mock.calls.find(([path, init]) => String(path).endsWith('/instruments') && (init as RequestInit)?.method === 'POST');
        expect(assignCall).toBeDefined();
        expect(String(assignCall![0])).toBe('/team-assignments/musicians/musician-1/instruments');
        expect(JSON.parse(String(assignCall![1]?.body))).toEqual({ instrumentCode: 'ACOUSTIC_GUITAR', skillLevelCode: 'BEGINNER' });
        expect(assignCall![2]).toMatchObject({ actorId: 'leader-1' });
        expect(node.textContent).toContain('Skill assignment recorded with audit attribution.');
        expect(node.textContent).toContain('ACOUSTIC_GUITAR');
    });

    it('shows a redaction-safe message when skills are not returned', async () => {
        const request = vi.fn().mockImplementation((path: string) => {
            if (String(path).endsWith('/skills')) return Promise.resolve({ musicianId: 'musician-1', assignments: [] });
            return Promise.resolve(directory);
        });
        const node = await render(<Musicians session={worshipLeader} apiClient={{ getAdminSession: vi.fn(), request } as unknown as AdminApiClient} />);

        await act(async () => {
            setSelectValue(node.querySelector('#skills-musician') as HTMLSelectElement, 'musician-1');
        });

        expect(node.textContent).toContain('No skill assignments returned or not permitted.');
    });

    it('renders forbidden state without the manage capability', async () => {
        const request = vi.fn();
        const node = await render(<Musicians session={viewer} apiClient={{ getAdminSession: vi.fn(), request } as unknown as AdminApiClient} />);

        expect(request).not.toHaveBeenCalled();
        expect(node.textContent).toContain('You do not have access');
    });
});
