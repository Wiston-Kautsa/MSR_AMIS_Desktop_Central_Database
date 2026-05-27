# MSR-AMIS API

This module is the centralized backend for MSR-AMIS.

Current target architecture:

`JavaFX Desktop -> Spring Boot API -> PostgreSQL`

The API and PostgreSQL run on the server. Desktop clients call the API directly in `REMOTE_API` mode. Deployments that intentionally enable `AUTO` mode can use a local SQLite mirror and queue while offline.

## What is included

- Spring Boot 3 backend
- PostgreSQL configuration
- Flyway schema migrations
- JWT-based authentication
- user, department, equipment, assignment, distribution, return, dashboard, report, audit, and asset history endpoints
- PostgreSQL maintenance-log schema support for asset history timelines
- PostgreSQL sync infrastructure schema for the backend sync contract
- lifecycle enforcement for users, equipment, and assignments
- audit logging

## Local run

```powershell
..\mvnw.cmd -f msr-amis-api\pom.xml spring-boot:run
```

The API reads the repo root `.env` file first when it exists. Matching values in `.env` override machine-level environment variables and Java system properties.

Main settings:

- `MSR_AMIS_DB_URL`
- `MSR_AMIS_DB_USERNAME`
- `MSR_AMIS_DB_PASSWORD`
- `MSR_AMIS_JWT_SECRET`
- `MSR_AMIS_API_PORT`
- `MSR_AMIS_JWT_EXPIRATION_SECONDS`
- `MSR_AMIS_EXPOSE_RESET_CODE_ON_EMAIL_FAILURE`
- `MSR_AMIS_SMTP_HOST`
- `MSR_AMIS_SMTP_PORT`
- `MSR_AMIS_SMTP_USERNAME`
- `MSR_AMIS_SMTP_PASSWORD`
- `MSR_AMIS_SMTP_FROM`
- `MSR_AMIS_SUPER_USER_STATUS_EMAILS_ENABLED`
- `MSR_AMIS_OPERATION_EMAILS_ENABLED`
- `MSR_AMIS_OPERATION_EMAIL_RECIPIENTS`

Start from the root [.env.example](../.env.example).

## Server deployment notes

On the server, configure:

```env
MSR_AMIS_API_PORT=8090
MSR_AMIS_DB_URL=jdbc:postgresql://localhost:5432/msr_amis
MSR_AMIS_DB_USERNAME=msr_amis_user
MSR_AMIS_DB_PASSWORD=your_password
MSR_AMIS_JWT_SECRET=replace_with_generated_base64_secret
MSR_AMIS_JWT_EXPIRATION_SECONDS=28800
MSR_AMIS_EXPOSE_RESET_CODE_ON_EMAIL_FAILURE=false
MSR_AMIS_SUPER_USER_STATUS_EMAILS_ENABLED=true
MSR_AMIS_OPERATION_EMAILS_ENABLED=false
MSR_AMIS_OPERATION_EMAIL_RECIPIENTS=admin@example.com
```

If PostgreSQL is hosted on another server:

```env
MSR_AMIS_DB_URL=jdbc:postgresql://POSTGRES_SERVER_IP:5432/msr_amis
```

Run the packaged API jar:

```powershell
java -jar msr-amis-api-0.0.1-SNAPSHOT.jar
```

Check health:

```powershell
Invoke-RestMethod http://localhost:8090/actuator/health
```

Client desktops should point to the server API URL, not `localhost`, unless the API is running on the same computer.

PostgreSQL should be reachable by the API server only. Client desktops should not connect directly to PostgreSQL.

If MIS reports `API not reachable`, confirm the health endpoint works on the server, then test `http://SERVER_IP_OR_NAME:8090/actuator/health` from a client computer. If local health works but client health fails, check the client `.env`, server firewall, and network path. See [Troubleshooting](../documentation/docs/troubleshooting.md).

## Endpoints

- `POST /api/auth/login`
- `POST /api/auth/setup/initial-admin`
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
- `POST /api/departments`
- `PUT /api/departments/{name}`
- `DELETE /api/departments/{name}`
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
- `GET /api/system/data-maintenance`
- `POST /api/system/data-maintenance/reset`
- `GET /actuator/health`

The sync implementation is documented in [Sync Backend Contract](../documentation/docs/sync-backend-contract.md) and [Sync Implementation Blueprint](../documentation/docs/sync-implementation-blueprint.md). The central sync schema is created by Flyway migrations `V22__add_sync_infrastructure.sql` and `V23__align_sync_table_names.sql`.

Current sync API status:

- `POST /api/sync/push` is active for `EQUIPMENT`, `ASSIGNMENT`, `DISTRIBUTION`, `RETURN`, `USER`, and `DEPARTMENT` records.
- Supported equipment operations are `CREATE`, `UPDATE`, `UPSERT`, `DELETE`, and `STATUS`.
- Supported assignment and user operations are `CREATE`, `UPDATE`, `STATUS`, and `DELETE`.
- Supported department operations are `CREATE`, `UPDATE`, and `DELETE`.
- Distribution and return records are applied through batch operation payloads.
- Push responses include a `results[]` array with one result per queue item.
- `GET /api/sync/queue`, `GET /api/sync/status`, `GET /api/sync/audit`, `GET /api/sync/conflicts`, and `POST /api/sync/retry` are available.
- Super Admin sync maintenance endpoints are available for clearing completed logs, clearing the queue, resetting sync state, and force-releasing locks.
- `GET /api/sync/pull` currently acknowledges and audits pull requests but does not yet return full central entity snapshots.
- Audit-log generic push records are not part of the current central apply path.

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
- Asset history includes registration, issue/distribution, maintenance, maintenance-completed, and return events.
- Audit logging can be written to and read from the backend in `REMOTE_API` mode.
- Super Admin users can review and reset controlled operational data from the data maintenance endpoints.
- Departments support list, create, rename, and delete endpoints.
- `MSR` is the default department and cannot be renamed or deleted.
- Departments still used by users or assignments cannot be deleted.
- PostgreSQL includes `maintenance_log` so central asset history can include maintenance rows when present.

## Notes

- The API is intended to sit behind the desktop client as the system authority.
- Desktop clients should not connect directly to PostgreSQL in production.
- Offline desktop work is uploaded later through Sync Center. The API remains the only write path to PostgreSQL.
