import { describe, expect, it } from 'vitest';

import {
    cadentiaApiOperations,
    cadentiaApiOperationsById,
    cadentiaApiRoutes,
} from '../src/generated/cadentia-api/routes';

describe('generated Cadentia API route contract', () => {
    it('exposes only documented public OpenAPI routes', () => {
        expect(cadentiaApiRoutes.length).toBeGreaterThan(0);
        expect(cadentiaApiRoutes).not.toContain('/actuator/health');
        expect(cadentiaApiRoutes).not.toContain('/internal/test-only');
        expect(cadentiaApiRoutes.every((route) => !route.includes('/private/'))).toBe(true);
    });

    it('keeps high-risk admin workflows behind preview and confirmation shapes', () => {
        expect(cadentiaApiRoutes).toContain('/admin/rollback-previews');
        expect(cadentiaApiRoutes).toContain('/admin/rollbacks');
        expect(cadentiaApiRoutes).toContain('/admin/feature-flags/{flagKey}:preview');
        expect(cadentiaApiRoutes).toContain('/admin/feature-flags/{flagKey}:confirm');
    });

    it('generates operation metadata from split OpenAPI path files', () => {
        expect(cadentiaApiOperations.length).toBeGreaterThan(cadentiaApiRoutes.length);
        expect(cadentiaApiOperationsById.getAdminSession).toMatchObject({
            method: 'GET',
            path: '/admin/session',
        });
        expect(cadentiaApiOperationsById.updateAdminInstanceConfiguration).toMatchObject({
            method: 'PUT',
            path: '/admin/instance-configuration',
        });
    });

    it('keeps high-risk operation IDs paired with the documented HTTP methods', () => {
        expect(cadentiaApiOperationsById.createAdminRollbackPreview).toMatchObject({
            method: 'POST',
            path: '/admin/rollback-previews',
        });
        expect(cadentiaApiOperationsById.executeAdminRollback).toMatchObject({
            method: 'POST',
            path: '/admin/rollbacks',
        });
        expect(cadentiaApiOperationsById.previewAdminFeatureFlagChange).toMatchObject({
            method: 'POST',
            path: '/admin/feature-flags/{flagKey}:preview',
        });
        expect(cadentiaApiOperationsById.confirmAdminFeatureFlagChange).toMatchObject({
            method: 'POST',
            path: '/admin/feature-flags/{flagKey}:confirm',
        });
    });
});
