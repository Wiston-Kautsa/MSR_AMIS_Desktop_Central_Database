# MSR-AMIS API Migration Plan

## Purpose

This document records the migration path from the original JavaFX + SQLite design to the current API/PostgreSQL architecture.

Status: the migration is functionally complete. Current work is deployment hardening, documentation, and operational validation.

## Current Architecture

The current operating architecture is:

`JavaFX Desktop -> local SQLite mirror -> Spring Boot API -> PostgreSQL`

The desktop application is now primarily a client with a local offline mirror. The backend owns authentication, authorization, business rules, persistence, dashboard aggregation, reporting, asset history, and audit logging. PostgreSQL is the primary database.

## What Is Already Implemented

### Desktop service boundary

The desktop app uses service abstractions instead of controller-to-database coupling for the main modules:

- `AuthService`
- `EquipmentService`
- `UserService`
- `AssignmentService`
- `DistributionService`
- `ReturnService`
- `DashboardService`
- `ReportService`
- `AssetHistoryService`
- `DataMaintenanceService`
- `SyncCenterService`

`ServiceRegistry` selects local or API-backed implementations. In `AUTO` mode, the local implementation is used as the working mirror and routes changes to the API when a central session is available.

### API-backed desktop modules

The JavaFX app can run in `AUTO` or `REMOTE_API` mode for:

- login
- password reset
- users
- equipment
- assignments
- distributions
- returns
- dashboard
- reports
- asset history
- audit logs
- data maintenance
- Sync Center
- department management
- maintenance tracking/reporting through the desktop local maintenance store

### Backend modules already available

The Spring Boot backend includes:

- `auth`
- `users`
- `departments`
- `equipment`
- `assignments`
- `distributions`
- `returns`
- `dashboard`
- `reports`
- `asset history`
- `audit logs`
- PostgreSQL `maintenance_log` schema support for asset history

### Centralized operational policy already enforced

- the protected primary `SUPER_ADMIN` email is configured with `MSR_AMIS_PRIMARY_SUPER_ADMIN_EMAIL`
- setup and reserved account emails are configured with `MSR_AMIS_SETUP_*_EMAIL` and `MSR_AMIS_RESERVED_*_EMAILS`
- these email values can be changed from `.env` during development or server environment variables in production
- `ADMIN` cannot create or manage `SUPER_ADMIN`
- user listing is role-filtered on the backend
- desktop backup/restore actions are disabled in `REMOTE_API` mode
- desktop Backup & Restore is also hidden in `AUTO` when the app is using the central API
- `AUTO` mode supports local SQLite fallback and queued offline changes
- Sync Center replays valid queued changes and refreshes SQLite from PostgreSQL
- Sync Center is available from `Data & Records -> Sync Center`
- Sync Center access is role-based: Super Admin has full access, Admin has own-record access, User has no access
- applied queue records are removed from the active queue list after successful push
- departments can be created, renamed, and deleted from `Administration -> Departments`
- asset history includes maintenance events

## Configuration

### Desktop

Use:

- `MSR_AMIS_DATA_MODE=REMOTE_API` for strict centralized production
- `MSR_AMIS_DATA_MODE=AUTO` only when offline work and Sync Center recovery are intentionally enabled
- `MSR_AMIS_API_BASE_URL=http://SERVER_IP_OR_NAME:8090`

Important:

- the application no longer silently defaults to `LOCAL_DATABASE` when the mode is missing
- unsupported mode values now fail instead of falling back quietly
- use `localhost` only when the API runs on the same computer
- the current local development `.env` uses `http://localhost:8090`
- for `API not reachable`, use [Troubleshooting](troubleshooting.md) to check server health, PostgreSQL, client `.env`, firewall, and Sync Center recovery

### Backend

Use:

- `MSR_AMIS_DB_URL`
- `MSR_AMIS_DB_USERNAME`
- `MSR_AMIS_DB_PASSWORD`
- `MSR_AMIS_API_PORT`
- `MSR_AMIS_JWT_SECRET`
- `MSR_AMIS_JWT_EXPIRATION_SECONDS`
- `MSR_AMIS_EXPOSE_RESET_CODE_ON_EMAIL_FAILURE`

## Remaining Work

The migration is no longer blocked by missing architecture. The remaining work is mostly deployment and hardening:

- stand up the real shared PostgreSQL environment
- run the backend as the authoritative server process
- validate all desktop workflows in `AUTO` mode and strict `REMOTE_API` mode
- validate offline queue and Sync Center workflows with real users
- clean remaining legacy local-only helpers where they are no longer needed
- decide whether to keep `LOCAL_DATABASE` as a development-only mode or remove it entirely later
- fix non-blocking desktop UI issues such as CSS warnings
- validate department management in both online and offline `AUTO` mode
- validate maintenance history in Asset History for assets with maintenance records
- validate the API-not-reachable checklist with at least one server-side failure and one client/network failure

## Current Development Status

The system is now in the stabilization and deployment phase, not the early migration phase.
