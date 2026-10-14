# Progress

Current state for session hand-offs. Full history lives in GitHub Issues and PRs.

## Current

- **Milestone:** Ambiguous scenario. #9's decisions and SLOs are approved (PR #29,
  `docs/scenarios/03-ambiguous.md`). Rate limiting rejected for now.
- **Active issue:** #35 organize into packages (branch `issue-35/package-refactor-s1`). Decisions
  recorded on #35: feature packages (`link`, `redirect`, `analytics`, `security`) plus `web` and
  `management`, each with a `package-info.java`; one PR, two commits. Commit 1: the main scan excludes
  `@ManagementContextConfiguration` classes (`ManagementContextScanIT`), so #27's
  `OnChildApplicationContextCondition` is gone. Commit 2: the moves.

## Recently completed

- #27 Shutdown inside the grace period (PR #36). Management context's shutdown phase set to 3 s
  explicitly (`ManagementShutdownConfiguration`); main gets 7 s.
- #24 DB fail-fast and bounded readiness (PR #34). `503` ≤ 3 s, one log line; readiness ≤ 2 s.
- #25 Metrics and SLOs (PR #33). `/actuator/metrics` on 8081 only, no key; SLOs in the README.

## Up next

- Path to done: #35 (this session) → close #9 → #10. **Backlog frozen:** a new finding becomes an
  issue only if it breaks the active issue's criteria or an approved SLO; otherwise it goes on
  #10's future-work list below.
- Future work (for #10's limitations section): #31 form bodies parsed before the key check
  (unreproduced). Test JVM logs show `-06:00`, not UTC. From #24's review: a Flyway-specific
  timeout; a readiness recovery test after a real DB stop/start; log the original exception's type
  when a failed rollback replaces it; log-capture ITs could pick up an unrelated thread's WARN.
  From #35: package boundaries aren't enforced (Spring Modulith's `verify()` would be the standard
  route); `web`'s advice knows every feature's exceptions (`ErrorResponseException` would remove
  that).

## Carry-forward notes

- `main` is protected: every change goes through a PR. Renaming a CI job breaks merging until
  branch protection's required check names are updated.
- New ITs need `TestApiKey` via `@DynamicPropertySource`, or the startup guard fails them.
- PIT counts only unit tests: IT-covered wiring shows as uncovered. Never lower
  `mutationThreshold`; add unit tests at seams instead.
- Async tests: `TestExecutorService` and `@Primary` `ClickRecorder` / `ClickEventWriter` beans.
- CSRF stays off only while auth is header-only: cookie/session auth or CORS means re-enabling it.
- Destination policy decisions (Q1–Q12, PD-07 as `MISSING_HOST`) are recorded in PR #20.
- Management-context classes go in `META-INF/spring/...ManagementContextConfiguration.imports`
  only; the main component scan excludes them by annotation (`UrlShortenerApplication`).
