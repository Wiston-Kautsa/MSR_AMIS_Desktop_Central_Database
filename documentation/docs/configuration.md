# MSR-AMIS Configuration

## Overview

The project supports development configuration through a root `.env` file and deployment configuration through real environment variables.

Use [.env.example](/D:/School/JAVA/MSR-AMIS_destop_application/.env.example) as the starting template.

## Desktop Configuration

The desktop app reads configuration in this order:

1. Java system properties
2. OS environment variables
3. root `.env`

Key desktop settings:

- `MSR_AMIS_DATA_MODE`
- `MSR_AMIS_API_BASE_URL`

Supported aliases for development convenience:

- `APP_MODE`
- `API_BASE_URL`

Example:

```env
MSR_AMIS_DATA_MODE=AUTO
MSR_AMIS_API_BASE_URL=http://192.168.1.10:8090

APP_MODE=AUTO
API_BASE_URL=http://192.168.1.10:8090
```

Use `localhost` only when the API is running on the same computer as the desktop app. Client machines should point to the server IP address or DNS name.

## API Configuration

The Spring Boot API reads:

- `MSR_AMIS_DB_URL`
- `MSR_AMIS_DB_USERNAME`
- `MSR_AMIS_DB_PASSWORD`
- `MSR_AMIS_API_PORT`
- `MSR_AMIS_JWT_SECRET`
- `MSR_AMIS_JWT_EXPIRATION_SECONDS`

The API also imports the root `.env` file as an optional configuration source during development.

Example:

```env
MSR_AMIS_DB_URL=jdbc:postgresql://localhost:5432/msr_amis
MSR_AMIS_DB_USERNAME=postgres
MSR_AMIS_DB_PASSWORD=postgres
MSR_AMIS_API_PORT=8090
```

## Mode Policy

### `REMOTE_API`

- strict online mode
- requires a reachable API base URL
- shows an API unreachable error if the API is down
- useful for testing fully centralized behavior

### `AUTO`

- recommended day-to-day desktop mode
- keeps SQLite as the local working mirror
- sends changes to PostgreSQL through the API when online
- queues changes locally when offline
- requires Sync Center processing after offline work

### `LOCAL_DATABASE`

- available for development or controlled local-only fallback
- does not automatically share changes unless used with the sync queue through `AUTO`

## Guidance

- use `.env` for local development
- use real environment variables in server deployment
- do not commit real secrets into the repository
- keep `.env` local; it is ignored by git
- configure desktop clients with the server API URL, not `localhost`, unless the API is installed locally
- use `AUTO` for normal users who may experience network outages
