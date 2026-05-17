# Sync Center Implementation Blueprint

## 1. Final Sync Architecture

The final MSR-AMIS sync architecture is:

```text
Desktop App -> API Server -> PostgreSQL
```

Rules:

- desktop clients must never connect directly to production PostgreSQL
- all central writes must go through authenticated API endpoints
- PostgreSQL is the central source of truth
- SQLite remains a local mirror and offline queue store only
- Sync Center is a monitor/controller, not the business engine

Trigger model:

```text
Hybrid
```

- background auto sync for normal operation
- manual Admin/Super Admin sync tools for recovery and governance

Sync pattern:

```text
Bi-directional with controlled conflict resolution
```

- push local queued changes to the API
- pull central state back to the desktop
- do not overwrite conflicts silently

## 2. Sync Database Tables

Implemented central schema migrations:

- `V22__add_sync_infrastructure.sql`
- `V23__align_sync_table_names.sql`

Minimum required central tables:

```text
sync_queue
sync_sessions
sync_audit_logs
sync_conflicts
sync_locks
sync_settings
```

Additional hardening tables:

```text
sync_quarantine
sync_checkpoints
```

### `sync_queue`

Stores individual pending or processed sync records.

Core columns:

- `id`
- `sync_session_id`
- `entity_type`
- `entity_id`
- `operation`
- `payload_json`
- `baseline_json`
- `status`
- `retry_count`
- `created_at`
- `updated_at`
- `created_by`
- `machine_id`
- `idempotency_key`
- `error_message`
- `checksum`

Key rules:

- `idempotency_key` is unique
- status must be one of `PENDING`, `PROCESSING`, `APPLIED`, `FAILED`, `REJECTED`, `CONFLICT`, `QUARANTINED`
- operation must be one of `CREATE`, `UPDATE`, `DELETE`, `STATUS`, `MERGE`, `RESTORE`

### `sync_sessions`

Tracks each sync run.

Core columns:

- `id`
- `session_token`
- `started_by`
- `machine_id`
- `machine_name`
- `status`
- `started_at`
- `ended_at`

### `sync_audit_logs`

Stores sync audit events.

Core columns:

- `id`
- `sync_session_id`
- `user_id`
- `machine_id`
- `action`
- `entity_type`
- `record_count`
- `status`
- `error_message`
- `duration_ms`
- `created_at`

### `sync_conflicts`

Stores local and central payload snapshots when records conflict.

Core columns:

- `id`
- `sync_queue_id`
- `entity_type`
- `entity_id`
- `local_payload`
- `central_payload`
- `conflict_type`
- `resolution_status`
- `resolution_action`
- `resolved_by`
- `resolved_at`
- `created_at`

### `sync_locks`

Prevents concurrent sync execution.

Core columns:

- `id`
- `locked_by`
- `machine_id`
- `session_token`
- `started_at`
- `expires_at`
- `status`

Rules:

- only one `ACTIVE` lock can exist
- expired locks may be replaced by the backend
- Super Admin can force-release

### `sync_settings`

Stores server-side sync configuration.

Default keys:

- `sync.mode`
- `sync.strategy`
- `sync.batch_size`
- `sync.max_retries`
- `sync.poll_interval_seconds`
- `sync.lock_ttl_seconds`
- `sync.retention_days`

## 3. Java Service Classes

Create these backend service classes under:

```text
msr-amis-api/src/main/java/com/mycompany/msr/amis/api/service
```

### `SyncService`

Main orchestration service.

Responsibilities:

- handle push
- handle pull
- coordinate validation
- coordinate lock acquisition/release
- process queue in dependency order
- create sessions
- write audit logs
- return sync summaries

### `SyncQueueService`

Queue persistence and queue operations.

Responsibilities:

- insert queued records
- enforce idempotency key behavior
- update queue status
- load queue by scope
- count pending/failed/conflict/quarantined records

### `SyncAuditService`

Central sync audit writer/reader.

Responsibilities:

- write sync audit rows
- expose filtered audit history
- include user, machine, entity, action, count, result, duration, and error

### `SyncConflictService`

Conflict lifecycle.

Responsibilities:

- create conflict records
- store local and central snapshots
- list open conflicts
- resolve conflicts with `KEEP_LOCAL`, `KEEP_CENTRAL`, or `MERGE`
- enforce role permissions

### `SyncLockService`

Concurrency protection.

Responsibilities:

- acquire lock
- release lock
- detect expired lock
- force-release lock for Super Admin
- block simultaneous push/resync operations

### `SyncValidationService`

Pre-sync validation and business rules.

Responsibilities:

- duplicate asset codes
- duplicate serial numbers
- mandatory fields
- malformed phone/NID values
- orphan assignments
- returns without distribution
- stock/quantity checks
- return ownership checks
- quarantine invalid records where appropriate

## 4. Backend API Endpoints

Create:

```text
msr-amis-api/src/main/java/com/mycompany/msr/amis/api/controller/SyncController.java
```

Required endpoints:

```text
POST /api/sync/push
GET  /api/sync/pull
GET  /api/sync/status
GET  /api/sync/queue
POST /api/sync/retry
GET  /api/sync/conflicts
POST /api/sync/conflicts/resolve
GET  /api/sync/audit
POST /api/sync/queue/clear-completed
POST /api/sync/queue/clear
POST /api/sync/reset
POST /api/sync/lock/force-release
```

### `POST /api/sync/push`

Must:

- authenticate user
- authorize Admin/Super Admin
- validate payload checksum
- enforce idempotency key
- acquire sync lock
- validate records
- process records in dependency order
- apply transaction boundary per batch
- create conflicts instead of unsafe overwrite
- quarantine invalid records
- write audit logs

Current implemented scope:

- `EQUIPMENT` records only in the generic central push apply engine
- supported equipment operations: `CREATE`, `UPDATE`, `UPSERT`, `DELETE`, `STATUS`
- idempotency is enforced through `sync_queue.idempotency_key`
- the response includes `results[]`, one item per pushed queue record
- JavaFX API mode now reads local SQLite `sync_queue`, sends pending/failed equipment rows to this endpoint, then marks local rows `APPLIED` or `FAILED` based on `results[]`

Not implemented yet in this generic endpoint:

- `ASSIGNMENT`
- `DISTRIBUTION`
- `RETURN`
- `USER`
- `DEPARTMENT`
- `AUDIT`

### `GET /api/sync/pull`

Must:

- return central records by entity scope
- include API version
- include schema version
- include server time
- include compatibility status

Current implemented scope:

- records a pull audit event
- returns a success message for the desktop
- does not yet return grouped central entity snapshots

Next implementation step:

- return central equipment snapshot first, then assignments, distributions, returns, users, departments, and audit logs.

### `GET /api/sync/status`

Must return:

- queue counts
- conflict counts
- lock state
- API version
- schema version
- server time
- maintenance mode
- last successful sync
- last failed sync

### `GET /api/sync/queue`

Current implemented scope:

- returns central queue rows from `sync_queue`
- supports optional `entityType` and `status` filters
- used by JavaFX Sync Center when no local queue rows exist in remote API mode

### `POST /api/sync/retry`

Must:

- retry failed records
- Admin limited to own records
- Super Admin can retry all
- increment retry count
- respect max retry setting

### `GET /api/sync/conflicts`

Must:

- list unresolved conflicts
- support filters by entity, user, machine, status, date range

### `POST /api/sync/conflicts/resolve`

Must:

- support `KEEP_LOCAL`
- support `KEEP_CENTRAL`
- support `MERGE`
- write audit log
- enforce role rules

### `GET /api/sync/audit`

Must:

- return detailed sync audit logs
- support filters by session, user, machine, entity, status, date range

## 5. Entity Sync Order

Backend queue processing order:

```text
Users
-> Departments
-> Equipment
-> Assignments
-> Distribution
-> Returns
-> Audit Logs
```

This prevents broken references.

## 6. Safety Controls Required Before Production

Do not enable production sync until these exist in backend code:

- idempotency key enforcement
- sync lock enforcement
- optimistic locking/version checks
- transaction boundary per batch
- dependency-aware ordering
- validation before apply
- conflict snapshot storage
- audit logging
- retry limits
- quarantine path
- authentication and authorization

## 7. JavaFX Connection Rule

The JavaFX Sync Center must call desktop service abstractions only.

Allowed:

```text
SyncCenterController -> SyncCenterService -> ApiSyncService -> API
```

Not allowed:

```text
SyncCenterController -> PostgreSQL
```

The controller must not own sync business rules.

## 8. Implementation Order

### Deliverable 1

Central schema:

- complete
- migrations `V22` and `V23`

### Deliverable 2

Backend service skeleton:

- `SyncService`
- `SyncQueueService`
- `SyncAuditService`
- `SyncConflictService`
- `SyncLockService`
- `SyncValidationService`

### Deliverable 3

Backend API controller and DTOs.

### Deliverable 4

Push MVP:

- lock
- idempotency
- validation
- queue apply
- audit

Current status:

- complete for equipment create/update/upsert/delete/status through `/api/sync/push`
- remaining entities still need central handlers

### Deliverable 5

Pull MVP:

- scoped central snapshot
- schema/API version
- server time

Current status:

- endpoint exists, but central snapshot payload is not implemented yet

### Deliverable 6

Conflict and retry flow.

### Deliverable 7

Connect JavaFX panel to API-backed sync service.
