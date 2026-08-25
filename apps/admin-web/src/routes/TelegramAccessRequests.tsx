import { useEffect, useMemo, useState } from 'react';
import { hasCapability } from '../auth/permissions';
import type { AdminSession } from '../auth/session';
import { adminEnvironment } from '../config/environment';
import { createAdminApiClient, type AdminApiClient, type AdminApiError } from '../generated/cadentia-api/client';
import { approveTelegramAccessRequest, listTelegramAccessRequests, rejectTelegramAccessRequest, type TelegramAccessRequest } from '../telegram-access';
import { LocalizedView } from '../i18n';
import { Badge, Breadcrumbs, DataTable, PageHeader, StatePanel, redactSensitiveError } from './admin-ui';

const decisionFailureMessage = (status?: number) => {
    if (status === 400) return 'Backend validation rejected this decision. Review the reason field before retrying.';
    if (status === 401) return 'Your admin session expired. Sign in again before retrying.';
    if (status === 403) return 'You are not authorized to decide Telegram access requests.';
    if (status === 404) return 'This access request is no longer available. Reload the queue.';
    if (status === 409) return 'This access request was already decided. Reload the queue.';
    return 'The decision failed safely. No protected details were exposed.';
};

export const TelegramAccessRequests = ({ session, apiClient: providedApiClient }: { session: AdminSession; apiClient?: AdminApiClient }) => {
    const apiClient = useMemo(() => providedApiClient ?? createAdminApiClient({ environment: adminEnvironment, getAccessToken: async () => null }), [providedApiClient]);
    const [items, setItems] = useState<TelegramAccessRequest[] | null>(null);
    const [state, setState] = useState<'loading' | 'ready' | 'empty' | 'forbidden' | 'unauthorized' | 'error'>('loading');
    const [message, setMessage] = useState('');
    const [reasons, setReasons] = useState<Record<string, string>>({});
    const [pendingDecisions, setPendingDecisions] = useState<Record<string, boolean>>({});
    const allowed = hasCapability(session, 'MANAGE_TELEGRAM_ACCESS');

    const load = async () => {
        setState('loading');
        try {
            const response = await listTelegramAccessRequests(apiClient, 'PENDING');
            setItems(response.items);
            setState(response.items.length ? 'ready' : 'empty');
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

    const decide = async (request: TelegramAccessRequest, action: 'approve' | 'reject') => {
        setMessage('');
        setPendingDecisions((current) => ({ ...current, [request.requestId]: true }));
        try {
            const reason = reasons[request.requestId]?.trim() || undefined;
            if (action === 'approve') {
                await approveTelegramAccessRequest(apiClient, request.requestId, session.actorId, reason);
            } else {
                await rejectTelegramAccessRequest(apiClient, request.requestId, session.actorId, reason);
            }
            setItems((current) => {
                const next = (current ?? []).filter((item) => item.requestId !== request.requestId);
                setState(next.length ? 'ready' : 'empty');
                return next;
            });
            setMessage(action === 'approve'
                ? 'Access request approved. The requester was notified on Telegram.'
                : 'Access request rejected. The requester was notified on Telegram.');
        } catch (caught) {
            setMessage(decisionFailureMessage((caught as AdminApiError).status));
        } finally {
            setPendingDecisions((current) => ({ ...current, [request.requestId]: false }));
        }
    };

    const rows = (items ?? []).map((request) => [
        <>{new Date(request.requestedAt).toLocaleString()}<br /><small>{request.requestId}</small></>,
        <code>{request.maskedReference}</code>,
        <Badge severity="warning">{request.status}</Badge>,
        <div>
            <input
                aria-label={`Decision reason for ${request.maskedReference}`}
                placeholder="Optional decision reason"
                value={reasons[request.requestId] ?? ''}
                onChange={(event) => setReasons((current) => ({ ...current, [request.requestId]: event.target.value }))}
                disabled={pendingDecisions[request.requestId]}
            />
            <button type="button" disabled={pendingDecisions[request.requestId]} onClick={() => void decide(request, 'approve')}>Approve</button>{' '}
            <button type="button" disabled={pendingDecisions[request.requestId]} onClick={() => void decide(request, 'reject')}>Reject</button>
        </div>,
    ]);

    return <LocalizedView><main className="admin-shell" aria-labelledby="telegram-access-title">
        <Breadcrumbs items={[{ label: 'Admin', href: '/admin' }, { label: 'Telegram access' }]} />
        <PageHeader
            eyebrow="Telegram channel"
            title="Telegram access requests"
            titleId="telegram-access-title"
            description="Self-service access requests submitted from the Telegram bot. Approving links the requester with the default Telegram role and notifies them on Telegram; rejecting notifies them without granting access. Raw Telegram identifiers are never shown."
        />
        {message && <p role="status" className="admin-shell__panel">{message}</p>}
        <StatePanel state={state} title="Telegram access requests" onRetry={() => void load()} />
        {state === 'ready' && rows.length > 0 && (
            <section aria-labelledby="telegram-access-queue-title" className="admin-shell__panel">
                <h2 id="telegram-access-queue-title">Pending access requests</h2>
                <DataTable caption="Pending Telegram access requests" columns={['Requested', 'Reference', 'Status', 'Decision']} rows={rows} />
            </section>
        )}
    </main></LocalizedView>;
};
