# Walkthrough

This is a guided tour of the repository in the order it is best presented: the problem, the
running system, the guardrails, the three scenarios, the code and tests, how AI was used, and the
judgment calls and limits. Each stop says what to open, what to show, and the point it makes.

**Before you start:** have the stack running (`docker compose up --build`; see the
[README](../README.md#run-with-docker)), and have the published demo key ready:
`local-demo-key-NOT-A-SECRET-do-not-use-elsewhere`.

| Part | Stops |
|---|---|
| [1. The problem](#1-the-problem) | The PRD, the issues |
| [2. It runs](#2-it-runs) | One command, the smoke test, the demo page, the API docs, an outage |
| [3. Guardrails before code](#3-guardrails-before-code) | AI configuration, quality gates, a PR as an audit record |
| [4. The three scenarios](#4-the-three-scenarios) | Greenfield, brownfield, ambiguous |
| [5. The code](#5-the-code) | Packages, the redirect, link creation, security, configuration |
| [6. The tests](#6-the-tests) | The tests that carry the most weight |
| [7. How AI was used](#7-how-ai-was-used) | Workflow, guardrails, rejections, the experiment |
| [8. Judgment and limits](#8-judgment-and-limits) | Decisions, risks, limitations, scaling |

## 1. The problem

### The PRD

- **Open:** [PRD #1](https://github.com/ianverderame/url-shortener-ai-assisted/issues/1).
- **Show:** the problem statement and the two things to deliver (the service, and a controlled,
  traceable AI workflow); the user stories; the out-of-scope list; the ambiguity log; the phase
  dependency graph at the bottom. Then the comments: the
  [AI design review and its triage](https://github.com/ianverderame/url-shortener-ai-assisted/issues/1#issuecomment-6136870015),
  the [cut of link expiry during slicing](https://github.com/ianverderame/url-shortener-ai-assisted/issues/1#issuecomment-6136948155),
  and the [demo-page exception](https://github.com/ianverderame/url-shortener-ai-assisted/issues/1#issuecomment-6163316317).
- **Point:** requirements were written down, challenged and cut before any code was written. The PRD
  paraphrases the brief; the brief itself is never committed. The plan was stress-tested with
  `/grill-me` and written with `/write-a-prd`
  ([AI usage → personal skills](ai-usage.md#the-engineers-personal-skills)).

### The issues

- **Open:** [#2](https://github.com/ianverderame/url-shortener-ai-assisted/issues/2) as an
  example, and the [issue template](../.github/ISSUE_TEMPLATE/work-item.md).
- **Show:** intent, constraints, technical context, acceptance criteria, blockers and subagent
  opportunities in every issue.
- **Point:** each issue is a vertical slice that a session can work from alone, created with
  `/prd-to-issue` in dependency order.

## 2. It runs

### One command

- **Open:** the [README](../README.md#run-with-docker) and the port table.
- **Show:** `docker compose up --build` is the only step; Docker is the only prerequisite. Both
  app ports listen on `127.0.0.1` only, and PostgreSQL is not published at all.

### The smoke test

- **Run:** `docker compose run --rm smoke` → `SMOKE PASS`.
- **Point:** it checks readiness (database included), creating a link, the redirect (`302`,
  `no-store`), stats, a rejected destination and an unknown code. CI's `reviewer-path` job runs
  the same test from a clean checkout on every change.

### The demo page

- **Open:** <http://127.0.0.1:8080/> and paste the demo key into section 1.
- **Show each section:**
  1. **API key:** the page keeps it in memory only, with no cookies or browser storage.
  2. **Shorten a URL:** create a link, then open the short link; it lands on the destination.
  3. **Click stats:** the total and the days that had clicks, for the link just created.
  4. **Error contract:** three buttons, three RFC 7807 problems: `401` without a key, `400` with
     `UNSUPPORTED_SCHEME` for an `ftp://` URL, `404` for an unknown code.
  5. **More:** links to the API docs, readiness and metrics.
- **Point:** it's one static file served from the API's own origin, so it needs no CORS and no
  change to the security settings. It never contains a key, sets no cookies, and renders
  destinations as text only; `DemoPageIT` checks all three. It is a deliberate, recorded exception
  to the PRD's "no UI"
  ([#41](https://github.com/ianverderame/url-shortener-ai-assisted/issues/41)).

### The API docs

- **Open:** <http://127.0.0.1:8080/swagger-ui/index.html>, click **Authorize**, paste the key,
  then try `POST /api/v1/links`.
- **Point:** every documented status code is one the service really returns, pinned by
  `ApiDocsIT` ([#17](https://github.com/ianverderame/url-shortener-ai-assisted/issues/17)). Two
  known gaps in the generated schema are listed in the
  [engineering summary](engineering-summary.md#assumptions-limitations-and-trade-offs).

### An outage (optional)

- **Run:** `docker compose stop postgres`, follow a short link, check readiness, then
  `docker compose start postgres` and try again
  ([manual testing guide, step 13](manual-testing.md#13-database-outage-and-recovery)).
- **Show:** a `503` problem in about a second, readiness `DOWN` while liveness stays `UP`, and
  recovery without restarting the app.
- **Point:** "make it reliable" became these measured behaviors (see part 4).

## 3. Guardrails before code

### AI configuration first

- **Open:** [PR #12](https://github.com/ianverderame/url-shortener-ai-assisted/pull/12). Its first
  commit holds only AI configuration, no application code.
- **Show:**
  - [`CLAUDE.md`](../CLAUDE.md): eight rules, the workflow, and the confirmed Spring Boot 4 idioms.
  - [`.claude/settings.json`](../.claude/settings.json): `deny` and `ask` rules.
  - [`.claude/agents/`](../.claude/agents/): `pr-reviewer` (read-only), `spec-test-designer`,
    `reviewer-simulator`.
  - PR #12's "Deny rule shown working, and its known bypass".
- **Point:** these are guardrails, not guarantees, and the repository says so. The limits are
  written down, and the engineer's review is the control that matters.

### Quality gates

- **Open:** [`ci.yml`](../.github/workflows/ci.yml), then
  [PR #14](https://github.com/ianverderame/url-shortener-ai-assisted/pull/14), the throwaway gate
  demo.
- **Show:** one commit per gate (unformatted code, a compiler warning, a failing test, a planted
  secret), each failing its check. Then, in
  [PR #13](https://github.com/ianverderame/url-shortener-ai-assisted/pull/13), branch protection
  rejecting a direct push and refusing a merge with a red check.
- **Point:** each of these gates was shown to fail before it was trusted to pass. (The mutation
  job also fails below 85%, but it is not one of the required checks.)

### A PR as an audit record

- **Open:** [PR #40](https://github.com/ianverderame/url-shortener-ai-assisted/pull/40) and the
  [PR template](../.github/pull_request_template.md).
- **Show:** AI usage by activity; "Generated, edited, rejected", where the assistant's own main
  finding (removing a transaction) was rejected after review; subagent triage; verification per
  acceptance criterion.
- **Point:** each change records what AI generated, what was changed or refused and why, and how
  it was verified. The early PRs' sign-off sections were corrected during #10, with a visible note
  ([AI usage → known gaps](ai-usage.md#known-gaps-in-the-record)).

## 4. The three scenarios

### Greenfield

- **Open:** [`01-greenfield.md`](scenarios/01-greenfield.md).
- **Show:** the tracer bullet, then auth hardening, then the URL policy. Then, in
  [PR #16](https://github.com/ianverderame/url-shortener-ai-assisted/pull/16), show the key-check
  bypass (`/api/v1/%6Cinks`) that `pr-reviewer` found and a failing test confirmed.
- **Point:** the policy's adversarial test table was designed from the spec before any policy code
  existed, and its open questions were the engineer's to answer.

### Brownfield

- **Open:** [`02-brownfield.md`](scenarios/02-brownfield.md).
- **Show:** the review of `v0.1.0` as someone else's code; the
  [impact analysis and decisions](https://github.com/ianverderame/url-shortener-ai-assisted/issues/8#issuecomment-6154021156)
  approved before any analytics code; the behavior-neutral first commit; and the mutation score,
  raised from 80% to 85% and held there when new code pushed it down.
- **Point:** existing code was understood and strengthened before it was extended.

### Ambiguous

- **Open:** [`03-ambiguous.md`](scenarios/03-ambiguous.md), then the README's
  [SLO table](../README.md#metrics-and-service-level-objectives).
- **Show:** how `v0.1.0` was measured under failure first; each candidate meaning decided by the
  engineer; four SLOs, each with the test that measures it; and the click drain, recorded as
  descoped rather than claimed.
- **Point:** an ambiguous requirement became numbers before any code was written.

## 5. The code

### Packages

- **Open:** the six `package-info.java` files under
  [`src/main/java/.../urlshortener/`](../src/main/java/io/github/ianverderame/urlshortener/).
- **Point:** one package per feature, each stating what it may depend on, with no cycles
  ([#35](https://github.com/ianverderame/url-shortener-ai-assisted/issues/35)).

### The redirect, the hot path

- **Open:** [`RedirectController`](../src/main/java/io/github/ianverderame/urlshortener/redirect/RedirectController.java),
  then [`ClickRecorder`](../src/main/java/io/github/ianverderame/urlshortener/analytics/ClickRecorder.java).
- **Point:** the route only matches the 8-character code format. Only a `GET` records a click,
  and it is handed to a bounded queue that never blocks or throws; a lost click is counted by
  reason.

### Creating a link

- **Open:** [`LinkService.create`](../src/main/java/io/github/ianverderame/urlshortener/link/LinkService.java),
  then [`DestinationPolicy`](../src/main/java/io/github/ianverderame/urlshortener/link/DestinationPolicy.java).
- **Point:** the Javadoc on `create` explains why each attempt runs in a transaction, which is why
  a review proposal to remove the transaction was rejected. The policy stores exactly the string it
  validated.

### Security

- **Open:** [`SecurityConfig`](../src/main/java/io/github/ianverderame/urlshortener/security/SecurityConfig.java).
- **Point:** the comment at the CSRF line names the assumptions that make disabling it safe, and
  the test that pins them (`CsrfAssumptionsIT`). The CodeQL alert was answered with a test, not
  silenced.

### Configuration

- **Open:** [`application.properties`](../src/main/resources/application.properties).
- **Point:** the timeouts and the shutdown budget carry their arithmetic and the issue that decided
  them.

## 6. The tests

| Test | What to show |
|---|---|
| [`DestinationPolicyTest`](../src/test/java/io/github/ianverderame/urlshortener/link/DestinationPolicyTest.java) | The adversarial table: every row asserts the outcome and the reason |
| [`ClickRecordingBackpressureIT`](../src/test/java/io/github/ianverderame/urlshortener/analytics/ClickRecordingBackpressureIT.java) | The performance guard without a wall clock: the click writer is held on a latch and the redirect must still return `302` |
| [`DatabaseHangIT`](../src/test/java/io/github/ianverderame/urlshortener/DatabaseHangIT.java) | A paused database with a query in flight still gets a `503` within 3 s |
| [`CsrfAssumptionsIT`](../src/test/java/io/github/ianverderame/urlshortener/security/CsrfAssumptionsIT.java) | A false-positive alert turned into a pinned assumption |
| [`SensitiveDataLeakIT`](../src/test/java/io/github/ianverderame/urlshortener/SensitiveDataLeakIT.java) | No submitted URL or key in any response or log |
| [`DemoPageIT`](../src/test/java/io/github/ianverderame/urlshortener/DemoPageIT.java) | No key, no cookie, no HTML sinks in the demo page |

- **Point:** tests go through the HTTP API on a real PostgreSQL, time is injected, nothing sleeps,
  and the PIT job fails below 85% mutation coverage
  ([engineering summary → testing approach](engineering-summary.md#testing-approach)).

## 7. How AI was used

- **Open:** [AI usage](ai-usage.md).
- **Show, in order:**
  1. [The workflow](ai-usage.md#the-workflow) diagram and the engineer's personal skills.
  2. [Guardrails and their limits](ai-usage.md#guardrails-and-their-limits): which are enforced
     by a tool, and which are only instructions.
  3. [Rejected and corrected AI output](ai-usage.md#rejected-and-corrected-ai-output): the auth
     bypass, the stale "latest" Maven version, the rejected transaction removal.
  4. [Pitfalls and countermeasures](ai-usage.md#pitfalls-and-countermeasures), including two
     sessions sharing one checkout.
  5. [The labeled experiment](ai-usage.md#labeled-experiment-does-constraining-the-output-matter):
     a bare prompt got 48 of 237 outcomes wrong; constraints carried the project's decisions, and
     also one of its mistakes.
- **Point:** AI drafted most of the code and documents; the engineer decided, reviewed and merged,
  and the record shows where the AI was wrong.

## 8. Judgment and limits

- **Open:** the [engineering summary](engineering-summary.md).
- **Show:** [key decisions](engineering-summary.md#key-decisions) with their rationale;
  [risks](engineering-summary.md#risks-and-failure-scenarios), each mapped to a guardrail and a
  test; [limitations](engineering-summary.md#assumptions-limitations-and-trade-offs), stated
  plainly; the [scaling path](engineering-summary.md#scaling-path-and-future-work), each step with
  its trigger.
- **Point:** what was left out was left out deliberately, and is written down.

**For anything else**, the [artifact index](engineering-summary.md#artifact-index) lists the main
documents, files, tests and PRs, and [requirements coverage](requirements-coverage.md) maps each
requirement of the brief to its evidence. To try every feature by hand, follow the
[manual testing guide](manual-testing.md).
