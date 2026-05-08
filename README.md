# MSR-AMIS

MSR-AMIS is a JavaFX desktop client backed by a Spring Boot API and PostgreSQL.

Current production operating model:

`Desktop Client -> local SQLite mirror -> REST API -> PostgreSQL`

PostgreSQL remains the central source of truth. The desktop uses SQLite as a local working mirror so users can continue working when the API or network is temporarily unavailable.

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
- PostgreSQL is the central source of truth.
- `AUTO` mode is the recommended desktop mode for day-to-day use.
- In `AUTO` mode, the desktop uses SQLite locally, sends changes to PostgreSQL through the API when online, queues changes when offline, and refreshes SQLite from PostgreSQL after sync.
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

- `AUTO`
  Recommended day-to-day desktop mode. The app works online through the API when reachable and falls back to local SQLite when unreachable.
- `REMOTE_API`
  Strict online mode. Desktop talks directly to the API and cannot continue if the API is unreachable.
- `LOCAL_DATABASE`
  Local-only mode for development or controlled fallback work.

## Desktop Packaging

Build the app image, MSI, and EXE:

```powershell
.\scripts\build-desktop.cmd
```

Generated files:

- `dist\MSR AMIS\MSR AMIS.exe`
- `dist\MSR AMIS-1.0.0.msi`
- `dist\MSR AMIS-1.0.0.exe`

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
- [Daily Operations](</D:/School/JAVA/MSR-AMIS_destop_application/documentation/docs/daily-operations.md>)
- [Current System Alignment](</D:/School/JAVA/MSR-AMIS_destop_application/documentation/docs/current-system-alignment.md>)
- [Final Corrected Design](</D:/School/JAVA/MSR-AMIS_destop_application/documentation/docs/final-corrected-design.md>)
- [API Migration Plan](</D:/School/JAVA/MSR-AMIS_destop_application/documentation/docs/api-migration-plan.md>)
