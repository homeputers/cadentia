CREATE TABLE auth_users (
    user_id uuid PRIMARY KEY,
    email text NOT NULL UNIQUE,
    display_name text NOT NULL,
    password_hash text NOT NULL,
    status text NOT NULL DEFAULT 'ACTIVE',
    failed_login_attempts integer NOT NULL DEFAULT 0,
    locked_until timestamptz,
    password_changed_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT auth_users_status_check CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE auth_sessions (
    session_id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES auth_users(user_id),
    refresh_token_hash text NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    last_used_at timestamptz NOT NULL DEFAULT now(),
    user_agent text,
    ip_address inet
);

CREATE INDEX auth_sessions_user_id_idx ON auth_sessions(user_id);
CREATE INDEX auth_sessions_active_refresh_idx ON auth_sessions(refresh_token_hash, expires_at)
    WHERE revoked_at IS NULL;

CREATE TABLE auth_password_reset_tokens (
    token_id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES auth_users(user_id),
    token_hash text NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    used_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX auth_password_reset_active_idx ON auth_password_reset_tokens(token_hash, expires_at)
    WHERE used_at IS NULL;
