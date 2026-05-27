# MSR-AMIS Server Deployment Steps

Use [Docker Server Deployment](docker-server-deployment.md) as the canonical
step-by-step guide for the local server.

Recommended server shape:

```text
Desktop client PCs -> http://YOUR_SERVER_HOST:8090 -> API container -> PostgreSQL container
```

Short checklist:

```bash
sudo apt update
sudo apt install -y git docker.io docker-compose-plugin
git clone https://github.com/Wiston-Kautsa/MSR_AMIS_Desktop_Central_Database.git
cd MSR_AMIS_Desktop_Central_Database
cp docker.env.example docker.env
nano docker.env
docker compose --env-file ./docker.env up -d --build
docker compose --env-file ./docker.env ps
curl http://localhost:8090/actuator/health
```

Required server config values are documented in
[Docker Server Deployment](docker-server-deployment.md).

Client desktop `.env` files must use:

```env
MSR_AMIS_DATA_MODE=REMOTE_API
MSR_AMIS_API_BASE_URL=http://YOUR_SERVER_HOST:8090
MSR_AMIS_AUTO_MIRROR_AFTER_MUTATION=false

APP_MODE=REMOTE_API
API_BASE_URL=http://YOUR_SERVER_HOST:8090
```
