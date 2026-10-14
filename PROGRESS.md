# Progress

Current state for session hand-offs. Full history lives in GitHub Issues and PRs.

## Current

- **Active issue:** none. v1.0 is complete: every PRD (#1) requirement is met or its gap is
  documented (`docs/requirements-coverage.md`), and `v1.0.0` tags the close-out's (#44) merge
  commit.

## Recently completed

- #44 Close-out: this file carried to `main`; `v1.0.0` tagged after merge. A review of `main`
  against the brief and the PRD found no gaps beyond those already published.
- #10 Wrap-up (PR #43): engineering summary, AI usage, walkthrough, manual testing guide,
  requirements coverage; PR template split; early PRs' sign-off record corrected. Accepted risk:
  README fixes after the `reviewer-simulator` run, and guide steps 6 and 8, not re-run live.
- #41 Demo page (PR #42): static page at `/`; third freeze exception; relaxes PRD's "no UI" (#1).

## Up next

- Nothing scheduled. New work is a fresh decision by the engineer and starts with a new issue.
- Future work and limitations: `docs/engineering-summary.md` (assumptions, limitations and
  trade-offs; scaling path and future work) and `docs/requirements-coverage.md` (where coverage
  is thinner).

## Carry-forward notes

- `main` is protected; renaming a CI job breaks the required checks. Never lower `mutationThreshold`.
  The PIT job is still named "report-only" though it fails below 85%, and isn't a required check.
- New ITs need `TestApiKey` via `@DynamicPropertySource`. Both checkouts share compose project
  `url-shortener`: check `docker ps` first. CSRF stays off only while auth is header-only.
