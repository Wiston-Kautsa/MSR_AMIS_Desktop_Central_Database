# MSR-AMIS Demo Reset Runbook

## 1. Local desktop mode

Use this when `.env` is set to `LOCAL_DATABASE`.

1. Sign in as `SUPER_ADMIN`.
2. Open `Backup & Sync`.
3. Expand `Reset Local Demo Data`.
4. Type `RESET DEMO DATA`.
5. Click `Reset Demo Data`.

What it clears:
- `equipment`
- `assignments`
- `distribution`
- `returns`
- `password_reset_audit`
- `audit_log`

What it preserves:
- user accounts
- departments
- backup files

Safety behavior:
- the app creates a local safety backup before deletion
- after reset, one fresh audit entry is written describing the reset

## 2. Remote API mode

Use this when `.env` is set to `REMOTE_API`.

The desktop app does not expose database cleanup in this mode. Reset PostgreSQL directly:

```powershell
psql -U postgres -d msr_amis -f docs/remote-api-demo-reset.sql
```

What that SQL clears:
- `returns`
- `distribution`
- `assignments`
- `equipment`
- `audit_log`

What it preserves:
- `users`
- `departments`
- Flyway migration history

## 3. Recommended demo reset procedure

Before a demo:
1. Start PostgreSQL.
2. Start the API if you are using `REMOTE_API`.
3. Reset the database using the matching mode above.
4. Start the desktop app.
5. Log in with your prepared demo account.
6. Enter or import the demo data you want to present.

After a demo:
1. Submit or keep a safety backup if you want that session preserved.
2. Run the same reset path again before the next presentation.

## 4. Hard reset note

If you also want to remove non-system users in PostgreSQL, the optional SQL block at the bottom of [remote-api-demo-reset.sql](/D:/School/JAVA/MSR-AMIS_destop_application/docs/remote-api-demo-reset.sql) can be used, but that is more destructive and should be done only if you intend to rebuild demo users afterward.
