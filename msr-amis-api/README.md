# MSR-AMIS API

This module is the centralized backend for MSR-AMIS.

Current target architecture:

`JavaFX Desktop -> Spring Boot API -> PostgreSQL`

## What is included

- Spring Boot 3 backend
- PostgreSQL configuration
- Flyway schema migrations
- JWT-based authentication
- user, equipment, assignment, distribution, return, dashboard, report, audit, and asset history endpoints
- lifecycle enforcement for users, equipment, and assignments
- audit logging

## Local run

```powershell
..\mvnw.cmd -f msr-amis-api\pom.xml spring-boot:run
```

The API reads configuration from environment variables and can also import the repo root `.env` file during development.

Main settings:

- `MSR_AMIS_DB_URL`
- `MSR_AMIS_DB_USERNAME`
- `MSR_AMIS_DB_PASSWORD`
- `MSR_AMIS_JWT_SECRET`
- `MSR_AMIS_API_PORT`
- `MSR_AMIS_JWT_EXPIRATION_SECONDS`

Start from the root [.env.example](/D:/School/JAVA/MSR-AMIS_destop_application/.env.example).

## Endpoints

- `POST /api/auth/login`
- `GET /api/auth/me`
- `POST /api/auth/password-reset/request`
- `POST /api/auth/password-reset/confirm`
- `POST /api/auth/initial-password/change`
- `POST /api/auth/bootstrap-admin/complete`
- `GET /api/equipment`
- `GET /api/equipment/categories`
- `GET /api/equipment/{assetCode}`
- `POST /api/equipment`
- `PUT /api/equipment/{assetCode}`
- `PATCH /api/equipment/{assetCode}/status`
- `DELETE /api/equipment/{assetCode}`
- `GET /api/departments`
- `GET /api/users`
- `POST /api/users`
- `PUT /api/users/{userId}`
- `PATCH /api/users/{userId}/status`
- `DELETE /api/users/{userId}`
- `GET /api/assignments`
- `GET /api/assignments/pending-returns`
- `GET /api/assignments/available-stock`
- `GET /api/assignments/{id}/distributed-count`
- `GET /api/assignments/{id}/outstanding-assets`
- `POST /api/assignments`
- `PUT /api/assignments/{id}`
- `PATCH /api/assignments/{id}/status`
- `DELETE /api/assignments/{id}`
- `GET /api/distributions/current`
- `GET /api/distributions/available-equipment`
- `GET /api/distributions/asset/{assetCode}`
- `POST /api/distributions/batch`
- `GET /api/returns`
- `POST /api/returns/complete`
- `GET /api/dashboard/summary`
- `GET /api/reports/inventory`
- `GET /api/reports/assignments`
- `GET /api/reports/distributions`
- `GET /api/reports/returns`
- `GET /api/reports/outstanding`
- `GET /api/assets/{assetCode}/history`
- `GET /api/audit-logs`
- `POST /api/audit-logs`
- `GET /actuator/health`

## Role and Access Policy

- The backend enforces role-based access control.
- A protected primary `SUPER_ADMIN` account exists by policy.
- `ADMIN` users cannot manage `SUPER_ADMIN` users.
- Delete operations are more restricted than freeze/retire operations.
- The last active `SUPER_ADMIN` cannot be frozen or deleted.

## Users API behavior

- `GET /api/users` returns filtered results based on the caller role.
- `SUPER_ADMIN` sees `SUPER_ADMIN`, `ADMIN`, and `USER`.
- `ADMIN` sees only `ADMIN` and `USER`.
- `USER` sees only `USER`.
- Only the primary super admin can assign the `SUPER_ADMIN` role.
- `ADMIN` cannot manage any `SUPER_ADMIN`.
- The primary super admin cannot be deleted, frozen, or demoted.

## Operational API behavior

- Assignments include `distributedCount` and derived status from the backend.
- Distribution validates category matching and blocks already assigned assets.
- Retired equipment cannot be assigned.
- Frozen assignments cannot be changed.
- Retired assignments are closed.
- Returns support batch save, outstanding remarks, and replacement asset creation.
- Dashboard and report data come from centralized backend queries.
- Asset history is available from the backend per asset code.
- Audit logging can be written to and read from the backend in `REMOTE_API` mode.

## Notes

- The API is intended to sit behind the desktop client as the system authority.
- Desktop clients should not connect directly to PostgreSQL in production.
- If true offline synchronization is needed later, that will require additional sync-engine work beyond the current centralized API design.
