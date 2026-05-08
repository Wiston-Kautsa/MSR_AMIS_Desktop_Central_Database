# MSR-AMIS Final Corrected Design

## 1. System Overview

MSR-AMIS is a JavaFX desktop application that operates with a local SQLite mirror, a centralized backend API, and a central PostgreSQL database.

Final architecture:

`JavaFX Desktop -> local SQLite mirror -> Spring Boot API -> PostgreSQL`

## 2. Design Principles

- Desktop handles UI only
- Backend handles security, business logic, and persistence
- PostgreSQL is the single source of truth
- SQLite is an offline mirror and queue store
- Production desktops should normally use `AUTO` mode

## 3. Roles

### `SUPER_ADMIN`

- full system authority
- can manage `SUPER_ADMIN`, `ADMIN`, and `USER`
- can view all users

### `ADMIN`

- can manage `ADMIN` and `USER`
- cannot create or manage `SUPER_ADMIN`

### `USER`

- no user-management authority
- operational access only according to module permissions

## 4. Protected Accounts

### Primary super admin

- `wkautsa@gmail.com`
- role: `SUPER_ADMIN`
- intended first use: password reset

This account is intentionally protected by backend rules.

### Default admin

- `admin@msr.local`
- username: `admin`
- role: `ADMIN`

This account is not the super admin.

### Default user

- `user@msr.local`
- username: `user`
- role: `USER`

The seeded admin and user accounts both use `admin123` until deliberately changed.

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

## 7. Desktop Responsibilities

The desktop is responsible for:

- forms
- tables
- navigation
- displaying feedback
- calling service abstractions

Controllers should not directly implement core backend rules.

## 8. Reporting and History

The centralized backend provides:

- dashboard summary
- inventory report
- assignment report
- distribution report
- return report
- outstanding report
- asset history per asset code

## 9. Audit and Backup

### Audit

- audit logging is available through backend endpoints in centralized mode

### Backup

- in centralized mode, backup must be treated as a server responsibility
- PostgreSQL backups are the official system backup
- SQLite can contain unsynced offline work from one desktop, but it is not the official database

## 10. Mode Policy

### `AUTO`

- recommended day-to-day mode
- uses SQLite as the local mirror
- uses the backend API and PostgreSQL when reachable
- queues offline work when unreachable
- refreshes SQLite from PostgreSQL after sync

### `REMOTE_API`

- strict online mode
- uses the backend API as the authority
- cannot continue if the API is unreachable

### `LOCAL_DATABASE`

- retained for development or controlled local-only scenarios

## 11. Production Direction

The correct production state is:

- one backend deployment
- one PostgreSQL database
- many desktop clients
- desktop clients configured in `AUTO` mode for offline continuity
- PostgreSQL wins when local offline changes conflict with central changes

## 12. Current Project Stage

The project is in stabilization and deployment phase.

The main remaining work is:

- deployment hardening
- centralized environment setup
- end-to-end operational validation
- operational validation of Sync Center workflows
