# url-shortener-ai-assisted

A URL shortener service (Java 21, Spring Boot, PostgreSQL) built through an engineer-led,
AI-assisted workflow. Requirements, decomposition, and progress are tracked in this repository's
GitHub Issues and Pull Requests.

> Work in progress. The service creates short links (API key required) and redirects them, every
> destination passes a strict URL policy, and every error is an RFC 7807 problem response. The
> remaining features arrive slice by slice (see the issues).

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
follows it (`302` to the destination, `Cache-Control: no-store`), checks that a destination with
user information is rejected with `400`, and checks that an unknown code returns `404`. It prints
`SMOKE PASS` and exits 0 when all of that holds.

### Try it

```bash
curl -i -X POST http://127.0.0.1:8080/api/v1/links \
  -H "X-API-Key: local-demo-key-NOT-A-SECRET-do-not-use-elsewhere" \
  -H "Content-Type: application/json" \
  -d '{"destination":"https://example.com/"}'
```

The response contains `shortUrl`; opening it redirects to the destination.

### Destination rules

A destination must be an `http` or `https` URL of at most 2,048 characters with a host. It is
rejected if it contains whitespace, control or invisible format characters, user information
(`user@host`), a non-ASCII or punycode (`xn--`) host, any other non-ASCII character, or anything
that is not a valid URI. Nothing is trimmed or re-encoded: percent-encode characters yourself.

The scheme and host are lowercased, and everything else is kept exactly as sent. That string is
what is stored, returned as `destination`, and sent in the redirect's `Location`. A rejection is a
`400` problem whose `reason` property names the rule, for example `USERINFO_NOT_ALLOWED`.

- **IP-literal hosts are accepted on purpose.** The service never fetches destinations, so there
  is no server-side request forgery exposure.
- **The shortener's own host is rejected,** ignoring case, a trailing dot and the port. Under
  compose that host is `localhost`, so every `localhost` destination is rejected locally. Another
  name for the same host (`127.0.0.1`, a DNS alias) is not detected, since no DNS lookups are
  made. That redirect loop is a known limitation.

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

The app refuses to start if `APP_API_KEY` is missing, shorter than 32 characters, contains anything
other than printable ASCII (including whitespace, such as a key file's trailing newline), or is the
published demo key (which is only accepted under the `local` profile that compose sets). Clients
send the key in the `X-API-Key` header.

Short URLs are built from `APP_PUBLIC_BASE_URL` (default `http://localhost:8080`), never from
request headers. It must be an `http` or `https` URL with a host and no credentials, query, or
fragment. A path is allowed (`https://example.com/s` gives `https://example.com/s/{code}`), but the
app itself only serves `/{code}`, so a proxy in front must strip that prefix.

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
