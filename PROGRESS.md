# Progress

Current state for session hand-offs. Full history lives in GitHub Issues and PRs.

## Current

- **Milestone:** Foundation
- **Active issue:** #3 Quality gates and branch protection. PR #13 is green and waiting for
  engineer sign-off and merge. All acceptance criteria are verified there: throwaway PR #14
  (closed) showed each gate failing and the PIT artifact; the direct push to `main` and the
  merge with a red check were both rejected.
- **After merge:** confirm that CodeQL runs on `main` and that Dependabot accepts its config.

## Recently completed

- #2 AI guardrails, runnable skeleton, Spring Boot 4 setup checks (PR #12, merged).
- #3 gates: google-java-format 1.36.1 via Spotless (1.37.0 breaks in Spotless 3.10.4),
  `-Xlint:all -Werror`, unit and integration stages (`-DskipUTs`), PIT report job, gitleaks
  8.30.1 (exact demo-key allowlist, `HEAD` history only), CodeQL, Dependabot. Branch protection
  on `main`: PR plus 4 required checks, strict, enforced for admins.

## Up next

- #11 Mockito Java agent, strictly after #3 merges (both touch `pom.xml` and test JVM setup).
- Then #4 tracer bullet (create + redirect).

## Carry-forward notes

- For #5: Spring Security applies on the management port. Permit health and `/error`
  explicitly, or the compose healthcheck, smoke test, and unknown-path errors break.
- In #4, switch the `pit-report` upload to `if-no-files-found: error` (no report until then).
- If gitleaks flags the demo key in a new context, check the captured secret before touching the
  allowlist (it matches the exact literal only).
- #6 adversarial URL table is posted on #6 (210 cases, 12 open questions for the engineer).
- Required check names are in branch protection: renaming a CI job breaks merging until the
  protection is updated.
