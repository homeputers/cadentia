---
name: Local development setup
description: How the Cadentia admin console and API are configured for local development in this Replit environment
---

# Local Development Setup

## Rule
Three env vars are required for the admin console to work in local dev. All set in Replit shared env via setEnvVars:
- `CADENTIA_INSTANCE_ID=local-development` — enables LocalDevelopmentBypassSecurityConfig (no auth required) and grants all capabilities in AdminOperationsController
- `VITE_CADENTIA_CHURCH_INSTANCE_ID=local-development` — clears the "Missing church instance" gate in bootstrapAdminSession
- `VITE_CADENTIA_API_BASE_URL=/api` — prevents resolveApiUrl from throwing TypeError (blank string → invalid URL)

**Why:** The admin web has a pre-flight check for churchInstanceId and uses resolveApiUrl which throws on empty apiBaseUrl. Both failures show as non-descriptive UI states.

**How to apply:** If admin console regresses to "Missing church instance" or "Admin shell unavailable", check these three vars first. Do not write .env files (Replit blocks them); use setEnvVars instead.

## LocalDevelopmentBypassSecurityConfig
Added at `apps/api/src/main/java/com/cadentia/api/config/LocalDevelopmentBypassSecurityConfig.java`.
Active only when `cadentia.instance.id=local-development`. Permits all requests with @Order(1) (higher priority than SecurityConfig). Without it, the admin web fetch gets 401 but has no login UI to use it.

## Local admin credentials
- Username: `admin` (set via `CADENTIA_LOCAL_ADMIN_USERNAME=admin` env var; default was "user")  
- Password: `cadentia` (default in application.yml)
- These are still used for direct API curl access; the bypass config removes the need for credentials in the browser.
