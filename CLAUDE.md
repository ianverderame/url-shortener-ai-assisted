# CLAUDE.md

Project instructions for AI assistants working in this repository. These are **guardrails, not
guarantees**: they steer the assistant, and the engineer's review of every pull request is the
control that matters. Enforced rules live in `.claude/settings.json`; see "Limits" below.

## Project

URL shortener service: Java 21, Spring Boot 4.1.x, PostgreSQL, Maven wrapper, Docker Compose.
Requirements: the PRD (GitHub issue #1). Work items: GitHub Issues. Current state: `PROGRESS.md`.

- Base package: `io.github.ianverderame.urlshortener`
- Persistence: plain JDBC (`JdbcClient`), not JPA. Flyway migrations are additive only.
- Time: UTC everywhere (JVM, container, SQL). Inject a `Clock`; never sleep in tests.
- Errors: RFC 7807 problem responses for every failure.

## Rules

1. **One issue per session.** Work only inside the active issue's scope. Anything out of scope
   becomes a new GitHub Issue; do not implement it.
2. **Test first.** Every behavior starts with a failing test; every bug fix starts with a failing
   regression test. Test through public interfaces; don't mock internal collaborators.
3. **No unverified dependencies.** Before adding any dependency, plugin, or container image,
   confirm it exists from an official source (Maven Central, the vendor's docs, the image
   registry) and pin an exact version. Never use `latest` or version ranges. Prefer versions
   managed by the Spring Boot BOM over explicit overrides.
4. **Never log or echo secrets or destinations.** No API keys, and no full submitted URLs, in
   logs, exception messages, or problem details.
5. **No secrets in the repo.** The only key in the repo is the clearly labeled local demo key.
6. **YAGNI.** Build what the active issue's acceptance criteria require, nothing more.
7. **The engineer commits, merges, and closes.** Don't commit, push, merge, or close issues
   unless the engineer asks in the current session.
8. **High-impact changes need engineer sign-off on the PR:** schema migrations, security or
   authentication configuration, new dependencies, build configuration, CI workflows, and AI
   configuration (this file, `.claude/`).

## Workflow

Issue → branch `issue-{n}/{slug}-s{session}` → failing test → implementation → `./mvnw verify`
→ `pr-reviewer` subagent → PR using the template (AI usage recorded as you go) → CI green →
engineer review and merge.

Quality gates (CI runs all of them; `main` only changes through a PR with green checks):
- Formatting: google-java-format via Spotless. `./mvnw spotless:apply` before committing;
  `verify` fails on unformatted code.
- Compiler warnings are errors (see "Compiler lint" below).
- Tests: `./mvnw test` (unit) and `./mvnw verify -DskipUTs` (integration only); `verify` runs both.
- Mutation testing: `./mvnw test-compile pitest:mutationCoverage` (report in
  `target/pit-reports/`). Fails below `mutationThreshold` in `pom.xml` (85% since #7); raise it as
  tests improve, never lower it.
- Secret scanning: gitleaks with `.gitleaks.toml`. The only allowlist entry is the exact demo-key
  literal; never widen it.
- CodeQL (Java) and Dependabot (Maven, Actions, Docker, Compose; grouped weekly, majors ignored).
- Pins Dependabot can't see, kept in step by hand in the same PR as the related bump: the
  Testcontainers Postgres tag (match `compose.yaml`), the CI `java-version` (match the Temurin
  image), `GITLEAKS_VERSION`/SHA in `ci.yml`, and the google-java-format version in `pom.xml`
  (tied to the Spotless version; 1.37.0 fails in Spotless 3.10.4).

Subagents (`.claude/agents/`):
- `pr-reviewer`: read-only review of a change against its issue.
- `spec-test-designer`: designs tests from the spec, without reading the implementation.
- `reviewer-simulator`: follows only the README in an isolated worktree, as an evaluator would.

## Subagent usage

- Background prep for the *next* issue is encouraged: read-only analysis, and spec-only test
  design.
- Parallel implementation is allowed only for slices touching disjoint modules, each in its own
  worktree.
- At most two issue PRs open at once (Dependabot PRs aside).
- Every subagent run is recorded in the PR's AI-usage section.
- Known conflict: never run #3 and #11 in parallel (both change `pom.xml` and the test JVM setup).

## Spring Boot 4 idioms

Confirmed against Spring Boot 4.1.1 in issue #2. Don't use the Boot 3 equivalents.

- **Starters are modular:** `spring-boot-starter-webmvc` (not `-web`), `-actuator`, `-flyway`;
  test starters are per module: `spring-boot-starter-webmvc-test`, `-actuator-test`,
  `-flyway-test`.
- **Flyway on PostgreSQL** needs `spring-boot-starter-flyway` plus `flyway-database-postgresql`.
- **Jackson 3** is the default: packages are `tools.jackson.*`, not `com.fasterxml.jackson.*`
  (annotations stay in `com.fasterxml.jackson.annotation`). `ProblemDetail` serializes as flat
  RFC 7807 JSON; custom properties appear at the top level.
- **Testcontainers 2.x:** artifacts `testcontainers-postgresql`, `testcontainers-junit-jupiter`;
  class `org.testcontainers.postgresql.PostgreSQLContainer` (not generic). Wire it with
  `@ServiceConnection`. Pin the image tag to match `compose.yaml`.
- **HTTP tests:** `RestTestClient` (`org.springframework.test.web.servlet.client`), included in
  `spring-boot-starter-webmvc-test`. Not `TestRestTemplate` (it needs an extra module).
  Ports: `@LocalServerPort`, `@LocalManagementPort` (`org.springframework.boot.test.web.server`).
- **Mocks:** `@MockitoBean` / `@MockitoSpyBean`. `@MockBean` no longer exists in Boot 4.
- **Integration tests** are named `*IT` and run by failsafe in `./mvnw verify`.
- **Spring Security 7:** lambda DSL only; match paths with
  `PathPatternRequestMatcher.withDefaults().matcher(...)`. Security **does** apply on the separate
  management port: health must be explicitly permitted there, or the compose healthcheck and smoke
  test break. Permit `/error` too, or unknown paths under a `permitAll()` rule return 401.
- **springdoc-openapi 3.1.x** targets Boot 4.1.
- **Compiler lint:** `-Xlint:all -Werror` is enforced by the build with no exclusions. If an
  annotation processor is ever added, set `-proc` explicitly instead of excluding the
  `processing` lint.
- **PIT** 1.30.0 with `pitest-junit5-plugin` 1.2.3 runs on Boot's JUnit 6 (it resolves the
  platform launcher at runtime).
- **Postgres 18 images** keep data under `/var/lib/postgresql` (not `.../data`).

## Limits of these guardrails

- Instructions in this file are advisory; the model can misread or ignore them.
- Permission rules in `.claude/settings.json` gate Claude Code's own tools. A `Read` deny rule
  blocks the file tools, not every shell command (e.g. `cat .env` through Bash), and a
  `Bash(git push *)` rule doesn't match every spelling of a push (e.g. `git -C . push`).
- Subagent restrictions that are not expressed as a tool allowlist are instructions only.
- None of this replaces review: the engineer reviews every PR before merge.
