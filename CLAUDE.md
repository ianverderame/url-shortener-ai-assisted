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

Subagents (`.claude/agents/`):
- `pr-reviewer`: read-only review of a change against its issue.
- `spec-test-designer`: designs tests from the spec, without reading the implementation.
- `reviewer-simulator`: follows only the README in an isolated worktree, as an evaluator would.

## Spring Boot 4 idioms

_Added as each item on the issue #2 verify-at-setup checklist is confirmed._

## Limits of these guardrails

- Instructions in this file are advisory; the model can misread or ignore them.
- Permission rules in `.claude/settings.json` gate Claude Code's own tools. A `Read` deny rule
  blocks the file tools, not every shell command (e.g. `cat .env` through Bash), and a
  `Bash(git push *)` rule doesn't match every spelling of a push (e.g. `git -C . push`).
- Subagent restrictions that are not expressed as a tool allowlist are instructions only.
- None of this replaces review: the engineer reviews every PR before merge.
