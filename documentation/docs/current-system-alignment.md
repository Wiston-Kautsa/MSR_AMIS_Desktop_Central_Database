# MSR-AMIS Current System Alignment

## Purpose

This document corrects earlier draft documentation and states how the current system actually behaves.

## Confirmed Architecture

The implemented architecture is:

`Desktop Client -> local SQLite mirror -> REST API -> Central PostgreSQL Database`

The desktop is not the source of truth. PostgreSQL is the source of truth, and the backend API controls access to it.

## Production Mode Reality

- `REMOTE_API` is the safest centralized production mode because users cannot continue unless the API is reachable
- `AUTO` remains available for deployments that intentionally allow offline work and Sync Center recovery
- missing mode configuration no longer silently drops the app back to local SQLite assumptions
- the login screen in API mode no longer presents the local setup account as the normal production path

## Account and Role Reality

Account email identities are configuration, not hardcoded policy. Production should set them with real server environment variables. Development can set them in the root `.env` file.

### Protected super admin

- email: configured by `MSR_AMIS_PRIMARY_SUPER_ADMIN_EMAIL`
- role: `SUPER_ADMIN`
- intended first use: password reset
- this account is deliberately protected by backend policy

### Default admin

- email: configured by `MSR_AMIS_SETUP_ADMIN_EMAIL`
- username: `admin`
- role: `ADMIN`

This is not the production super admin account.

### Default user

- email: configured by `MSR_AMIS_SETUP_USER_EMAIL`
- username: `user`
- role: `USER`

## Important Correction to Older Drafts

Older drafts described the setup admin account and its default password as a one-time setup-only account that must always be disabled immediately after initialization.

That wording is incomplete for the current implementation.

The current implemented policy is:

- the primary `SUPER_ADMIN` email is configured outside code
- setup admin and user emails are configured outside code
- reserved role emails are configured with comma-separated environment values
- account email settings can be changed in `.env` for development or server environment variables for production
- changing these settings requires an API restart

If the project later changes the setup account lifecycle, that should be done as a deliberate backend policy change, not assumed from older drafts.

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
- local maintenance tracking and maintenance report
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
- Sync Center is available in the desktop sidebar under `Data & Records`
- Sync Center is hidden from User accounts, scoped to the current Admin for Admin users, and fully available to Super Admin users
- applied queue records are removed from the active queue list after successful push

If the API is not reachable, the operational response is documented in [Troubleshooting](troubleshooting.md). The main checks are API health on the server, PostgreSQL service status, client `.env` server URL, and firewall access to port `8090`.

Older desktop-centric backup assumptions should not be treated as correct for centralized deployment. PostgreSQL server backups remain the official backup path.

## Department Management Reality

Departments are now a managed feature.

Implemented:

- `GET /api/departments`
- `POST /api/departments`
- `PUT /api/departments/{name}`
- `DELETE /api/departments/{name}`
- `Administration -> Departments` desktop screen
- department selection or typed entry in user and assignment forms
- department values stored on users and assignments
- `MSR` as the default department

Rules:

- `SUPER_ADMIN` and `ADMIN` can manage departments.
- The default `MSR` department cannot be renamed or deleted.
- A department that is still used by users or assignments cannot be deleted.
- Renaming a department updates matching users and assignments.
- Offline department changes in `AUTO` mode are queued and processed by Sync Center.

## Authentication Correction

The backend login path was corrected so both of these now authenticate correctly against the live API:

- the configured setup admin email
- the configured primary super admin email

## Current Operational State

At the time of this document:

- PostgreSQL is running locally
- the backend API is running successfully
- desktop login works against the centralized backend
- desktop `REMOTE_API` mode supports strict centralized operation
- desktop `AUTO` mode supports local SQLite fallback when the API is unreachable
- Sync Center is responsible for replaying offline changes and refreshing SQLite from PostgreSQL
- the rebuilt desktop package includes the Sync Center sidebar entry
- the desktop includes Department Management under `Administration -> Departments`
- the desktop includes Maintenance Tracking and Maintenance Report
- Asset History includes maintenance events as part of the asset timeline and checks local `maintenance_log` for selected assets so completed maintenance remains visible
- PostgreSQL includes `maintenance_log` schema support
- outstanding return reasons are preserved in `distribution.outstanding_remarks`
- exports are centralized under `Downloads\MSR-AMIS`
- the May 18, 2026 desktop installer includes updated bulk enrolment templates, complete table column headers and wider report columns, equipment metadata columns, maintenance tracking/reporting, Asset History maintenance events and direct maintenance-log fallback, role-based Sync Center access, preserved outstanding return reasons, centralized exports, and Department Management

## Conclusion

The current system is no longer accurately described as an unfinished API migration prototype.

It is now better described as:

- a centralized desktop client system
- with a working Spring Boot backend
- backed by PostgreSQL
- in stabilization and deployment phase

