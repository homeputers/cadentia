import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { hasCapability } from '../auth/permissions';
import type { AdminSession } from '../auth/session';
import { adminEnvironment } from '../config/environment';
import { createAdminApiClient, type AdminApiClient, type AdminApiError } from '../generated/cadentia-api/client';
import { confirmFeatureFlagChange, getInstanceConfiguration, listFeatureFlags, previewFeatureFlagChange, updateInstanceConfiguration, type FeatureFlagList, type FeatureFlagPreview, type InstanceConfiguration } from '../operational-surfaces';
import { AuditReferenceLink, Badge, Breadcrumbs, DataTable, Field, PageHeader, StatePanel, redactSensitiveError } from './admin-ui';

const canUpdate = (config: InstanceConfiguration) => config.allowedActions.includes('UPDATE');
const deferred = (title: string) => <section className="admin-shell__panel"><h2>{title}</h2><p>This capability is not yet available for the current instance or release. This does not imply missing permissions or hidden data.</p></section>;
const operationalFailureMessage = (status?: number) => {
    if (status === 400 || status === 422) return 'Backend validation rejected this operations change. Review required fields, reason, and confirmation text before retrying.';
    if (status === 401) return 'Your admin session expired. Sign in again before retrying operations changes.';
    if (status === 403) return 'You are not authorized to change this operations setting.';
    if (status === 404) return 'The requested operations setting is not available for this instance.';
    if (status === 409 || status === 412) return 'Operations state changed. Reload settings and request a fresh backend preview before retrying.';
    if (status === 501) return 'This operations capability is documented but persistent backend support is not configured for this instance.';
    return 'Operations change failed safely. No protected backend details were exposed.';
};

export const InstanceSettings = ({ session, apiClient: providedApiClient }: { session: AdminSession; apiClient?: AdminApiClient }) => {
    const apiClient = useMemo(() => providedApiClient ?? createAdminApiClient({ environment: adminEnvironment, getAccessToken: async () => null }), [providedApiClient]);
    const [config, setConfig] = useState<InstanceConfiguration | null>(null);
    const [flags, setFlags] = useState<FeatureFlagList | null>(null);
    const [flagPreviews, setFlagPreviews] = useState<Record<string, FeatureFlagPreview>>({});
    const [flagConfirmation, setFlagConfirmation] = useState<Record<string, string>>({});
    const [state, setState] = useState<'loading' | 'ready' | 'forbidden' | 'unauthorized' | 'error'>('loading');
    const [message, setMessage] = useState('');
    const [flagMessage, setFlagMessage] = useState('');
    const allowed = hasCapability(session, 'MANAGE_INSTANCE_CONFIGURATION');

    useEffect(() => {
        if (!allowed) { setState('forbidden'); return; }
        Promise.all([getInstanceConfiguration(apiClient), hasCapability(session, 'MANAGE_FEATURE_FLAGS') ? listFeatureFlags(apiClient) : Promise.resolve(null)])
            .then(([loadedConfig, loadedFlags]) => { setConfig(loadedConfig); setFlags(loadedFlags); setState('ready'); })
            .catch((caught) => { const apiError = caught as AdminApiError; setMessage(redactSensitiveError(apiError.message)); setState(apiError.status === 401 ? 'unauthorized' : apiError.status === 403 ? 'forbidden' : 'error'); });
    }, [allowed, apiClient, session]);

    const submit = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        if (!config || !canUpdate(config)) return;
        const form = new FormData(event.currentTarget);
        try {
            const updated = await updateInstanceConfiguration(apiClient, { displayName: String(form.get('displayName')), defaultLocale: String(form.get('defaultLocale')), timeZone: String(form.get('timeZone')), diagnosticsEnabled: form.get('diagnosticsEnabled') === 'on', botChannelsEnabled: form.get('botChannelsEnabled') === 'on', expectedVersion: config.concurrency.version, actorId: session.actorId, reason: String(form.get('reason') || 'Admin settings update') }, config.concurrency.etag);
            setConfig(updated); setMessage('Settings updated with backend validation and audit attribution.');
        } catch (caught) { setMessage(operationalFailureMessage((caught as AdminApiError).status)); }
    };

    const previewFlag = async (flagKey: string, enabled: boolean, reason: string) => {
        const flag = flags?.flags.find((candidate) => candidate.flagKey === flagKey);
        if (!flag || !flag.allowedActions.includes('PREVIEW')) return;
        setFlagMessage('');
        try {
            const preview = await previewFeatureFlagChange(apiClient, flag, enabled, session.actorId, reason || 'Feature flag preview');
            setFlagPreviews((current) => ({ ...current, [flagKey]: preview }));
            setFlagConfirmation((current) => ({ ...current, [flagKey]: '' }));
        } catch (caught) {
            setFlagMessage(operationalFailureMessage((caught as AdminApiError).status));
        }
    };

    const confirmFlag = async (flagKey: string) => {
        const preview = flagPreviews[flagKey];
        if (!preview || flagConfirmation[flagKey] !== preview.previewId) return;
        setFlagMessage('');
        try {
            const updated = await confirmFeatureFlagChange(apiClient, flagKey, preview.previewId, session.actorId, flagConfirmation[flagKey]);
            setFlags((current) => current ? { ...current, flags: current.flags.map((flag) => flag.flagKey === updated.flagKey ? updated : flag) } : current);
            setFlagPreviews((current) => { const next = { ...current }; delete next[flagKey]; return next; });
            setFlagMessage(`${updated.flagKey} updated with backend confirmation and audit attribution.`);
        } catch (caught) {
            setFlagMessage(operationalFailureMessage((caught as AdminApiError).status));
        }
    };

    return <main className="admin-shell" aria-labelledby="settings-title"><Breadcrumbs items={[{ label: 'Admin', href: '/admin' }, { label: 'Instance settings' }]} /><PageHeader eyebrow="Operational configuration" title="Instance settings" titleId="settings-title" description="Configuration surfaces render only documented API data. Edits are enabled only when validated mutation endpoints and allowed actions are present." /><StatePanel state={state} title="Instance settings">{message && <p>{message}</p>}</StatePanel>{config && state === 'ready' && <><form className="admin-shell__panel" onSubmit={submit}><h2>General instance configuration</h2><Field label="Display name">{({ inputId }) => <input id={inputId} name="displayName" defaultValue={config.displayName} readOnly={!canUpdate(config)} />}</Field><Field label="Default locale">{({ inputId }) => <input id={inputId} name="defaultLocale" defaultValue={config.defaultLocale} readOnly={!canUpdate(config)} />}</Field><Field label="Time zone">{({ inputId }) => <input id={inputId} name="timeZone" defaultValue={config.timeZone} readOnly={!canUpdate(config)} />}</Field><label><input type="checkbox" name="diagnosticsEnabled" defaultChecked={Boolean(config.diagnosticsEnabled)} disabled={!canUpdate(config)} /> Diagnostics enabled</label><label><input type="checkbox" name="botChannelsEnabled" defaultChecked={Boolean(config.botChannelsEnabled)} disabled={!canUpdate(config)} /> Bot channels enabled</label><Field label="Audit reason">{({ inputId }) => <input id={inputId} name="reason" defaultValue="Operational configuration update" disabled={!canUpdate(config)} />}</Field><p>Version {config.concurrency.version}; If-Match {config.concurrency.etag}</p>{config.lastAuditReference && <AuditReferenceLink auditId={config.lastAuditReference.auditEventId} />}<button type="submit" disabled={!canUpdate(config)}>Save with backend validation</button>{!canUpdate(config) && <p>Read-only: update endpoint or authorization is not available for this instance.</p>}</form>{config.connectors?.length ? <DataTable caption="Enabled connectors" columns={['Connector', 'State', 'Credentials']} rows={config.connectors.map((c) => [c.label, <Badge severity={c.enabled ? 'success' : 'neutral'}>{c.status}</Badge>, c.credentialState ?? 'Redacted'])} /> : deferred('Connectors')}{config.botChannels?.length ? <DataTable caption="Bot channels" columns={['Channel', 'State', 'Status']} rows={config.botChannels.map((c) => [c.label, c.enabled ? 'Enabled' : 'Disabled', c.status])} /> : deferred('Bot channels')}{config.scoringProfiles?.length ? <DataTable caption="Scoring profiles" columns={['Profile', 'Active', 'Policy version']} rows={config.scoringProfiles.map((p) => [p.label, p.active ? 'Active' : 'Inactive', p.policyVersion])} /> : deferred('Scoring profiles')}{config.operationalSettings?.length ? <DataTable caption="Operational settings" columns={['Setting', 'Value', 'Editable']} rows={config.operationalSettings.map((s) => [s.label, s.value, s.editable ? 'Yes' : 'No'])} /> : deferred('Operational settings')}{flags ? <section className="admin-shell__panel" aria-labelledby="feature-flags-title"><h2 id="feature-flags-title">Feature flags</h2>{flagMessage && <p role="status">{flagMessage}</p>}{flags.flags.map((flag) => { const preview = flagPreviews[flag.flagKey]; const nextEnabled = !flag.enabled; return <form key={flag.flagKey} onSubmit={(event) => { event.preventDefault(); void previewFlag(flag.flagKey, nextEnabled, String(new FormData(event.currentTarget).get('reason') || 'Feature flag change')); }}><h3>{flag.description ?? flag.flagKey}</h3><p><Badge severity={flag.enabled ? 'success' : 'neutral'}>{flag.enabled ? 'Enabled' : 'Disabled'}</Badge> Version {flag.concurrency.version}; actions {flag.allowedActions.join(', ')}</p>{flag.lastAuditReference && <AuditReferenceLink auditId={flag.lastAuditReference.auditEventId} />}<Field label="Audit reason">{({ inputId }) => <input id={inputId} name="reason" defaultValue={`Set ${flag.flagKey} ${nextEnabled ? 'on' : 'off'}`} disabled={!flag.allowedActions.includes('PREVIEW')} />}</Field><button type="submit" className="secondary" disabled={!flag.allowedActions.includes('PREVIEW')}>Preview {nextEnabled ? 'enable' : 'disable'}</button>{preview && <section aria-labelledby={`preview-${flag.flagKey}`}><h4 id={`preview-${flag.flagKey}`}>Preview {preview.previewId}</h4><p>{preview.impactSummary ?? 'Backend preview returned no additional impact summary.'}</p>{preview.blockers.map((blocker) => <p key={blocker} role="alert"><Badge severity="danger">{blocker}</Badge></p>)}<Field label={`Type preview ID ${preview.previewId}`}>{({ inputId }) => <input id={inputId} value={flagConfirmation[flag.flagKey] ?? ''} onInput={(event) => { const value = event.currentTarget.value; setFlagConfirmation((current) => ({ ...current, [flag.flagKey]: value })); }} />}</Field><button type="button" className="danger" disabled={!flag.allowedActions.includes('CONFIRM') || preview.blockers.length > 0 || flagConfirmation[flag.flagKey] !== preview.previewId} onClick={() => void confirmFlag(flag.flagKey)}>Confirm backend feature-flag change</button></section>}</form>; })}</section> : deferred('Feature flags')}</>}</main>;
};
