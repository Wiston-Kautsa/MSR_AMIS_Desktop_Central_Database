# MSR-AMIS Daily Operations

## Purpose

This guide explains how staff should use MSR-AMIS during normal operation, network failure, synchronization, and server maintenance.

## Normal Daily Use

Recommended desktop mode:

```env
MSR_AMIS_DATA_MODE=AUTO
APP_MODE=AUTO
```

In `AUTO` mode:

- PostgreSQL is the primary database.
- The API is the only path to PostgreSQL.
- SQLite is kept on each desktop as a local mirror.
- If the API is reachable, changes are sent to PostgreSQL.
- If the API is unreachable, changes are saved locally and queued.

## Connection Status

The dashboard connection label should be checked before important work.

### `ONLINE (AUTO)`

The API is reachable. Users can work normally.

Data flow:

```text
Desktop SQLite mirror -> API -> PostgreSQL
```

After remote writes, SQLite is refreshed from PostgreSQL.

### `OFFLINE (AUTO)`

The API or network is unavailable. Users can continue working locally if their account already exists in SQLite.

Data flow:

```text
Desktop SQLite -> local sync queue
```

Changes are not yet in PostgreSQL.

### `ONLINE`

The app is in strict `REMOTE_API` mode and connected to the API.

### `OFFLINE`

The app is in strict `REMOTE_API` mode and cannot reach the API. Users cannot continue until the API is reachable or the app is configured for `AUTO`.

### `LOCAL DATABASE`

The app is using local SQLite only. This is for development or controlled fallback, not normal shared production operation.

## What Users Should Do When API Is Not Reachable

1. Check the connection label.
2. If it says `OFFLINE (AUTO)`, continue working only if the work is urgent.
3. Remember that offline changes are not yet visible to other users.
4. When the network/API is restored, log in again while online.
5. Open Sync Center.
6. Process pending changes.
7. Confirm the status message says SQLite was refreshed from PostgreSQL.

If the user cannot log in offline, they must wait for the API because their account has not yet been mirrored to SQLite on that computer.

## Sync Center

Sync Center is the control point for offline work.

Use it when:

- the app was used while `OFFLINE (AUTO)`
- a user reports that local changes are not visible to others
- an administrator wants to refresh SQLite from PostgreSQL

Sync behavior:

- valid queued changes are applied to PostgreSQL through the API
- conflicts are rejected
- API validation or business-rule failures are rejected
- failures are recorded for review
- SQLite is refreshed from PostgreSQL at the end

Rejected records should not be ignored. An administrator should read the rejection reason and decide whether to recreate the change manually.

## Conflict Rules

PostgreSQL wins.

If a record changed in PostgreSQL while a desktop was offline, the offline queued change should not overwrite the PostgreSQL record automatically.

Examples:

- A user edits an assignment offline, but another user already changed it in PostgreSQL.
- A user deletes equipment offline, but the equipment was already assigned or retired centrally.
- A local user creation conflicts with an existing PostgreSQL email or username.

In these cases the queued action is rejected and SQLite is refreshed from PostgreSQL.

## User Accounts

Users can log in offline only after they have logged in online on the same computer before. Online login mirrors the user profile into SQLite.

Email addresses must be unique. The system uses the full normalized email as the internal username for created accounts, so these are different accounts:

```text
wkautsa@yahoo.com
wkautsa@nlgfc.gov.mw
```

The same exact email cannot belong to multiple users.

## Right-Click Actions

Tables support right-click actions where available:

- Users: edit, delete, freeze, unfreeze, refresh
- Equipment: edit, delete, retire, restore, refresh
- Assignments: edit, delete, freeze, unfreeze, retire, restore, refresh

Menu actions are clickable, but backend security still applies. If the current user is not allowed to perform an action, the system should show an error instead of silently doing nothing.

## Server Administrator Checklist

At the start of the day or after a server restart:

1. Confirm PostgreSQL is running.
2. Confirm the API service is running.
3. Check API health:

```powershell
Invoke-RestMethod http://localhost:8090/actuator/health
```

Expected:

```json
{"status":"UP"}
```

4. Confirm client computers point to the server API URL, not `localhost`.
5. Confirm firewall allows client access to the API port.

## Client Configuration Checklist

Each client should use:

```env
MSR_AMIS_DATA_MODE=AUTO
MSR_AMIS_API_BASE_URL=http://SERVER_IP_OR_NAME:8090

APP_MODE=AUTO
API_BASE_URL=http://SERVER_IP_OR_NAME:8090
```

Use `localhost` only if the API runs on that same computer.

## Backup Responsibility

PostgreSQL is the primary database, so official backups must be taken from PostgreSQL on the server.

SQLite is a local mirror and queue store. It should not be treated as the official backup source unless an administrator is recovering unsynced offline work from a specific client computer.

## Installer Update

After code changes, rebuild the desktop package:

```powershell
.\scripts\build-desktop.cmd
```

Installers are generated in:

```text
dist\MSR AMIS-1.0.0.msi
dist\MSR AMIS-1.0.0.exe
```

Clients must install the updated MSI/EXE before they receive desktop fixes.
