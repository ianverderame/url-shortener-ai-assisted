# url-shortener-ai-assisted

A URL shortener service (Java 21, Spring Boot, PostgreSQL) built through an engineer-led,
AI-assisted workflow. Requirements, decomposition, and progress are tracked in this repository's
GitHub Issues and Pull Requests.

> Work in progress. The service creates short links (API key required) and redirects them, and
> every error is an RFC 7807 problem response. Destination validation is minimal for now; it and
> the remaining features arrive slice by slice (see the issues).

## Run with Docker

The only prerequisite is [Docker](https://docs.docker.com/get-docker/): Docker Engine 25 or
later with Compose v2 (Docker Desktop includes both).

```bash
docker compose up --build
```

This starts PostgreSQL and the app. The app waits for the database, then becomes healthy.

| Port | What | Reachable from |
|---|---|---|
| `8080` | Application API and interactive docs (`/swagger-ui/index.html`) | `127.0.0.1` only |
| `8081` | Health (`/actuator/health/readiness`, `/actuator/health/liveness`) | `127.0.0.1` only |

PostgreSQL is not published to your machine.

### Smoke test

With the stack running, in a second terminal:

```bash
docker compose run --rm smoke
```

It checks that readiness is UP with the database included, creates a link with the demo key,
follows it (`302` to the destination, `Cache-Control: no-store`), and checks that an unknown code
returns `404`. It prints `SMOKE PASS` and exits 0 when all of that holds.

### Try it

```bash
curl -i -X POST http://127.0.0.1:8080/api/v1/links \
  -H "X-API-Key: local-demo-key-NOT-A-SECRET-do-not-use-elsewhere" \
  -H "Content-Type: application/json" \
  -d '{"destination":"https://example.com/"}'
```

The response contains `shortUrl`; opening it redirects to the destination.

### Stop

```bash
docker compose down
```

Add `-v` to also delete the database volume.

> The compose file contains a **published local demo API key** and database password. They are
> labeled as such and are not secrets. Never reuse them outside local development.

## Run without Docker

_Manually verified._ Requires JDK 21 and a running PostgreSQL 18 that you provide. On Linux and
macOS the Maven wrapper also needs `unzip` (it verifies the checksum of the Maven `.zip`).

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/urlshortener
export SPRING_DATASOURCE_USERNAME=urlshortener
export SPRING_DATASOURCE_PASSWORD=<your password>
export APP_API_KEY=<a key of at least 32 characters>
./mvnw spring-boot:run
```

The app refuses to start if `APP_API_KEY` is missing, shorter than 32 characters, or the published
demo key (which is only accepted under the `local` profile that compose sets). Clients send the key
in the `X-API-Key` header.

On Windows, use `mvnw.cmd` and set the variables with `$env:NAME = "value"` (PowerShell).
Readiness is then at `http://localhost:8081/actuator/health/readiness`.

## Build and test

Requires JDK 21 and Docker (the integration tests start PostgreSQL with Testcontainers), plus
`unzip` on Linux and macOS for the Maven wrapper.

```bash
./mvnw verify
```

`verify` also checks formatting (google-java-format) and treats compiler warnings as errors.
Fix formatting with `./mvnw spotless:apply`.
