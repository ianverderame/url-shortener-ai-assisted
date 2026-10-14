# Progress

Current state for session hand-offs. Full history lives in GitHub Issues and PRs.

## Current

- **Active issue:** #10 wrap-up docs (`issue-10/wrap-up-docs-s1`), docs only. Five new docs in
  `docs/`; README polished; `reviewer-simulator` passed (6 frictions fixed). Next: review, PR.

## Recently completed

- #41 Demo page (PR #42): static page at `/`; third freeze exception; relaxes PRD's "no UI" (#1).
- #39 Review cleanup (PR #40): readability fixes; create's transaction stays (a `503` stores nothing).

## Up next

- Path to done: #10 only. **Backlog frozen:** new findings go below unless they break an SLO.
- Future work (#10's limitations; details in `03-ambiguous.md` and the PRs):
  - #28's click drain and loss-ratio target. Untested: main's 7 s shutdown share, the
    just-used-connection 2.5 s path, three stats-range details. DB stop/start recovery: manual only.
  - Manual run: wrong-length code `404` says "No static resource"; OpenAPI problem schema nests
    `properties`; its `503` omits DB outage. Demo page: no browser test; hard-codes 8081.
    Performance: guarded, not measured; next step a query-plan (index) test. The PIT job is
    still named "report-only" though it fails below 85%, and isn't a required check.
  - #31 form bodies before the key check; test logs `-06:00`; a Flyway timeout; log a replaced
    exception's type; log capture sees all threads; no Spring Modulith; rate limiting (rejected
    on #9); click writes autocommit, so `write_failure` can overcount after a hang.
  - Scaling path (#39): redirect cache; batched click writes; daily stats rollup; shared IT contexts.

## Carry-forward notes

- `main` is protected; renaming a CI job breaks the required checks. Never lower `mutationThreshold`.
- New ITs need `TestApiKey` via `@DynamicPropertySource`. Both checkouts share compose project
  `url-shortener`: check `docker ps` first. CSRF stays off only while auth is header-only.
