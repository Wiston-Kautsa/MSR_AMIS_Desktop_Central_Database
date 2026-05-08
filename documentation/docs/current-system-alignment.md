# MSR-AMIS Current System Alignment

## Purpose

This document corrects earlier draft documentation and states how the current system actually behaves.

## Confirmed Architecture

The implemented architecture is:

`Desktop Client -> local SQLite mirror -> REST API -> Central PostgreSQL Database`

The desktop is not the source of truth. PostgreSQL is the source of truth, and the backend API controls access to it.

## Production Mode Reality

- `AUTO` is the intended day-to-day desktop mode
- `REMOTE_API` remains available as strict online mode
- missing mode configuration no longer silently drops the app back to local SQLite assumptions
- the login screen in API mode no longer presents the local setup account as the normal production path

## Account and Role Reality

### Protected super admin

- email: `wkautsa@gmail.com`
- role: `SUPER_ADMIN`
- intended first use: password reset
- this account is deliberately protected by backend policy

### Default admin

- email: `admin@msr.local`
- username: `admin`
- role: `ADMIN`

This is not the production super admin account.

### Default user

- email: `user@msr.local`
- username: `user`
- role: `USER`

## Important Correction to Older Drafts

Older drafts described `admin@msr.local / admin123` as a one-time setup-only account that must always be disabled immediately after initialization.

That is not the current implemented backend policy.

The current implemented policy is:

- `wkautsa@gmail.com` remains the protected primary `SUPER_ADMIN`
- `admin@msr.local` is retained as a normal seeded `ADMIN`
- `user@msr.local` is retained as a seeded `USER`

If the project later decides to remove the seeded admin, that should be done as a deliberate backend policy change, not assumed from older drafts.

## Backend Ownership

The backend now owns:

- authentication
- password reset
- role filtering
- user creation and status control
- equipment management
- assignments
- distributions
- returns
- dashboard aggregation
- report generation
- asset history retrieval
- audit logging

## Desktop Responsibility

The desktop now primarily handles:

- UI rendering
- user input
- calling service abstractions
- session token usage
- displaying results from the backend

In `AUTO` mode, controllers use service abstractions that can work against the local SQLite mirror and route remote work through the API when a central session is available.

## Current Exceptions and Legacy Support

Local implementations exist for `AUTO` and `LOCAL_DATABASE` behavior:

- local auth
- local equipment
- local users
- local assignments
- local distribution
- local returns
- local dashboard
- local reports
- local asset history
- local audit fallback

In `AUTO` mode these paths provide the local mirror and offline queue behavior. In `LOCAL_DATABASE` mode they are local-only.

## Backup and Sync Correction

In strict `REMOTE_API` mode:

- desktop backup/restore/publish actions are intentionally disabled
- centralized data should be backed up on the server side

In `AUTO` mode:

- offline changes are stored in SQLite and queued
- Sync Center uploads valid queued changes through the API
- rejected changes remain visible for review
- SQLite is refreshed from PostgreSQL after sync

Older desktop-centric backup assumptions should not be treated as correct for centralized deployment. PostgreSQL server backups remain the official backup path.

## Authentication Correction

The backend login path was corrected so both of these now authenticate correctly against the live API:

- `admin@msr.local`
- `wkautsa@gmail.com`

## Current Operational State

At the time of this document:

- PostgreSQL is running locally
- the backend API is running successfully
- desktop login works against the centralized backend
- desktop `AUTO` mode supports local SQLite fallback when the API is unreachable
- Sync Center is responsible for replaying offline changes and refreshing SQLite from PostgreSQL

## Conclusion

The current system is no longer accurately described as an unfinished API migration prototype.

It is now better described as:

- a centralized desktop client system
- with a working Spring Boot backend
- backed by PostgreSQL
- in stabilization and deployment phase
