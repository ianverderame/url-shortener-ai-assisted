# Manual testing guide

This guide is a hands-on tour of every feature of the running service: create a link, follow it,
read its stats, see the destination policy and the errors, check health and metrics, and watch the
service ride out a database outage. Follow it from top to bottom; each step says what to run and
what you should see.

The smoke test (step 2) and `./mvnw verify` are the automated equivalent. This guide is for seeing
the behavior for yourself.

## Before you start

- Docker Engine 25 or later with Compose v2 or later (Docker Desktop includes both), running.
- Ports `8080` and `8081` free on your machine.
- A terminal in the repository root.

**Which commands to use.** Commands are given for Windows PowerShell and for bash (Linux, macOS,
Git Bash). They differ in three places, all handled below:

- In Windows PowerShell 5.1, `curl` is an alias for `Invoke-WebRequest`. Always type **`curl.exe`**.
- Windows PowerShell 5.1 strips the quotes from JSON passed on a command line to `curl.exe`, so
  POST bodies are written to a temporary file first (step 4).
- `Invoke-RestMethod` throws on an error status and hides the response body, so error cases use
  `curl.exe`.

Where a step shows only a `curl.exe` command, bash users type `curl` instead, and `$KEY` and
`$CODE` (set in step 4) in place of `$key` and `$code`. The PowerShell
commands were run end to end on Windows PowerShell 5.1. The bash commands follow
the README and the smoke test.

## 1. Start the stack

```bash
docker compose up --build
```

This builds the app image (the Maven build runs inside it, so the first build is the slowest),
starts PostgreSQL, runs the database migrations, and starts the app. Leave it running. In a
**second terminal**, check that the app is healthy:

```bash
docker compose ps
```

✅ The `app` row shows `(healthy)`.

## 2. Run the smoke test

```bash
docker compose run --rm smoke
```

✅ Six progress lines (readiness, create, follow, stats, rejected destination, unknown code), then
`SMOKE PASS`. If a check fails, a `SMOKE FAIL:` line names it.

## 3. Readiness

```bash
curl.exe -s http://127.0.0.1:8081/actuator/health/readiness
```

✅ `{"components":{"db":{"status":"UP"},"readinessState":{"status":"UP"}},"status":"UP"}`

Port `8081` is the management port (health and metrics). It needs no key.

## 4. Create a short link

The API key is sent in the `X-API-Key` header. Compose runs the app with a **published local demo
key**; it is not a secret, and the app rejects it outside the `local` profile.

**PowerShell.** Set the key, define a helper that POSTs a destination, then create a link:

```powershell
$key = 'local-demo-key-NOT-A-SECRET-do-not-use-elsewhere'

function New-TestLink($dest) { $f = New-TemporaryFile; @{ destination = $dest } | ConvertTo-Json -Compress | Set-Content -Encoding ascii $f; curl.exe -s -w "`n=> HTTP %{http_code}`n" -X POST http://127.0.0.1:8080/api/v1/links -H "X-API-Key: $key" -H 'Content-Type: application/json' --data-binary "@$f"; Remove-Item $f }

New-TestLink 'https://example.com/'
```

**bash:**

```bash
KEY=local-demo-key-NOT-A-SECRET-do-not-use-elsewhere
curl -s -w '\n=> HTTP %{http_code}\n' -X POST http://127.0.0.1:8080/api/v1/links \
  -H "X-API-Key: $KEY" -H 'Content-Type: application/json' \
  -d '{"destination":"https://example.com/"}'
```

✅ `=> HTTP 201` and a body with four fields:

```json
{"code":"Hlljahlk","shortUrl":"http://localhost:8080/Hlljahlk","destination":"https://example.com/","createdAt":"2026-10-14T07:12:10.896177Z"}
```

`code` is 8 letters and digits; `createdAt` is UTC. **Copy your `code`**: the next steps use it.

```powershell
$code = 'Hlljahlk'   # replace with your code
```

```bash
CODE=Hlljahlk        # replace with your code
```

## 5. Follow the short link

```bash
curl.exe -i "http://127.0.0.1:8080/$code"
```

(bash: `curl -i "http://127.0.0.1:8080/$CODE"`)

✅ The raw redirect, not followed:

```text
HTTP/1.1 302
Location: https://example.com/
Cache-Control: no-store
```

`no-store` keeps browsers from caching the redirect, so every visit is counted. You'll also see
`X-Content-Type-Options`, `X-XSS-Protection` and `X-Frame-Options`: Spring Security's standard
hardening headers.

Now open the `shortUrl` in your normal browser. ✅ It lands on example.com.

Every `GET` of a short link counts as one click, so the link now has **2 clicks**.

## 6. Read the link's stats

**PowerShell** (a one-line summary, then the full response):

```powershell
Invoke-RestMethod -Uri "http://127.0.0.1:8080/api/v1/links/$code/stats" -Headers @{ 'X-API-Key' = $key } | Select-Object code,from,to,total
$today = (Get-Date).ToUniversalTime().ToString('yyyy-MM-dd'); $from = (Get-Date).ToUniversalTime().AddDays(-2).ToString('yyyy-MM-dd')
Invoke-RestMethod -Uri "http://127.0.0.1:8080/api/v1/links/$code/stats?from=$from&to=$today" -Headers @{ 'X-API-Key' = $key } | ConvertTo-Json -Depth 3
```

**bash:**

```bash
curl -s -H "X-API-Key: $KEY" "http://127.0.0.1:8080/api/v1/links/$CODE/stats"
TODAY=$(date -u +%F); FROM=$(date -u -d '2 days ago' +%F)   # macOS: date -u -v-2d +%F
curl -s -H "X-API-Key: $KEY" "http://127.0.0.1:8080/api/v1/links/$CODE/stats?from=$FROM&to=$TODAY"
```

✅ Without `from` and `to`, you get the 30 days ending today (UTC), with `total` = 2. With a range
(UTC dates, both inclusive; the commands above use the last three days), you get one entry per day, oldest first, with
`0` for days without clicks. For example:

```json
{"code":"Hlljahlk","from":"2026-10-12","to":"2026-10-14","total":2,
 "days":[{"date":"2026-10-12","clicks":0},{"date":"2026-10-13","clicks":0},{"date":"2026-10-14","clicks":2}]}
```

Clicks are recorded off the redirect path; if a click is missing, ask again a moment later.

An invalid range (`from` after `to`):

```bash
curl.exe -i -H "X-API-Key: $key" "http://127.0.0.1:8080/api/v1/links/$code/stats?from=2026-10-14&to=2026-10-01"
```

✅ `HTTP/1.1 400`, `Content-Type: application/problem+json`:

```json
{"detail":"'from' must not be after 'to'.","instance":"/api/v1/links/Hlljahlk/stats","status":400,"title":"Bad Request","type":"about:blank"}
```

Every error is an RFC 7807 problem in this shape. `type: about:blank` means "no more specific type
than the status". Error responses also carry `Cache-Control: no-cache, no-store, ...`.

## 7. Destination policy

Each of these is rejected with `400`, and the `reason` property names the rule. The `detail` never
repeats the URL you sent.

In PowerShell, run `New-TestLink '<destination>'`. In bash, run the step 4 command with the
destination changed.

| Destination | Why it's rejected | ✅ `reason` |
|---|---|---|
| `https://trusted.example@evil.example/` | user information (a phishing pattern) | `USERINFO_NOT_ALLOWED` |
| `ftp://example.com/file` | only `http` and `https` | `UNSUPPORTED_SCHEME` |
| `http://localhost:8080/<code>` | the shortener's own host (a redirect loop) | `SELF_HOST` |
| `https://xn--80ak6aa92e.com/` | punycode host (homograph risk) | `NON_ASCII_HOST` |
| `https://example.com/a b` | whitespace | `WHITESPACE_OR_CONTROL` |

Example:

```json
{"detail":"Destination must not include user information.","instance":"/api/v1/links","status":400,"title":"Bad Request","type":"about:blank","reason":"USERINFO_NOT_ALLOWED"}
```

One that succeeds, showing normalization:

```powershell
New-TestLink 'HTTPS://Example.COM/Keep/This/Case'
```

✅ `201` with `"destination":"https://example.com/Keep/This/Case"`: the scheme and host are
lowercased; the rest is kept exactly as sent.

## 8. Authentication

The second command sends a wrong key: the demo key with one character added.

```powershell
$wrong = $key + 'x'
curl.exe -i "http://127.0.0.1:8080/api/v1/links/$code/stats"
curl.exe -i -H "X-API-Key: $wrong" "http://127.0.0.1:8080/api/v1/links/$code/stats"
```

(bash: `WRONG="${KEY}x"`, then the same commands with `curl`, `$CODE` and `$WRONG`)

✅ Both return the same `401`:

```json
{"detail":"A valid X-API-Key header is required.","instance":"/api/v1/links/Hlljahlk/stats","status":401,"title":"Unauthorized","type":"about:blank"}
```

A missing key and a wrong key are indistinguishable, and the key you sent is never echoed.
Redirects (step 5) need no key.

## 9. Unknown codes

```bash
curl.exe -i http://127.0.0.1:8080/Zz9Zz9Zz
curl.exe -i http://127.0.0.1:8080/abc
```

✅ Both return `404` problems. The first, a well-formed code with no link, says
`"No link has this code."`. The second isn't 8 characters, so it never reaches the redirect
handler and carries Spring's own wording, `"No static resource abc."`. That is a known
inconsistency (see the
[engineering summary](engineering-summary.md#assumptions-limitations-and-trade-offs)).

## 10. Interactive API docs (Swagger UI)

Open <http://127.0.0.1:8080/swagger-ui/index.html>.

1. Click **Authorize**, paste the demo key, click **Authorize**, then **Close**.
2. Open **POST /api/v1/links**, click **Try it out**.
3. In the request body, replace `"string"` with a public URL, keeping the quotes:
   `{ "destination": "https://www.wikipedia.org/" }`. Don't use a `localhost` URL; it's rejected
   as `SELF_HOST`.
4. Click **Execute**.

✅ `201` with a new `code` and `shortUrl`. Swagger also shows the equivalent `curl` command. Try
`"https://user@example.com/"` to see a `400` problem in the response panel.

Known gaps in the generated docs: the problem schema shows extra fields nested under
`properties`, but real responses put them at the top level (like `reason`), with `type`
`about:blank`; and the `503` description covers only code-generation exhaustion, not a database
outage (step 13).

## 11. Demo page

The demo page offers the same features in a browser, without a terminal. Open
<http://127.0.0.1:8080/>. (The engineer walked through the page end to end against the compose
stack in [PR #42](https://github.com/ianverderame/url-shortener-ai-assisted/pull/42).)

1. **API key:** paste the demo key. The page keeps it in memory only, with no cookies or browser
   storage, so a reload clears it. Your browser may offer to save it as a password; decline.
2. **Shorten a URL:** enter a public URL and click **Shorten**. ✅ The short link appears; open it
   and it lands on the destination.
3. **Click stats:** click **Get stats** for the code. ✅ The total and the days that had clicks.
4. **Error contract:** each button triggers one problem response. ✅ **Create without a key**
   shows `401`, **Create with an ftp:// URL** shows `400` with `UNSUPPORTED_SCHEME`, and **Stats
   for an unknown code** shows `404`.
5. **More:** links to Swagger UI (step 10), and to readiness and metrics on port `8081`.

The page is one static file served from the API's own origin, so it needs no CORS and the API's
security settings are unchanged. Destinations are rendered as text only.

## 12. Metrics

```bash
curl.exe -s http://127.0.0.1:8081/actuator/metrics
curl.exe -s "http://127.0.0.1:8081/actuator/metrics/http.server.requests?tag=status:302"
curl.exe -s http://127.0.0.1:8081/actuator/metrics/clicks.lost
```

✅
- The name list is long (JVM, Tomcat, Spring Security). Look for the four that matter here:
  `clicks.lost`, `links.code.generation.exhausted`, `http.server.requests`,
  `hikaricp.connections.*`.
- `http.server.requests` with `status:302` has a `COUNT` equal to the redirects served, one per
  click (4 if you followed this guide: the smoke test, curl, your browser in step 5, and the
  demo page in step 11). `MAX` resets to `0.0` after a
  short quiet period; that is how Micrometer reports maxima.
- `clicks.lost` has `COUNT` `0.0`, with tag values `overflow`, `write_failure`, `shutdown`.

## 13. Database outage and recovery

Stop the database while the app keeps running:

```bash
docker compose stop postgres
```

Time a redirect:

```powershell
curl.exe -s -w "`n=> HTTP %{http_code} in %{time_total}s`n" "http://127.0.0.1:8080/$code"
```

(bash: `curl -s -w '\n=> HTTP %{http_code} in %{time_total}s\n' "http://127.0.0.1:8080/$CODE"`)

✅ A `503` problem, well within 3 seconds (about 1 s in the run behind this guide), with no
internal details:

```json
{"detail":"The service is temporarily unavailable. Try again.","instance":"/Hlljahlk","status":503,"title":"Service Unavailable","type":"about:blank"}
```

Check health:

```powershell
curl.exe -s -w "`n=> HTTP %{http_code}`n" http://127.0.0.1:8081/actuator/health/readiness
curl.exe -s -w "`n=> HTTP %{http_code}`n" http://127.0.0.1:8081/actuator/health/liveness
```

(bash: the same with `curl` and `'\n=> HTTP %{http_code}\n'`)

✅ Readiness `DOWN` (`503`, with `db` `DOWN`); liveness `UP` (`200`). The app should get no
traffic, but it doesn't need a restart.

Bring the database back and repeat the readiness check and the redirect:

```bash
docker compose start postgres
```

✅ Readiness `UP` and the redirect `302` again, with no restart of the app. In the run behind
this guide, it recovered on the first try, straight after `start`.

## 14. Clean shutdown and teardown

```bash
docker compose stop app
docker compose ps -a
```

✅ The `app` row shows `Exited (143)`: a clean exit on SIGTERM. The `postgres` row shows `5432/tcp`
with no host mapping, because the database is never published to your machine.

```bash
docker compose down -v
```

`-v` also deletes the database volume, so the next run starts empty; leave it off to keep your
links. Add `--rmi local` to also delete the app image.

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| An `Invoke-WebRequest` error from a `curl` command | You typed `curl` in Windows PowerShell, which runs `Invoke-WebRequest`. Use `curl.exe`. |
| A POST with JSON typed on the `curl.exe` command line fails | Windows PowerShell 5.1 strips the quotes. Use the `New-TestLink` helper from step 4. |
| An error shows a status but no body in PowerShell | `Invoke-RestMethod` hides error bodies. Use `curl.exe -i`. |
| Every `localhost` destination is rejected | Intended (`SELF_HOST`): it would redirect to the shortener itself. Use a public URL. |
| Stats show one click fewer than expected | Clicks are recorded asynchronously; ask again a moment later. `HEAD` requests don't count. |
| `app` never becomes healthy | Check `docker compose logs app`. Ports `8080`/`8081` must be free. |
