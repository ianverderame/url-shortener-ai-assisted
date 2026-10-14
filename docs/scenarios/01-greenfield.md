# Scenario 1: Greenfield

How the URL shortener went from an empty repository to the tagged `v0.1.0` baseline, built with an
AI assistant under engineer control. Requirements are in the PRD,
[#1](https://github.com/ianverderame/url-shortener-ai-assisted/issues/1). Each issue's PR has the
full AI-usage record: what was generated, edited or rejected, the prompts used, and each subagent
finding and how it was triaged.

| Issue | What it delivered | PR |
|---|---|---|
| [#2](https://github.com/ianverderame/url-shortener-ai-assisted/issues/2) Foundation: guardrails and skeleton | `CLAUDE.md`, permission rules, subagents, templates, then a runnable Spring Boot 4 + PostgreSQL skeleton | [#12](https://github.com/ianverderame/url-shortener-ai-assisted/pull/12) |
| [#3](https://github.com/ianverderame/url-shortener-ai-assisted/issues/3) Foundation: quality gates | Formatting, `-Werror`, split unit/integration runs, PIT, gitleaks, CodeQL, Dependabot, branch protection | [#13](https://github.com/ianverderame/url-shortener-ai-assisted/pull/13) (gate demo: [#14](https://github.com/ianverderame/url-shortener-ai-assisted/pull/14), never merged) |
| [#11](https://github.com/ianverderame/url-shortener-ai-assisted/issues/11) Foundation: Mockito as a Java agent | Removes the JDK dynamic-attach warning before it becomes an error | [#15](https://github.com/ianverderame/url-shortener-ai-assisted/pull/15) |
| [#4](https://github.com/ianverderame/url-shortener-ai-assisted/issues/4) Tracer bullet: create and redirect | `POST /api/v1/links` and `GET /{code}` end to end, collision retry, OpenAPI, smoke test | [#16](https://github.com/ianverderame/url-shortener-ai-assisted/pull/16) |
| [#5](https://github.com/ianverderame/url-shortener-ai-assisted/issues/5) Auth hardening and error contract | Startup key guard, Spring Security with digest comparison, one RFC 7807 contract, no URL or key leaks | [#18](https://github.com/ianverderame/url-shortener-ai-assisted/pull/18) |
| [#6](https://github.com/ianverderame/url-shortener-ai-assisted/issues/6) URL policy hardening | The destination URL policy, driven by an adversarial table; the constrained-vs-bare experiment | [#20](https://github.com/ianverderame/url-shortener-ai-assisted/pull/20) |

## Decomposition

- **The PRD came first, then vertical slices.** The PRD was broken into issues that each cut
  through every layer, rather than one issue per layer. Each issue lists its own constraints,
  acceptance criteria, blockers and subagent opportunities, so a session can work from the issue
  alone.
- **Guardrails before code.** #2's first commit held only AI configuration: project rules,
  tool-permission rules, and three subagents with narrow jobs. The skeleton came in a second commit,
  so the rules were in force for every line of application code.
- **Gates before features.** #3 made formatting, compiler warnings, tests, secret scanning and CodeQL
  required checks before the first feature (#4), so no feature work could skip them. Mutation
  testing runs in CI too, as a job that fails below its threshold. #11 was found during #3 and split out rather than folded in.
- **A tracer bullet, then hardening.** #4 was deliberately minimal (a basic key check and a
  scheme-plus-host check) to prove the path from request to database to redirect. #5 and #6
  hardened the two security boundaries separately, so each could be reviewed on its own.
- **Tests designed before the implementation they test.** The adversarial URL table for #6 was
  drafted during #3 by `spec-test-designer`, working only from the PRD and the issue, before any
  policy code existed. Its open questions went to the engineer instead of being guessed.

## Execution

- **One issue per session**, on a branch named `issue-{n}/{slug}-s{session}`, with `PROGRESS.md`
  as the hand-off between sessions. Out-of-scope findings became new issues (#11, #17, #19), not
  extra changes.
- **Test first.** Each behavior began as a failing test run through public interfaces. Security
  fixes began as failing regression tests: for example, the encoded-path key bypass in #4 and
  whitespace around the demo key in #5.
- **The engineer owns decisions, commits and merges.** The assistant drafts and proposes;
  ambiguous points in the spec were put to the engineer as explicit choices. #6's twelve
  open questions (normalization, precedence, punycode, character strictness and others) were
  answered before the test table was finalized.
- **Version facts were checked, not assumed.** Spring Boot 4 differs from the Boot 3 idioms most
  AI training data reflects (modular starters, Jackson 3, Testcontainers 2, Security 7). #2
  confirmed each one and recorded it in `CLAUDE.md`, so later sessions don't fall back on Boot 3
  habits.

## Validation

- **Automated gates on every PR:** formatting, `-Xlint:all -Werror`, unit and integration tests
  (Testcontainers PostgreSQL), the compose smoke test, gitleaks, CodeQL and Dependabot. `main`
  changes only through a PR with green checks.
- **Mutation testing as a test-quality check.** PIT showed whether the tests actually pin the
  behavior. In #6 it found boundary gaps (the 63-character label, the 253-character host, ports 1
  and 65535) and checks that duplicated the JDK's own validation. The gaps were closed and the
  duplicate code removed, so all 111 mutants in the URL policy are killed.
- **Independent review before the engineer's.** `pr-reviewer` reviewed every PR against its
  issue and `CLAUDE.md`. It found real defects: a raw-URI path that bypassed the key check (#4), a
  fail-open 401 path (#5), and wrong rejection reasons for inputs with userinfo but no host (#6).
  Every finding was triaged in the PR as fixed, rejected with a reason, or deferred to a named
  issue.
- **False positives were checked, not waved through.** The CodeQL CSRF alert on #5 was dismissed
  only after `CsrfAssumptionsIT` pinned the assumptions that make disabled CSRF safe
  (header-only auth, no cookies or sessions).
- **A labeled experiment on constraining AI output (#6).** The same validator was requested
  twice: once from a bare prompt, and once from the PRD rules plus the adversarial table and the
  engineer's decisions. Run against the full table, the bare version handled the classic attacks
  (schemes, userinfo, raw CR/LF, backslash tricks). It still got 48 of 237 accept/reject outcomes
  wrong, 33 of them on rows the PRD decides by itself, because it brought its own defaults:
  SSRF-style blocking of IP literals, IDN conversion of look-alike hosts, and trimming whitespace
  instead of rejecting it. The constrained version got every outcome right; it had seen the
  expected answers, so this shows that constraints carry the project's decisions, not that one
  output was smarter. Constraints also carry the author's mistakes. The constrained prompt
  repeated a too-narrow definition of a missing host that `pr-reviewer` later caught in the real
  implementation, and the constrained output reproduced the same 7 wrong reasons.
  [PR #20](https://github.com/ianverderame/url-shortener-ai-assisted/pull/20) has both prompts
  and the full results.
- **The engineer reviewed and merged every PR**, and tagged `main` as `v0.1.0` after #6 merged.
  That baseline is the starting point for the brownfield (#7, #8) and ambiguous (#9) scenarios.
