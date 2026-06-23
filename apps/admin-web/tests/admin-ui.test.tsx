import React from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { act } from 'react-dom/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { Breadcrumbs, ConfirmationDialog, DataTable, Field, FilterPanel, StatePanel, SupportDebugPanel, redactSensitiveError } from '../src/routes/admin-ui';

let container: HTMLDivElement;
let root: Root;

const render = (ui: React.ReactNode) => {
    container = document.createElement('div');
    document.body.appendChild(container);
    act(() => { root = createRoot(container); root.render(<>{ui}</>); });
    return container;
};

afterEach(() => {
    act(() => { root?.unmount(); });
    container?.remove();
});

describe('shared admin UI foundations', () => {
    it('renders accessible navigation breadcrumbs and semantic tables', () => {
        const node = render(<><Breadcrumbs items={[{ label: 'Admin', href: '/admin' }, { label: 'Imports' }]} /><DataTable caption="Candidates" columns={['Name', 'Status']} rows={[[ 'Candidate 1', 'Needs review' ]]} /></>);
        expect(node.querySelector('nav[aria-label="Breadcrumb"]')).not.toBeNull();
        expect(node.querySelector('caption')?.textContent).toBe('Candidates');
        expect([...node.querySelectorAll('th')].every((cell) => cell.getAttribute('scope') === 'col')).toBe(true);
    });

    it('renders accessible form validation', () => {
        const node = render(<FilterPanel title="Filters"><Field label="Candidate status" error="Status is required">{({ inputId, errorId }) => <input id={inputId} aria-invalid="true" aria-describedby={errorId} />}</Field></FilterPanel>);
        const input = node.querySelector('input')!;
        expect(node.querySelector('label')?.getAttribute('for')).toBe(input.id);
        expect(input.getAttribute('aria-describedby')).toContain('error');
        expect(node.querySelector('[role="alert"]')?.textContent).toBe('Status is required');
    });

    it('focuses high-risk confirmation dialogs and renders audit/version context', () => {
        const onCancel = vi.fn();
        const node = render(<ConfirmationDialog open title="Confirm rollback" acknowledgement="Type rollback in the feature screen before continuing." facts={['2 records affected']} auditActor="operator-1" versionContext="If-Match: v7" onCancel={onCancel} onConfirm={vi.fn()} />);
        expect(node.querySelector('[role="dialog"]')?.getAttribute('aria-modal')).toBe('true');
        expect(document.activeElement?.textContent).toBe('Confirm rollback');
        expect(node.textContent).toContain('operator-1');
        expect(node.textContent).toContain('If-Match: v7');
    });

    it('covers retryable, forbidden, and safe support/error states', () => {
        const onRetry = vi.fn();
        const node = render(<><StatePanel state="partial-failure" title="Partial" onRetry={onRetry} /><StatePanel state="forbidden" title="Forbidden" /><SupportDebugPanel environment={{ apiBaseUrl: 'https://api.example.test', authIssuerUrl: 'https://issuer.example.test', identityProviderClientId: 'client', churchInstanceId: 'church', featureFlags: [], diagnosticsEnabled: true, buildVersion: '1.2.3', buildCommit: 'abc123', buildTimestamp: '2026-06-22T00:00:00Z' }} /></>);
        expect(node.querySelector('.admin-state--partial-failure button')?.textContent).toBe('Retry');
        expect(node.querySelector('.admin-state--forbidden')?.textContent).toContain('No protected details were loaded');
        expect(node.textContent).toContain('1.2.3');
        expect(node.textContent).not.toContain('client');
        expect(redactSensitiveError('Bearer abc.def token=secret user@example.test')).toBe('Bearer [redacted] token=[redacted] [redacted-email]');
    });
});
