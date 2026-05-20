# Server Hosting

This setup lets testers install only the desktop app and log in. The database credentials stay on the server.

Target shape:

```text
Desktop EXE -> HTTPS API URL -> API server -> local/private PostgreSQL
```

Do not point the desktop app directly at PostgreSQL.
For good performance, keep the API and PostgreSQL on the same server or the
same private LAN/VPC. The desktop app should cross the network only once, to
the API.

## 1. Create PostgreSQL

Create PostgreSQL on the same VPS/server as the API, or on a private database
host in the same region/network.

Keep these values:

- database JDBC URL
- database username
- database password

For a same-server PostgreSQL service, use localhost:

```env
MSR_AMIS_DB_URL=jdbc:postgresql://localhost:5432/msr_amis
```

For a managed PostgreSQL provider, use a database in the same region as the API
host and keep `sslmode=require` when the provider requires SSL.

## 2. Configure The API Server

Use [server.env.example](../../server.env.example) as the server environment template.

Required settings:

```env
MSR_AMIS_DB_URL=jdbc:postgresql://localhost:5432/msr_amis
MSR_AMIS_DB_USERNAME=msr_amis_user
MSR_AMIS_DB_PASSWORD=replace_with_database_password
MSR_AMIS_JWT_SECRET=replace_with_base64_secret
MSR_AMIS_PRIMARY_SUPER_ADMIN_EMAIL=msramis@nlgfc.gov.mw
```

Generate a JWT secret in PowerShell:

```powershell
[Convert]::ToBase64String((1..64 | ForEach-Object { Get-Random -Maximum 256 }))
```

Most cloud hosts provide `PORT` automatically. The API now supports that value. On a VPS or local server, use:

```env
MSR_AMIS_API_PORT=8090
```

## 3. Deploy The API

### Git-Based Docker Host

Use this when the server will clone the project from Git and Docker will run
both PostgreSQL and the API:

```powershell
Copy-Item docker.env.example docker.env
# Edit docker.env and replace database password, JWT secret, account emails, and SMTP values.
docker compose --env-file .\docker.env up -d --build
docker compose --env-file .\docker.env ps
```

Inside Docker, the API connects to PostgreSQL using the internal service name
`db`, so the JDBC URL is `jdbc:postgresql://db:5432/msr_amis`. Do not use
`localhost` for the database URL inside the API container.

Use a real copied env file with secret values on the server, not
`docker.env.example`.

For the full checklist, see [Docker Server Deployment](docker-server-deployment.md).

### Java Runtime Host

Prepare the server files:

```powershell
.\scripts\package-server-deployment.ps1
```

Copy `dist\server` to the server. On the server, copy `server.env.example` to
`server.env`, fill in real values, and start the API:

```powershell
.\run-api-server.ps1 -EnvFile .\server.env -JarPath .\msr-amis-api.jar
```

## 4. Check Health

On the server:

```powershell
Invoke-RestMethod http://localhost:8090/actuator/health
```

From a client computer:

```powershell
Invoke-RestMethod https://YOUR_API_HOST/actuator/health
```

The response should include `status` as `UP`.

## 5. Check Server Performance

On the API server, verify the API reaches PostgreSQL locally or over the
private network:

```powershell
Invoke-RestMethod http://localhost:8090/actuator/health
```

If the desktop is slow but this local health check is fast, check the client to
API network. If the local health check is slow, check PostgreSQL CPU, memory,
disk, and query indexes.

## 6. Build Desktop For Testers

Set the hosted API URL before packaging:

```powershell
$env:MSR_AMIS_PACKAGE_API_BASE_URL="https://YOUR_API_HOST"
.\scripts\build-desktop.cmd
```

Share only:

- `dist\MSR AMIS-1.0.0.exe`
- tester username
- tester password

The tester should not receive database credentials or API source code.
