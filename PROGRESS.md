# Progress

Current state for session hand-offs. Full history lives in GitHub Issues and PRs.

## Current

- **Milestone:** Ambiguous scenario. #9's decisions and SLOs are approved (PR #29,
  `docs/scenarios/03-ambiguous.md`): fail fast ≤ 3 s with `503`, readiness ≤ 2 s, shutdown exits
  `143` inside 30 s, metrics on 8081 only, an 8 KiB body cap (`413` before parsing), and unknown
  JSON properties as `400` (approved contract change). Rate limiting rejected for now.
- **Active issue:** #27 shutdown inside the grace period (branch
  `issue-27/shutdown-grace-period-s1`, PR pending review). The management context's own
  shutdown-phase timeout defect (it fell back to Spring Framework's 10 s default, not
  `spring.lifecycle.timeout-per-shutdown-phase`) is fixed: `ManagementShutdownConfiguration` sets
  it to 3 s explicitly; main's share of the budget is now 7 s. Verified by
  `ShutdownTimeoutBudgetIT` plus three new CI `reviewer-path` scripted checks (C1b load
  reconciliation, C2 idle/DB-down, C3 in-flight/DB-down).

## Recently completed

- #24 DB fail-fast and bounded readiness (PR #34). `503` ≤ 3 s, one log line; readiness ≤ 2 s.
- #25 Metrics and SLOs (PR #33). `/actuator/metrics` on 8081 only, no key; `MetricsIT` pins the
  exposed set; the four SLOs and their meters are in the README.
- #26 Request bodies (PR #32). `RequestBodyLimitFilter` after the security chain (no key → `401`
  first), 8 KiB cap → `413`, strict Jackson (unknown property → `400`). PIT 89%.

## Up next

- Path to done: #27 (this session, done, PR pending) → **#35 package refactor (next session)** →
  close #9 → #10.
- **Backlog frozen, with one exception.** #35 (organize into packages) was opened 2026-10-13: #27
  hit a real problem from the flat package (`OnChildApplicationContextCondition` working around a
  component-scan collision that a package boundary would have prevented), and the engineer wants
  it addressed before #9/#10. Otherwise the freeze holds: a new finding becomes an issue only if it
  breaks an acceptance criterion or an approved SLO of the active issue; everything else goes on
  #10's future-work list below.
- Future work (for #10's limitations section): #31 form bodies parsed before the key check
  (unreproduced). Test JVM logs show `-06:00`, not UTC. From #24's review: a Flyway-specific
  timeout; a readiness recovery test after a real DB stop/start; log the original exception's type
  when a failed rollback replaces it; log-capture ITs could pick up an unrelated thread's WARN.

## Carry-forward notes

- `main` is protected: every change goes through a PR. Renaming a CI job breaks merging until
  branch protection's required check names are updated.
- New ITs need `TestApiKey` via `@DynamicPropertySource`, or the startup guard fails them.
- PIT counts only unit tests: IT-covered wiring shows as uncovered. Never lower
  `mutationThreshold`; add unit tests at seams instead.
- Async tests: `TestExecutorService` and `@Primary` `ClickRecorder` / `ClickEventWriter` beans.
- CSRF stays off only while auth is header-only: cookie/session auth or CORS means re-enabling it.
- Destination policy decisions (Q1–Q12, PD-07 as `MISSING_HOST`) are recorded in PR #20.
- `@ManagementContextConfiguration` classes are also `@Configuration`-annotated, so the main
  app's own component scan picks them up too (same package). Guard management-only beans with
  `OnChildApplicationContextCondition` (added in #27) or they get a second, independent instance
  registered in the main context.
