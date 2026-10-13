# Progress

Current state for session hand-offs. Full history lives in GitHub Issues and PRs.

## Current

- **Milestone:** Foundation
- **Active issue:** none

## Recently completed

- #2 AI guardrails, runnable skeleton, Spring Boot 4 setup checks (PR #12, merged). Boot 4.1.1,
  Temurin 21.0.12.1 (JRE noble runtime), Maven 3.10.0 with wrapper checksum, Postgres 18.6.
  Health on management port 8081; both ports loopback-only. CI: `verify` + reviewer path.
  Boot 4 idioms recorded in `CLAUDE.md`.

## Up next

- #3 Quality gates and branch protection (unblocked by #2). Lint exclusions: none needed.
- #11 Load Mockito as a Java agent in test JVMs (small; can go alongside #3).
- Then #4 tracer bullet (create + redirect).

## Carry-forward notes

- For #5: Spring Security applies on the management port. Permit health and `/error`
  explicitly, or the compose healthcheck, smoke test, and unknown-path errors break.
- New agent definitions and `.claude/settings.json` load only at session start: start sessions
  in the repo folder.
