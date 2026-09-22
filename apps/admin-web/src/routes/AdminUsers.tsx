import { useEffect, useState } from 'react';
import { adminEnvironment } from '../config/environment';
import { createAdminApiClient, type AdminApiClient, type AdminApiError } from '../generated/cadentia-api/client';
import type { AdminSession } from '../auth/session';
import { adminUserRoles, createAdminUser, listAdminUsers, replaceAdminUserRoles, updateAdminUser, type AdminUser, type AdminUserRole, type AdminUserStatus } from '../admin-users';
import { Badge, Breadcrumbs, DataTable, Field, PageHeader, RoleBadge, StatePanel } from './admin-ui';

const defaultClient = () => createAdminApiClient({ environment: adminEnvironment, getAccessToken: async () => null });

export const AdminUsers = ({ session, apiClient = defaultClient() }: { session: AdminSession; apiClient?: AdminApiClient }) => {
    const [users, setUsers] = useState<AdminUser[]>([]);
    const [state, setState] = useState<'loading' | 'ready' | 'empty' | 'error'>('loading');
    const [error, setError] = useState('');
    const [subject, setSubject] = useState('');
    const [displayName, setDisplayName] = useState('');
    const [email, setEmail] = useState('');
    const [newRoles, setNewRoles] = useState<AdminUserRole[]>(['VIEWER']);
    const [editingRoles, setEditingRoles] = useState<Record<string, AdminUserRole[]>>({});
    const [reason, setReason] = useState('');
    const [busy, setBusy] = useState(false);

    const load = async () => {
        setState('loading');
        try {
            const response = await listAdminUsers(apiClient);
            setUsers(response.items);
            setState(response.items.length ? 'ready' : 'empty');
        } catch (caught) {
            const apiError = caught as AdminApiError;
            setError(apiError.status === 403 ? 'You do not have permission to manage users.' : 'User administration could not be loaded.');
            setState('error');
        }
    };

    useEffect(() => { void load(); }, []);

    const submitCreate = async () => {
        if (!subject.trim() || !displayName.trim() || newRoles.length === 0) return;
        setBusy(true);
        try {
            const created = await createAdminUser(apiClient, session.actorId, { externalSubject: subject.trim(), displayName: displayName.trim(), email: email.trim() || undefined, roles: newRoles });
            setUsers((current) => [...current, created].sort((left, right) => left.displayName.localeCompare(right.displayName)));
            setSubject(''); setDisplayName(''); setEmail(''); setNewRoles(['VIEWER']);
            setState('ready');
        } catch (caught) {
            setError((caught as AdminApiError).status === 409 ? 'That identity is already provisioned for this church instance.' : 'The user could not be created.');
        } finally { setBusy(false); }
    };

    const toggleRole = (roles: AdminUserRole[], role: AdminUserRole): AdminUserRole[] => roles.includes(role) ? roles.filter((value) => value !== role) : [...roles, role];
    const saveRoles = async (user: AdminUser) => {
        const roles = editingRoles[user.userId] ?? user.roles;
        if (!roles.length || !reason.trim()) return;
        setBusy(true);
        try {
            const updated = await replaceAdminUserRoles(apiClient, session.actorId, user, roles, reason.trim());
            setUsers((current) => current.map((item) => item.userId === updated.userId ? updated : item));
            setEditingRoles((current) => { const next = { ...current }; delete next[user.userId]; return next; });
            setReason('');
        } catch { setError('The role update could not be saved. Refresh and try again if another administrator changed this user.'); }
        finally { setBusy(false); }
    };

    const toggleStatus = async (user: AdminUser) => {
        setBusy(true);
        try {
            const updated = await updateAdminUser(apiClient, session.actorId, user, { displayName: user.displayName, email: user.email ?? undefined, status: user.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE' });
            setUsers((current) => current.map((item) => item.userId === updated.userId ? updated : item));
        } catch { setError('The user status could not be changed. Refresh and try again if another administrator changed this user.'); }
        finally { setBusy(false); }
    };

    const roleCheckboxes = (selected: AdminUserRole[], setSelected: (roles: AdminUserRole[]) => void) => <div className="admin-chip-list" role="group" aria-label="Roles">
        {adminUserRoles.map((role) => <label key={role}><input type="checkbox" checked={selected.includes(role)} onChange={() => setSelected(toggleRole(selected, role))} /> <RoleBadge role={role} /></label>)}
    </div>;

    const rows = users.map((user) => {
        const selected = editingRoles[user.userId] ?? user.roles;
        return [
            <><strong>{user.displayName}</strong><br /><small>{user.email || 'No email'} · {user.externalSubject}</small></>,
            <Badge severity={user.status === 'ACTIVE' ? 'success' : 'warning'}>{user.status}</Badge>,
            <>{user.roles.map((role) => <RoleBadge key={role} role={role} />)}</>,
            <div className="admin-user-actions">{roleCheckboxes(selected, (roles) => setEditingRoles((current) => ({ ...current, [user.userId]: roles })))}<button type="button" disabled={busy || !reason.trim() || selected.length === 0} onClick={() => void saveRoles(user)}>Save roles</button><button type="button" className="secondary" disabled={busy} onClick={() => void toggleStatus(user)}>{user.status === 'ACTIVE' ? 'Suspend' : 'Reactivate'}</button></div>,
        ];
    });

    return <main className="admin-shell" aria-labelledby="admin-users-title">
        <Breadcrumbs items={[{ label: 'Admin', href: '/admin' }, { label: 'User administration' }]} />
        <PageHeader eyebrow="Administration" title="User administration" titleId="admin-users-title" description="Provision identities from the configured identity provider and assign tenant-scoped roles. Changes take effect on the next authenticated request." />
        <section className="admin-shell__panel" aria-labelledby="create-user-title">
            <h2 id="create-user-title">Provision user</h2>
            <div className="admin-form-grid">
                <Field label="Identity-provider subject" required>{({ inputId }) => <input id={inputId} value={subject} onChange={(event) => setSubject(event.target.value)} placeholder="sub from the identity provider" />}</Field>
                <Field label="Display name" required>{({ inputId }) => <input id={inputId} value={displayName} onChange={(event) => setDisplayName(event.target.value)} />}</Field>
                <Field label="Email">{({ inputId }) => <input id={inputId} type="email" value={email} onChange={(event) => setEmail(event.target.value)} />}</Field>
                <Field label="Initial roles" required>{() => roleCheckboxes(newRoles, setNewRoles)}</Field>
                <button type="button" disabled={busy || !subject.trim() || !displayName.trim() || !newRoles.length} onClick={() => void submitCreate()}>Provision identity</button>
            </div>
        </section>
        <section className="admin-shell__panel" aria-labelledby="users-list-title">
            <h2 id="users-list-title">Provisioned users</h2>
            {error && <p role="alert" className="admin-shell__warning">{error}</p>}
            <StatePanel state={state} title="Admin users" onRetry={() => void load()}>{rows.length > 0 && <DataTable caption="Tenant-scoped admin users" columns={['Identity', 'Status', 'Effective roles', 'Role and status actions']} rows={rows} />}</StatePanel>
            <Field label="Reason for role changes" description="Required before saving a role assignment.">{({ inputId }) => <input id={inputId} value={reason} onChange={(event) => setReason(event.target.value)} />}</Field>
        </section>
    </main>;
};
