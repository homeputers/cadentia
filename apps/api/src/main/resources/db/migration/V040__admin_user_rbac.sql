CREATE TABLE admin_users (
    user_id uuid PRIMARY KEY,
    church_instance_id text NOT NULL,
    external_subject text NOT NULL,
    display_name text NOT NULL,
    email text,
    status text NOT NULL DEFAULT 'ACTIVE',
    version bigint NOT NULL DEFAULT 1,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT admin_users_status_check CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    CONSTRAINT admin_users_version_check CHECK (version > 0),
    CONSTRAINT admin_users_instance_subject_unique UNIQUE (church_instance_id, external_subject)
);

CREATE TABLE admin_user_roles (
    user_id uuid NOT NULL REFERENCES admin_users (user_id) ON DELETE CASCADE,
    role_code text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, role_code),
    CONSTRAINT admin_user_roles_role_check CHECK (role_code IN (
        'VIEWER', 'WORSHIP_LEADER', 'CATALOG_EDITOR', 'DOCTRINAL_REVIEWER',
        'MUSICAL_REVIEWER', 'ADMIN', 'TEAM_SCHEDULER', 'ASSIGNED_MUSICIAN',
        'REPORTING_VIEWER', 'INTEGRATION_MANAGER'))
);

CREATE INDEX admin_users_instance_status_idx ON admin_users (church_instance_id, status);
CREATE INDEX admin_user_roles_role_idx ON admin_user_roles (role_code);

COMMENT ON TABLE admin_users IS 'Tenant-scoped identities provisioned in Cadentia after external authentication.';
COMMENT ON TABLE admin_user_roles IS 'Effective RBAC assignments for tenant-scoped Cadentia admin identities.';
