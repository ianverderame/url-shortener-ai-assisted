# Progress

Current state for session hand-offs. Full history lives in GitHub Issues and PRs.

## Current

- **Milestone:** Ambiguous scenario. #9's decisions and SLOs are approved (PR #29,
  `docs/scenarios/03-ambiguous.md`). Rate limiting rejected for now.
- **Active issue:** #35 organize into packages (branch `issue-35/package-refactor-s1`, PR pending).
  Feature packages `link`, `redirect`, `analytics`, `security`, plus `web` and `management`, each
  with a `package-info.java` (decisions on #35). The main scan excludes
  `@ManagementContextConfiguration` classes, so #27's `OnChildApplicationContextCondition` is gone.
  Other packages read links through `LinkLookup`; the service's own errors extend
  `web.ProblemException`, so `web` knows no feature. Exact problem bodies are pinned in
  `ErrorContractIT`, `LinkCreationCollisionIT` (503) and `RequestBodyLimitIT` (413).

## Recently completed

- #27 Shutdown inside the grace period (PR #36). Management context's shutdown phase set to 3 s
  explicitly (`ManagementShutdownConfiguration`); main gets 7 s.
- #24 DB fail-fast and bounded readiness (PR #34). `503` ≤ 3 s, one log line; readiness ≤ 2 s.

## Up next

- Path to done: #35 (this session) → close #9 → #10. **Backlog frozen:** a new finding becomes an
  issue only if it breaks the active issue's criteria or an approved SLO; otherwise it goes on
  #10's future-work list below.
- Future work (for #10's limitations section): #31 form bodies parsed before the key check
  (unreproduced). Test JVM logs show `-06:00`, not UTC. From #24's review: a Flyway-specific
  timeout; a readiness recovery test after a real DB stop/start; log the original exception's type
  when a failed rollback replaces it; log-capture ITs could pick up an unrelated thread's WARN.
  From #35: package boundaries aren't enforced (Spring Modulith `verify()`); three stats-range
  details (after today, before 0001-01-01, over 366 days) aren't asserted by any test.

## Carry-forward notes

- `main` is protected (PR only); renaming a CI job breaks branch protection's required checks.
- New ITs need `TestApiKey` via `@DynamicPropertySource`. PIT counts unit tests only: never lower
  `mutationThreshold`, add unit tests at seams.
- Async tests: `TestExecutorService` and `@Primary` `ClickRecorder` / `ClickEventWriter` beans.
- CSRF stays off only while auth is header-only. Destination policy decisions: PR #20. Package
  layout and the management-context scan rule: `CLAUDE.md` (#35).
