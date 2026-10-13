# Progress

Current state for session hand-offs. Full history lives in GitHub Issues and PRs.

## Current

- **Milestone:** Greenfield (Foundation complete: #2, #3, #11 merged)
- **Active issue:** #4 tracer bullet, create and redirect (branch
  `issue-4/tracer-bullet-create-redirect-s1`). All acceptance criteria covered by tests.

## Recently completed

- #11 Mockito as a Java agent in test JVMs (PR #15). PIT minions inherit it via `argLine`.
- #3 Quality gates and branch protection (PR #13). PR plus 4 required checks on `main`.

## Up next

- #5 auth hardening and error contract, then #6 URL policy hardening (closes v0.1.0).

## Carry-forward notes

- `main` is protected: every change goes through a PR. Renaming a CI job breaks merging until
  branch protection's required check names are updated.
- For #5: Spring Security applies on the management port; permit health, `/error`,
  `/v3/api-docs/**` and `/swagger-ui/**`. Replace `ApiKeyInterceptor`, keeping MVC path
  matching (a raw-URI prefix check was bypassable, caught in #4 review). Move the bare 401 and
  `LinkController`'s local `@ExceptionHandler`s into the global error contract.
- For #6: `LinkService.validate` is the placeholder the URL policy replaces (adversarial table
  on #6). `LinkService` has no unit test yet; PIT's baseline needs one.
- If gitleaks flags the demo key (`compose.yaml` `x-demo-api-key`, README), check the captured
  secret before touching the allowlist.
