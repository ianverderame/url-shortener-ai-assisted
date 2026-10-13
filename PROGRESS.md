# Progress

Current state for session hand-offs. Full history lives in GitHub Issues and PRs.

## Current

- **Milestone:** Foundation
- **Active issue:** #3 Quality gates and branch protection, branch `issue-3/quality-gates-s1`
- **Done locally:** Spotless + google-java-format 1.36.1 (1.37.0 breaks in Spotless 3.10.4),
  `-Xlint:all -Werror`, unit/IT stages (`-DskipUTs`), PIT report job, gitleaks 8.30.1 (exact
  demo-key allowlist; scans `HEAD` history only), CodeQL, Dependabot, `CLAUDE.md` gates and
  Subagent usage sections.
- **Remaining:** push + PR; throwaway branch proving CI fails on format / warning / test /
  secret (and a PIT artifact); branch protection on `main` (engineer runs it); direct-push and
  red-check rejection demos; `pr-reviewer`.

## Recently completed

- #2 AI guardrails, runnable skeleton, Spring Boot 4 setup checks (PR #12, merged).

## Up next

- #11 Mockito Java agent, strictly after #3 merges (both touch `pom.xml` and test JVM setup).
- Then #4 tracer bullet (create + redirect).

## Carry-forward notes

- For #5: Spring Security applies on the management port. Permit health and `/error`
  explicitly, or the compose healthcheck, smoke test, and unknown-path errors break.
- PIT writes no report until there is mutable code; #4 produces the first real one. In #4,
  switch the `pit-report` upload to `if-no-files-found: error`.
- If gitleaks flags the demo key in a new context, check the captured secret before touching the
  allowlist (it matches the exact literal only).
- #6 adversarial URL table: drafted during #3 by `spec-test-designer`, to post on #6.
