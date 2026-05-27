# MSR-AMIS Docker Server Deployment

This is the recommended server deployment for MSR-AMIS.

```text
Desktop client PCs -> http://YOUR_SERVER_HOST:8090 -> API container -> PostgreSQL container
```

The server clones the project from GitHub. Docker then builds and runs the API
and PostgreSQL. Desktop users receive only the desktop installer and their login
details. They must not receive `docker.env`, database credentials, or server
source access unless they are part of the deployment team.

## Deployment Reference

| Property | Value |
| --- | --- |
| Deployment shape | Desktop client PCs -> MSR-AMIS API on server port `8090` -> PostgreSQL on Docker network port `5432` |
| Current API URL | `http://YOUR_SERVER_HOST:8090` |
| Primary platform | Linux server, Ubuntu or Debian |
| Optional platform | Windows Server or Windows development machine using Docker Desktop |
| Security rule | `docker.env` stays on the server only. Desktop users receive only the installer, API URL, username, and password. |

Important: keep one server configuration and one desktop configuration.
Do not mix database, JWT, SMTP, or reserved-account variables into a desktop
`.env` file. Desktop PCs need only the API URL and remote-mode settings.

Architecture:

```text
Desktop Client PCs
Installed MSR-AMIS desktop application
HTTP -> http://YOUR_SERVER_HOST:8090

MSR-AMIS API Container
Published port 8090 | Spring Boot
JDBC -> jdbc:postgresql://msr-amis-db:5432/msr_amis

PostgreSQL Container
Internal port 5432 | msr-amis-db service name
```

Repository:

```text
https://github.com/Wiston-Kautsa/MSR_AMIS_Desktop_Central_Database.git
```

## Step 1 - Install Server Requirements

Run this on the Ubuntu/Debian server:

```bash
sudo apt update
sudo apt install -y git docker.io docker-compose-plugin
sudo systemctl enable docker
sudo systemctl start docker
```

Check the tools:

```bash
git --version
docker --version
docker compose version
```

Optional: allow the current Linux user to run Docker without `sudo`:

```bash
sudo usermod -aG docker $USER
newgrp docker
```

If this is skipped, run Docker commands with `sudo`.

## Step 2 - Clone The Project

Run this on the server:

```bash
git clone https://github.com/Wiston-Kautsa/MSR_AMIS_Desktop_Central_Database.git
cd MSR_AMIS_Desktop_Central_Database
```

## Step 3 - Create The Server Docker Environment

Create the real server config file:

```bash
cp docker.env.example docker.env
```

Generate a JWT secret:

```bash
openssl rand -base64 64
```

Edit `docker.env`:

```bash
nano docker.env
```

Set these values before starting the system:

```env
POSTGRES_DB=msr_amis
POSTGRES_USER=msr_amis_user
POSTGRES_PASSWORD=replace_with_strong_database_password

MSR_AMIS_DB_USERNAME=msr_amis_user
MSR_AMIS_DB_PASSWORD=replace_with_same_strong_database_password
MSR_AMIS_API_PORT=8090
MSR_AMIS_JWT_SECRET=replace_with_generated_base64_secret
MSR_AMIS_JWT_EXPIRATION_SECONDS=28800

MSR_AMIS_PRIMARY_SUPER_ADMIN_EMAIL=msramis@nlgfc.gov.mw
MSR_AMIS_SETUP_ADMIN_EMAIL=setup-admin@example.com
MSR_AMIS_SETUP_USER_EMAIL=setup-user@example.com
MSR_AMIS_RESERVED_SUPER_ADMIN_EMAILS=msramis@nlgfc.gov.mw
MSR_AMIS_RESERVED_ADMIN_EMAILS=setup-admin@example.com
MSR_AMIS_RESERVED_USER_EMAILS=setup-user@example.com

MSR_AMIS_EXPOSE_RESET_CODE_ON_EMAIL_FAILURE=false

MSR_AMIS_SMTP_HOST=mail.nlgfc.gov.mw
MSR_AMIS_SMTP_PORT=465
MSR_AMIS_SMTP_USERNAME=msramis@nlgfc.gov.mw
MSR_AMIS_SMTP_PASSWORD=replace_with_mailbox_password
MSR_AMIS_SMTP_FROM=msramis@nlgfc.gov.mw
MSR_AMIS_SMTP_STARTTLS=false
MSR_AMIS_SMTP_SSL=true
MSR_AMIS_SMTP_TIMEOUT_MS=10000

MSR_AMIS_SUPER_USER_STATUS_EMAILS_ENABLED=true
MSR_AMIS_OPERATION_EMAILS_ENABLED=false
MSR_AMIS_OPERATION_EMAIL_RECIPIENTS=msramis@nlgfc.gov.mw
```

Keep `docker.env` only on the server. It is ignored by Git and must not be
committed.

## Step 4 - Start The System

From the project root on the server:

```bash
docker compose --env-file ./docker.env up -d --build
```

This starts:

- `msr-amis-db`
- `msr-amis-api`

PostgreSQL data is stored in the Docker volume
`msr-amis-postgres-data`, so data survives normal rebuilds and restarts.

Do not run `docker compose down -v` unless you intentionally want to delete the
database volume.

## Step 5 - Check Container Status

```bash
docker compose --env-file ./docker.env ps
```

Expected result:

```text
msr-amis-db    healthy
msr-amis-api   healthy
```

Check API health on the server:

```bash
curl http://localhost:8090/actuator/health
```

Expected:

```json
{"status":"UP"}
```

If the API is not healthy, check logs:

```bash
docker compose --env-file ./docker.env logs api
docker compose --env-file ./docker.env logs db
```

## Step 6 - Open The Firewall

Allow client computers to reach the API:

```bash
sudo ufw allow 8090/tcp
sudo ufw status
```

From a client PC, test:

```powershell
Invoke-RestMethod http://YOUR_SERVER_HOST:8090/actuator/health
```

The response should show `UP`.

## Step 7 - Confirm The Primary Super Admin

The configured primary Super Admin is:

```text
email:    msramis@nlgfc.gov.mw
username: MSRAMIS Admin
name:     MSRAMIS Admin
role:     SUPER_ADMIN
```

The API migration sets this username and display name automatically when the
server starts.

## Step 8 - Configure Desktop Clients

On each desktop client, the installed `.env` must point to the server API:

```env
MSR_AMIS_DATA_MODE=REMOTE_API
MSR_AMIS_API_BASE_URL=http://YOUR_SERVER_HOST:8090
MSR_AMIS_AUTO_MIRROR_AFTER_MUTATION=false

APP_MODE=REMOTE_API
API_BASE_URL=http://YOUR_SERVER_HOST:8090
```

Use the current server IP address or DNS name.

Do not use `localhost` on client PCs unless the API is running on that same PC.

## Step 9 - Build The Desktop Installer For This Server

On the development machine, set the server API URL before packaging:

```powershell
$env:MSR_AMIS_PACKAGE_API_BASE_URL="http://YOUR_SERVER_HOST:8090"
.\scripts\build-desktop.cmd
```

Give users only:

- the desktop installer from `dist`
- their username
- their password

## Step 10 - Update The Server Later

On the server:

```bash
cd MSR_AMIS_Desktop_Central_Database
git pull
docker compose --env-file ./docker.env up -d --build
docker compose --env-file ./docker.env ps
curl http://localhost:8090/actuator/health
```

After the server update is healthy, rebuild the MSI and EXE on the development
machine if desktop code or packaged configuration changed:

```powershell
$env:MSR_AMIS_PACKAGE_API_BASE_URL="http://YOUR_SERVER_HOST:8090"
.\scripts\build-desktop.cmd
```

Distribute the refreshed files from `dist`:

- `dist\MSR AMIS-1.0.0.msi`
- `dist\MSR AMIS-1.0.0.exe`

## Step 11 - Stop Or Restart The System

Restart:

```bash
docker compose --env-file ./docker.env restart
```

Stop without deleting data:

```bash
docker compose --env-file ./docker.env down
```

Start again:

```bash
docker compose --env-file ./docker.env up -d
```
