# Progress

Current state for session hand-offs. Full history lives in GitHub Issues and PRs.

## Current

- **Active issue:** #9 close-out (branch `issue-9/scenario-closeout-s2`): Execution and Validation
  in `docs/scenarios/03-ambiguous.md`. #24 to #27 are merged; #28 (click drain) was descoped.

## Recently completed

- #35 Feature packages (PR #37); the main scan excludes `@ManagementContextConfiguration` classes.
- #27 Shutdown inside the grace period (PR #36): management phase 3 s, main 7 s.
- #24 DB fail-fast and bounded readiness (PR #34): `503` ≤ 3 s, readiness ≤ 2 s.

## Up next

- Path to done: close #9 (this session) → #10. **Backlog frozen:** a finding becomes an issue only
  if it breaks the active issue's criteria or an approved SLO; otherwise it goes on the list below.
- Future work (#10's limitations section; details in `03-ambiguous.md` and the PRs):
  - #28's click drain and the loss-ratio target. `ClickRecorder.shutdown()`'s Javadoc still says
    the drain is #9.
  - Untested: main's 7 s shutdown share with a hanging bean; DB recovery after a real stop/start;
    the just-used-connection 2.5 s path; three stats-range details (#35).
  - #31 form bodies parsed before the key check; test JVM logs show `-06:00`, not UTC; a
    Flyway-specific timeout; log a replaced exception's type; log-capture ITs see all threads;
    package boundaries not enforced (Spring Modulith); rate limiting (rejected on #9).

## Carry-forward notes

- `main` is protected (PR only); renaming a CI job breaks branch protection's required checks.
- New ITs need `TestApiKey` via `@DynamicPropertySource`. PIT counts unit tests only: never lower
  `mutationThreshold`, add unit tests at seams.
- Async tests: `TestExecutorService` and `@Primary` `ClickRecorder` / `ClickEventWriter` beans.
- CSRF stays off only while auth is header-only. Destination policy decisions: PR #20. Package
  layout rules: `CLAUDE.md` (#35).
