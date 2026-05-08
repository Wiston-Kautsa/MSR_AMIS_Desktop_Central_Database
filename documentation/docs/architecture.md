# MSR-AMIS Architecture

## Current Architecture

The current production architecture is:

`Desktop Client -> local SQLite mirror -> REST API -> PostgreSQL`

This means:

- the desktop does not connect directly to PostgreSQL
- the desktop keeps a local SQLite mirror for offline continuity
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

### API

- authentication and password reset
- role-based access control
- validation
- lifecycle rules for users, equipment, and assignments
- reporting and dashboard aggregation
- audit logging

### Database

- centralized persistence
- transactional consistency
- shared multi-user data access

## Operating Modes

### `AUTO`

- recommended day-to-day desktop mode
- desktop uses local SQLite as the working mirror
- when the API is reachable, changes are sent to PostgreSQL through the API
- when the API is unreachable, changes are saved locally and queued
- when the API returns, Sync Center replays valid queued changes and refreshes SQLite from PostgreSQL

### `REMOTE_API`

- strict online mode
- desktop talks to API directly
- API talks to PostgreSQL
- the desktop cannot continue if the API is unreachable

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

## Backup and Sync

Current behavior:

- PostgreSQL is the primary database and authoritative state.
- SQLite is overwritten from PostgreSQL during mirror refresh.
- Offline creates, updates, deletes, status changes, distributions, and returns are captured in a local sync queue.
- Sync Center replays queued changes through the API when a central session is available.
- If PostgreSQL has changed the same record while the desktop was offline, the queued local change is rejected instead of overwriting central data.
- API validation and business-rule failures are marked as rejected.
- After sync, SQLite is refreshed from PostgreSQL so the local database status matches the central state.

Important limits:

- A user must have logged in online on that computer before they can authenticate offline from the SQLite mirror.
- A valid central session is required before pending offline actions can be uploaded.
- Rejected queue items must be reviewed in Sync Center and corrected manually if needed.

## Recommended Direction

The current recommended production direction remains:

- keep PostgreSQL behind the API
- run desktop clients in `AUTO` mode for offline resilience
- use `.env` in development
- use real environment variables in deployment
