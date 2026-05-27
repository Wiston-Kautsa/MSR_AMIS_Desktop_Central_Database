# Documentation

This folder contains the project documentation set for MSR-AMIS.

Main documents:

- [Current System Alignment](docs/current-system-alignment.md)
- [System Overview](docs/system-overview.md)
- [Configuration](docs/configuration.md)
- [Daily Operations](docs/daily-operations.md)
- [Docker Server Deployment](docs/docker-server-deployment.md)
- [Server Deployment Steps](docs/server-deployment-steps.md)
- [Server Hosting](docs/server-hosting.md)
- [Sync Backend Contract](docs/sync-backend-contract.md)
- [Sync Implementation Blueprint](docs/sync-implementation-blueprint.md)
- [Architecture](docs/architecture.md)
- [Deployment](docs/deployment.md)
- [Troubleshooting](docs/troubleshooting.md)
- [Final Corrected Design](docs/final-corrected-design.md)
- [API Migration Plan](docs/api-migration-plan.md)

API module notes remain in:

- [API README](../msr-amis-api/README.md)

For the local server setup, use [Docker Server Deployment](docs/docker-server-deployment.md). The Java runtime guide is kept only as an alternative for administrators who deliberately choose not to use Docker: [Alternative Java Runtime Deployment](docs/server-runtime-deployment.md).

Start with [Current System Alignment](docs/current-system-alignment.md) when checking whether a process description matches the code. For `API not reachable`, use the troubleshooting checklist.

Server backup scripts:

- [PostgreSQL backup](../scripts/postgres-backup.ps1)
- [Register scheduled PostgreSQL backup](../scripts/register-postgres-backup-task.ps1)
