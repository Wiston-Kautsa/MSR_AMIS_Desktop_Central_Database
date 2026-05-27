# Alternative Java Runtime Deployment

The recommended local server deployment is Docker Compose. Use
[Docker Server Deployment](docker-server-deployment.md) unless the server
administrator has deliberately chosen to run PostgreSQL as a normal database
service and the API as a Java process.

```text
Desktop client PCs -> http://YOUR_SERVER_HOST:8090 -> API on server -> PostgreSQL on server :5432
```

## 1. Install Server Requirements

Install these on the server:

- Java 17 or newer
- PostgreSQL
- PowerShell 7 if the server is Linux and you want to use the included
  PowerShell helper scripts

Check Java:

```powershell
java -version
```

## 2. Create PostgreSQL Database

Open PostgreSQL as an admin user and run:

```sql
CREATE DATABASE msr_amis;
CREATE USER msr_amis_user WITH PASSWORD 'strong_password_here';
GRANT ALL PRIVILEGES ON DATABASE msr_amis TO msr_amis_user;
```

Connect to the `msr_amis` database and run:

```sql
GRANT ALL ON SCHEMA public TO msr_amis_user;
ALTER SCHEMA public OWNER TO msr_amis_user;
```

## 3. Prepare Server Files

From the project root on your development machine:

```powershell
.\scripts\package-server-deployment.ps1
```

This creates:

```text
dist\server\msr-amis-api.jar
dist\server\server.env.example
dist\server\run-api-server.ps1
dist\server\README-server-runtime.md
```

Copy the contents of `dist\server` to the server.

## 4. Configure Server Environment

On the server, copy:

```powershell
Copy-Item .\server.env.example .\server.env
```

Edit `server.env` and replace the placeholders:

```env
MSR_AMIS_API_PORT=8090
MSR_AMIS_DB_URL=jdbc:postgresql://localhost:5432/msr_amis
MSR_AMIS_DB_USERNAME=msr_amis_user
MSR_AMIS_DB_PASSWORD=strong_password_here
MSR_AMIS_JWT_SECRET=replace_with_real_base64_secret
MSR_AMIS_PRIMARY_SUPER_ADMIN_EMAIL=msramis@nlgfc.gov.mw
```

Generate a JWT secret:

```powershell
[Convert]::ToBase64String((1..64 | ForEach-Object { Get-Random -Maximum 256 }))
```

Keep `server.env` only on the server. Do not give it to desktop users.

## 5. Start The API

From the folder containing the copied server files:

```powershell
.\run-api-server.ps1 -EnvFile .\server.env -JarPath .\msr-amis-api.jar
```

The first start runs database migrations automatically through Flyway.

## 6. Check Health

On the server:

```powershell
Invoke-RestMethod http://localhost:8090/actuator/health
```

Expected:

```json
{"status":"UP"}
```

From a client PC:

```powershell
Invoke-RestMethod http://YOUR_SERVER_HOST:8090/actuator/health
```

If the client PC cannot reach the API, allow inbound TCP `8090` in the server
firewall.

## 7. Configure Desktop Clients

Client `.env` files must point to the server, not `localhost`:

```env
MSR_AMIS_DATA_MODE=REMOTE_API
MSR_AMIS_API_BASE_URL=http://YOUR_SERVER_HOST:8090
MSR_AMIS_AUTO_MIRROR_AFTER_MUTATION=false

APP_MODE=REMOTE_API
API_BASE_URL=http://YOUR_SERVER_HOST:8090
```

## 8. Build Desktop Installer For This Server

Before packaging the desktop installer:

```powershell
$env:MSR_AMIS_PACKAGE_API_BASE_URL="http://YOUR_SERVER_HOST:8090"
.\scripts\build-desktop.cmd
```

Give users only the installer and their login credentials. Do not share database
credentials, `server.env`, or API source code with desktop users.

## 9. Run As A Background Service

For a permanent deployment, run the API as a service instead of leaving a
terminal open.

On Windows Server, use Task Scheduler or NSSM to run:

```powershell
powershell.exe -ExecutionPolicy Bypass -File C:\Path\To\run-api-server.ps1 -EnvFile C:\Path\To\server.env -JarPath C:\Path\To\msr-amis-api.jar
```

On Linux, create a `systemd` service that sets the same environment variables
and runs:

```bash
java -jar /opt/msr-amis/msr-amis-api.jar
```
