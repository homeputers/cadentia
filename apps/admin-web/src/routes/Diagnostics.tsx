import { useEffect, useMemo, useState } from 'react';
import { hasCapability } from '../auth/permissions';
import type { AdminSession } from '../auth/session';
import { adminEnvironment } from '../config/environment';
import { createAdminApiClient, type AdminApiClient, type AdminApiError } from '../generated/cadentia-api/client';
import { getDiagnostics, type DiagnosticsResponse, type RecommendationDiagnostic } from '../operational-surfaces';
import { AuditReferenceLink, Badge, Breadcrumbs, DataTable, PageHeader, StatePanel, redactSensitiveError } from './admin-ui';

const redactId = (value?: string) => value ? `${value.slice(0, 8)}…${value.slice(-4)}` : 'Redacted by backend';
const fmt = (items: string[]) => items.length ? items.join(', ') : 'None reported by backend';
const inputText = (item: { name: string; value: string | number | boolean }) => `${item.name}: ${String(item.value)}`;

const row = (diagnostic: RecommendationDiagnostic) => [
    <><strong>{diagnostic.recommendationId}</strong><br /><small>{diagnostic.generatedAt}</small></>,
    <ul>{diagnostic.scoringInputs.map((input) => <li key={input.name}>{inputText(input)}</li>)}</ul>,
    <><Badge severity="neutral">{diagnostic.policyVersion}</Badge><p>Reasons: {fmt(diagnostic.reasonCodes)}</p><p>Blockers: {fmt(diagnostic.eligibilityBlockers)}</p></>,
    <><p>{diagnostic.readModelFreshness.readModel} updated {diagnostic.readModelFreshness.updatedAt}</p><p>Lag: {diagnostic.readModelFreshness.lagSeconds ?? 'n/a'} seconds</p><p>Cache: {diagnostic.cacheStatus}</p></>,
    <><p>Correlation {redactId(diagnostic.correlationId)}</p><p>Trace {redactId(diagnostic.traceId)}</p>{diagnostic.auditReference && <AuditReferenceLink auditId={diagnostic.auditReference.auditEventId} />}</>,
];

export const Diagnostics = ({ session, apiClient: providedApiClient }: { session: AdminSession; apiClient?: AdminApiClient }) => {
    const apiClient = useMemo(() => providedApiClient ?? createAdminApiClient({ environment: adminEnvironment, getAccessToken: async () => null }), [providedApiClient]);
    const [data, setData] = useState<DiagnosticsResponse | null>(null);
    const [state, setState] = useState<'loading' | 'empty' | 'ready' | 'unauthorized' | 'forbidden' | 'error'>('loading');
    const [error, setError] = useState('');
    const allowed = hasCapability(session, 'VIEW_DIAGNOSTICS') && adminEnvironment.featureFlags.includes('admin-diagnostics');

    useEffect(() => {
        if (!allowed) { setState('empty'); return; }
        void getDiagnostics(apiClient).then((response) => { setData(response); setState(response.capabilityEnabled === false ? 'empty' : 'ready'); }).catch((caught) => {
            const apiError = caught as AdminApiError; setError(redactSensitiveError(apiError.message)); setState(apiError.status === 401 ? 'unauthorized' : apiError.status === 403 ? 'forbidden' : 'error');
        });
    }, [allowed, apiClient]);

    return <main className="admin-shell" aria-labelledby="diagnostics-title"><Breadcrumbs items={[{ label: 'Admin', href: '/admin' }, { label: 'Diagnostics' }]} /><PageHeader eyebrow="Authorized operations" title="Recommendation diagnostics" titleId="diagnostics-title" description="Backend-provided scoring and recommendation diagnostics. The UI displays server facts only and does not recompute scores, eligibility, rankings, cache correctness, or health conclusions." />{!allowed ? <section className="admin-state admin-state--empty"><h2>Diagnostics unavailable</h2><p>Diagnostics are not available for this instance or release.</p></section> : <StatePanel state={state} title="Diagnostics" onRetry={() => window.location.reload()}>{error && <p>{error}</p>}</StatePanel>}{data && state === 'ready' && <><p>Generated {data.generatedAt} for this church instance.</p><DataTable caption="Recommendation scoring diagnostics" columns={['Recommendation', 'Scoring inputs', 'Backend reasons and policy', 'Freshness and cache', 'Redacted references']} rows={(data.recommendations ?? []).map(row)} />{data.components?.length ? <DataTable caption="Operational diagnostic components" columns={['Component', 'Status', 'Redacted', 'Checked']} rows={data.components.map((c) => [c.name, c.status, c.redactionApplied ? 'Yes' : 'No', c.lastCheckedAt ?? 'n/a'])} /> : null}</>}</main>;
};
