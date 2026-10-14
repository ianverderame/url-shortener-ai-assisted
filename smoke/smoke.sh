#!/bin/sh
# End-to-end smoke test, run inside the compose network: `docker compose run --rm smoke`.
set -eu

APP_URL="${APP_URL:-http://app:8080}"
MANAGEMENT_URL="${MANAGEMENT_URL:-http://app:8081}"
: "${APP_API_KEY:?APP_API_KEY must be set (compose passes the local demo key)}"
DESTINATION="https://example.com/smoke?check=1"

fail() {
  echo "SMOKE FAIL: $1" >&2
  exit 1
}

# Prints the value of a response header (case-insensitive), without the trailing CR.
header() {
  printf '%s\n' "$1" | tr -d '\r' | grep -i "^$2:" | head -n 1 | sed 's/^[^:]*: *//'
}

echo "Checking readiness (database included)..."
# curl -f fails on the 503 that readiness returns when it is not UP.
readiness=$(curl -fsS "$MANAGEMENT_URL/actuator/health/readiness") || fail "readiness endpoint not reachable or not UP"
echo "$readiness" | grep -q '"db":{"status":"UP"' || fail "database is not UP in readiness: $readiness"

echo "Creating a link..."
created=$(curl -sS -w '\n%{http_code}' -X POST "$APP_URL/api/v1/links" \
  -H "X-API-Key: $APP_API_KEY" -H 'Content-Type: application/json' \
  -d "{\"destination\":\"$DESTINATION\"}") || fail "create request failed"
status=$(printf '%s\n' "$created" | tail -n 1)
body=$(printf '%s\n' "$created" | sed '$d')
[ "$status" = "201" ] || fail "create returned $status, expected 201"
code=$(printf '%s' "$body" | sed -n 's/.*"code":"\([0-9A-Za-z]\{8\}\)".*/\1/p')
[ -n "$code" ] || fail "create response has no 8-character base62 code"
printf '%s' "$body" | grep -q "\"shortUrl\":\"[^\"]*/$code\"" || fail "shortUrl does not end with the code"

echo "Following the short link..."
headers=$(curl -sS -o /dev/null -D - "$APP_URL/$code") || fail "redirect request failed"
status=$(printf '%s\n' "$headers" | head -n 1 | cut -d ' ' -f 2)
[ "$status" = "302" ] || fail "redirect returned $status, expected 302"
[ "$(header "$headers" Location)" = "$DESTINATION" ] || fail "Location is not the destination"
[ "$(header "$headers" Cache-Control)" = "no-store" ] || fail "Cache-Control is not no-store"

echo "Reading the link's stats..."
# Clicks are recorded asynchronously, so poll a bounded number of times for the one GET above.
total=""
attempt=0
while [ "$attempt" -lt 20 ]; do
  attempt=$((attempt + 1))
  stats=$(curl -sS -w '\n%{http_code}' "$APP_URL/api/v1/links/$code/stats" \
    -H "X-API-Key: $APP_API_KEY") || fail "stats request failed"
  status=$(printf '%s\n' "$stats" | tail -n 1)
  [ "$status" = "200" ] || fail "stats returned $status, expected 200"
  total=$(printf '%s\n' "$stats" | sed '$d' | sed -n 's/.*"total":\([0-9]*\).*/\1/p')
  [ "$total" = "1" ] && break
  sleep 0.5
done
[ "$total" = "1" ] || fail "stats total is '$total' after $attempt attempts, expected 1"
printf '%s\n' "$stats" | grep -q '"days":\[' || fail "stats response has no days"

echo "Creating a link to a rejected destination..."
rejected=$(curl -sS -w '\n%{http_code}' -X POST "$APP_URL/api/v1/links" \
  -H "X-API-Key: $APP_API_KEY" -H 'Content-Type: application/json' \
  -d '{"destination":"https://trusted.example@evil.example/"}') || fail "rejected-create request failed"
status=$(printf '%s\n' "$rejected" | tail -n 1)
[ "$status" = "400" ] || fail "a userinfo destination returned $status, expected 400"
printf '%s\n' "$rejected" | grep -q '"reason":"USERINFO_NOT_ALLOWED"' || fail "the 400 does not carry reason USERINFO_NOT_ALLOWED"

echo "Checking an unknown code..."
status=$(curl -sS -o /dev/null -w '%{http_code}' "$APP_URL/Zz9Zz9Zz") || fail "unknown-code request failed"
# This fixed code could in theory have been generated earlier; with 62^8 codes that is negligible.
[ "$status" = "404" ] || fail "unknown code returned $status, expected 404"

echo "SMOKE PASS"
