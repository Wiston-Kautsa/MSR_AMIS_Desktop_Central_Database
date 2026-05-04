# MSR-AMIS

MSR-AMIS is a JavaFX desktop client backed by a Spring Boot API and PostgreSQL.

Current production direction:

`Desktop Client -> REST API -> PostgreSQL`

## Project Structure

- `src/`
  Desktop JavaFX application
- `msr-amis-api/`
  Spring Boot backend API
- `documentation/`
  Architecture, deployment, and configuration notes

## Current Operating Model

- The desktop is the user interface.
- The API owns authentication, authorization, business rules, validation, and persistence.
- PostgreSQL is the central source of truth in `REMOTE_API` mode.
- The dashboard shows a live connection indicator so users can see whether they are connected to the central system.

## Configuration

The project now supports a root `.env` file for development.

Desktop configuration resolution order:

1. Java system properties
2. OS environment variables
3. root `.env`

API configuration:

- Spring Boot imports the root `.env` file as an optional config source.
- Production deployments should prefer real environment variables over a local `.env`.

Start from [.env.example](/D:/School/JAVA/MSR-AMIS_destop_application/.env.example).

## Main Modes

- `REMOTE_API`
  Intended production mode. Desktop talks to the API, and the API talks to PostgreSQL.
- `LOCAL_DATABASE`
  Retained for development or controlled fallback work. Not the target production model.

## Development

Desktop compile:

```powershell
.\mvnw.cmd -DskipTests compile
```

API compile:

```powershell
cd msr-amis-api
..\mvnw.cmd -DskipTests compile
```

## Documentation

- [Documentation Index](</D:/School/JAVA/MSR-AMIS_destop_application/documentation/README.md>)
- [Architecture](</D:/School/JAVA/MSR-AMIS_destop_application/documentation/docs/architecture.md>)
- [Configuration](</D:/School/JAVA/MSR-AMIS_destop_application/documentation/docs/configuration.md>)
- [Deployment](</D:/School/JAVA/MSR-AMIS_destop_application/documentation/docs/deployment.md>)
- [Current System Alignment](</D:/School/JAVA/MSR-AMIS_destop_application/documentation/docs/current-system-alignment.md>)
- [Final Corrected Design](</D:/School/JAVA/MSR-AMIS_destop_application/documentation/docs/final-corrected-design.md>)
- [API Migration Plan](</D:/School/JAVA/MSR-AMIS_destop_application/documentation/docs/api-migration-plan.md>)
