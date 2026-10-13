# Progress

Current state for session hand-offs. Full history lives in GitHub Issues and PRs.

## Current

- **Milestone:** Ambiguous scenario. #9's decisions and SLOs are approved (PR #29,
  `docs/scenarios/03-ambiguous.md`): fail fast ≤ 3 s with `503`, readiness ≤ 2 s, shutdown exits
  `143` inside 30 s, metrics on 8081 only, an 8 KiB body cap (`413` before parsing), and unknown
  JSON properties as `400` (approved contract change). Rate limiting rejected for now.
- **Active issue:** #24 DB fail-fast and bounded readiness (branch `issue-24/db-fail-fast-s1`, PR
  pending review). DB outage → `503` ≤ 3 s, one log line; readiness ≤ 2 s; ITs run with Hikari's
  validation always on (`aliveBypassWindowMs=0`). Timeouts: 1 s connection, 0.5 s validation, 1 s
  socket.

## Recently completed

- #25 Metrics and SLOs (PR #33). `/actuator/metrics` on 8081 only, no key; `MetricsIT` pins the
  exposed set; the four SLOs and their meters are in the README.
- #26 Request bodies (PR #32). `RequestBodyLimitFilter` after the security chain (no key → `401`
  first), 8 KiB cap → `413`, strict Jackson (unknown property → `400`). PIT 89%.
- #8 Click capture and stats (PR #30). Async `ClickRecorder`, V2 `click_events`, stats endpoint.

## Up next

- Path to done: #24 → #27 (blocked by #24) → close #9 → #10. Nothing else.
- **Backlog frozen.** A new finding becomes an issue only if it breaks an acceptance criterion or
  an approved SLO of the active issue; otherwise it goes on #10's future-work list below.
- Future work (for #10's limitations section): #31 form bodies parsed before the key check
  (unreproduced). Test JVM logs show `-06:00`, not UTC (fix in #24 only if it breaks timing tests).
  From #24's review: a Flyway-specific timeout (migrations share the 1 s socket timeout); a
  readiness recovery test after a real DB stop/start; log the original exception's type when a
  failed rollback replaces it; log-capture ITs could pick up an unrelated thread's WARN.

## Carry-forward notes

- `main` is protected: every change goes through a PR. Renaming a CI job breaks merging until
  branch protection's required check names are updated.
- New ITs need `TestApiKey` via `@DynamicPropertySource`, or the startup guard fails them.
- PIT counts only unit tests: IT-covered wiring shows as uncovered. Never lower
  `mutationThreshold`; add unit tests at seams instead.
- Async tests: `TestExecutorService` and `@Primary` `ClickRecorder` / `ClickEventWriter` beans.
- CSRF stays off only while auth is header-only: cookie/session auth or CORS means re-enabling it.
- Destination policy decisions (Q1–Q12, PD-07 as `MISSING_HOST`) are recorded in PR #20.
