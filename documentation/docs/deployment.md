# MSR-AMIS Deployment

## Production Shape

Recommended production deployment:

`Desktop Client -> local SQLite mirror -> API Server -> PostgreSQL Server`

The desktop should call the API. It should not connect directly to PostgreSQL over the network.

PostgreSQL is the primary database. SQLite exists on each desktop computer as an offline mirror and queue store.

## Local Development

For development:

- PostgreSQL can be installed locally
- the API can run on `localhost`
- the desktop can run in `AUTO` mode against the local API

Typical local values:

```env
MSR_AMIS_DATA_MODE=AUTO
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

Server API environment:

```env
MSR_AMIS_API_PORT=8090
MSR_AMIS_DB_URL=jdbc:postgresql://localhost:5432/msr_amis
MSR_AMIS_DB_USERNAME=postgres
MSR_AMIS_DB_PASSWORD=your_password
MSR_AMIS_JWT_SECRET=your_base64_secret
MSR_AMIS_JWT_EXPIRATION_SECONDS=28800
```

Run the API:

```powershell
java -jar msr-amis-api-0.0.1-SNAPSHOT.jar
```

Health check:

```powershell
Invoke-RestMethod http://localhost:8090/actuator/health
```

Expected response:

```json
{"status":"UP"}
```

For production, run the API as a Windows Service or system service so it starts automatically after server reboot.

## Client Deployment

Install either:

- `dist\MSR AMIS-1.0.0.msi`
- `dist\MSR AMIS-1.0.0.exe`

Client desktop configuration:

```env
MSR_AMIS_DATA_MODE=AUTO
MSR_AMIS_API_BASE_URL=http://SERVER_IP_OR_NAME:8090

APP_MODE=AUTO
API_BASE_URL=http://SERVER_IP_OR_NAME:8090
```

Example:

```env
MSR_AMIS_API_BASE_URL=http://192.168.1.10:8090
API_BASE_URL=http://192.168.1.10:8090
```

Do not use `localhost` on client machines unless the API is installed on that same client machine.

## Operational Guidance

- prefer API access over direct database access
- keep database credentials on the server side
- use strong passwords and controlled network access
- use environment variables for deployment secrets
- back up PostgreSQL on the server side in centralized mode
- keep desktop clients in `AUTO` mode if offline work is required
- after offline work, users must log in while online and process Sync Center
- rejected Sync Center records should be reviewed by an administrator

## Offline Note

In `AUTO` mode the desktop can continue working with SQLite if the API or network is unavailable.

Offline changes are not immediately in PostgreSQL. They are queued locally and must be processed when the API is reachable again.

When online sync runs:

- valid queued changes are uploaded to PostgreSQL through the API
- invalid or conflicting changes are rejected
- SQLite is refreshed from PostgreSQL so local state matches the primary database
