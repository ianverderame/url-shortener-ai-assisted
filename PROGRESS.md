# Progress

Current state for session hand-offs. Full history lives in GitHub Issues and PRs.

## Current

- **Milestone:** Greenfield (Foundation complete; #4 and #5 merged)
- **Active issue:** #6 URL policy hardening (branch `issue-6/url-policy-hardening-s1`). All
  acceptance criteria covered locally; awaiting pr-reviewer and PR. After merge: tag `v0.1.0`.

## Recently completed

- #5 Auth hardening and error contract (PR #18, merged, all 6 CI checks green). Startup key
  guard, Spring Security with digest comparison, one RFC 7807 contract, no URL/key leaks.
  CodeQL CSRF alert dismissed as a false positive; `CsrfAssumptionsIT` pins why.
- #4 Tracer bullet, create and redirect (PR #16). Create, 302 redirect, collision retry, docs.

## Up next

- #6 URL policy hardening (closes v0.1.0). Also ready: #17 (OpenAPI status codes) and #19
  (CLAUDE.md code-scanning triage rule), both found during #5.

## Carry-forward notes

- `main` is protected: every change goes through a PR. Renaming a CI job breaks merging until
  branch protection's required check names are updated.
- Integration tests get a per-JVM generated key (`TestApiKey`) via `@DynamicPropertySource`; a new
  IT without it fails to start (the startup guard).
- `LinkService` still has no unit test; it is covered by ITs only, so PIT can't see it. The URL
  policy itself has no surviving mutants.
- CSRF stays off only while auth is header-only: cookie/session auth or CORS means re-enabling it.
