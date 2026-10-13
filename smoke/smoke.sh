#!/bin/sh
# End-to-end smoke test, run inside the compose network: `docker compose run --rm smoke`.
# Later slices add their flows here.
set -eu

MANAGEMENT_URL="${MANAGEMENT_URL:-http://app:8081}"

fail() {
  echo "SMOKE FAIL: $1" >&2
  exit 1
}

echo "Checking readiness (database included)..."
# curl -f fails on the 503 that readiness returns when it is not UP.
readiness=$(curl -fsS "$MANAGEMENT_URL/actuator/health/readiness") || fail "readiness endpoint not reachable or not UP"
echo "$readiness" | grep -q '"db":{"status":"UP"' || fail "database is not UP in readiness: $readiness"

echo "SMOKE PASS"
