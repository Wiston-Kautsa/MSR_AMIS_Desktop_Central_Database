# MSR-AMIS Final Corrected Design

## 1. System Overview

MSR-AMIS is a JavaFX desktop application that operates against a centralized backend API and central PostgreSQL database.

Final architecture:

`JavaFX Desktop -> Spring Boot API -> PostgreSQL`

## 2. Design Principles

- Desktop handles UI only
- Backend handles security, business logic, and persistence
- Database is the single source of truth
- Production should use centralized API mode

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
- desktop backup synchronization actions are intentionally disabled in `REMOTE_API` mode

## 10. Mode Policy

### `REMOTE_API`

- intended production mode
- uses the backend API as the authority

### `LOCAL_DATABASE`

- retained only for development or controlled fallback scenarios
- not the target production model

## 11. Production Direction

The correct production state is:

- one backend deployment
- one PostgreSQL database
- many desktop clients
- no silent local split-brain behavior between users

## 12. Current Project Stage

The project is in stabilization and deployment phase.

The main remaining work is:

- deployment hardening
- centralized environment setup
- end-to-end operational validation
- cleanup of legacy local-only support where no longer needed
