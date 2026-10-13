# Progress

Current state for session hand-offs. Full history lives in GitHub Issues and PRs.

## Current

- **Milestone:** Ambiguous scenario. #9's decisions and SLOs are approved (PR #29,
  `docs/scenarios/03-ambiguous.md`): fail fast ≤ 3 s with `503`, readiness ≤ 2 s, shutdown exits
  `143` inside 30 s, metrics on 8081 only, an 8 KiB body cap (`413` before parsing), and unknown
  JSON properties as `400` (approved contract change). Rate limiting rejected for now.
- **Active issue:** #26 request bodies (branch `issue-26/request-body-limits-s1`). Done, pending
  review and PR: `RequestBodyLimitFilter` after the security chain (no key → `401` first), strict
  Jackson, `413` in OpenAPI and `ApiDocsIT`. PIT 89%. #25 runs in parallel in its own worktree.

## Recently completed

- #8 Click capture and stats (PR #30). Async `ClickRecorder`, V2 `click_events`, stats endpoint.
- #17 OpenAPI documents real status codes and the `X-API-Key` scheme (PR #23).
- #19 `CLAUDE.md` rule for triaging code-scanning alerts (PR #22).

## Up next

- #24 (DB down/slow; edits `ProblemDetailsAdvice`, so after #26), then #27 (blocked by #24).
  #28 is closed. #31 (form bodies parsed before the key check; found reviewing #26). Then #10.

## Carry-forward notes

- `main` is protected: every change goes through a PR. Renaming a CI job breaks merging until
  branch protection's required check names are updated.
- New ITs need `TestApiKey` via `@DynamicPropertySource`, or the startup guard fails them.
- PIT counts only unit tests: IT-covered wiring shows as uncovered. Never lower
  `mutationThreshold`; add unit tests at seams instead.
- Async tests: `TestExecutorService` and `@Primary` `ClickRecorder` / `ClickEventWriter` beans.
- CSRF stays off only while auth is header-only: cookie/session auth or CORS means re-enabling it.
- Destination policy decisions (Q1–Q12, PD-07 as `MISSING_HOST`) are recorded in PR #20.
