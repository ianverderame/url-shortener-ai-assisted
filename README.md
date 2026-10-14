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
| `8081` | Health (`/actuator/health/readiness`, `/actuator/health/liveness`) and metrics (`/actuator/metrics`), no key needed | `127.0.0.1` only |

PostgreSQL is not published to your machine.

### Smoke test

With the stack running, in a second terminal:

```bash
docker compose run --rm smoke
```

It checks that readiness is UP with the database included, creates a link with the demo key,
follows it (`302` to the destination, `Cache-Control: no-store`), and reads the link's stats until
they show that one click (recording is asynchronous, so it polls up to 20 times, half a second
apart). It also checks that a destination with user information is rejected with `400`, and that
an unknown code returns `404`. It prints `SMOKE PASS` and exits 0 when all of that holds.

### Try it

```bash
curl -i -X POST http://127.0.0.1:8080/api/v1/links \
  -H "X-API-Key: local-demo-key-NOT-A-SECRET-do-not-use-elsewhere" \
  -H "Content-Type: application/json" \
  -d '{"destination":"https://example.com/"}'
```

The response contains `shortUrl`; opening it redirects to the destination.

Each `GET` of a short link counts as one click (`HEAD` and unknown codes don't). To read a link's
clicks, use its `code`:

```bash
curl -i "http://127.0.0.1:8080/api/v1/links/{code}/stats?from=2026-10-01&to=2026-10-13" \
  -H "X-API-Key: local-demo-key-NOT-A-SECRET-do-not-use-elsewhere"
```

`from` and `to` are UTC dates, both inclusive. Without them you get the 30 days ending today
(UTC). The response has the range `total` and one entry per day in `days`, oldest first, with `0`
for days without clicks. A range longer than 366 days, `from` after `to`, `to` after today, or a
date before `0001-01-01` is a `400`; an unknown code is a `404`. Clicks are recorded off the
redirect path, so one can take a moment to appear, and under heavy load or a database outage a
click can be dropped (counted, never blocking the redirect).

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

### Metrics and service-level objectives

The built-in metrics endpoint is served on the management port only (the application port does
not serve it), without a key. Health and metrics are the only actuator endpoints exposed.

```bash
curl http://127.0.0.1:8081/actuator/metrics                      # names of all meters
curl http://127.0.0.1:8081/actuator/metrics/hikaricp.connections.timeout
curl "http://127.0.0.1:8081/actuator/metrics/clicks.lost?tag=reason:write_failure"
curl "http://127.0.0.1:8081/actuator/metrics/http.server.requests?tag=status:503"
```

Service-specific meters: `links.code.generation.exhausted` (link creation gave up after repeated
code collisions) and `clicks.lost`, tagged `reason` (`overflow`, `write_failure`, `shutdown`).
Request meters (`http.server.requests`) and the connection pool meters (`hikaricp.connections.*`)
come from Spring Boot; the pool meters appear once the pool has started.

These are the service's objectives (decided in
[#9](https://github.com/ianverderame/url-shortener-ai-assisted/issues/9)) and how each is measured:

| SLO | Target | Measured by |
|---|---|---|
| Bounded failure | 100% of API requests answer within 3 s with a `503` problem while the database is down or hung | Integration tests against a stopped or paused Testcontainers database: [`DatabaseOutageIT`](src/test/java/io/github/ianverderame/urlshortener/DatabaseOutageIT.java), [`DatabaseHangIT`](src/test/java/io/github/ianverderame/urlshortener/DatabaseHangIT.java), [`DatabaseHangAtPoolStartIT`](src/test/java/io/github/ianverderame/urlshortener/DatabaseHangAtPoolStartIT.java) (each failure also logs one line); `hikaricp.connections.timeout`, and `http.server.requests` with `status:503` |
| Truthful readiness | Readiness reports `DOWN` within 2 s per probe while the database is unreachable, and `UP` within one probe interval after recovery | [`ReadinessDuringOutageIT`](src/test/java/io/github/ianverderame/urlshortener/ReadinessDuringOutageIT.java), which also checks that liveness stays `UP`; the compose healthcheck (3 s timeout) gets an answer instead of timing out |
| Clean shutdown | 100% of SIGTERMs exit with code `143` inside the 30 s grace period, in every database state | [`ShutdownTimeoutBudgetIT`](src/test/java/io/github/ianverderame/urlshortener/management/ShutdownTimeoutBudgetIT.java) pins the management context's own shutdown-phase timeout; the CI `reviewer-path` job's scripted checks cover a healthy shutdown under load (reconciled against the database), and a hung database both idle and with a request in flight ([#27](https://github.com/ianverderame/url-shortener-ai-assisted/issues/27)) |
| Analytics loss counted | Every lost click is counted by reason; the target ratio is set once a baseline is measured | `clicks.lost` (by `reason`) against clicks served: `http.server.requests` with `method:GET` and `status:302` (each redirect `GET` is one click) |

The first three objectives are verified by the tests and checks named in the table.

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
