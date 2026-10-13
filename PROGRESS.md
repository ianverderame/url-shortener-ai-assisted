# Progress

Current state for session hand-offs. Full history lives in GitHub Issues and PRs.

## Current

- **Milestone:** Foundation
- **Active issue:** #11 Mockito Java agent in test JVMs (branch `issue-11/mockito-java-agent-s1`).

## Recently completed

- #3 Quality gates and branch protection (PR #13, merged). google-java-format 1.36.1 via
  Spotless, `-Xlint:all -Werror`, unit/IT stages, PIT report job, gitleaks, CodeQL, Dependabot.
  `main` is protected: PR plus 4 required checks, strict, enforced for admins.
- #2 AI guardrails, runnable skeleton, Spring Boot 4 setup checks (PR #12, merged).

## Up next

- #4 tracer bullet (create + redirect).

## Carry-forward notes

- `main` is protected: every change, this file included, goes through a PR.
- Required check names are in branch protection: renaming a CI job breaks merging until the
  protection is updated.
- For #5: Spring Security applies on the management port. Permit health and `/error`
  explicitly, or the compose healthcheck, smoke test, and unknown-path errors break.
- In #4, switch the `pit-report` upload to `if-no-files-found: error` (no report until then).
- In #4, check that PIT's minion JVMs get the Mockito agent (PIT reuses surefire's `argLine`;
  unverifiable in #11 because PIT starts no JVM without mutations).
- If gitleaks flags the demo key in a new context, check the captured secret before touching the
  allowlist (it matches the exact literal only).
- #6 adversarial URL table is posted on #6 (210 cases, 12 open questions for the engineer).
