import React from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { act } from 'react-dom/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ActionBadge, Badge, ConfirmationDialog, DataTable, Field, FilterPanel, PageHeader } from '../src/routes/admin-ui';

let container: HTMLDivElement;
let root: Root;

const render = async (ui: React.ReactNode) => {
    container = document.createElement('div');
    document.body.appendChild(container);
    await act(async () => { root = createRoot(container); root.render(<>{ui}</>); });
    return container;
};

afterEach(() => {
    act(() => { root?.unmount(); });
    container?.remove();
});

describe('admin accessibility contract', () => {
    it('uses a labelled page heading, labels, captions, scoped table headers, and textual badges', async () => {
        const node = await render(<main aria-labelledby="page-title"><PageHeader title="Candidate review" titleId="page-title" /><FilterPanel title="Filters"><Field label="Reviewer">{({ inputId }) => <input id={inputId} />}</Field></FilterPanel><DataTable caption="Rollback impacted records" columns={['Record', 'Status']} rows={[[ 'candidate-1', <Badge severity="warning">Stale preview</Badge> ]]} /><ActionBadge capability="EXECUTE_ROLLBACK" /></main>);

        expect(node.querySelectorAll('h1')).toHaveLength(1);
        expect(node.querySelector('main')?.getAttribute('aria-labelledby')).toBe('page-title');
        expect(node.querySelector('label')?.getAttribute('for')).toBe(node.querySelector('input')?.id);
        expect(node.querySelector('caption')?.textContent).toBe('Rollback impacted records');
        expect([...node.querySelectorAll('th')].every((header) => header.getAttribute('scope') === 'col')).toBe(true);
        expect(node.textContent).toContain('Stale preview');
        expect(node.textContent).toContain('Allowed action: EXECUTE ROLLBACK');
    });

    it('keeps high-risk confirmations keyboard reachable and moves focus to the dialog heading', async () => {
        const onCancel = vi.fn();
        const onConfirm = vi.fn();
        const node = await render(<ConfirmationDialog open title="Execute rollback" acknowledgement="Confirm the backend preview ID before continuing." facts={['No catalog data is changed by UI rollback.']} auditActor="admin-1" versionContext="preview-v2" onCancel={onCancel} onConfirm={onConfirm} />);
        const buttons = [...node.querySelectorAll('button')];

        expect(node.querySelector('[role="dialog"]')?.getAttribute('aria-modal')).toBe('true');
        expect(document.activeElement?.textContent).toBe('Execute rollback');
        expect(buttons.map((button) => button.textContent)).toEqual(['Cancel', 'I understand, continue']);
        buttons[0].focus();
        expect(document.activeElement?.textContent).toBe('Cancel');
    });
});
