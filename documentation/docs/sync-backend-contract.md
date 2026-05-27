# MSR-AMIS Sync Backend Contract

## Purpose

This document freezes the backend sync model before further UI work.

The production sync architecture is:

```text
Desktop -> API -> PostgreSQL
```

Desktop clients must never connect directly to production PostgreSQL.

## Sync Model

MSR-AMIS uses a hybrid trigger model:

- background auto sync for normal queue draining
- manual Admin/Super Admin controls for recovery, review, retry, and forced refresh

MSR-AMIS uses controlled bi-directional sync:

- push local queued desktop changes to the API
- pull central PostgreSQL state back through the API
- require conflict rules before overwriting central records

## Role Matrix

| Feature | USER | ADMIN | SUPER_ADMIN |
| --- | --- | --- | --- |
| View Sync Status | No | Yes | Yes |
| Push Sync | No | Yes | Yes |
| Pull Sync | No | Yes | Yes |
| Retry Failed | No | Yes | Yes |
| Resolve Conflicts | No | Limited | Yes |
| Force Full Resync | No | No | Yes |
| Clear Queue | No | No | Yes |
| Force Release Lock | No | No | Yes |
| Edit Sync Config | No | No | Yes |

Admin access is scoped to Admin/User actor records. Super Admin sync records remain hidden from Admin and User accounts.

## Database Schema

Flyway migration:

```text
V22__add_sync_infrastructure.sql
```

Core central tables:

- `sync_sessions`
- `sync_queue`
- `sync_audit`
- `sync_conflicts`
- `sync_lock`
- `sync_settings`
- `sync_quarantine`
- `sync_checkpoints`

Important schema rules:

- `sync_queue.idempotency_key` is unique
- only one active sync lock is allowed
- payloads are stored as `JSONB`
- conflicts keep both local and central payload snapshots
- checkpoints allow resumable sync
- quarantine prevents one bad record from blocking the whole queue

## Entity Processing Order

Backend processing must be dependency-aware:

```text
Users
-> Departments
-> Equipment
-> Assignments
-> Distribution
-> Returns
-> Audit Logs
```

Returns must never be applied before their assignment/distribution exists centrally.

## Conflict Rules

### Users

- central is authoritative
- Admin cannot overwrite Super Admin records
- inactive/draft users do not sync

### Equipment

- duplicate asset code or serial number creates a conflict
- stale record version creates a conflict
- destructive status changes require confirmation and audit

### Assignments

- only approved/active assignments sync
- quantity cannot exceed available stock
- assignment dependencies must exist before distribution sync

### Distribution

- asset must exist
- asset must be assignable
- asset cannot already be actively distributed

### Returns

- return must match an existing distribution
- approved returns are not overwritten by later offline changes
- returned asset must belong to the assignment being closed

## Required Endpoints

All endpoints require authentication.

## Current Implementation Status

As of the current code state, the backend sync foundation is implemented with these active capabilities:

- central sync tables exist through Flyway migrations `V22` and `V23`
- `POST /api/sync/push` accepts per-record queue payloads and returns per-record results
- equipment push supports `CREATE`, `UPDATE`, `UPSERT`, `DELETE`, and `STATUS`
- assignment push supports `CREATE`, `UPDATE`, `STATUS`, and `DELETE`
- distribution push supports batch distribution payloads
- return push supports batch return payloads
- user push supports `CREATE`, `UPDATE`, `STATUS`, and `DELETE`
- department push supports `CREATE`, `UPDATE`, and `DELETE`
- equipment idempotency keys are recorded in `sync_queue`
- `GET /api/sync/queue` returns central queue rows for the JavaFX Sync Center
- `GET /api/sync/status` returns API/database/schema/lock/count status
- `GET /api/sync/audit` returns central sync audit rows
- `GET /api/sync/conflicts` and `POST /api/sync/conflicts/resolve` are present for conflict review/resolution
- `POST /api/sync/retry` moves failed/conflict/quarantined rows back to pending
- Super Admin endpoints exist for clear completed logs, clear queue, reset sync state, and force-release lock

Current limitations:

- `GET /api/sync/pull` is still a lightweight acknowledgement endpoint. It records a pull audit event and tells the desktop to refresh available central views, but it does not yet return a full central snapshot payload grouped by entity.
- Audit-log generic push is not part of the current central apply engine.
- The JavaFX conflict review buttons `Keep Local`, `Keep Central`, and `Merge` still require stored field-level snapshots and do not yet perform automatic overwrite/merge resolution.

### `POST /api/sync/push`

Push queued desktop changes to the API.

Required behaviors:

- validate JWT and role
- require idempotency key per record
- acquire sync lock or reject with `409`
- validate payload checksum
- process records in dependency order
- use transaction boundary per batch
- create conflicts instead of overwriting stale central records
- quarantine invalid records that should not block the whole batch
- write `sync_audit`
- return session summary

Request shape:

```json
{
  "sessionToken": "SYNC-20260517-001",
  "machineId": "MSR-LIL-005",
  "machineName": "MSR-LIL-005",
  "dryRun": false,
  "scope": {
    "entities": ["EQUIPMENT", "ASSIGNMENT", "DISTRIBUTION", "RETURN"]
  },
  "records": [
    {
      "entityType": "EQUIPMENT",
      "entityId": "TAB-0012",
      "operation": "UPDATE",
      "idempotencyKey": "MSR-LIL-005:EQUIPMENT:TAB-0012:UPDATE:001",
      "checksum": "sha256...",
      "payload": {},
      "baseline": {}
    }
  ]
}
```

Response shape:

```json
{
  "sessionToken": "SYNC-20260517-001",
  "status": "SUCCESS",
  "total": 10,
  "applied": 42,
  "failed": 0,
  "conflicts": 2,
  "quarantined": 1,
  "message": "Push processed 10 sync record(s).",
  "results": [
    {
      "queueId": 1,
      "entityType": "EQUIPMENT",
      "entityId": "TAB-0012",
      "operation": "UPDATE",
      "status": "APPLIED",
      "message": "Equipment update applied"
    }
  ]
}
```

Current equipment operations:

| Operation | Current behavior |
| --- | --- |
| `CREATE` | Upserts equipment by `assetCode`; rejects duplicate serial on another asset |
| `UPDATE` | Upserts equipment by `assetCode`; rejects duplicate serial on another asset |
| `UPSERT` | Same behavior as update/create through `syncEquipment` |
| `DELETE` | Deletes the central equipment row if it exists; repeated delete is idempotent |
| `STATUS` | Updates central equipment status through the equipment status service |

### `GET /api/sync/pull`

Pull central changes for a desktop.

Query parameters:

- `since`
- `machineId`
- `entities`

Response:

- central records grouped by entity
- schema version
- API version
- server time
- compatibility status

Implementation note:

The current endpoint returns a success message only. Full entity snapshot response is still pending.

### `GET /api/sync/status`

Return health and governance status.

Response includes:

- API connectivity status
- PostgreSQL status
- schema version
- API version
- sync lock state
- central maintenance mode
- last successful sync
- last failed sync
- queue counts
- conflict counts
- quarantine counts

### `POST /api/sync/retry`

Retry failed or quarantined records.

Rules:

- Admin may retry own records
- Super Admin may retry all records
- retry count increments
- retry policy uses exponential backoff

### `GET /api/sync/conflicts`

Return open conflicts.

Filters:

- entity type
- status
- user
- machine ID
- date range

### `POST /api/sync/resolve-conflict`

Resolve one conflict.

Allowed actions:

- `KEEP_LOCAL`
- `KEEP_CENTRAL`
- `MERGE`

Rules:

- Super Admin can resolve any conflict
- Admin can resolve limited assigned conflicts
- all resolutions are audited

### `GET /api/sync/audit`

Return sync audit history.

Filters:

- sync session
- user
- machine ID
- entity type
- result status
- date range

### `POST /api/sync/lock`

Acquire a central sync lock.

Rules:

- one active lock at a time
- lock has `expires_at`
- expired locks may be replaced
- active non-expired lock returns `409`

### `DELETE /api/sync/lock`

Release a lock.

Rules:

- owner may release own lock
- Super Admin may force release
- force release is audited

### `POST /api/sync/resync`

Force full pull from central state.

Rules:

- Super Admin only
- requires recent verified backup for destructive modes
- creates audit entry

### Implemented Super Admin maintenance endpoints

The current API exposes these operational endpoints:

```text
POST /api/sync/queue/clear-completed
POST /api/sync/queue/clear
POST /api/sync/reset
POST /api/sync/lock/force-release
GET  /api/sync/queue
```

These are restricted to `SUPER_ADMIN` where destructive or lock-related.

## Safety Controls

Backend must implement these before production sync is trusted:

- idempotency keys
- optimistic locking or record version checks
- payload checksum verification
- transaction boundary per batch
- sync lock
- validation before apply
- dependency ordering
- conflict snapshot storage
- immutable audit trail strategy
- retry with exponential backoff
- quarantine for invalid records

## Validation Rules

Pre-sync validation must check:

- duplicate asset codes
- duplicate serial numbers
- missing required fields
- malformed phone/NID values
- orphan assignments
- returns without matching distribution
- invalid foreign keys
- business rules such as stock availability and return ownership

Validation failures should not silently write to central PostgreSQL.

## Deployment Defaults

Recommended defaults:

```text
Sync mode: HYBRID
Strategy: BIDIRECTIONAL_CONTROLLED_CONFLICTS
Poll interval: 5 minutes
Batch size: 100
Max retries: 3
Lock TTL: 15 minutes
Audit retention: 90 days
Conflict SLA: 24 hours
```

## MVP Build Order

1. Central sync schema
2. Backend `POST /api/sync/push`
3. Backend `GET /api/sync/pull`
4. Backend sync audit
5. Retry failed
6. Central sync lock

## Phase 2

1. Conflict resolution
2. Validation service
3. Auto sync scheduler
4. Offline queue integration
5. Quarantine review

## Phase 3

1. Rollback tokens
2. Scoped sync filters
3. Disaster recovery hooks
4. Analytics
5. Approval workflow

## Testing Contract

Minimum required tests:

- normal push
- normal pull
- duplicate idempotency key does not duplicate records
- API offline
- token expired
- sync lock conflict
- same asset changed locally and centrally
- timeout mid-batch and resume
- unauthorized user blocked
- tampered checksum rejected
- destructive sync requires Super Admin or explicit approval
