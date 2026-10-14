# Requirements coverage

This page shows where each requirement of the assignment brief is met, with links to the
evidence. The brief itself is not committed; its items are paraphrased here, section by section,
as in the PRD ([#1][i1]). Rows marked **Partial** say what is missing, and the
[last section](#where-coverage-is-thinner) collects them.

## Objective

| Requirement (paraphrased) | Where it's met |
|---|---|
| Take a requirement all the way to a result someone can review, with AI helping along the way | The PRD ([#1][i1]) → issues → one reviewed PR per issue; the [walkthrough](walkthrough.md) tours the result |
| Show the requirement being understood, broken down and carried out step by step, and the output being produced and checked | The three scenario docs, each covering decomposition, execution and validation: [greenfield](scenarios/01-greenfield.md), [brownfield](scenarios/02-brownfield.md), [ambiguous](scenarios/03-ambiguous.md) |
| The engineer drives and AI speeds things up; no hands-off agent pipeline | [AI usage → the workflow](ai-usage.md#the-workflow): decisions put to the engineer, every merge the engineer's |

## Scenario

| Requirement (paraphrased) | Where it's met |
|---|---|
| Build a URL shortener from nothing, covering its main APIs, usage analytics and reliability | Core APIs: [#4][i4]–[#6][i6]; analytics: [#8][i8]; reliability: [#9][i9] and [#24][i24]–[#27][i27]. Overview: [engineering summary → the service](engineering-summary.md#architecture-the-service) |
| Then finish and improve it with AI help, showing good engineering judgment along the way | The brownfield work on the tagged `v0.1.0` ([02-brownfield.md](scenarios/02-brownfield.md)); judgment calls in [key decisions](engineering-summary.md#key-decisions) and the [trade-offs](engineering-summary.md#assumptions-limitations-and-trade-offs) |

## Scope

| Requirement (paraphrased) | Where it's met |
|---|---|
| Greenfield: new systems and features | [01-greenfield.md](scenarios/01-greenfield.md) ([#2][i2]–[#6][i6]); new features added later: click analytics ([#8][i8]) and the demo page ([#41][i41]) |
| Brownfield: enhancements | Click analytics added to the existing redirect path ([PR #30][pr30]); reliability added to the running service ([03-ambiguous.md](scenarios/03-ambiguous.md)) |
| Brownfield: refactors | The behavior-neutral refactor that opened the analytics work ([#8][i8]); feature packages ([PR #37][pr37]); review cleanup ([PR #40][pr40]) |
| Brownfield: bug fixes | The review of `v0.1.0` ([PR #21][pr21]); defects found while characterizing reliability, each fix starting from a failing test ([#24][i24], [#26][i26], [#27][i27]) |
| Test improvements | Mutation score 80% → 85%, enforced in CI ([PR #21][pr21]); tests that could never fail, rewritten ([PR #21][pr21]); exact problem bodies pinned before a refactor ([PR #37][pr37]) |
| Documentation improvements | OpenAPI made to match real responses ([PR #23][pr23]); README sections per feature; the documents in `docs/` |
| Well-defined and ambiguous requirements | Well-defined: issues with acceptance criteria ([#4][i4]–[#8][i8]). Ambiguous: "make it reliable" normalized into SLOs before any code ([#9][i9], [03-ambiguous.md](scenarios/03-ambiguous.md)) |

## Core requirements

### 1. Requirement understanding

| Requirement (paraphrased) | Where it's met |
|---|---|
| Work out what is really being asked, find where it is ambiguous, and restate it as a well-defined engineering problem | The PRD's problem statement and ambiguity log ([#1][i1]); the 12 open questions on the URL policy, answered by the engineer ([PR #20][pr20]); for "reliable", the failure behavior measured first and each meaning then decided ([#9 decision record][i9decisions]) |

### 2. Task decomposition

| Requirement (paraphrased) | Where it's met |
|---|---|
| Break the high-level requirements down into concrete tasks, with their dependencies and order | Vertical-slice issues, each with acceptance criteria and "Blocked by" ([#2][i2]–[#10][i10]); the phase dependency graph in the PRD; reliability sliced around analytics ([03-ambiguous.md](scenarios/03-ambiguous.md#decomposition)) |

### 3. Codebase reasoning (brownfield)

| Requirement (paraphrased) | Where it's met |
|---|---|
| Show which parts of the existing system a change affects (modules, APIs, data flows), and understand its architecture | The impact analysis approved before any analytics code ([#8][i8comment]); the fresh-context review of `v0.1.0` ([#7][i7comment]); the [architecture](engineering-summary.md#architecture-the-service); package boundaries stated in each `package-info.java` ([#35][i35]) |

### 4. AI-assisted execution

| Requirement (paraphrased) | Where it's met | Note |
|---|---|---|
| Apply AI to every activity: writing code, debugging, refactoring, writing tests, documenting, preparing reviews | The "AI usage by activity" table in every PR (e.g. [PR #30][pr30]); [AI usage](ai-usage.md) | |
| Give each task a stated goal, its constraints, how it will be accepted, and the technical context it needs | The [issue template](../.github/ISSUE_TEMPLATE/work-item.md); every issue, e.g. [#2][i2] | |
| Prompt deliberately, and refine when the first answer is wrong | "Key prompts and refinements" in every PR; the [labeled experiment](ai-usage.md#labeled-experiment-does-constraining-the-output-matter) comparing a bare and a constrained prompt | |
| Keep a record of which AI output was kept, changed or thrown away, with the reason | "Generated, edited, rejected" in every PR; the [rollup](ai-usage.md#rejected-and-corrected-ai-output) | |
| Gate AI output with static analysis, linting, tests, security checks and performance checks | Analysis: CodeQL. Linting: google-java-format and `-Xlint:all -Werror`. Tests: unit, integration on real PostgreSQL, and PIT at 85% (its job fails below that but isn't a required check). Security: gitleaks, CodeQL, `SensitiveDataLeakIT`, `CsrfAssumptionsIT`. Performance: `ClickRecordingBackpressureIT`, plus the 3 s / 2 s bounds under failure. Formatting, the compiler warning, a failing test and a planted secret were each shown failing ([PR #14][pr14]) | **Partial** for performance: see [below](#where-coverage-is-thinner) |
| Use AI securely | `deny` rules on secrets files; no secrets in the repo, only a labeled demo key; gitleaks; every version checked against an official source ([guardrails](ai-usage.md#guardrails-and-their-limits)); the brief is never committed | The `deny` rule doesn't cover the shell, as documented |
| Have a human approve changes with a large blast radius | The PR template's high-impact section; `ask` rules on push, merge, `pom.xml` and migrations; branch protection | The early PRs' record was corrected in #10 ([known gaps](ai-usage.md#known-gaps-in-the-record)) |
| The engineer stays accountable for whether the result is correct, maintainable and ready for production | The ownership attestation in every PR; the engineer merges every PR | Left unticked in two PRs ([known gaps](ai-usage.md#known-gaps-in-the-record)) |

### 5. Engineering output

| Requirement (paraphrased) | Where it's met |
|---|---|
| Code fit for production, cleanly designed and easy to maintain | [`src/main`](../src/main/java/io/github/ianverderame/urlshortener/), one package per feature ([#35][i35]); formatting and warnings enforced in CI |
| Definitions of the API and the database schema | OpenAPI at `/v3/api-docs` and Swagger UI, pinned by `ApiDocsIT`; Flyway migrations [`V1`, `V2`](../src/main/resources/db/migration/), with `click_events` pinned by `ClickEventsSchemaIT` |
| Unit and integration tests | 496 unit and 154 integration tests ([testing approach](engineering-summary.md#testing-approach)) |
| Documentation to go with it | The [README](../README.md) and `docs/` |

### 6. Validation and risk control

| Requirement (paraphrased) | Where it's met |
|---|---|
| Name the risks, the trade-offs and the ways the system can fail, and set up the checks and guardrails against them | [Risks and failure scenarios](engineering-summary.md#risks-and-failure-scenarios), each mapped to its guardrail and test; [trade-offs](engineering-summary.md#assumptions-limitations-and-trade-offs); the SLOs in the [README](../README.md#metrics-and-service-level-objectives); [AI pitfalls and countermeasures](ai-usage.md#pitfalls-and-countermeasures) |

### 7. Controlled oversight

| Requirement (paraphrased) | Where it's met |
|---|---|
| The engineer is in charge and approves everything; AI helps inside individual tasks | One issue per session; decisions put to the engineer as explicit choices; `pr-reviewer` before the engineer's review; merges only by the engineer ([AI usage → the workflow](ai-usage.md#the-workflow)) |

### 8. Final engineering summary

| Requirement (paraphrased) | Where it's met |
|---|---|
| Plan and rationale | [Execution approach](engineering-summary.md#architecture-the-ai-assisted-workflow) and [key decisions](engineering-summary.md#key-decisions) |
| Artifacts | [Artifact index](engineering-summary.md#artifact-index) |
| Risks, trade-offs and validation | [Risks](engineering-summary.md#risks-and-failure-scenarios), [testing approach](engineering-summary.md#testing-approach), [trade-offs](engineering-summary.md#assumptions-limitations-and-trade-offs) |
| Assumptions and limitations | [Assumptions, limitations and trade-offs](engineering-summary.md#assumptions-limitations-and-trade-offs) |

## Deliverables

| Deliverable (paraphrased) | Where it's met |
|---|---|
| A prototype that runs from end to end | `docker compose up --build` and the smoke test ([README](../README.md#run-with-docker)); CI runs both from a clean checkout on every change; a README-only evaluator run passed in #10; the [manual testing guide](manual-testing.md) |
| An overview of the architecture: its parts, the tools, how the work was carried out, the flow of control, and the main decisions | [The service](engineering-summary.md#architecture-the-service), [the workflow](engineering-summary.md#architecture-the-ai-assisted-workflow), [key decisions](engineering-summary.md#key-decisions), and the control-flow diagram in [AI usage](ai-usage.md#the-workflow) |
| One write-up per scenario (greenfield, brownfield, ambiguous): how it was broken down, done and checked | [01-greenfield.md](scenarios/01-greenfield.md), [02-brownfield.md](scenarios/02-brownfield.md), [03-ambiguous.md](scenarios/03-ambiguous.md) |
| How to set it up | [README](../README.md): run with Docker, smoke test, run without Docker, build and test |
| How it was tested, and its limits and trade-offs | [Testing approach](engineering-summary.md#testing-approach); [assumptions, limitations and trade-offs](engineering-summary.md#assumptions-limitations-and-trade-offs) |

## Evaluation criteria

| Criterion (paraphrased) | Where to look | Note |
|---|---|---|
| How effective the AI-assisted execution is | [AI usage](ai-usage.md): rejected and corrected output, the experiment, and pitfalls each tested by a real case | |
| How well the system is designed | [Architecture](engineering-summary.md#architecture-the-service) and [key decisions](engineering-summary.md#key-decisions) | |
| How deeply the work was broken down, and how well it was carried out | The three scenario docs; the issues and their blockers | |
| How realistic and solid the results are | The running service and [manual testing guide](manual-testing.md); tests on real PostgreSQL; mutation testing | |
| How rigorously results were checked and risks managed | [Risks](engineering-summary.md#risks-and-failure-scenarios); each SLO backed by a test ([03-ambiguous.md](scenarios/03-ambiguous.md#validation)); core gates shown failing ([PR #14][pr14]) | |
| Whether decisions are clear and can be defended | [Key decisions](engineering-summary.md#key-decisions); the decision records for #6 ([PR #20][pr20]), [#8][i8comment] and [#9][i9decisions] | |
| Sound engineering basics: modular, testable, reliable, secure and scalable code, changed safely | Modular: feature packages ([#35][i35]). Testable: persistence seams and test-first. Reliable: the four SLOs. Secure: the [security risks](engineering-summary.md#security). Scalable: the [scaling path](engineering-summary.md#scaling-path-and-future-work). Safe change: PR-only `main`, required checks, additive migrations | **Partial** for scalable: see [below](#where-coverage-is-thinner) |
| Engineering judgment | Scope cuts (expiry, rate limiting, the click drain recorded as descoped); the backlog freeze; AI output rejected on review ([PR #40][pr40]); [trade-offs](engineering-summary.md#assumptions-limitations-and-trade-offs) | |

## Expectation

| Expectation (paraphrased) | Where to look |
|---|---|
| Treat it as real production work: sound design, AI used to go faster, the engineer owning the result, reasoning that holds up | The [engineering summary](engineering-summary.md) and [AI usage](ai-usage.md); every PR's verification table and ownership attestation |

## Where coverage is thinner

- **Performance as a quality gate.** One deterministic guard runs in CI on every change: the
  redirect must keep returning `302` while the click writer is blocked
  (`ClickRecordingBackpressureIT`). Latency is also bounded and tested under failure (a `503`
  within 3 s, readiness within 2 s), and a 1 s socket timeout caps every query. What's missing:
  - no measured latency or throughput for the normal path, and no target for one (#9 dropped
    p99 and availability targets because there is no hosted deployment to measure them on);
  - no gate that fails when the normal path gets slower but stays under the 1 s cap (the PRD rules
    out timing the normal path because such tests are flaky);
  - no load test or benchmark (out of scope in the PRD);
  - stats are aggregated from raw click events on each request, so at high click volume a stats
    query would hit the 1 s cap and return a `503`; a daily rollup is on the scaling path.

  A deterministic next step, needing no timing, would be a test that checks the redirect and
  stats query plans use their indexes.
- **Scalability** is a documented path with triggers
  ([scaling path](engineering-summary.md#scaling-path-and-future-work)), not something
  demonstrated: the service has only run as a single instance.
- **High-impact sign-off** is met, but the early PRs' record needed correcting in #10
  ([known gaps](ai-usage.md#known-gaps-in-the-record)).

[i1]: https://github.com/ianverderame/url-shortener-ai-assisted/issues/1
[i2]: https://github.com/ianverderame/url-shortener-ai-assisted/issues/2
[i4]: https://github.com/ianverderame/url-shortener-ai-assisted/issues/4
[i6]: https://github.com/ianverderame/url-shortener-ai-assisted/issues/6
[i7comment]: https://github.com/ianverderame/url-shortener-ai-assisted/issues/7#issuecomment-6151244031
[i8]: https://github.com/ianverderame/url-shortener-ai-assisted/issues/8
[i8comment]: https://github.com/ianverderame/url-shortener-ai-assisted/issues/8#issuecomment-6154021156
[i9]: https://github.com/ianverderame/url-shortener-ai-assisted/issues/9
[i9decisions]: https://github.com/ianverderame/url-shortener-ai-assisted/issues/9#issuecomment-6154763002
[i10]: https://github.com/ianverderame/url-shortener-ai-assisted/issues/10
[i24]: https://github.com/ianverderame/url-shortener-ai-assisted/issues/24
[i26]: https://github.com/ianverderame/url-shortener-ai-assisted/issues/26
[i27]: https://github.com/ianverderame/url-shortener-ai-assisted/issues/27
[i35]: https://github.com/ianverderame/url-shortener-ai-assisted/issues/35
[i41]: https://github.com/ianverderame/url-shortener-ai-assisted/issues/41
[pr14]: https://github.com/ianverderame/url-shortener-ai-assisted/pull/14
[pr20]: https://github.com/ianverderame/url-shortener-ai-assisted/pull/20
[pr21]: https://github.com/ianverderame/url-shortener-ai-assisted/pull/21
[pr23]: https://github.com/ianverderame/url-shortener-ai-assisted/pull/23
[pr30]: https://github.com/ianverderame/url-shortener-ai-assisted/pull/30
[pr37]: https://github.com/ianverderame/url-shortener-ai-assisted/pull/37
[pr40]: https://github.com/ianverderame/url-shortener-ai-assisted/pull/40
