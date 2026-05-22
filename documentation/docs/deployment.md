# MSR-AMIS Deployment

## Production Shape

Recommended production deployment:

```text
Desktop Client -> http://143.198.153.43:8090 -> API Server -> PostgreSQL Server :5432
```

The desktop must call the API. It must not connect directly to PostgreSQL over
the network.

For the local server rollout, use Docker Compose. The server clones the GitHub
repository, creates `docker.env`, and starts both PostgreSQL and the API from
the same project folder.

Main guide:

- [Docker Server Deployment](docker-server-deployment.md)

## 1. Prepare The Server

Install these on the server:

- Git
- Docker Engine or Docker Desktop
- Docker Compose

Check:

```bash
git --version
docker --version
docker compose version
```

## 2. Clone The Repository

```bash
git clone https://github.com/Wiston-Kautsa/MSR_AMIS_Desktop_Central_Database.git
cd MSR_AMIS_Desktop_Central_Database
```

## 3. Configure Docker Environment

Create the real server environment file:

```bash
cp docker.env.example docker.env
```

Edit `docker.env` and replace all passwords and secrets.

Required production values:

```env
POSTGRES_DB=msr_amis
POSTGRES_USER=msr_amis_user
POSTGRES_PASSWORD=replace_with_strong_database_password
MSR_AMIS_DB_USERNAME=msr_amis_user
MSR_AMIS_DB_PASSWORD=replace_with_same_strong_database_password
MSR_AMIS_API_PORT=8090
MSR_AMIS_JWT_SECRET=replace_with_generated_base64_secret
MSR_AMIS_PRIMARY_SUPER_ADMIN_EMAIL=msramis@nlgfc.gov.mw
MSR_AMIS_SMTP_HOST=mail.nlgfc.gov.mw
MSR_AMIS_SMTP_PORT=465
MSR_AMIS_SMTP_USERNAME=msramis@nlgfc.gov.mw
MSR_AMIS_SMTP_PASSWORD=replace_with_mailbox_password
MSR_AMIS_SMTP_FROM=msramis@nlgfc.gov.mw
MSR_AMIS_SMTP_STARTTLS=false
MSR_AMIS_SMTP_SSL=true
MSR_AMIS_SUPER_USER_STATUS_EMAILS_ENABLED=true
MSR_AMIS_OPERATION_EMAILS_ENABLED=false
MSR_AMIS_OPERATION_EMAIL_RECIPIENTS=msramis@nlgfc.gov.mw
```

Generate the JWT secret:

```bash
openssl rand -base64 64
```

Keep `docker.env` only on the server. Do not send it to desktop users and do
not commit it to Git.

## 4. Start The System

```bash
docker compose --env-file ./docker.env up -d --build
```

Check containers:

```bash
docker compose --env-file ./docker.env ps
```

Check API health:

```bash
curl http://localhost:8090/actuator/health
```

Expected response:

```json
{"status":"UP"}
```

## 5. Open Server Firewall

Allow client PCs to reach only the API port:

```text
TCP 8090
```

Do not expose PostgreSQL port `5432` to desktop clients.

From a client PC, test:

```bash
curl http://143.198.153.43:8090/actuator/health
```

## 6. Confirm Primary Super Admin

After the API starts and migrations run, the configured primary Super Admin is:

```text
email: msramis@nlgfc.gov.mw
username: MSRAMIS Admin
name: MSRAMIS Admin
role: SUPER_ADMIN
```

System status emails are sent only to `MSR_AMIS_PRIMARY_SUPER_ADMIN_EMAIL` when
`MSR_AMIS_SUPER_USER_STATUS_EMAILS_ENABLED=true`.

## 7. Configure Desktop Clients

Each desktop client `.env` must point to the server:

```env
MSR_AMIS_DATA_MODE=REMOTE_API
MSR_AMIS_API_BASE_URL=http://143.198.153.43:8090
APP_MODE=REMOTE_API
API_BASE_URL=http://143.198.153.43:8090
```

Example:

```env
MSR_AMIS_API_BASE_URL=http://143.198.153.43:8090
API_BASE_URL=http://143.198.153.43:8090
```

Do not use `localhost` on client machines unless the API is installed on that
same client machine.

## 8. Update The Server Later

From the server repository folder:

```bash
git pull
docker compose --env-file ./docker.env up -d --build
docker compose --env-file ./docker.env ps
curl http://localhost:8090/actuator/health
```

## 9. Backup Guidance

PostgreSQL is the primary database in centralized deployment. Backups must be
taken on the server side.

The included backup scripts are available for Windows environments where
PostgreSQL tools are installed:

```powershell
.\scripts\postgres-backup.ps1
.\scripts\register-postgres-backup-task.ps1 -StartTime "22:00"
```

Desktop Backup & Restore is for `LOCAL_DATABASE` mode only. It is not the
official backup process for centralized deployment.

## 10. Troubleshooting

If a client shows `API not reachable`:

1. Check `http://localhost:8090/actuator/health` on the server.
2. Check `http://143.198.153.43:8090/actuator/health` from the client PC.
3. Confirm the client `.env` uses the server address, not `localhost`.
4. Confirm the server firewall allows inbound TCP `8090`.
5. Check logs:

```bash
docker compose --env-file ./docker.env logs -f api
docker compose --env-file ./docker.env logs -f db
```

See [Troubleshooting](troubleshooting.md) for the longer checklist.
