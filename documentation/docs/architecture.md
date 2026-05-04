# MSR-AMIS Architecture

## Current Architecture

The current production architecture is:

`Desktop Client -> REST API -> PostgreSQL`

This means:

- the desktop does not connect directly to PostgreSQL
- the API is the enforcement point for security and business rules
- PostgreSQL is the central source of truth

## Component Responsibilities

### Desktop

- JavaFX screens and navigation
- form input and feedback
- session token usage
- API consumption through service abstractions
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

### `REMOTE_API`

- intended production mode
- desktop talks to API
- API talks to PostgreSQL
- users should rely on this mode for shared operation

### `LOCAL_DATABASE`

- legacy/development mode
- desktop uses local SQLite-backed services
- useful for controlled local work, but not the intended shared production model

## Connectivity

Because the production system depends on the API and central PostgreSQL, network availability matters.

The desktop now shows:

- `ONLINE` when the API health endpoint is reachable
- `OFFLINE` when the central server is unreachable
- `LOCAL DATABASE` when the app is running in local mode

This is important because users need to know whether they are working against the central system.

## Backup and Sync

Current status:

- the project has a manual desktop backup/submission workflow for local database scenarios
- it does not yet have a real automated offline sync engine

Still missing if true offline-central sync is needed later:

- record-level change queue
- `PENDING_SYNC` / `SYNCED` tracking
- conflict detection and resolution rules
- background or manual push/pull synchronization

## Recommended Direction

The current recommended production direction remains:

- keep PostgreSQL behind the API
- keep desktop clients on API access only
- use `.env` in development
- use real environment variables in deployment
