# MSR-AMIS Deployment

## Production Shape

Recommended production deployment:

`Desktop Client -> API Server -> PostgreSQL Server`

The desktop should call the API. It should not connect directly to PostgreSQL over the network.

## Local Development

For development:

- PostgreSQL can be installed locally
- the API can run on `localhost`
- the desktop can run in `REMOTE_API` mode against the local API

Typical local values:

```env
MSR_AMIS_DATA_MODE=REMOTE_API
MSR_AMIS_API_BASE_URL=http://localhost:8090
MSR_AMIS_DB_URL=jdbc:postgresql://localhost:5432/msr_amis
```

## Server Deployment

Recommended server deployment:

- Linux server or VPS
- PostgreSQL installed on the server
- Spring Boot API deployed on the same server or a trusted application server
- PostgreSQL not exposed directly to desktop clients
- reverse proxy and firewall rules applied as needed

## Operational Guidance

- prefer API access over direct database access
- keep database credentials on the server side
- use strong passwords and controlled network access
- use environment variables for deployment secrets
- back up PostgreSQL on the server side in centralized mode

## Offline Note

Central PostgreSQL alone does not provide offline capability.

If offline operation is required later, the project will need:

- local persistence
- sync queue
- conflict handling
- controlled merge strategy

That is a separate architecture feature from the centralized online deployment.
