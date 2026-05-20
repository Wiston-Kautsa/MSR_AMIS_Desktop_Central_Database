# MSR-AMIS

MSR-AMIS is a JavaFX desktop client backed by a Spring Boot API and PostgreSQL.

Centralized operating model:

`Desktop Client -> local SQLite mirror -> REST API -> PostgreSQL`

PostgreSQL remains the central source of truth. The desktop can run in strict online mode or, when offline work is intentionally enabled, use SQLite as a local working mirror and queue.

## Project Structure

- `src/`
  Desktop JavaFX application
- `msr-amis-api/`
  Spring Boot backend API
- `documentation/`
  Architecture, deployment, and configuration notes

## Current Operating Model

- The desktop is the user interface.
- The API owns authentication, authorization, business rules, validation, and persistence.
- PostgreSQL is the central source of truth.
- `REMOTE_API` mode is the safest centralized production default because users cannot continue when the API is unavailable.
- `AUTO` mode is available for deployments that intentionally allow offline work. In `AUTO` mode, the desktop uses SQLite locally, sends changes to PostgreSQL through the API when online, queues changes when offline, and refreshes SQLite from PostgreSQL after sync.
- The dashboard shows a live connection indicator so users can see whether they are connected to the central system.
- Sync Center is available from `Data & Records -> Sync Center` and is used after offline work.
- Sync Center access is role-based: Super Admin has full access, Admin can process and view only their own queue/audit records, and User accounts do not see the panel.
- Department Management is available from `Administration -> Departments`; `MSR` is the default protected department.
- Maintenance Tracking records maintenance issue, action taken, performer, date, cost, and status. Maintenance is included in Maintenance Report and Asset History.
- Backup & Restore is a local SQLite feature and is hidden unless the desktop is running in `LOCAL_DATABASE` mode.
- In centralized use, official backups must be taken from PostgreSQL on the server.

## Configuration

The project now supports a root `.env` file for development.

Desktop configuration resolution order:

1. root `.env`
2. Java system properties
3. OS environment variables

API configuration:

- Spring Boot imports the root `.env` file as the first config source when it exists.
- Values in `.env` override matching OS environment variables and Java system properties.

Start from [.env.example](.env.example).

Current production-oriented `.env` shape:

```env
MSR_AMIS_DATA_MODE=REMOTE_API
MSR_AMIS_API_BASE_URL=http://SERVER_IP_OR_NAME:8090

APP_MODE=REMOTE_API
API_BASE_URL=http://SERVER_IP_OR_NAME:8090

MSR_AMIS_API_PORT=8090
MSR_AMIS_DB_URL=jdbc:postgresql://localhost:5432/msr_amis
MSR_AMIS_DB_USERNAME=postgres
MSR_AMIS_DB_PASSWORD=postgres
MSR_AMIS_JWT_SECRET=your_base64_secret
MSR_AMIS_JWT_EXPIRATION_SECONDS=28800
MSR_AMIS_EXPOSE_RESET_CODE_ON_EMAIL_FAILURE=false
MSR_AMIS_PRIMARY_SUPER_ADMIN_EMAIL=msramis@nlgfc.gov.mw
MSR_AMIS_SETUP_ADMIN_EMAIL=setup-admin@example.com
MSR_AMIS_SETUP_USER_EMAIL=setup-user@example.com
MSR_AMIS_RESERVED_SUPER_ADMIN_EMAILS=msramis@nlgfc.gov.mw
MSR_AMIS_RESERVED_ADMIN_EMAILS=setup-admin@example.com
MSR_AMIS_RESERVED_USER_EMAILS=setup-user@example.com
```

Account emails are configured from environment values, or from `.env` during development. `MSR_AMIS_PRIMARY_SUPER_ADMIN_EMAIL`, `MSR_AMIS_SETUP_ADMIN_EMAIL`, and `MSR_AMIS_SETUP_USER_EMAIL` can be changed without code changes. `MSR_AMIS_RESERVED_SUPER_ADMIN_EMAILS`, `MSR_AMIS_RESERVED_ADMIN_EMAILS`, and `MSR_AMIS_RESERVED_USER_EMAILS` are comma-separated lists, so reserved addresses can be added or removed by editing `.env` or the server environment and restarting the API.

Password reset email sender settings use the same `.env` file during development:

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
MSR_AMIS_OPERATION_EMAILS_ENABLED=false
MSR_AMIS_OPERATION_EMAIL_RECIPIENTS=admin@example.com
```

Use `localhost` only when the desktop and API run on the same computer. Client machines must point to the API server address.

For installed desktop clients, use [desktop-client.env.example](desktop-client.env.example). Rename it to `.env` in the installed `MSR AMIS` folder and replace `SERVER_IP_OR_NAME` with the computer or server running the API. The packaged desktop now checks for `.env` in the launch folder, the packaged `app` folder, and the installed application folder.

The installer also places an editable `.env` file in the installed `MSR AMIS` folder. If the API server IP changes, edit that file and restart the desktop application:

```env
MSR_AMIS_API_BASE_URL=http://NEW_SERVER_IP:8090
API_BASE_URL=http://NEW_SERVER_IP:8090
```

To build an installer that already contains the client API URL, set `MSR_AMIS_PACKAGE_API_BASE_URL` before running the package script:

```powershell
$env:MSR_AMIS_PACKAGE_API_BASE_URL="http://SERVER_IP_OR_NAME:8090"
.\scripts\build-desktop.cmd
```

Saved desktop credentials are opt-in. The login screen asks before saving credentials after successful sign-in. It does not fill saved credentials by default; saved emails appear as suggestions while typing, and the password fills only after selecting a saved email. For the strongest production setup, store saved secrets in the operating-system credential vault; otherwise save only the email address and require the password at sign-in.

Equipment purchase cost and maintenance cost use local currency display, for example `MWK 150,000.00`. Users can type plain numbers and the desktop formats them before saving.

## API Not Reachable

If MIS reports `API not reachable`, check the API health endpoint on the server:

```powershell
Invoke-RestMethod http://localhost:8090/actuator/health
```

Client computers must test the server address, not `localhost`:

```powershell
Invoke-RestMethod http://SERVER_IP_OR_NAME:8090/actuator/health
```

Normal client `.env` files must use `http://SERVER_IP_OR_NAME:8090`. See [Troubleshooting](documentation/docs/troubleshooting.md) for the full checklist covering API startup, PostgreSQL, firewall, client configuration, and Sync Center recovery.

## Server Hosting For Testers

For temporary online testing, host only the API and PostgreSQL. Testers install the desktop `.exe`, log in, and the app calls the hosted API.

Use [server.env.example](server.env.example) for server-only environment variables and [Server Hosting](documentation/docs/server-hosting.md) for the deployment checklist. For server runtime setup, use [Server Runtime Deployment](documentation/docs/server-runtime-deployment.md). For Git-based Docker deployment, use [Docker Server Deployment](documentation/docs/docker-server-deployment.md).

Prepare server runtime files:

```powershell
.\scripts\package-server-deployment.ps1
```

This creates a copyable server bundle in `dist\server`.

The desktop client is installed on user machines, then its `.env` API URL is set to the server address, for example `http://SERVER_IP_OR_NAME:8090`.

Docker deployment is also available when the server clones the project from Git:

```powershell
Copy-Item docker.env.example docker.env
# Edit docker.env and replace database password, JWT secret, account emails, and SMTP values.
docker compose --env-file .\docker.env up -d --build
docker compose --env-file .\docker.env ps
Invoke-RestMethod http://localhost:8090/actuator/health
```

Build the API jar:

```powershell
.\scripts\build-api.cmd
```

Build a tester desktop installer that points to the hosted API:

```powershell
$env:MSR_AMIS_PACKAGE_API_BASE_URL="https://YOUR_API_HOST"
.\scripts\build-desktop.cmd
```

## Main Modes

- `AUTO`
  Offline-capable mode. The app works online through the API when reachable and falls back to local SQLite when unreachable. Use it only where Sync Center review and conflict handling are part of operations.
- `REMOTE_API`
  Strict online mode. Desktop talks directly to the API and cannot continue if the API is unreachable. This is the safest production default for fully centralized operation.
- `LOCAL_DATABASE`
  Local-only mode for development or controlled fallback work.

## Desktop Packaging

Build the app image, MSI, and EXE:

```powershell
.\scripts\build-desktop.cmd
```

Generated files:

- `dist\MSR AMIS\MSR AMIS.exe`
- `dist\MSR AMIS-1.0.0.msi`
- `dist\MSR AMIS-1.0.0.exe`

Current package build: May 17, 2026. This desktop package includes the updated bulk enrolment templates, full table column headers, equipment operational metadata, maintenance tracking/reporting, Asset History with maintenance events, role-based Sync Center access, active queue cleanup after successful push, and Department Management.

## Development

Desktop compile:

```powershell
.\mvnw.cmd -DskipTests compile
```

API compile:

```powershell
cd msr-amis-api
..\mvnw.cmd -DskipTests compile
```

Run the API locally:

```powershell
.\mvnw.cmd -q -f msr-amis-api\pom.xml spring-boot:run
```

Health check:

```powershell
Invoke-RestMethod http://localhost:8090/actuator/health
```

Server PostgreSQL backup:

```powershell
.\scripts\postgres-backup.ps1
```

Register a daily Windows backup task:

```powershell
.\scripts\register-postgres-backup-task.ps1 -StartTime "22:00"
```

These backup scripts are intended to run on the server where PostgreSQL tools are installed.

## Documentation

- [Documentation Index](documentation/README.md)
- [System Overview](documentation/docs/system-overview.md)
- [Sync Backend Contract](documentation/docs/sync-backend-contract.md)
- [Sync Implementation Blueprint](documentation/docs/sync-implementation-blueprint.md)
- [Architecture](documentation/docs/architecture.md)
- [Configuration](documentation/docs/configuration.md)
- [Deployment](documentation/docs/deployment.md) including PostgreSQL server hosting and backup guidance
- [Daily Operations](documentation/docs/daily-operations.md)
- [Troubleshooting](documentation/docs/troubleshooting.md)
- [Current System Alignment](documentation/docs/current-system-alignment.md)
- [Final Corrected Design](documentation/docs/final-corrected-design.md)
- [API Migration Plan](documentation/docs/api-migration-plan.md)
