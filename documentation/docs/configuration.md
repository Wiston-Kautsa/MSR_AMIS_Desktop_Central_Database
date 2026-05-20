# MSR-AMIS Configuration

## Overview

The project supports development configuration through a root `.env` file and deployment configuration through real environment variables.

Use [.env.example](../../.env.example) as the starting template.

## Desktop Configuration

The desktop app reads configuration in this order:

1. root `.env`
2. Java system properties
3. OS environment variables

Key desktop settings:

- `MSR_AMIS_DATA_MODE`
- `MSR_AMIS_API_BASE_URL`

Supported aliases for development convenience:

- `APP_MODE`
- `API_BASE_URL`

Example:

```env
MSR_AMIS_DATA_MODE=REMOTE_API
MSR_AMIS_API_BASE_URL=http://SERVER_IP_OR_NAME:8090

APP_MODE=REMOTE_API
API_BASE_URL=http://SERVER_IP_OR_NAME:8090
```

Use `localhost` only when the API is running on the same computer as the desktop app. Client machines should point to the server IP address or DNS name.

If users see `API not reachable`, first confirm that client `.env` files do not point to `localhost` unless the API is installed on that same client computer. Normal MIS clients should use:

```env
MSR_AMIS_API_BASE_URL=http://SERVER_IP_OR_NAME:8090
API_BASE_URL=http://SERVER_IP_OR_NAME:8090
```

For the full recovery checklist, see [Troubleshooting](troubleshooting.md).

## API Configuration

The Spring Boot API reads:

- `MSR_AMIS_DB_URL`
- `MSR_AMIS_DB_USERNAME`
- `MSR_AMIS_DB_PASSWORD`
- `MSR_AMIS_API_PORT`
- `MSR_AMIS_JWT_SECRET`
- `MSR_AMIS_JWT_EXPIRATION_SECONDS`
- `MSR_AMIS_PRIMARY_SUPER_ADMIN_EMAIL`
- `MSR_AMIS_SETUP_ADMIN_EMAIL`
- `MSR_AMIS_SETUP_USER_EMAIL`
- `MSR_AMIS_RESERVED_SUPER_ADMIN_EMAILS`
- `MSR_AMIS_RESERVED_ADMIN_EMAILS`
- `MSR_AMIS_RESERVED_USER_EMAILS`
- `MSR_AMIS_EXPOSE_RESET_CODE_ON_EMAIL_FAILURE`
- `MSR_AMIS_SMTP_HOST`
- `MSR_AMIS_SMTP_PORT`
- `MSR_AMIS_SMTP_USERNAME`
- `MSR_AMIS_SMTP_PASSWORD`
- `MSR_AMIS_SMTP_FROM`
- `MSR_AMIS_SMTP_STARTTLS`
- `MSR_AMIS_SMTP_SSL`
- `MSR_AMIS_SMTP_TIMEOUT_MS`
- `MSR_AMIS_SUPER_USER_STATUS_EMAILS_ENABLED`
- `MSR_AMIS_OPERATION_EMAILS_ENABLED`
- `MSR_AMIS_OPERATION_EMAIL_RECIPIENTS`

The API imports the root `.env` file as the first configuration source when it exists, so `.env` values override matching OS environment variables and Java system properties.

Example:

```env
MSR_AMIS_DB_URL=jdbc:postgresql://localhost:5432/msr_amis
MSR_AMIS_DB_USERNAME=msr_amis_user
MSR_AMIS_DB_PASSWORD=strong_password_here
MSR_AMIS_API_PORT=8090
MSR_AMIS_JWT_SECRET=your_base64_secret
MSR_AMIS_JWT_EXPIRATION_SECONDS=28800
MSR_AMIS_PRIMARY_SUPER_ADMIN_EMAIL=msramis@nlgfc.gov.mw
MSR_AMIS_SETUP_ADMIN_EMAIL=setup-admin@example.com
MSR_AMIS_SETUP_USER_EMAIL=setup-user@example.com
MSR_AMIS_RESERVED_SUPER_ADMIN_EMAILS=msramis@nlgfc.gov.mw
MSR_AMIS_RESERVED_ADMIN_EMAILS=setup-admin@example.com
MSR_AMIS_RESERVED_USER_EMAILS=setup-user@example.com
MSR_AMIS_EXPOSE_RESET_CODE_ON_EMAIL_FAILURE=false
MSR_AMIS_OPERATION_EMAILS_ENABLED=false
MSR_AMIS_OPERATION_EMAIL_RECIPIENTS=admin@example.com
```

If PostgreSQL is hosted on a separate database server, use that server address:

```env
MSR_AMIS_DB_URL=jdbc:postgresql://POSTGRES_SERVER_IP:5432/msr_amis
```

Only the API server should need PostgreSQL credentials. Desktop clients should use `MSR_AMIS_API_BASE_URL` and should not connect directly to PostgreSQL.

`MSR_AMIS_EXPOSE_RESET_CODE_ON_EMAIL_FAILURE=true` is a development fallback for testing password reset when SMTP is unavailable. Keep it `false` in production.

## Account Email Configuration

Account emails are not fixed in code. They are controlled by server environment variables in production, or by the root `.env` file during development.

Use these values:

```env
MSR_AMIS_PRIMARY_SUPER_ADMIN_EMAIL=msramis@nlgfc.gov.mw
MSR_AMIS_SETUP_ADMIN_EMAIL=setup-admin@example.com
MSR_AMIS_SETUP_USER_EMAIL=setup-user@example.com
MSR_AMIS_RESERVED_SUPER_ADMIN_EMAILS=msramis@nlgfc.gov.mw
MSR_AMIS_RESERVED_ADMIN_EMAILS=setup-admin@example.com
MSR_AMIS_RESERVED_USER_EMAILS=setup-user@example.com
```

`MSR_AMIS_PRIMARY_SUPER_ADMIN_EMAIL` identifies the protected top-level super admin account. `MSR_AMIS_SETUP_ADMIN_EMAIL` and `MSR_AMIS_SETUP_USER_EMAIL` identify the temporary setup accounts. The three `MSR_AMIS_RESERVED_*_EMAILS` values are comma-separated lists. Add an address to reserve it; remove an address to stop reserving it.

After changing these values, restart the API. For a clean PostgreSQL database, Flyway applies the configured setup and primary account emails during migration. For an existing database, changing these values changes protection and reservation policy; it does not automatically rename arbitrary existing user records unless a migration explicitly handles that case.

## Password Reset Email

Password reset emails use SMTP configuration. The API and local desktop fallback read these values from the root `.env` file first when it exists, then from OS environment variables.

Required SMTP settings:

```env
MSR_AMIS_SMTP_HOST=smtp.gmail.com
MSR_AMIS_SMTP_PORT=587
MSR_AMIS_SMTP_USERNAME=your-email@example.com
MSR_AMIS_SMTP_PASSWORD=your-app-password
MSR_AMIS_SMTP_FROM=your-email@example.com
MSR_AMIS_SMTP_STARTTLS=true
MSR_AMIS_SMTP_SSL=false
MSR_AMIS_SMTP_TIMEOUT_MS=10000
MSR_AMIS_SUPER_USER_STATUS_EMAILS_ENABLED=true
```

To change the email account used by the system, change `MSR_AMIS_SMTP_USERNAME`, `MSR_AMIS_SMTP_PASSWORD`, and `MSR_AMIS_SMTP_FROM`, then restart the API. For Gmail, use an app password instead of the normal account password.

When `MSR_AMIS_SUPER_USER_STATUS_EMAILS_ENABLED=true`, every active user with role `SUPER_ADMIN` receives an email for system status events recorded in the backend audit log. These notifications include the action, module, record ID, actor, and details. Email delivery failures are logged by the API and do not block the original system action.

When `MSR_AMIS_OPERATION_EMAILS_ENABLED=true`, distribution and return actions send operational emails to the comma-separated recipients in `MSR_AMIS_OPERATION_EMAIL_RECIPIENTS`. These messages use the configured SMTP sender and never roll back a completed distribution or return if email delivery fails.

## Saved Desktop Credentials

Saved desktop credentials are user opt-in. The login screen asks before saving credentials after successful sign-in.

Saved credentials are not filled by default when the login screen opens. Saved email addresses are shown as suggestions while typing, and the saved password is filled only after the user selects one of those saved email addresses.

The current desktop implementation encrypts saved login data before storing it in the local Java preferences store for the Windows user. This is acceptable for convenience on a private workstation, but it is not the strongest credential-storage model.

For a hardened deployment, use an operating-system credential vault for saved secrets:

- Windows Credential Manager on Windows
- Keychain on macOS
- Secret Service or an equivalent keyring on Linux

If an OS credential vault is not available, the safest policy is to save only the email address and require the password at every sign-in.

## Local Currency Format

Equipment purchase cost and maintenance cost are displayed in Malawi Kwacha using this format:

```text
MWK 150,000.00
```

Users can type plain numbers in the desktop fields. The app formats valid numeric values when the field loses focus and before saving.

## Mode Policy

### `REMOTE_API`

- strict online mode
- requires a reachable API base URL
- shows an API unreachable error if the API is down
- safest production default for fully centralized behavior

### `AUTO`

- offline-capable mode for deployments that intentionally allow local fallback
- keeps SQLite as the local working mirror
- sends changes to PostgreSQL through the API when online
- queues changes locally when offline
- requires Sync Center processing after offline work
- Sync Center is opened from `Data & Records -> Sync Center`

### `LOCAL_DATABASE`

- available for development or controlled local-only fallback
- does not automatically share changes unless used with the sync queue through `AUTO`

## Guidance

- use `.env` for local development
- use `.env` on deployments where these project values must override machine-level environment variables
- do not commit real secrets into the repository
- keep `.env` local; it is ignored by git
- configure desktop clients with the server API URL, not `localhost`, unless the API is installed locally
- use `REMOTE_API` for the safest centralized rollout
- use `AUTO` only where users are allowed to continue during network outages and administrators will review Sync Center results
- official centralized backups are PostgreSQL server backups, not desktop Backup & Restore
- desktop Backup & Restore is available only in local SQLite mode for development or controlled local-only work
