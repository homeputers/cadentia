import type { AdminCapability, AdminSession } from './session';

export type AdminRoute = {
    href: string;
    label: string;
    requiredCapability: AdminCapability;
    requiredFeature?: string;
};

export const adminRoutes: AdminRoute[] = [
    { href: '/admin/imports', label: 'Import review', requiredCapability: 'VIEW_IMPORT_QUEUE' },
    { href: '/admin/audit', label: 'Audit history', requiredCapability: 'VIEW_AUDIT' },
    { href: '/admin/diagnostics', label: 'Diagnostics', requiredCapability: 'VIEW_DIAGNOSTICS', requiredFeature: 'admin-diagnostics' },
    { href: '/admin/settings', label: 'Instance settings', requiredCapability: 'MANAGE_INSTANCE_CONFIGURATION' },
];

export const hasCapability = (session: AdminSession, capability: AdminCapability): boolean =>
    session.capabilities.includes(capability);

export const visibleRoutes = (session: AdminSession, enabledFeatures: string[]): AdminRoute[] =>
    adminRoutes.filter(
        (route) =>
            hasCapability(session, route.requiredCapability) &&
            (!route.requiredFeature || enabledFeatures.includes(route.requiredFeature)),
    );

export const canRenderAction = (session: AdminSession, capability: AdminCapability): boolean =>
    hasCapability(session, capability);
