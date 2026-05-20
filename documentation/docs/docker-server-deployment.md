# Docker Server Deployment

Use this when the server will clone the project from Git and Docker will run
both PostgreSQL and the API.

```text
Desktop client PCs -> API container :8090 -> PostgreSQL container :5432
```

The server needs the Git checkout because Docker builds the API image from the
`msr-amis-api` source. Desktop users do not receive source code, database
credentials, or `docker.env`.

## 1. Install Server Requirements

Install:

- Git
- Docker
- Docker Compose plugin

Ubuntu example:

```bash
sudo apt update
sudo apt install -y git docker.io docker-compose-plugin
sudo systemctl enable docker
sudo systemctl start docker
```

Check:

```bash
git --version
docker --version
docker compose version
```

## 2. Clone The Project

On the server:

```bash
git clone YOUR_REPOSITORY_URL
cd MSR-AMIS_destop_application
```

For updates later:

```bash
git pull
```

## 3. Configure Docker Environment

Create the real server environment file:

```bash
cp docker.env.example docker.env
```

Edit `docker.env` and replace every placeholder:

```env
POSTGRES_DB=msr_amis
POSTGRES_USER=msr_amis_user
POSTGRES_PASSWORD=strong_database_password

MSR_AMIS_DB_USERNAME=msr_amis_user
MSR_AMIS_DB_PASSWORD=strong_database_password
MSR_AMIS_API_PORT=8090
MSR_AMIS_JWT_SECRET=strong_base64_secret

MSR_AMIS_PRIMARY_SUPER_ADMIN_EMAIL=msramis@nlgfc.gov.mw
MSR_AMIS_SETUP_ADMIN_EMAIL=setup-admin@example.com
MSR_AMIS_SETUP_USER_EMAIL=setup-user@example.com
```

Generate a JWT secret:

```bash
openssl rand -base64 64
```

Keep `docker.env` only on the server.

## 4. Start The Containers

From the project root on the server:

```bash
docker compose --env-file ./docker.env up -d --build
```

This starts:

- `msr-amis-db`
- `msr-amis-api`

PostgreSQL data is stored in the Docker volume
`msr-amis-postgres-data`, so data survives container rebuilds.

## 5. Check Status

```bash
docker compose --env-file ./docker.env ps
```

Check API health on the server:

```bash
curl http://localhost:8090/actuator/health
```

Expected:

```json
{"status":"UP"}
```

## 6. Open Firewall

Allow inbound TCP `8090`.

Ubuntu example:

```bash
sudo ufw allow 8090/tcp
```

From a client PC:

```powershell
Invoke-RestMethod http://SERVER_IP_OR_NAME:8090/actuator/health
```

## 7. Configure Desktop Clients

Client `.env` files must point to the server:

```env
MSR_AMIS_DATA_MODE=REMOTE_API
MSR_AMIS_API_BASE_URL=http://SERVER_IP_OR_NAME:8090
MSR_AMIS_AUTO_MIRROR_AFTER_MUTATION=false

APP_MODE=REMOTE_API
API_BASE_URL=http://SERVER_IP_OR_NAME:8090
```

## 8. Build Desktop Installer For This Server

On your development machine:

```powershell
$env:MSR_AMIS_PACKAGE_API_BASE_URL="http://SERVER_IP_OR_NAME:8090"
.\scripts\build-desktop.cmd
```

Give users only:

- the desktop installer from `dist`
- their username
- their password

## 9. Update The Server Later

On the server:

```bash
git pull
docker compose --env-file ./docker.env up -d --build
```

Check health again:

```bash
curl http://localhost:8090/actuator/health
```
