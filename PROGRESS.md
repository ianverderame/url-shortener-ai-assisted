# Progress

Current state for session hand-offs. Full history lives in GitHub Issues and PRs.

## Current

- **Milestone:** Greenfield (Foundation complete; #4 merged)
- **Active issue:** #5 auth hardening and error contract (branch
  `issue-5/auth-hardening-error-contract-s1`). All acceptance criteria covered by tests; awaiting
  pr-reviewer and PR.

## Recently completed

- #4 Tracer bullet, create and redirect (PR #16, merged, all 6 CI checks green). Create, 302
  redirect, collision retry with a 503 and a metric, OpenAPI docs, extended smoke test.
  pr-reviewer caught a raw-URI auth bypass, fixed with regression tests.
- #11 Mockito as a Java agent in test JVMs (PR #15). PIT minions inherit it via `argLine`.

## Up next

- #6 URL policy hardening (closes v0.1.0). #17 (OpenAPI status codes and problem responses) found
  during #5.

## Carry-forward notes

- `main` is protected: every change goes through a PR. Renaming a CI job breaks merging until
  branch protection's required check names are updated.
- Integration tests get a per-JVM generated key (`TestApiKey`) via `@DynamicPropertySource`; a new
  IT without it fails to start (the startup guard).
- For #6: `LinkService.validate` is the placeholder the URL policy replaces (adversarial table
  on #6). `LinkService` has no unit test yet; PIT's baseline needs one.
- If gitleaks flags the demo key (`compose.yaml` `x-demo-api-key`, README), check the captured
  secret before touching the allowlist.
