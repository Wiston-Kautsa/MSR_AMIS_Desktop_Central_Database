# MSR-AMIS Current System Alignment

## Purpose

This document corrects earlier draft documentation and states how the current system actually behaves.

## Confirmed Architecture

The implemented architecture is:

`Desktop Client -> REST API -> Central PostgreSQL Database`

The desktop is not the source of truth in production mode. The backend is.

## Production Mode Reality

- `REMOTE_API` is the intended production mode
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

Main controllers no longer directly query SQLite for the primary centralized flows.

## Current Exceptions and Legacy Support

Some local implementations still exist for `LOCAL_DATABASE` mode:

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

These are development or fallback paths, not the intended production model.

## Backup and Sync Correction

In `REMOTE_API` mode:

- desktop backup/restore/publish actions are intentionally disabled
- centralized data should be backed up on the server side

Older desktop-centric backup assumptions should not be treated as correct for centralized deployment.

## Authentication Correction

The backend login path was corrected so both of these now authenticate correctly against the live API:

- `admin@msr.local`
- `wkautsa@gmail.com`

## Current Operational State

At the time of this document:

- PostgreSQL is running locally
- the backend API is running successfully
- desktop login works against the centralized backend

## Conclusion

The current system is no longer accurately described as an unfinished API migration prototype.

It is now better described as:

- a centralized desktop client system
- with a working Spring Boot backend
- backed by PostgreSQL
- in stabilization and deployment phase
