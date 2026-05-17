# MSR-AMIS Architecture

## Current Architecture

The centralized architecture is:

`Desktop Client -> local SQLite mirror -> REST API -> PostgreSQL`

This means:

- the desktop does not connect directly to PostgreSQL
- the desktop can keep a local SQLite mirror for offline continuity when `AUTO` mode is enabled
- the API is the enforcement point for security and business rules
- PostgreSQL is the central source of truth

## Component Responsibilities

### Desktop

- JavaFX screens and navigation
- form input and feedback
- session token usage
- API consumption through service abstractions
- local SQLite mirror for offline use
- offline change queue and Sync Center
- connection-state display for the user
- maintenance tracking and local maintenance report support
- local Backup & Restore only when running in `LOCAL_DATABASE` mode

### API

- authentication and password reset
- role-based access control
- validation
- lifecycle rules for users, equipment, and assignments
- reporting and dashboard aggregation
- audit logging
- asset history that includes registration, issue, maintenance, and return events

### Database

- centralized persistence
- transactional consistency
- shared multi-user data access
- official backup source in centralized deployment

## Operating Modes

### `AUTO`

- offline-capable mode for deployments that intentionally allow local fallback
- desktop uses local SQLite as the working mirror
- when the API is reachable, changes are sent to PostgreSQL through the API
- when the API is unreachable, changes are saved locally and queued
- when the API returns, Sync Center replays valid queued changes and refreshes SQLite from PostgreSQL

### `REMOTE_API`

- strict online mode
- desktop talks to API directly
- API talks to PostgreSQL
- the desktop cannot continue if the API is unreachable
- safest production default when offline work is not required

### `LOCAL_DATABASE`

- local-only mode
- desktop uses local SQLite-backed services
- useful for development or controlled fallback work

## Connectivity

Because PostgreSQL is central but the desktop can use SQLite in `AUTO` mode, users must understand the connection state.

The desktop now shows:

- `ONLINE (AUTO)` when the API health endpoint is reachable and SQLite is being kept as the local mirror
- `OFFLINE (AUTO)` when the API is unreachable and changes are being kept locally
- `ONLINE` when strict `REMOTE_API` mode is connected
- `OFFLINE` when strict `REMOTE_API` mode cannot reach the central server
- `LOCAL DATABASE` when the app is running in local mode

This is important because offline work must be synchronized later.

If users report `API not reachable`, first verify the API health endpoint on the server, then test the server URL from a client computer and confirm the client `.env` uses the server IP or DNS name instead of `localhost`. See [Troubleshooting](troubleshooting.md).

## Backup and Sync

Current behavior:

- PostgreSQL is the primary database and authoritative state.
- SQLite is overwritten from PostgreSQL during mirror refresh.
- Offline creates, updates, deletes, status changes, distributions, and returns are captured in a local sync queue.
- Sync Center replays queued changes through the API when a central session is available.
- Sync Center is exposed in the desktop navigation at `Data & Records -> Sync Center`.
- Sync Center is role-based: Super Admin sees and processes all records; Admin sees and processes only their own records; User accounts do not see the panel.
- Applied queue records are removed from the active queue list after push and remain available through audit/history.
- If PostgreSQL has changed the same record while the desktop was offline, the queued local change is rejected instead of overwriting central data.
- API validation and business-rule failures are marked as rejected.
- After sync, SQLite is refreshed from PostgreSQL so the local database status matches the central state.

Important limits:

- A user must have logged in online on that computer before they can authenticate offline from the SQLite mirror.
- A valid central session is required before pending offline actions can be uploaded.
- Rejected queue items must be reviewed in Sync Center and corrected manually if needed.
- Desktop Backup & Restore is not the official centralized backup path.

## Maintenance and Asset History

Maintenance is recorded from `Equipment Inventory -> Maintenance Tracking`.

Current behavior:

- maintenance records store asset code, issue, action taken, performed by, maintenance date, cost, and status
- open maintenance sets the equipment status to `MAINTENANCE`
- completed maintenance returns the equipment status to `AVAILABLE`
- Maintenance Report can filter and export maintenance records
- Asset History includes maintenance and maintenance-completed events alongside registration, issue, and return events
- PostgreSQL has a `maintenance_log` table so central asset history can include maintenance rows when present

## Department Handling

Departments are database-backed names managed from the desktop under `Administration -> Departments`.

Current behavior:

- `MSR` is the default department.
- `SUPER_ADMIN` and `ADMIN` can create departments.
- `SUPER_ADMIN` and `ADMIN` can rename departments.
- Renaming a department updates matching user and assignment records.
- Departments that are still used by users or assignments cannot be deleted.
- The default `MSR` department cannot be renamed or deleted.
- Offline department changes in `AUTO` mode are captured in the sync queue and replayed through Sync Center.

## Recommended Direction

Recommended production direction:

- use `REMOTE_API` for the safest centralized rollout where all work must be immediately written through the API
- use `AUTO` only where the organization accepts offline work, Sync Center review, and manual handling of rejected queued changes
- keep PostgreSQL private to the API server in both modes
- use `.env` in development
- use real environment variables in deployment
- use the troubleshooting checklist whenever API reachability fails
