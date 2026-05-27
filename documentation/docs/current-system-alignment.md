# MSR-AMIS Current System Alignment

## Purpose

This document records the current implemented state of MSR-AMIS. Use it to check whether older notes, deployment steps, and operating procedures still match the code.

## Current Architecture

The implemented production path is:

```text
JavaFX Desktop -> REST API -> PostgreSQL
```

The desktop also keeps a local SQLite store. In `REMOTE_API` mode SQLite is not the source of truth. In `AUTO` mode SQLite is the offline mirror and queue store until Sync Center pushes changes to the API.

## Security Configuration

Production secrets must come from environment variables or a private `.env`/`docker.env` file. They must not be committed to Git.

Required API values:

- `MSR_AMIS_DB_USERNAME`
- `MSR_AMIS_DB_PASSWORD`
- `MSR_AMIS_JWT_SECRET`

The API no longer has a fallback database username, fallback database password, or fallback JWT signing secret. If these values are missing, startup should fail instead of silently using a known credential.

Server and documentation examples use `YOUR_SERVER_HOST`. The source tree should not contain a real public server IP address.

## Desktop Modes

### `REMOTE_API`

- strict centralized mode
- safest production default
- requires a reachable API before users can work
- official writes go through PostgreSQL immediately
- desktop Backup & Restore is hidden

### `AUTO`

- offline-capable mode
- desktop writes locally when the API is unreachable
- local changes are placed in the sync queue
- Admin or Super Admin must use Sync Center after the API returns
- SQLite is refreshed from PostgreSQL after sync processing

### `LOCAL_DATABASE`

- local SQLite-only mode
- useful for development and controlled local-only work
- desktop Backup & Restore is visible only in this mode

## Account And Role State

Account policy is configured outside source code:

- `MSR_AMIS_PRIMARY_SUPER_ADMIN_EMAIL`
- `MSR_AMIS_SETUP_ADMIN_EMAIL`
- `MSR_AMIS_SETUP_USER_EMAIL`
- `MSR_AMIS_RESERVED_SUPER_ADMIN_EMAILS`
- `MSR_AMIS_RESERVED_ADMIN_EMAILS`
- `MSR_AMIS_RESERVED_USER_EMAILS`

The primary Super Admin email is protected by backend policy. Setup Admin and setup User emails are reserved bootstrap accounts and can be changed by environment configuration before deployment.

User Management current visibility:

| Role | User Management View |
| --- | --- |
| `SUPER_ADMIN` | all roles |
| `ADMIN` | Admin and User roles |
| `USER` | User role accounts |

Admin can manage Admin and User accounts, including freeze/unfreeze. Super Admin can manage all roles and delete users. User accounts can only view the User-role directory and cannot manage accounts.

## Audit Logs

Audit Logs are available to Super Admin and Admin.

| Role | Audit Scope |
| --- | --- |
| `SUPER_ADMIN` | all audit logs |
| `ADMIN` | Admin/User activity visible to the admin scope; Super Admin activity is hidden |
| `USER` | no Audit Logs panel |

Audit records are created for authentication and operational actions. In API modes, the API owns central audit persistence. Local fallback exists for `AUTO` and `LOCAL_DATABASE`.

## Sync Center

Sync Center is available from:

```text
Data & Records -> Sync Center
```

Access:

| Role | Sync Center Scope |
| --- | --- |
| `SUPER_ADMIN` | full queue, audit, conflicts, and recovery actions |
| `ADMIN` | Admin/User actor queue and audit records; Super Admin records hidden |
| `USER` | hidden |

The API `/api/sync/push` endpoint now accepts:

- `EQUIPMENT`
- `ASSIGNMENT`
- `DISTRIBUTION`
- `RETURN`
- `USER`
- `DEPARTMENT`

The API still rejects unsupported entity types with `Unsupported sync entity`.

Current Sync Center limitations:

- `GET /api/sync/pull` records a successful pull request and tells the desktop to refresh; it does not yet return a full grouped central snapshot payload.
- Conflict review exists, but the JavaFX `Keep Local`, `Keep Central`, and `Merge` buttons still show a not-ready message because automatic field-level merge/overwrite resolution is not enabled yet.

## Desktop Screens And Current UI Behavior

Equipment:

- Add Equipment supports bulk import with a progress counter.
- Equipment List no longer exposes Export/PDF buttons.
- Maintenance Tracking is available and feeds Maintenance Report and Asset History.

Assignments and distribution:

- Create Assignment creates assignment requests.
- Assignment List no longer exposes Export/PDF buttons.
- Distribute Equipment supports manual and bulk distribution.
- Bulk distribution has a progress counter.
- Distribution List is a listing screen and does not expose Export/PDF buttons.
- Distribution Report is the report screen and exposes Export/PDF buttons.

Returns:

- Return Equipment contains separate Return Entry and Bulk Import sections.
- Bulk return import has a progress counter.
- Return Equipment List no longer exposes Export/PDF buttons.
- Return Report is the report screen and exposes Export/PDF buttons.

Reports with Export/PDF:

- Inventory Report
- Assignment Report
- Distribution Report
- Asset History
- Return Report
- Outstanding Report
- Maintenance Report

Table readability:

- dashboard-loaded tables use wider columns and horizontal scrolling
- table headers and content are kept readable instead of clipped where possible
- sidebar labels were widened so report names remain visible

## Saved Desktop Credentials

Saved credentials are opt-in after successful login. The login screen does not automatically fill credentials when it opens.

Current behavior:

- saved email addresses appear as suggestions while typing
- selecting a saved email fills the email field and then fills the saved password
- choosing a different suggestion fills that selected account, not a different stored account

Saved secrets are encrypted before being stored in the local Java preferences store. For a stricter production policy, save only email addresses or use the operating-system credential vault.

## Deployment And Packaging

Desktop clients should normally use:

```env
MSR_AMIS_DATA_MODE=REMOTE_API
MSR_AMIS_API_BASE_URL=http://YOUR_SERVER_HOST:8090
APP_MODE=REMOTE_API
API_BASE_URL=http://YOUR_SERVER_HOST:8090
```

Use `localhost` only when the API is running on the same computer.

Packaging requires an explicit server URL:

```powershell
$env:MSR_AMIS_PACKAGE_API_BASE_URL="http://YOUR_SERVER_HOST:8090"
.\scripts\build-desktop.cmd
```

This requirement prevents accidental builds that contain an old hardcoded server address.

## Backup Reality

Centralized production backup is PostgreSQL backup on the server.

Desktop Backup & Restore is a local SQLite feature for `LOCAL_DATABASE` mode only. It is not the official backup path for centralized deployment.

## Current Test Coverage

The API has tests for authentication, equipment facade behavior, sync service behavior, and sync validation. Sync Center still needs broader end-to-end tests for offline distribution/return replay, idempotency retry behavior, central pull snapshots, and UI conflict resolution.

## Production Notes

Use HTTPS in front of the API for real production use. The current documentation examples use HTTP on port `8090` for local/server testing. Put nginx, Caddy, IIS reverse proxy, or another approved HTTPS terminator in front of the API before public or wider network exposure.
