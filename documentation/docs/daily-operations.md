# MSR-AMIS Daily Operations

## Purpose

This guide explains how staff should use MSR-AMIS during normal operation, network failure, synchronization, and server maintenance.

## Normal Daily Use

Safest centralized production mode:

```env
MSR_AMIS_DATA_MODE=REMOTE_API
APP_MODE=REMOTE_API
```

In `REMOTE_API` mode, the desktop must reach the API before users can work. Use this mode when all production work must be written to PostgreSQL immediately.

Offline-capable mode:

```env
MSR_AMIS_DATA_MODE=AUTO
APP_MODE=AUTO
```

Use `AUTO` only when the organization allows users to continue during network/API outages and accepts the Sync Center review process.

In centralized operation:

- PostgreSQL is the primary database.
- The API is the only path to PostgreSQL.
- SQLite is kept on each desktop for local storage and can act as a mirror in `AUTO` mode.
- If the API is reachable, changes are sent to PostgreSQL.
- If the API is unreachable in `AUTO`, changes are saved locally and queued.
- If the API is unreachable in `REMOTE_API`, users must wait until connectivity is restored.

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
5. Open `Data & Records -> Sync Center`.
6. Process pending changes.
7. Confirm the status message says SQLite was refreshed from PostgreSQL.

If the user cannot log in offline, they must wait for the API because their account has not yet been mirrored to SQLite on that computer.

## Administrator Troubleshooting: API Not Reachable

When MIS shows that the API is not reachable, check these items in order.

The same checklist is also available as a standalone reference in [Troubleshooting](troubleshooting.md).

### 1. Confirm the API is running on the server

Run this on the API server:

```powershell
Invoke-RestMethod http://localhost:8090/actuator/health
```

Expected:

```json
{"status":"UP"}
```

If this fails, the API is not running or did not bind to port `8090`.

### 2. Confirm PostgreSQL is running

The API needs PostgreSQL before it can start correctly. On the server, confirm the PostgreSQL service is running.

```powershell
Get-Service *postgres*
```

The service should show `Running`.

### 3. Start the API

From the project root on the server:

```powershell
.\mvnw.cmd -f msr-amis-api\pom.xml spring-boot:run
```

If startup is blocked by test compilation during local development, use:

```powershell
.\mvnw.cmd -f msr-amis-api\pom.xml -Dmaven.test.skip=true spring-boot:run
```

For production, run the packaged API as a Windows service or scheduled startup task so it starts automatically after server restart.

### 4. Test from a client computer

From a desktop client, test the server address, not `localhost`:

```powershell
Invoke-RestMethod http://SERVER_IP_OR_NAME:8090/actuator/health
```

If the server health check works locally but fails from the client, check the network connection, server IP/name, and Windows Firewall.

### 5. Check client `.env`

Client computers must point to the API server:

```env
MSR_AMIS_DATA_MODE=REMOTE_API
MSR_AMIS_API_BASE_URL=http://SERVER_IP_OR_NAME:8090

APP_MODE=REMOTE_API
API_BASE_URL=http://SERVER_IP_OR_NAME:8090
```

Use `localhost` only when the desktop app and API are running on the same computer. If a normal client uses `localhost`, it will look for an API on that client machine and show API not reachable.

### 6. Check the firewall

Allow inbound access to API port `8090` on the server. If the server can reach `localhost:8090` but clients cannot reach `SERVER_IP_OR_NAME:8090`, firewall or network routing is the likely cause.

### 7. Use Sync Center after recovery

If users worked while `OFFLINE (AUTO)`, they must open:

```text
Data & Records -> Sync Center
```

Process pending changes after the API is reachable again.

## Sync Center

Sync Center is the control point for offline work.

Open it from:

```text
Data & Records -> Sync Center
```

Use it when:

- the app was used while `OFFLINE (AUTO)`
- a user reports that local changes are not visible to others
- an administrator wants to refresh SQLite from PostgreSQL

Sync behavior:

- valid queued changes are applied to PostgreSQL through the API
- conflicts are rejected
- API validation or business-rule failures are rejected
- failures are recorded for review
- applied queue records are removed from the active queue list after a successful push
- SQLite is refreshed from PostgreSQL at the end

Current implementation note:

- equipment queue push is implemented for create, update, upsert, delete, and status changes
- the API returns a result for each pushed equipment queue item
- the desktop marks each local equipment queue row as applied or failed based on the API result
- pull currently acknowledges the request and updates sync audit/status; full central snapshot payload is still pending
- non-equipment generic push handlers are not complete yet, so assignment/distribution/return/user/department offline queue replay still requires the older desktop service path or future backend handlers

Role access:

| Role | Sync Center access |
| --- | --- |
| `SUPER_ADMIN` | Full queue, full audit, push all pending records, requeue rejected records |
| `ADMIN` | Own queue and own audit only, push own pending records |
| `USER` | Hidden |

Rejected records should not be ignored. An administrator should read the rejection reason and decide whether to recreate the change manually.

Device information:

- open `Sync Center -> Logs, Settings, and Recovery -> Device / Session`
- review `Device Name`, `Device ID`, `Logged-in User`, and `Sync Session ID`
- audit log rows also include machine/computer information where available

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

## Saved Credentials

The login screen can save credentials only after a successful sign-in and only after the user confirms the prompt.

The login form does not automatically fill the last saved email or password when it opens. If the user starts typing an email that was saved before, matching saved emails appear as choices. The password is filled only after the user selects a saved email from the dropdown.

Rules for users:

- save credentials only on a private, trusted computer
- do not save credentials on shared machines
- use `Forget Saved` when a saved login should be removed
- after changing or resetting a password, sign in again and choose whether to save the new credentials

Current desktop storage encrypts saved login data before placing it in the local Java preferences store for the Windows user. This prevents casual plaintext inspection, but it is not as strong as a real operating-system credential vault.

The most secure production approach is:

- save only the email address in the app
- store any saved password or long-lived secret in the operating-system credential vault, such as Windows Credential Manager, macOS Keychain, or Linux Secret Service
- never store database credentials on desktop clients
- prefer short-lived API tokens over saved passwords where possible

## Departments

Departments are managed from:

```text
Administration -> Departments
```

Rules:

- `MSR` is the default department.
- `MSR` cannot be renamed or deleted.
- Admin and Super Admin users can create and rename departments.
- A department that is still used by users or assignments cannot be deleted.
- Renaming a department updates matching user and assignment records.
- Offline department changes in `AUTO` mode are queued and processed through Sync Center.

## Cost Entry

Equipment purchase cost and maintenance cost should be recorded in Malawi Kwacha.

Users may type a plain number, such as:

```text
150000
```

The desktop formats it as:

```text
MWK 150,000.00
```

Bulk equipment import templates also use the same `MWK` format for `purchase_cost`.

## Maintenance Operations

Maintenance is opened from:

```text
Equipment Inventory -> Maintenance Tracking
```

Users should record:

- asset code
- issue
- action taken
- person who performed the maintenance
- maintenance date
- cost
- status

If maintenance is not completed, the asset is marked `MAINTENANCE`. If maintenance is completed, the asset is marked `AVAILABLE`.

Maintenance appears in:

- Maintenance Tracking
- Maintenance Report
- Asset History for the asset code

If an asset has completed maintenance and its current equipment status is back to `AVAILABLE`, Asset History still shows the maintenance row in the timeline details.

## Returns and Outstanding Reasons

When returning only part of an assignment, users must enter the reason why the remaining assets are still outstanding. The system stores that reason in `distribution.outstanding_remarks` for the remaining asset codes, and the Outstanding Report displays it from there.

Return Report columns are widened and long values wrap, so equipment names, serial numbers, sources, responsible officers, reasons, and remarks are visible instead of being shortened.

## Export Location

All desktop exports should land in:

```text
Downloads\MSR-AMIS
```

This includes CSV/PDF report exports and generated templates where the system controls the output path.

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
MSR_AMIS_DATA_MODE=REMOTE_API
MSR_AMIS_API_BASE_URL=http://SERVER_IP_OR_NAME:8090

APP_MODE=REMOTE_API
API_BASE_URL=http://SERVER_IP_OR_NAME:8090
```

Use `AUTO` only where offline work is approved and Sync Center recovery is part of normal operations. Use `localhost` only if the API runs on that same computer.

## Backup Responsibility

PostgreSQL is the primary database, so official backups must be taken from PostgreSQL on the server.

SQLite is a local mirror and queue store. It should not be treated as the official backup source unless an administrator is recovering unsynced offline work from a specific client computer.

The desktop `Backup & Restore` screen is intentionally available only when the app is running in `LOCAL_DATABASE` mode. In `AUTO` or `REMOTE_API` centralized operation, users should not use desktop backup as the official system backup.

Example PostgreSQL backup command on the server:

```powershell
pg_dump -U postgres -d msr_amis -f msr_amis_backup.sql
```

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

The current rebuilt installers were generated on May 18, 2026. This build includes updated bulk enrolment templates, complete table column header display, wider report columns, equipment metadata columns, maintenance tracking/reporting, Asset History with maintenance events and direct maintenance-log fallback, Department Management, role-based Sync Center access, active queue cleanup after successful push, preserved outstanding return reasons, and centralized exports to `Downloads\MSR-AMIS`.

Clients must install the updated MSI/EXE before they receive desktop fixes.

