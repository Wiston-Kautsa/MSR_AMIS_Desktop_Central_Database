# MSR-AMIS Deployment

## Production Shape

Recommended production deployment:

`Desktop Client -> local SQLite mirror -> API Server -> PostgreSQL Server`

The desktop should call the API. It should not connect directly to PostgreSQL over the network.

PostgreSQL is the primary database. SQLite exists on each desktop computer for local storage and, when `AUTO` mode is enabled, as an offline mirror and queue store.

## Local Development

For development:

- PostgreSQL can be installed locally
- the API can run on `localhost`
- the desktop can run in `REMOTE_API` mode against the local API, or `AUTO` mode when testing offline sync behavior

Typical local values:

```env
MSR_AMIS_DATA_MODE=REMOTE_API
MSR_AMIS_API_BASE_URL=http://localhost:8090
MSR_AMIS_DB_URL=jdbc:postgresql://localhost:5432/msr_amis
```

## Server Deployment

Recommended server deployment:

- Windows or Linux server
- PostgreSQL installed on the server or on a trusted database server
- Spring Boot API deployed on the same server or a trusted application server
- PostgreSQL not exposed directly to desktop clients
- reverse proxy and firewall rules applied as needed

Minimum server requirements:

- Java 17
- PostgreSQL
- inbound access to the API port, usually `8090`
- regular PostgreSQL backup process

## PostgreSQL Server Hosting

Host PostgreSQL on the server or on a trusted database server that only the API can reach.

Recommended network shape:

```text
Client PCs -> API Server :8090 -> PostgreSQL Server :5432
```

Desktop clients should never connect directly to PostgreSQL.

### 1. Install PostgreSQL

Install PostgreSQL on the server and record:

- PostgreSQL service name
- PostgreSQL port, usually `5432`
- `postgres` administrator password

### 2. Create the database

Create the application database:

```sql
CREATE DATABASE msr_amis;
```

For production, use a dedicated database user instead of the default `postgres` account:

```sql
CREATE USER msr_amis_user WITH PASSWORD 'strong_password_here';
GRANT ALL PRIVILEGES ON DATABASE msr_amis TO msr_amis_user;
```

Depending on the PostgreSQL version and ownership policy, the dedicated user may also need schema privileges after connecting to `msr_amis`:

```sql
GRANT ALL ON SCHEMA public TO msr_amis_user;
ALTER SCHEMA public OWNER TO msr_amis_user;
```

### 3. Configure PostgreSQL access

If the API and PostgreSQL run on the same server, keep PostgreSQL private and use:

```env
MSR_AMIS_DB_URL=jdbc:postgresql://localhost:5432/msr_amis
```

If the API runs on a different server, configure PostgreSQL to listen for that API server only.

In `postgresql.conf`:

```conf
listen_addresses = '*'
```

In `pg_hba.conf`, allow only the API server IP:

```conf
host    msr_amis    msr_amis_user    API_SERVER_IP/32    scram-sha-256
```

Restart PostgreSQL after changing these files.

### 4. Configure firewall rules

If the API and PostgreSQL are on the same server, do not expose PostgreSQL port `5432` to the network.

If PostgreSQL is on a separate database server, allow port `5432` only from the API server IP. Do not open PostgreSQL to all client PCs.

Client PCs need access only to the API port, usually `8090`.

Server API environment:

```env
MSR_AMIS_API_PORT=8090
MSR_AMIS_DB_URL=jdbc:postgresql://localhost:5432/msr_amis
MSR_AMIS_DB_USERNAME=msr_amis_user
MSR_AMIS_DB_PASSWORD=your_password
MSR_AMIS_JWT_SECRET=your_base64_secret
MSR_AMIS_JWT_EXPIRATION_SECONDS=28800
MSR_AMIS_EXPOSE_RESET_CODE_ON_EMAIL_FAILURE=false
```

If PostgreSQL is on a separate server:

```env
MSR_AMIS_DB_URL=jdbc:postgresql://POSTGRES_SERVER_IP:5432/msr_amis
```

Run the API:

```powershell
java -jar msr-amis-api-0.0.1-SNAPSHOT.jar
```

Flyway migrations run during API startup and create or update the schema automatically.

Health check:

```powershell
Invoke-RestMethod http://localhost:8090/actuator/health
```

Expected response:

```json
{"status":"UP"}
```

For production, run the API as a Windows Service or system service so it starts automatically after server reboot.

If the API is not reachable, first confirm this health check works on the server. Then test from a client computer with `http://SERVER_IP_OR_NAME:8090/actuator/health`. If the server check works but the client check fails, review firewall and network access to port `8090`. See [Troubleshooting](troubleshooting.md).

## Client Deployment

Install either:

- `dist\MSR AMIS-1.0.0.msi`
- `dist\MSR AMIS-1.0.0.exe`

Current installer build: May 17, 2026. This package contains the latest desktop changes for bulk enrolment, equipment/report table columns, maintenance tracking/reporting, Asset History with maintenance events, online/offline Sync Center behavior, role-based Sync Center access, active queue cleanup after successful push, and Department Management.

Client desktop configuration:

```env
MSR_AMIS_DATA_MODE=REMOTE_API
MSR_AMIS_API_BASE_URL=http://SERVER_IP_OR_NAME:8090

APP_MODE=REMOTE_API
API_BASE_URL=http://SERVER_IP_OR_NAME:8090
```

Example:

```env
MSR_AMIS_API_BASE_URL=http://192.168.1.10:8090
API_BASE_URL=http://192.168.1.10:8090
```

Do not use `localhost` on client machines unless the API is installed on that same client machine.

If a client shows `API not reachable`, check its `.env` first. Normal client computers must use the server IP or DNS name, not `localhost`.

## Operational Guidance

- prefer API access over direct database access
- keep database credentials on the server side
- use strong passwords and controlled network access
- use environment variables for deployment secrets
- back up PostgreSQL on the server side in centralized mode
- keep desktop clients in `REMOTE_API` mode for the safest centralized rollout
- use `AUTO` only if offline work is required and the Sync Center process is part of operations
- after offline work, users must log in while online and process `Data & Records -> Sync Center`
- rejected Sync Center records should be reviewed by an administrator
- desktop Backup & Restore is for `LOCAL_DATABASE` mode only; it is not the official backup process for centralized deployment
- keep PostgreSQL private to the API server whenever possible

## Offline Note

In `AUTO` mode the desktop can continue working with SQLite if the API or network is unavailable.

Offline changes are not immediately in PostgreSQL. They are queued locally and must be processed when the API is reachable again.

When online sync runs:

- valid queued changes are uploaded to PostgreSQL through the API
- invalid or conflicting changes are rejected
- SQLite is refreshed from PostgreSQL so local state matches the primary database

## Deployment Readiness

The project is ready for controlled pilot deployment after the server API URL, PostgreSQL credentials, firewall, and PostgreSQL backup process are configured.

Before wider rollout:

- install the rebuilt MSI or EXE on test client machines
- verify each client points to the server API URL, not `localhost`
- test login, equipment, bulk enrolment, assignment, distribution, return, maintenance, reports, user management, audit logs, and Sync Center
- test Asset History for an asset that has registration, distribution, maintenance, and return events
- test department create, rename, delete-blocked-when-in-use, and default `MSR` protection
- test one offline workflow in `AUTO` mode and confirm Sync Center applies or rejects queued changes correctly
- confirm PostgreSQL backup and restore procedures outside the desktop app

## PostgreSQL Backup

Official backups must be taken from PostgreSQL on the server.

Example manual backup:

```powershell
pg_dump -U msr_amis_user -d msr_amis -f C:\Backups\msr_amis_backup.sql
```

The repository includes a server-side backup script:

```powershell
.\scripts\postgres-backup.ps1
```

Default behavior:

- database: `msr_amis`
- host: `localhost`
- port: `5432`
- user: `msr_amis_user`
- backup folder: `C:\Backups\MSR-AMIS`
- retention: `30` days
- backup format: PostgreSQL custom dump format, suitable for `pg_restore`

If PostgreSQL asks for a password, set `PGPASSWORD` for the session or configure a secure PostgreSQL password file on the server.

Example:

```powershell
$env:PGPASSWORD="strong_password_here"
.\scripts\postgres-backup.ps1
```

To schedule a daily Windows backup task at 10 PM:

```powershell
.\scripts\register-postgres-backup-task.ps1 -StartTime "22:00"
```

For production, schedule backups and periodically test restore on a separate database.
