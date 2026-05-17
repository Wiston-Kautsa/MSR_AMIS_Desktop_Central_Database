# MSR-AMIS Final Corrected Design

## 1. System Overview

MSR-AMIS is a JavaFX desktop application that operates with a local SQLite mirror, a centralized backend API, and a central PostgreSQL database.

Final architecture:

`JavaFX Desktop -> local SQLite mirror -> Spring Boot API -> PostgreSQL`

## 2. Design Principles

- Desktop handles UI only
- Backend handles security, business logic, and persistence
- PostgreSQL is the single source of truth
- SQLite is an offline mirror and queue store when `AUTO` mode is enabled
- Production desktops should normally use `REMOTE_API` unless offline work is an explicit operational requirement
- Desktop Backup & Restore is local-mode only; PostgreSQL server backup is the production backup path

## 3. Roles

### `SUPER_ADMIN`

- full system authority
- can manage `SUPER_ADMIN`, `ADMIN`, and `USER`
- can view all users
- can use full Sync Center, including all queue records and rejected-item retry
- can access Data Maintenance

### `ADMIN`

- can manage `ADMIN` and `USER`
- cannot create or manage `SUPER_ADMIN`
- can use Sync Center only for their own queue and audit records
- cannot retry rejected sync records

### `USER`

- no user-management authority
- operational access only according to module permissions
- no Sync Center access
- no Data Maintenance access

## 4. Protected Accounts

Protected and setup account emails are configurable. In production they should be set as server environment variables. In development they may be set in the root `.env` file.

### Primary super admin

- configured by `MSR_AMIS_PRIMARY_SUPER_ADMIN_EMAIL`
- role: `SUPER_ADMIN`
- intended first use: password reset

This account is intentionally protected by backend rules.

### Default admin

- configured by `MSR_AMIS_SETUP_ADMIN_EMAIL`
- username: `admin`
- role: `ADMIN`

This account is not the super admin.

### Default user

- configured by `MSR_AMIS_SETUP_USER_EMAIL`
- username: `user`
- role: `USER`

The seeded admin and user accounts both use `admin123` until deliberately changed.

Reserved account emails are configured separately with comma-separated lists:

- `MSR_AMIS_RESERVED_SUPER_ADMIN_EMAILS`
- `MSR_AMIS_RESERVED_ADMIN_EMAILS`
- `MSR_AMIS_RESERVED_USER_EMAILS`

Adding or removing an email from those lists changes whether the system treats that address as reserved after the API is restarted.

## 5. Authentication

### Login flow

1. User enters email or username and password in the desktop app
2. Desktop sends the login request to the API
3. API validates credentials against PostgreSQL
4. API returns a token and user profile
5. Desktop stores the token for the active session

### Password reset

- password reset is owned by the backend
- desktop does not own reset persistence logic in centralized mode

## 6. Backend Responsibilities

The backend is responsible for:

- auth
- authorization
- role enforcement
- validation
- business rules
- persistence
- dashboards
- reports
- asset history
- audit logs
- department management

## 7. Desktop Responsibilities

The desktop is responsible for:

- forms
- tables
- navigation
- displaying feedback
- calling service abstractions
- Sync Center interaction after offline work
- department management from the Administration section

Controllers should not directly implement core backend rules.

## 8. Reporting and History

The centralized backend provides:

- dashboard summary
- inventory report
- assignment report
- distribution report
- return report
- outstanding report
- maintenance report
- asset history per asset code

Asset history includes registration, issue/distribution, maintenance, maintenance-completed, and return events.

## 9. Audit and Backup

### Audit

- audit logging is available through backend endpoints in centralized mode

### Backup

- in centralized mode, backup must be treated as a server responsibility
- PostgreSQL backups are the official system backup
- SQLite can contain unsynced offline work from one desktop, but it is not the official database
- desktop Backup & Restore is available only in `LOCAL_DATABASE` mode

## 10. Mode Policy

### `AUTO`

- offline-capable mode
- uses SQLite as the local mirror
- uses the backend API and PostgreSQL when reachable
- queues offline work when unreachable
- refreshes SQLite from PostgreSQL after sync through `Data & Records -> Sync Center`
- Sync Center is role-based: Super Admin has full access, Admin has own-record access, User has no access
- applied queue records are removed from the active queue list after successful push
- API reachability problems are handled with the operational checklist in [Troubleshooting](troubleshooting.md)

### `REMOTE_API`

- strict online mode
- uses the backend API as the authority
- cannot continue if the API is unreachable
- safest default for centralized production

### `LOCAL_DATABASE`

- retained for development or controlled local-only scenarios

## 11. Production Direction

The correct production state is:

- one backend deployment
- one PostgreSQL database
- many desktop clients
- desktop clients configured in `REMOTE_API` for strict centralized operation
- `AUTO` enabled only where offline continuity is required and Sync Center review is operationally owned
- PostgreSQL wins when local offline changes conflict with central changes

## 12. Current Project Stage

The project is in stabilization and deployment phase.

The main remaining work is:

- deployment hardening
- centralized environment setup
- end-to-end operational validation
- operational validation of Sync Center workflows
- operational validation of Asset History including maintenance events
- operational validation of department management in online and offline `AUTO` mode
- operational validation of the API-not-reachable recovery checklist
