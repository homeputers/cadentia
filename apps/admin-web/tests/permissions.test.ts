import { describe, expect, it } from 'vitest';
import { canAccessRoute, canRenderAction, visibleRoutes } from '../src/auth/permissions';
import type { AdminCapability, AdminRole, AdminSession } from '../src/auth/session';

const session = (roles: AdminRole[], capabilities: AdminCapability[]): AdminSession => ({
    actorId: roles.join('-') || 'anonymous',
    displayName: roles.join(', ') || 'Anonymous',
    churchInstanceId: 'church-1',
    roles,
    capabilities,
});

describe('admin permission presentation helpers', () => {
    it('shows catalog editor import review and catalog review action', () => {
        const catalogEditor = session(['CATALOG_EDITOR'], ['VIEW_IMPORT_QUEUE', 'REVIEW_CATALOG', 'MANAGE_MODERATION', 'VIEW_AUDIT']);

        expect(visibleRoutes(catalogEditor, []).map((route) => route.href)).toEqual(['/admin/imports', '/admin/audit']);
        expect(canRenderAction(catalogEditor, 'REVIEW_CATALOG')).toBe(true);
        expect(canAccessRoute(catalogEditor, [], '/admin/imports/candidate-1')).toBe(true);
        expect(canAccessRoute(catalogEditor, [], '/admin/settings')).toBe(false);
    });

    it('shows doctrinal reviewer review action without admin settings', () => {
        const doctrinalReviewer = session(['DOCTRINAL_REVIEWER'], ['VIEW_IMPORT_QUEUE', 'REVIEW_CATALOG']);

        expect(visibleRoutes(doctrinalReviewer, []).map((route) => route.href)).toEqual(['/admin/imports']);
        expect(canRenderAction(doctrinalReviewer, 'MANAGE_INSTANCE_CONFIGURATION')).toBe(false);
        expect(canAccessRoute(doctrinalReviewer, [], '/admin/audit')).toBe(false);
    });

    it('shows musical reviewer review action without rollback execution', () => {
        const musicalReviewer = session(['MUSICAL_REVIEWER'], ['VIEW_IMPORT_QUEUE', 'REVIEW_CATALOG']);

        expect(canRenderAction(musicalReviewer, 'REVIEW_CATALOG')).toBe(true);
        expect(canRenderAction(musicalReviewer, 'EXECUTE_ROLLBACK')).toBe(false);
    });

    it('shows admin routes and high-risk actions when feature flags allow them', () => {
        const admin = session(['ADMIN'], [
            'VIEW_IMPORT_QUEUE',
            'VIEW_AUDIT',
            'VIEW_DIAGNOSTICS',
            'MANAGE_INSTANCE_CONFIGURATION',
            'EXECUTE_ROLLBACK',
        ]);

        expect(visibleRoutes(admin, ['admin-diagnostics']).map((route) => route.href)).toEqual([
            '/admin/imports',
            '/admin/audit',
            '/admin/diagnostics',
            '/admin/settings',
        ]);
        expect(canRenderAction(admin, 'EXECUTE_ROLLBACK')).toBe(true);
        expect(canAccessRoute(admin, ['admin-diagnostics'], '/admin/diagnostics')).toBe(true);
        expect(canAccessRoute(admin, [], '/admin/diagnostics')).toBe(false);
    });

    it('keeps read-only viewer navigation and mutations hidden', () => {
        const viewer = session(['VIEWER'], ['VIEW_IMPORT_QUEUE']);

        expect(visibleRoutes(viewer, []).map((route) => route.href)).toEqual(['/admin/imports']);
        expect(canRenderAction(viewer, 'REVIEW_CATALOG')).toBe(false);
    });

    it('hides all protected affordances for unauthorized users with no capabilities', () => {
        const unauthorized = session([], []);

        expect(visibleRoutes(unauthorized, [])).toEqual([]);
        expect(canRenderAction(unauthorized, 'REVIEW_CATALOG')).toBe(false);
    });
});
