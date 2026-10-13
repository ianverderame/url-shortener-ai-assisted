# Progress

Current state for session hand-offs. Full history lives in GitHub Issues and PRs.

## Current

- **Milestone:** Greenfield complete; `main` tagged `v0.1.0` (merge of PR #20)
- **Active issue:** #7 brownfield review of v0.1.0 (branch `issue-7/brownfield-review-s1`). Review
  triaged; fixes 1, 3, 4, 5, 7 and 9 (management port) done with regression tests; PIT 80% -> 85%
  (0 survivors, threshold 85 enforced). Awaiting issue comments and PR.

## Recently completed

- #6 URL policy hardening (PR #20). `DestinationPolicy`, 8 reason codes, `01-greenfield.md`.
- #5 Auth hardening and error contract (PR #18). Key guard, Spring Security, RFC 7807 contract.

## Up next

- #8 (unblocked by #7; impact analysis drafted, needs approval) and #9 (needs decisions; now
  also holds #7's deferred metrics exposure and body-size limit).
- Small, ready: #17 (OpenAPI status codes + `X-API-Key` scheme) and #19 (code-scanning rule).

## Carry-forward notes

- `main` is protected: every change goes through a PR. Renaming a CI job breaks merging until
  branch protection's required check names are updated.
- Integration tests get a per-JVM generated key (`TestApiKey`) via `@DynamicPropertySource`; a new
  IT without it fails to start (the startup guard).
- PIT counts only unit tests: wiring covered by ITs shows as uncovered. Raise `mutationThreshold`
  in `pom.xml` as tests improve; never lower it.
- #8 impact analysis drafted during #7 (not yet posted); `Clock` already exists, HEAD reaches the
  redirect handler.
- CSRF stays off only while auth is header-only: cookie/session auth or CORS means re-enabling it.
- Destination policy decisions (Q1–Q12, PD-07 as `MISSING_HOST`) are recorded in PR #20.
