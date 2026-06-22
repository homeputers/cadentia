import { describe, expect, it } from 'vitest';

import { cadentiaApiRoutes } from '../src/generated/cadentia-api/routes';

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
});
