# Progress

Current state for session hand-offs. Full history lives in GitHub Issues and PRs.

## Current

- **Milestone:** Foundation
- **Active issue:** #2 (AI guardrails, runnable skeleton, Spring Boot 4 setup checks)
- **Branch:** `issue-2/ai-guardrails-skeleton-s1`

## Decisions (issue #2)

- Runtime image `eclipse-temurin:21.0.12.1_1-jre-noble` (curl-based compose healthcheck); build
  image `eclipse-temurin:21.0.12.1_1-jdk-noble`. Spring Boot 4.1.1, Maven 3.10.0 (wrapper 3.3.4),
  `postgres:18.6-alpine3.24`, `curlimages/curl:8.22.0`. Exact tags, no digests; Actions pinned by SHA.
- Boot 4 checklist results are recorded as idioms in `CLAUDE.md`.

## Done

- AI workflow scaffolding (first commit).
- Runnable skeleton: app + Flyway (no migrations), health on management port 8081 (readiness
  includes DB), Dockerfile, compose (`postgres`/`app`/`smoke`), `HealthIT` on Testcontainers,
  CI (`verify` + reviewer-path), README run paths. Verified locally; CI not yet run.

## Next

- `pr-reviewer` run done and triaged (findings in the PR). Next: commit (set `mvnw` +x), push,
  open the PR with the template, confirm CI green.
- For #5: Security applies on the management port; permit health and `/error` explicitly.
