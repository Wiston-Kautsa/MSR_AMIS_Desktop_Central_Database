# MSR-AMIS System Overview

## Purpose

This document is the current inventory of what is implemented in MSR-AMIS. It should be used as the first reference for administrators, developers, and deployment staff.

## Architecture

Current architecture:

```text
JavaFX Desktop -> local SQLite mirror -> Spring Boot API -> PostgreSQL
```

PostgreSQL is the central source of truth. The desktop does not connect directly to PostgreSQL. Desktop clients talk to the API, and the API owns authentication, authorization, validation, business rules, reporting, audit logging, and central persistence.

SQLite is still used on desktop computers for local storage, local-only mode, and offline-capable `AUTO` mode.

## Operating Modes

### `REMOTE_API`

- strict online mode
- safest centralized production default
- desktop calls the API directly
- API writes to PostgreSQL
- users cannot continue if the API is unreachable
- desktop Backup & Restore is hidden

### `AUTO`

- offline-capable mode
- desktop keeps a local SQLite mirror
- online changes are sent to PostgreSQL through the API
- offline changes are saved locally and added to the sync queue
- Sync Center uploads valid queued work when the API is reachable again
- SQLite is refreshed from PostgreSQL after sync

### `LOCAL_DATABASE`

- local SQLite-only mode
- useful for development or controlled fallback work
- desktop Backup & Restore is available only in this mode for Admin and Super Admin users

## Desktop Navigation

The desktop sidebar contains these functional areas.

### Dashboard

- total assets
- available assets
- borrowed-this-month count
- returned-this-month count
- asset status chart
- utilization and availability rates
- alerts
- connection status label
- `Go Online` action for `AUTO` mode recovery

### Equipment Inventory

- Add Equipment
- Equipment List
- Maintenance Tracking

Equipment records include asset code, serial number, name, category, condition, source, entry date, status, and operational metadata such as purchase cost, location, warranty date, and supplier where configured.

Maintenance Tracking records maintenance issue, action taken, performed by, maintenance date, cost, and status. Open maintenance places the asset into `MAINTENANCE`; completed maintenance returns the asset to `AVAILABLE`.

### Equipment Issuing

- Create Assignment
- Distribute Equipment
- Distribution List
- Assignment List

Assignments track the person, department, equipment type, reason, quantity, and status. Distribution validates available equipment and records assigned assets, phone, national ID, outstanding remarks, and assigned date.

### Equipment Return

- Return Equipment
- Return Equipment List

Returns record who returned the asset, contact details, condition, remarks, and return date. Returned assets become available according to return rules.

### Reports

- Inventory Report
- Assignment Report
- Distribution Report
- Asset History
- Return Report
- Outstanding Report
- Maintenance Report

Reports support filtering and export where implemented. Cost fields use Malawi Kwacha formatting, for example `MWK 150,000.00`.

Asset History shows a single timeline per asset code. It includes:

- registration
- issue/distribution
- maintenance
- maintenance completed
- return

### Data & Records

- Backup & Restore
- Audit Logs
- Sync Center

Backup & Restore is local-mode only. In centralized production, official backups must be PostgreSQL server backups.

Audit Logs are available to Admin and Super Admin users.

Sync Center is available to Admin and Super Admin users. Normal User accounts do not see it.

### Administration

- Users
- Departments
- Data Maintenance

Users and Departments are available to Admin and Super Admin users. Data Maintenance is Super Admin only.

### Support

- About Us

## Roles and Access

### `SUPER_ADMIN`

Super Admin has full system authority.

Allowed:

- manage Super Admin, Admin, and User accounts
- manage departments
- view audit logs
- access Data Maintenance
- access full Sync Center
- process all sync queue records
- requeue rejected sync records
- reset controlled operational data

Protections:

- the primary configured Super Admin account cannot be deleted, frozen, or demoted
- the last active Super Admin cannot be frozen or deleted

### `ADMIN`

Admin has controlled operational authority.

Allowed:

- manage Admin and User accounts
- manage departments
- view audit logs
- access Sync Center
- process only their own queued sync records
- view their own sync queue and sync audit records

Restricted:

- cannot create or manage Super Admin accounts
- cannot access Data Maintenance
- cannot requeue rejected sync records
- cannot process other users' queued sync work

### `USER`

User has normal operational access.

Restricted:

- no user-management authority
- no department-management authority
- no audit-log access
- no Sync Center access
- no Data Maintenance access

User data changes should be handled through normal screens. In `AUTO` mode, offline work is queued automatically.

## Account Policy

Account emails are configured outside the codebase.

Important settings:

- `MSR_AMIS_PRIMARY_SUPER_ADMIN_EMAIL`
- `MSR_AMIS_SETUP_ADMIN_EMAIL`
- `MSR_AMIS_SETUP_USER_EMAIL`
- `MSR_AMIS_RESERVED_SUPER_ADMIN_EMAILS`
- `MSR_AMIS_RESERVED_ADMIN_EMAILS`
- `MSR_AMIS_RESERVED_USER_EMAILS`

The configured setup Admin and setup User accounts use `admin123` until deliberately changed. The primary Super Admin account is intended for protected first use and password reset flow.

Changing these values requires an API restart.

## Departments

Departments are managed from:

```text
Administration -> Departments
```

Rules:

- `MSR` is the default department
- `MSR` cannot be renamed or deleted
- Admin and Super Admin can create departments
- Admin and Super Admin can rename departments
- renaming a department updates matching user and assignment records
- departments used by users or assignments cannot be deleted
- offline department changes in `AUTO` mode are queued and processed by Sync Center

## Sync Center

Sync Center is opened from:

```text
Data & Records -> Sync Center
```

The panel shows:

- pending count
- applied count
- rejected count
- failed count
- online/offline readiness
- active queue items
- sync audit history
- device/session details including machine name, device ID, current user, and sync session ID

Applied records are removed from the active queue list after push, but they remain counted and visible through audit/history where relevant.

Access:

| Role | Access |
| --- | --- |
| `SUPER_ADMIN` | Full access |
| `ADMIN` | Own queue and own audit records only |
| `USER` | Hidden |

Actions:

| Action | `SUPER_ADMIN` | `ADMIN` | `USER` |
| --- | --- | --- | --- |
| Push pending queue | All records | Own records only | No |
| View queue | All records | Own records only | No |
| View audit | All records | Own records only | No |
| Requeue rejected records | Yes | No | No |

Conflict policy:

- PostgreSQL wins
- offline queued changes do not overwrite central records that changed while the desktop was offline
- rejected records remain available for administrator review

Current sync implementation status:

- equipment queue push is active through the API for `CREATE`, `UPDATE`, `UPSERT`, `DELETE`, and `STATUS`
- the JavaFX API Sync Center sends actual pending/failed local equipment queue records to `/api/sync/push`
- local queue rows are marked `APPLIED` or `FAILED` using the API per-record `results[]`
- central queue, audit, conflict, status, retry, clear queue, reset sync state, clear completed logs, and force-release lock endpoints exist
- `/api/sync/pull` currently records/acknowledges a pull request but does not yet return a full grouped central snapshot
- assignment, distribution, return, user, department, and audit-log handlers for the generic central push endpoint remain future work

## Maintenance

Maintenance is recorded from:

```text
Equipment Inventory -> Maintenance Tracking
```

Tracked fields:

- asset code
- issue
- action taken
- performed by
- maintenance date
- cost
- status

Statuses:

- `OPEN`
- `COMPLETED`

Maintenance is also included in:

- Maintenance Report
- Asset History timeline
- local SQLite `maintenance_log`
- PostgreSQL `maintenance_log` schema support

Current implementation note: the desktop maintenance screen writes through the local maintenance storage. The central PostgreSQL schema now contains `maintenance_log` so API asset history can include central maintenance rows when they exist.

## Audit Logs

Audit logs capture key operational events and can be viewed by Admin and Super Admin users.

Centralized audit records are handled by the API in API modes. Local audit fallback exists for `AUTO` and `LOCAL_DATABASE` behavior.

## Data Maintenance

Data Maintenance is Super Admin only.

It is intended for controlled operational reset/maintenance tasks. Normal users and Admin users should not have access to this panel.

## Backup

Centralized production backup:

- PostgreSQL backup on the server
- use `pg_dump`, `pg_restore`, or the repository backup scripts

Desktop Backup & Restore:

- visible only in `LOCAL_DATABASE` mode
- Admin and Super Admin only
- not the official backup path for centralized production

## API

The API provides centralized endpoints for:

- authentication
- password reset
- users
- departments
- equipment
- assignments
- distributions
- returns
- dashboard
- reports
- asset history
- audit logs
- data maintenance
- health checks

Health endpoint:

```text
GET /actuator/health
```

Expected healthy response:

```json
{"status":"UP"}
```

## PostgreSQL Schema

Flyway migrations manage the PostgreSQL schema. Current central tables include:

- `departments`
- `users`
- `equipment`
- `assignments`
- `distribution`
- `returns`
- `audit_log`
- `maintenance_log`

The `maintenance_log` table was added so asset history can include maintenance activity in the central database.

## Packaging

Build desktop installers with:

```powershell
.\scripts\build-desktop.cmd
```

Generated outputs:

- `dist\MSR AMIS\MSR AMIS.exe`
- `dist\MSR AMIS-1.0.0.msi`
- `dist\MSR AMIS-1.0.0.exe`

Current rebuilt desktop installers: May 17, 2026.

The current build includes:

- centralized API/PostgreSQL support
- offline-capable `AUTO` mode
- Sync Center
- role-based Sync Center access
- active queue cleanup after successful push
- Department Management
- Maintenance Tracking
- Maintenance Report
- Asset History with maintenance events
- audit logs
- operational reports
- local currency formatting
- updated dashboard connection state

## Production Recommendation

Use `REMOTE_API` for normal centralized production.

Use `AUTO` only where offline work is intentionally allowed and administrators are trained to monitor Sync Center.

Use `LOCAL_DATABASE` only for development or controlled local-only fallback.
