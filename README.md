# url-shortener-ai-assisted

A URL shortener service (Java 21, Spring Boot, PostgreSQL) built through an engineer-led,
AI-assisted workflow. Requirements, decomposition, and progress are tracked in this repository's
GitHub Issues and Pull Requests.

> Work in progress. The service currently starts, connects to PostgreSQL, and reports health.
> Features arrive slice by slice; see the issues.

## Run with Docker

The only prerequisite is [Docker](https://docs.docker.com/get-docker/): Docker Engine 25 or
later with Compose v2 (Docker Desktop includes both).

```bash
docker compose up --build
```

This starts PostgreSQL and the app. The app waits for the database, then becomes healthy.

| Port | What | Reachable from |
|---|---|---|
| `8080` | Application API | `127.0.0.1` only |
| `8081` | Health (`/actuator/health/readiness`, `/actuator/health/liveness`) | `127.0.0.1` only |

PostgreSQL is not published to your machine.

### Smoke test

With the stack running, in a second terminal:

```bash
docker compose run --rm smoke
```

It prints `SMOKE PASS` and exits 0 when readiness is UP with the database included.

### Stop

```bash
docker compose down
```

Add `-v` to also delete the database volume.

> The compose file contains a **published local demo API key** and database password. They are
> labeled as such and are not secrets. Never reuse them outside local development.

## Run without Docker

_Manually verified._ Requires JDK 21 and a running PostgreSQL 18 that you provide.

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/urlshortener
export SPRING_DATASOURCE_USERNAME=urlshortener
export SPRING_DATASOURCE_PASSWORD=<your password>
./mvnw spring-boot:run
```

On Windows, use `mvnw.cmd` and set the variables with `$env:NAME = "value"` (PowerShell).
Readiness is then at `http://localhost:8081/actuator/health/readiness`.

## Build and test

Requires JDK 21 and Docker (the integration tests start PostgreSQL with Testcontainers).

```bash
./mvnw verify
```
