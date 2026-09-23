# Cadentia Auth Service

The first-party authentication service owns email/password credentials and browser/API sessions. It issues short-lived RS256 JWT access tokens and rotates opaque refresh tokens stored only as SHA-256 hashes.

The service uses the shared PostgreSQL instance with an isolated `cadentia_auth` schema. It exposes its public signing key at `/oauth2/jwks`; configure the API with `CADENTIA_AUTH_PROVIDER=first-party`, `CADENTIA_AUTH_ISSUER_URL`, and `CADENTIA_AUTH_JWK_SET_URI`.

Set the same high-entropy `CADENTIA_AUTH_INTERNAL_API_KEY` in the auth service and API. The API uses it for private account lookup and invitation calls used by first-party user administration. Account invitations create an account with a one-time password-setup token; the API turns that token into an activation link for the admin web application.

For local development, the service generates an ephemeral RSA key pair when key environment variables are absent. Production deployments must provide stable PKCS#8/X.509 base64-encoded RSA keys through `CADENTIA_AUTH_PRIVATE_KEY_BASE64` and `CADENTIA_AUTH_PUBLIC_KEY_BASE64`.

Password reset requests intentionally return the same `202 Accepted` response for known and unknown email addresses. Set `CADENTIA_AUTH_LOG_PASSWORD_RESET_TOKENS=true` only for local development while an email notifier is being integrated.

For a fresh local database, set `CADENTIA_AUTH_BOOTSTRAP_EMAIL`, `CADENTIA_AUTH_BOOTSTRAP_DISPLAY_NAME`, and `CADENTIA_AUTH_BOOTSTRAP_PASSWORD` to seed the first account at startup. The values must be supplied together, and the password must contain at least 12 characters. Seeding is idempotent: an existing account with that email is not overwritten. When the API runs with first-party auth, the same bootstrap email automatically receives the tenant `ADMIN` role. No default credentials are created.

Example local startup configuration:

```text
CADENTIA_AUTH_PROVIDER=first-party
CADENTIA_AUTH_INTERNAL_API_KEY=use-the-same-random-secret-in-both-services
CADENTIA_AUTH_BOOTSTRAP_EMAIL=admin@example.com
CADENTIA_AUTH_BOOTSTRAP_DISPLAY_NAME=Cadentia Administrator
CADENTIA_AUTH_BOOTSTRAP_PASSWORD=choose-a-local-password-at-least-12-chars
```
