# MSR-AMIS Server Deployment Steps

Use this deployment shape:

```text
Desktop app on users' PCs
        ->
API on server :8090
        ->
PostgreSQL on the same server :5432
```

## 1. Prepare The Server

Install:

- Java 17 or newer
- PostgreSQL
- Git, or copy the project files to the server

## 2. Create The PostgreSQL Database

Open PostgreSQL as an admin user and run:

```sql
CREATE DATABASE msr_amis;
CREATE USER msr_amis_user WITH PASSWORD 'strong_password_here';
GRANT ALL PRIVILEGES ON DATABASE msr_amis TO msr_amis_user;
```

If PostgreSQL requires schema privileges, also run this while connected to the
`msr_amis` database:

```sql
GRANT ALL ON SCHEMA public TO msr_amis_user;
ALTER SCHEMA public OWNER TO msr_amis_user;
```

## 3. Configure Server Environment

Create a real server env file from `server.env.example`.

Minimum required values:

```env
MSR_AMIS_API_PORT=8090
MSR_AMIS_DB_URL=jdbc:postgresql://localhost:5432/msr_amis
MSR_AMIS_DB_USERNAME=msr_amis_user
MSR_AMIS_DB_PASSWORD=strong_password_here
MSR_AMIS_JWT_SECRET=your_generated_base64_secret
MSR_AMIS_PRIMARY_SUPER_ADMIN_EMAIL=your-super-admin@email.com
```

Generate a JWT secret in PowerShell:

```powershell
[Convert]::ToBase64String((1..64 | ForEach-Object { Get-Random -Maximum 256 }))
```

Do not put database credentials on desktop client computers.

## 4. Build The API

From the project root:

```powershell
.\scripts\build-api.cmd
```

The API jar will be created at:

```text
msr-amis-api\target\msr-amis-api-0.0.1-SNAPSHOT.jar
```

## 5. Run The API

From the project root on the server:

```powershell
java -jar msr-amis-api\target\msr-amis-api-0.0.1-SNAPSHOT.jar
```

Check health on the server:

```powershell
Invoke-RestMethod http://localhost:8090/actuator/health
```

Expected response:

```json
{"status":"UP"}
```

## 6. Open Firewall

Allow inbound TCP port `8090` on the server.

From another computer, test:

```powershell
Invoke-RestMethod http://SERVER_IP:8090/actuator/health
```

## 7. Configure Desktop Clients

On each desktop `.env`:

```env
MSR_AMIS_DATA_MODE=REMOTE_API
MSR_AMIS_API_BASE_URL=http://SERVER_IP:8090
MSR_AMIS_AUTO_MIRROR_AFTER_MUTATION=false

APP_MODE=REMOTE_API
API_BASE_URL=http://SERVER_IP:8090
```

## 8. Build Desktop Installer

Before packaging, set the server API URL:

```powershell
$env:MSR_AMIS_PACKAGE_API_BASE_URL="http://SERVER_IP:8090"
.\scripts\build-desktop.cmd
```

Give users the installer from `dist`.

## Production Note

For internal LAN hosting, `http://SERVER_IP:8090` is enough to start.

For production over the internet, use a domain and HTTPS:

```env
MSR_AMIS_API_BASE_URL=https://amis.yourdomain.com
```
