# MSR-AMIS Troubleshooting

## API Not Reachable

Use this checklist when the desktop shows `API not reachable`, `OFFLINE (AUTO)`, or cannot connect to the central system.

### 1. Check the API on the server

Run this on the API server:

```powershell
Invoke-RestMethod http://localhost:8090/actuator/health
```

Expected response:

```json
{"status":"UP"}
```

If this fails, the API is not running, did not start correctly, or is not listening on port `8090`.

### 2. Check PostgreSQL

The API needs PostgreSQL before it can start correctly.

```powershell
Get-Service *postgres*
```

The PostgreSQL service should show `Running`.

### 3. Start the API

From the project root on the server:

```powershell
.\mvnw.cmd -f msr-amis-api\pom.xml spring-boot:run
```

If local development startup is blocked by test compilation, use:

```powershell
.\mvnw.cmd -f msr-amis-api\pom.xml -Dmaven.test.skip=true spring-boot:run
```

For production, run the packaged API as a Windows service or scheduled startup task so it starts automatically after server restart.

### 4. Test from a client computer

Run this from a desktop client:

```powershell
Invoke-RestMethod http://143.198.153.43:8090/actuator/health
```

If the server can reach `localhost:8090` but the client cannot reach `143.198.153.43:8090`, check the server IP/name, network connection, and firewall.

### 5. Check client `.env`

Normal MIS client computers must point to the API server:

```env
MSR_AMIS_DATA_MODE=REMOTE_API
MSR_AMIS_API_BASE_URL=http://143.198.153.43:8090

APP_MODE=REMOTE_API
API_BASE_URL=http://143.198.153.43:8090
```

Use `AUTO` instead of `REMOTE_API` only on deployments that intentionally allow offline work and Sync Center recovery. Use `localhost` only when the desktop app and API are running on the same computer. If a client uses `localhost`, it will look for an API on that client machine and report `API not reachable`.

### 6. Check firewall access

Allow inbound access to API port `8090` on the server. Client PCs need access to the API port only. They should not connect directly to PostgreSQL port `5432`.

### 7. Continue safely in `AUTO` mode

If the desktop is in `AUTO` mode and shows `OFFLINE (AUTO)`, users can continue urgent work locally if their account has already been mirrored on that computer.

Offline changes are not visible to other users until they are synchronized.

### 8. Sync after recovery

After the API is reachable again, users who worked offline must open:

```text
Data & Records -> Sync Center
```

Process pending changes and confirm SQLite refreshes from PostgreSQL.

## Quick Diagnosis

- Server health check fails on `localhost:8090`: API is not running or failed startup.
- Server health check works, client health check fails: network, wrong server address, or firewall.
- Client `.env` uses `localhost`: client is pointing to itself instead of the API server.
- PostgreSQL is stopped: API may fail startup or become unhealthy.
- Desktop is in `REMOTE_API`: users cannot continue until the API is reachable.
- Desktop is in `AUTO`: users can work locally and sync later, subject to offline login availability.
