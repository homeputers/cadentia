import { useEffect, useState } from 'react';
import { adminEnvironment } from '../config/environment';
import { createAdminApiClient, type AdminApiClient, type AdminApiError } from '../generated/cadentia-api/client';
import type { AdminSession } from '../auth/session';
import { adminUserRoles, createAdminUser, listAdminUsers, replaceAdminUserRoles, updateAdminUser, type AdminUser, type AdminUserRole, type AdminUserStatus } from '../admin-users';
import { translateText, useI18n } from '../i18n';
import { Badge, Breadcrumbs, DataTable, Field, PageHeader, RoleBadge, StatePanel } from './admin-ui';

const defaultClient = () => createAdminApiClient({ environment: adminEnvironment, getAccessToken: async () => null });

export const AdminUsers = ({ session, apiClient = defaultClient() }: { session: AdminSession; apiClient?: AdminApiClient }) => {
    const { locale } = useI18n();
    const copy = (source: string) => translateText(locale, source);
    const isFirstParty = adminEnvironment.authMode !== 'oidc';
    const [users, setUsers] = useState<AdminUser[]>([]);
    const [state, setState] = useState<'loading' | 'ready' | 'empty' | 'error'>('loading');
    const [error, setError] = useState('');
    const [notice, setNotice] = useState('');
    const [invitationUrl, setInvitationUrl] = useState<string | null>(null);
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
            setError(apiError.status === 403 ? copy('You do not have permission to manage users.') : copy('User administration could not be loaded.'));
            setState('error');
        }
    };

    useEffect(() => { void load(); }, []);

    const submitCreate = async () => {
        if (!(isFirstParty ? email.trim() : subject.trim()) || !displayName.trim() || newRoles.length === 0) return;
        setBusy(true);
        setError('');
        setNotice('');
        setInvitationUrl(null);
        try {
            const created = await createAdminUser(apiClient, session.actorId, {
                ...(isFirstParty ? { email: email.trim() } : { externalSubject: subject.trim() }),
                displayName: displayName.trim(),
                roles: newRoles,
            });
            setUsers((current) => [...current, created].sort((left, right) => left.displayName.localeCompare(right.displayName)));
            setSubject(''); setDisplayName(''); setEmail(''); setNewRoles(['VIEWER']);
            setState('ready');
            if (created.invitationUrl) {
                setNotice(copy('User created. Share the activation link with the new user so they can set a password.'));
                setInvitationUrl(created.invitationUrl);
            } else {
                setNotice(copy('User access was created. The user can sign in with their existing authentication account.'));
            }
        } catch (caught) {
            const status = (caught as AdminApiError).status;
            setError(status === 409
                ? copy('That identity is already provisioned for this church instance.')
                    : status === 404
                        ? copy('No active first-party account exists for that email, and the account invitation could not be created.')
                    : status === 502
                        ? copy('The authentication service could not resolve this account. Check that it is running and that CADENTIA_AUTH_INTERNAL_API_KEY matches in both services.')
                        : status === 503
                            ? copy('First-party authentication is not configured in the API.')
                            : copy('The user could not be created.'));
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
        } catch { setError(copy('The role update could not be saved. Refresh and try again if another administrator changed this user.')); }
        finally { setBusy(false); }
    };

    const toggleStatus = async (user: AdminUser) => {
        setBusy(true);
        try {
            const updated = await updateAdminUser(apiClient, session.actorId, user, { displayName: user.displayName, email: user.email ?? undefined, status: user.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE' });
            setUsers((current) => current.map((item) => item.userId === updated.userId ? updated : item));
        } catch { setError(copy('The user status could not be changed. Refresh and try again if another administrator changed this user.')); }
        finally { setBusy(false); }
    };

    const roleCheckboxes = (selected: AdminUserRole[], setSelected: (roles: AdminUserRole[]) => void) => <div className="admin-users-role-picker" role="group" aria-label={copy('Roles')}>
        {adminUserRoles.map((role) => <label key={role}><input type="checkbox" checked={selected.includes(role)} onChange={() => setSelected(toggleRole(selected, role))} /> <RoleBadge role={role} /></label>)}
    </div>;

    const rows = users.map((user) => {
        const selected = editingRoles[user.userId] ?? user.roles;
        return [
            <><strong>{user.displayName}</strong><br /><small>{user.email || copy('No email')}{!isFirstParty && ` · ${user.externalSubject}`}</small></>,
            <Badge severity={user.status === 'ACTIVE' ? 'success' : 'warning'}>{copy(user.status)}</Badge>,
            <>{user.roles.map((role) => <RoleBadge key={role} role={role} />)}</>,
            <div className="admin-user-actions">{roleCheckboxes(selected, (roles) => setEditingRoles((current) => ({ ...current, [user.userId]: roles })))}<button type="button" disabled={busy || !reason.trim() || selected.length === 0} onClick={() => void saveRoles(user)}>{copy('Save roles')}</button><button type="button" className="secondary" disabled={busy} onClick={() => void toggleStatus(user)}>{copy(user.status === 'ACTIVE' ? 'Suspend' : 'Reactivate')}</button></div>,
        ];
    });

    return <main className="admin-shell" aria-labelledby="admin-users-title">
        <Breadcrumbs items={[{ label: 'Admin', href: '/admin' }, { label: 'User administration' }]} />
        <PageHeader eyebrow="Administration" title="User administration" titleId="admin-users-title" description={isFirstParty ? 'Create first-party accounts, send an activation link, and assign church-scoped roles. Changes take effect on the next authenticated request.' : 'Provision identities from the configured identity provider and assign tenant-scoped roles. Changes take effect on the next authenticated request.'} />
        <section className="admin-shell__panel" aria-labelledby="create-user-title">
            <h2 id="create-user-title">{copy(isFirstParty ? 'Add user' : 'Provision user')}</h2>
            <form className="admin-users-provision-grid" onSubmit={(event) => { event.preventDefault(); void submitCreate(); }}>
                <div className="admin-users-provision-fields">
                    {isFirstParty ? <Field label="Email" required>{({ inputId }) => <input id={inputId} type="email" value={email} onChange={(event) => setEmail(event.target.value)} placeholder="user@example.com" autoComplete="email" />}</Field> : <Field label="Identity-provider subject" required>{({ inputId }) => <input id={inputId} value={subject} onChange={(event) => setSubject(event.target.value)} placeholder="sub from the identity provider" />}</Field>}
                    <Field label="Display name" required>{({ inputId }) => <input id={inputId} value={displayName} onChange={(event) => setDisplayName(event.target.value)} autoComplete="name" />}</Field>
                </div>
                <Field label="Initial roles" required>{() => roleCheckboxes(newRoles, setNewRoles)}</Field>
                <div className="admin-users-provision-actions">
                    <button type="submit" disabled={busy || !(isFirstParty ? email.trim() : subject.trim()) || !displayName.trim() || !newRoles.length}>{copy(isFirstParty ? 'Create user and invite' : 'Provision identity')}</button>
                    <p>{copy('Select at least one role. You can change assignments later with a required reason.')}</p>
                </div>
                {notice && <p role="status" className="admin-shell__success">{notice}</p>}
                {invitationUrl && <div className="admin-user-invitation" role="status">
                    <strong>{copy('Activation link')}</strong>
                    <a href={invitationUrl} target="_blank" rel="noreferrer">{copy('Open activation link')}</a>
                    <button type="button" className="secondary" onClick={() => void navigator.clipboard?.writeText(invitationUrl)}>{copy('Copy activation link')}</button>
                </div>}
            </form>
        </section>
        <section className="admin-shell__panel" aria-labelledby="users-list-title">
            <h2 id="users-list-title">{copy('Provisioned users')}</h2>
            {error && <p role="alert" className="admin-shell__warning">{error}</p>}
            <StatePanel state={state} title="Admin users" onRetry={() => void load()}>{rows.length > 0 && <DataTable caption="Tenant-scoped admin users" columns={['Identity', 'Status', 'Effective roles', 'Role and status actions']} rows={rows} />}</StatePanel>
            <Field label="Reason for role changes" description="Required before saving a role assignment.">{({ inputId }) => <input id={inputId} value={reason} onChange={(event) => setReason(event.target.value)} />}</Field>
        </section>
    </main>;
};
