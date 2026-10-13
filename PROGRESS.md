# Progress

Current state for session hand-offs. Full history lives in GitHub Issues and PRs.

## Current

- **Milestone:** Foundation
- **Active issue:** #2 (AI guardrails, runnable skeleton, Spring Boot 4 setup checks)
- **Branch:** `issue-2/ai-guardrails-skeleton-s1`

## Decisions (issue #2)

- Runtime image: `eclipse-temurin:21.0.12.1_1-jre-noble` (has curl, so the compose healthcheck
  curls the management readiness endpoint). Build image: `eclipse-temurin:21.0.12.1_1-jdk-noble`.
- Spring Boot 4.1.1. Versions pinned by exact tag; no digests (Dependabot keeps them current).

## Done

- AI workflow scaffolding: `CLAUDE.md`, `.claude/settings.json`, three subagents, issue and PR
  templates, this file.

## Next

- Runnable skeleton: Maven wrapper, app, Flyway (no migrations), Dockerfile, compose with
  `postgres`/`app`/`smoke`, Testcontainers integration test, CI, README run path.
- Complete the Spring Boot 4 checklist and add confirmed idioms to `CLAUDE.md`.
