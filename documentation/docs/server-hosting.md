# Server Hosting

This setup lets users install only the desktop app and log in. Database
credentials stay on the server.

Recommended deployment:

```text
Desktop EXE -> http://143.198.153.43:8090 -> API container -> PostgreSQL container
```

For the full local server setup, use:

- [Docker Server Deployment](docker-server-deployment.md)

The server should clone the GitHub repository, create a real `docker.env`, and
start the containers with Docker Compose.

## Step 1 - Server Setup

```bash
sudo apt update
sudo apt install -y git docker.io docker-compose-plugin
git clone https://github.com/Wiston-Kautsa/MSR_AMIS_Desktop_Central_Database.git
cd MSR_AMIS_Desktop_Central_Database
cp docker.env.example docker.env
nano docker.env
docker compose --env-file ./docker.env up -d --build
```

## Step 2 - Health Check

On the server:

```bash
curl http://localhost:8090/actuator/health
```

From a client computer:

```powershell
Invoke-RestMethod http://143.198.153.43:8090/actuator/health
```

The response should include:

```json
{"status":"UP"}
```

## Step 3 - Desktop Installer

Set the hosted API URL before packaging:

```powershell
$env:MSR_AMIS_PACKAGE_API_BASE_URL="http://143.198.153.43:8090"
.\scripts\build-desktop.cmd
```

Share only:

- `dist\MSR AMIS-1.0.0.exe`
- user login details

Do not share database credentials or `docker.env` with desktop users.
