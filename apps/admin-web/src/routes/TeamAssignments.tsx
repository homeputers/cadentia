import { useEffect, useMemo, useState } from 'react';
import { hasCapability } from '../auth/permissions';
import type { AdminSession } from '../auth/session';
import { adminEnvironment } from '../config/environment';
import { createAdminApiClient, type AdminApiClient, type AdminApiError } from '../generated/cadentia-api/client';
import { listServicePlans, type ServicePlanSummary } from '../team-assignments';
import { LocalizedView } from '../i18n';
import { Badge, Breadcrumbs, DataTable, PageHeader, StatePanel, redactSensitiveError } from './admin-ui';

export const TeamAssignments = ({ session, apiClient: providedApiClient }: { session: AdminSession; apiClient?: AdminApiClient }) => {
    const apiClient = useMemo(() => providedApiClient ?? createAdminApiClient({ environment: adminEnvironment, getAccessToken: async () => null }), [providedApiClient]);
    const [plans, setPlans] = useState<ServicePlanSummary[] | null>(null);
    const [state, setState] = useState<'loading' | 'ready' | 'empty' | 'forbidden' | 'unauthorized' | 'error'>('loading');
    const [message, setMessage] = useState('');
    const allowed = hasCapability(session, 'VIEW_TEAM_ROSTER');

    const load = async () => {
        setState('loading');
        try {
            const response = await listServicePlans(apiClient);
            setPlans(response);
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

    const rows = (plans ?? []).map((plan) => [
        <a href={`/admin/team-assignments/${encodeURIComponent(plan.servicePlanId)}`}>{plan.title}</a>,
        new Date(plan.serviceDateTime).toLocaleString(),
        <Badge severity={plan.status === 'published' ? 'success' : plan.status === 'finalized' ? 'neutral' : 'warning'}>{plan.status}</Badge>,
    ]);

    return <LocalizedView><main className="admin-shell" aria-labelledby="team-assignments-title">
        <Breadcrumbs items={[{ label: 'Admin', href: '/admin' }, { label: 'Team assignments' }]} />
        <PageHeader
            eyebrow="Team planning"
            title="Team assignments"
            titleId="team-assignments-title"
            description="Choose a service plan to manage its team roster, substitutions, rehearsal participation, song-specific overrides, and assignment history. Song selection never happens here; this surface manages people only."
        />
        {message && <p role="status" className="admin-shell__panel">{message}</p>}
        <StatePanel state={state} title="Team assignments" onRetry={() => void load()} />
        {state === 'ready' && rows.length > 0 && (
            <section aria-labelledby="team-assignments-services-title" className="admin-shell__panel">
                <h2 id="team-assignments-services-title">Service plans</h2>
                <DataTable caption="Service plans available for team assignment" columns={['Service', 'Date', 'Status']} rows={rows} />
            </section>
        )}
    </main></LocalizedView>;
};
