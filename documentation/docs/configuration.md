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
MSR_AMIS_DATA_MODE=REMOTE_API
MSR_AMIS_API_BASE_URL=http://localhost:8090
```

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

- preferred production mode
- requires a reachable API base URL

### `LOCAL_DATABASE`

- available for development or controlled fallback
- not the intended shared production model

## Guidance

- use `.env` for local development
- use real environment variables in server deployment
- do not commit real secrets into the repository
- keep `.env` local; it is ignored by git
