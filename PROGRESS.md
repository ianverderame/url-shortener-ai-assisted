# Progress

Current state for session hand-offs. Full history lives in GitHub Issues and PRs.

## Current

- **Milestone:** Ambiguous scenario. #9's decisions and SLOs are approved (PR #29,
  `docs/scenarios/03-ambiguous.md`): fail fast ≤ 3 s with `503`, readiness ≤ 2 s, shutdown exits
  `143` inside 30 s, metrics on 8081 only, an 8 KiB body cap (`413` before parsing), and unknown
  JSON properties as `400` (approved contract change). Rate limiting rejected for now.
- **Active issue:** #25 metrics and SLOs (branch `issue-25/metrics-slos-s1`, PR pending review).
  `/actuator/metrics` on 8081 only, no key; `MetricsIT` pins the exposed set; SLOs in README.

## Recently completed

- #26 Request bodies (PR #32). `RequestBodyLimitFilter` after the security chain (no key → `401`
  first), 8 KiB cap → `413`, strict Jackson (unknown property → `400`). PIT 89%.
- #8 Click capture and stats (PR #30). Async `ClickRecorder`, V2 `click_events`, stats endpoint.
- #17 OpenAPI real status codes and `X-API-Key` scheme (PR #23); #19 code-scanning rule (PR #22).

## Up next

- #24 (DB down/slow), then #27 (blocked by #24). #28 is closed. #31 (form bodies parsed before
  the key check; found reviewing #26). Then #10. Check: test JVM logs show `-06:00`, not UTC.

## Carry-forward notes

- `main` is protected: every change goes through a PR. Renaming a CI job breaks merging until
  branch protection's required check names are updated.
- New ITs need `TestApiKey` via `@DynamicPropertySource`, or the startup guard fails them.
- PIT counts only unit tests: IT-covered wiring shows as uncovered. Never lower
  `mutationThreshold`; add unit tests at seams instead.
- Async tests: `TestExecutorService` and `@Primary` `ClickRecorder` / `ClickEventWriter` beans.
- CSRF stays off only while auth is header-only: cookie/session auth or CORS means re-enabling it.
- Destination policy decisions (Q1–Q12, PD-07 as `MISSING_HOST`) are recorded in PR #20.
