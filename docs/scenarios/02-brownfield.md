# Scenario 2: Brownfield

How the tagged `v0.1.0` baseline was treated as existing code: reviewed and corrected, then
extended with click analytics without changing how redirects behave. Requirements are in the PRD,
[#1](https://github.com/ianverderame/url-shortener-ai-assisted/issues/1). Each issue's PR has the
full AI-usage record: what was generated, edited or rejected, the prompts used, and each subagent
finding and how it was triaged.

| Issue | What it delivered | PR |
|---|---|---|
| [#7](https://github.com/ianverderame/url-shortener-ai-assisted/issues/7) Review of `v0.1.0` | A fresh-context bug hunt, edge-case fixes with regression tests, the mutation threshold raised 80% → 85% and enforced | [#21](https://github.com/ianverderame/url-shortener-ai-assisted/pull/21) |
| [#8](https://github.com/ianverderame/url-shortener-ai-assisted/issues/8) Click capture and stats | Asynchronous click recording on the redirect path, and `GET /api/v1/links/{code}/stats` with UTC daily counts | [#30](https://github.com/ianverderame/url-shortener-ai-assisted/pull/30) |

## Decomposition

- **Review before extension.** #7 came first, so the code #8 builds on had been reviewed and its
  tests strengthened. #7's findings that belonged elsewhere were routed, not fixed: metrics
  exposure and a body-size limit went to #9, and the OpenAPI security scheme was a duplicate of
  #17.
- **The impact analysis was prepared in parallel and approved before any code.** While #7 was in
  progress, a read-only `Explore` agent drafted #8's impact analysis from the `v0.1.0` code:
  modules, API, schema, data flow before and after, a test plan, risks, and eight open questions.
  It corrected two assumptions in the issue itself: a `Clock` bean already existed, and HEAD
  requests already reached the redirect handler (so the recorder needs an explicit method check).
  The engineer answered the questions (Q1–Q8: span counting, 400-vs-404 precedence, pool sizing,
  the code constraint, where metrics are exposed, how to inject a write failure, controller split,
  field names) and the decisions were posted on
  [#8](https://github.com/ianverderame/url-shortener-ai-assisted/issues/8) before work started.
- **A behavior-neutral first commit.** #8 opens with a refactor (`LinkNotFoundException` and a
  straight-line redirect) that the click hook could build on, with existing tests unchanged.
- **Seams at the persistence boundary, not mocks.** #7 introduced `LinkRepository` so
  `LinkService` could be unit-tested with an in-memory fake. #8 followed the same pattern with
  `ClickEventWriter` and `ClickStatsRepository`, and an injected executor for the recorder.

## Execution

- **Findings were verified before triage.** In #7, every "suspected" `pr-reviewer` finding was
  reproduced against the running compose stack before the engineer triaged it. For example, the
  HTML error pages were only confirmed once curl showed the HTML body, on both ports.
- **The refactor was proven neutral, not assumed.** Before and after #8's refactor, the 404
  response for GET and HEAD was captured from the running stack and compared byte for byte,
  including the RFC 7807 `instance` member, which no test pinned.
- **Red for the right reason.** Where a new test first failed only because a bean was missing,
  the wiring was added and the test re-run until it failed on the behavior itself (no click
  stored; stats route returning 404), and only then was the behavior implemented.
- **Tests that pass at once were checked by breaking the code.** The resilience tests cover
  behavior that already existed, so the recorder was sabotaged twice: writing on the request
  thread made the latch-blocked redirect hang (caught by the hang backstop), and not catching
  write failures turned the redirect into a 500. Both were reverted.
- **Work merged meanwhile was integrated, not overwritten.** #17 merged while #8 was in progress
  and changed the same controller. #8 was rebased onto it, kept #17's OpenAPI annotations, and
  documented the stats endpoint the same way. Its refactor commit was re-verified on its own
  after the rebase.

## Validation

- **Mutation testing as a gate that bites.** #7 measured `v0.1.0` at 80% (one survivor, 34
  uncovered) and raised it to 85% with tests that pin real behavior, then enforced 85% in CI.
  During #8, the new stats wiring pushed the score down to 83%. The threshold was not lowered or
  scoped down: the engineer chose to unit-test the error contract's mapping (`ProblemDetailsAdvice`),
  which killed all 13 of its mutations and brought the score to 89%.
- **Deterministic tests for asynchronous behavior.** The PRD forbids wall-clock tests. The click
  recorder is tested with a synchronous or deferred executor, and with a real bounded pool held
  on a latch: overflow and shutdown losses are counted exactly, with no sleeps. The performance
  guard holds the recorder's only worker on a latch and requires the redirect to stay `302`; its
  timeout is only a hang backstop.
- **Time is injected.** Stats tests use a fixed clock, and clicks are stored at exact instants
  (`23:59:59.999Z`, `00:00:00Z`) at both ends of the range, pinning UTC day buckets and the
  half-open SQL range on real PostgreSQL.
- **The schema is pinned.** `ClickEventsSchemaIT` checks that `click_events` holds exactly a link
  reference and an occurrence time, and that the link reference is a foreign key.
- **End to end.** The compose smoke test follows a link and then polls its stats, a bounded number
  of times, until the click appears.
- **Independent review before the engineer's.** `pr-reviewer` reviewed both PRs against their
  issues and `CLAUDE.md`. In #7, its second pass found that the management port still served HTML
  error pages; that was reproduced, fixed with a regression test, and recorded on the issue.
- **Honest reporting.** #7's conclusion says plainly that the core create, redirect and policy
  logic held up, and that the defects were at the edges.
