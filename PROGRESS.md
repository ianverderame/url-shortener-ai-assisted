# Progress

Current state for session hand-offs. Full history lives in GitHub Issues and PRs.

## Current

- **Active issue:** #39 review cleanup (`issue-39/review-cleanup-s1`), the second freeze exception
  after #35. Small readability fixes; create's transaction stays, and the reason it exists (a `503`
  stores nothing) is now written down. Removing it was proposed and rejected.

## Recently completed

- #9 Ambiguous scenario closed (PR #38): `03-ambiguous.md` complete; #28 (click drain) descoped.
- #35 Feature packages (PR #37); the main scan excludes `@ManagementContextConfiguration` classes.

## Up next

- Path to done: #39 → #10. **Backlog frozen:** new findings go on the list below unless they break
  the active issue's criteria or an approved SLO.
- Future work (#10's limitations; details in `03-ambiguous.md` and the PRs):
  - #28's click drain and loss-ratio target. Untested: main's 7 s shutdown share with a hanging
    bean, DB recovery after a real stop/start, the just-used-connection 2.5 s path, three
    stats-range details (#35).
  - #31 form bodies parsed before the key check; test logs show `-06:00`, not UTC; a Flyway
    timeout; log a replaced exception's type; log-capture ITs see all threads; no package-boundary
    enforcement (Spring Modulith); rate limiting (rejected on #9). Click writes are autocommitted,
    so `clicks.lost{reason=write_failure}` can overcount after a hang (#39).
  - Scaling path (#39): cache redirect lookups (links never change); batch click writes; a daily
    stats rollup (raw aggregation under the 1 s socket timeout); shared IT contexts (each class's
    `@DynamicPropertySource` gives it its own context and Postgres).

## Carry-forward notes

- `main` is protected (PR only); renaming a CI job breaks branch protection's required checks.
- New ITs need `TestApiKey` via `@DynamicPropertySource`. PIT counts unit tests only: never lower
  `mutationThreshold`. Async tests: `TestExecutorService`, `@Primary` recorder/writer beans.
- CSRF stays off only while auth is header-only. Destination policy: PR #20. Packages: `CLAUDE.md`.
