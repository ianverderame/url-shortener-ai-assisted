# Scenario 3: Ambiguous requirement

How the underspecified requirement "make it reliable" was turned into concrete, measurable,
engineer-approved requirements before any reliability code existed. Requirements are in the PRD,
[#1](https://github.com/ianverderame/url-shortener-ai-assisted/issues/1) (Further Notes → Candidate
reliability requirements). The evidence and every decision are recorded on
[#9](https://github.com/ianverderame/url-shortener-ai-assisted/issues/9).

| Issue | Scope | Relation to #8 (click capture) |
|---|---|---|
| [#9](https://github.com/ianverderame/url-shortener-ai-assisted/issues/9) Normalize "make it reliable" | Characterization, decisions, SLOs, and the issues below. No production code. | Ran in parallel |
| [#24](https://github.com/ianverderame/url-shortener-ai-assisted/issues/24) Fail fast and keep readiness bounded | A `503` problem within 3 s when the DB is down or hung; readiness answers within 2 s | Parallel, but shares `ProblemDetailsAdvice` with #8 (expect a merge) |
| [#25](https://github.com/ianverderame/url-shortener-ai-assisted/issues/25) Expose metrics and document the SLOs | `/actuator/metrics` on the management port only, unauthenticated; the four SLOs documented | Parallel-safe |
| [#26](https://github.com/ianverderame/url-shortener-ai-assisted/issues/26) Reject oversized and unknown-field bodies | `413` over 8 KiB before parsing; `400` for unknown JSON properties | Parallel-safe |
| [#27](https://github.com/ianverderame/url-shortener-ai-assisted/issues/27) Shutdown inside the grace period | A clean exit (`143`) within 30 s in every DB state | Parallel-safe; after #24 |
| [#28](https://github.com/ianverderame/url-shortener-ai-assisted/issues/28) Drain click events | Flush queued clicks within 5 s; count what can't be flushed. **Descoped, not built** (see Execution). | Blocked by #8, #27 and #25 |

## Decomposition

- **Measure before interpreting.** Before anyone listed what "reliable" could mean, a subagent ran
  `v0.1.0` and recorded what actually happens in three situations: the database stopped, the
  database hung (`docker compose pause`), and SIGTERM under load. It also sent oversized request
  bodies, a probe of an item deferred from #7. It ran in its own worktree under a separate Compose
  project and ports, so it didn't disturb the #8 session working in the main checkout. The
  [characterization](https://github.com/ianverderame/url-shortener-ai-assisted/issues/9#issuecomment-6154638642)
  turned assumptions into facts:
  - a DB outage gives a generic `500` after a 30 s wait, and queries already sent have no timeout
    of their own (one hung for 70 s, the length of the pause);
  - readiness tells the truth, but takes 30 s to do it, against a 3 s healthcheck timeout;
  - with the DB hung, shutdown ends in SIGKILL. The management context waits its 10 s default
    instead of the configured 20 s, and the two phases together use up the 30 s grace period;
  - a key holder's 50 MB body is parsed in full, and an unknown padding field still gets a `201`.

  It also showed what already works: recovery after an outage needs no restart, and every `201`
  matched a committed link during a loaded shutdown.
- **List the meanings, then apply the scope test.** "Reliable" was split into predictable failure,
  bounded latency, truthful health, a clean lifecycle, measured analytics loss, observability and
  narrow abuse resistance. Each was kept only if the PRD requires it or a stated goal needs it.
  Multi-instance availability, failover, exactly-once analytics and load testing were ruled out of
  scope.
- **Every candidate decided, with its evidence.** The AI drafted a recommendation for each PRD
  candidate and for the two items deferred from #7 (metrics exposure, body size limit). The
  engineer then decided each one as an explicit choice:

  | Candidate | Decision |
  |---|---|
  | Readiness reflects the DB | Changed: answers within 2 s |
  | Fast-failing DB calls | Confirmed and widened: `503` within 3 s, one log line per failure |
  | Drain clicks on shutdown | Re-scoped: shutdown always exits inside the grace period, with drain as a 5 s slice of a stated budget |
  | SLOs | Confirmed: a small set of four |
  | Rate limiting | Rejected for now, as future work |
  | Metrics exposure | Confirmed |
  | Body size limit | Confirmed, plus rejecting unknown fields |

  The [decision record](https://github.com/ianverderame/url-shortener-ai-assisted/issues/9#issuecomment-6154763002)
  has the rationale for each.
- **Vague words became numbers.** Each confirmed requirement has a measurable target: 3 s to fail,
  2 s for readiness, an 8 KiB body cap, and a 10 s / 5 s / 5 s shutdown budget inside 30 s. There
  are four SLOs (bounded failure, truthful readiness, clean shutdown, counted analytics loss), each
  naming the test or meter that will measure it. Availability percentages and p99 latency were
  dropped, because with no hosted deployment there is nothing that could measure them meaningfully.
- **Sliced by module and by dependency on #8.** Issues that don't touch click recording can run
  alongside #8. The click drain waits for #8, for the shutdown budget (#27) and for metrics
  exposure (#25). The one file shared with #8 is called out on #24.
- **Defects folded in, not duplicated.** Three defects found while characterizing each went into
  the issue that fixes them, named there as a defect so the fix starts with a failing regression
  test:
  - the generic `500` on a DB outage went to #24;
  - SIGKILL because of the management context's shutdown timeout went to #27;
  - unknown JSON fields being accepted went to #26.
- **Corrected AI output.** One AI draft of an issue comment said the content had been "reviewed by
  the engineer" before the engineer had seen it. The tool-permission rule for external writes
  blocked that post, and the line was removed before the comment went up with the engineer's
  permission.

## Execution

- **Parallel where the slicing allowed it.** #26 (body limits) and #25 (metrics) ran at the same
  time. #25 was implemented by a subagent in its own worktree, told to stay out of #26's files.
  `main`, which by then included #26, was merged into #25's branch, and `verify` was re-run before
  the push. #24 and #27 followed in order, because #27 depends on #24's timeouts.
- **The backlog was frozen.** From #24 on, a new finding became an issue only if it broke the
  active issue's criteria or an approved SLO. Anything else went on the future-work list for #10.
  #31 (form bodies parsed before the key check) was moved there. The engineer pulled two findings
  into #24 instead of deferring them: a failed rollback that replaced the query's original
  exception, and the pool starting against a hung database (5.2 s before the fix).
- **Choices went to the engineer.** #24's readiness bound could be met with a 1 s socket timeout
  (configuration only) or with a custom health indicator. The engineer chose the socket timeout,
  knowing it also caps Flyway migrations and the stats query; a Flyway-specific timeout is future
  work.
- **Root causes were found in the shipped jars, not assumed.** #27 decompiled Spring Boot and
  Spring Framework 4.1.1 and confirmed that the management context never receives
  `spring.lifecycle.timeout-per-shutdown-phase`. It falls back to Spring Framework's 10 s default,
  which is why a hung database ended in SIGKILL. The same check rejected a `pr-reviewer` claim that
  `server.shutdown` defaults to `immediate`: in Boot 4.1.1 it defaults to `GRACEFUL`. The claim
  matched older Boot versions.
- **Configuration before code, and no new dependencies.** #24 is timeouts, logger levels and one
  exception handler. #25 is one exposure property. #26 adds one filter and one Jackson property.
  #27 sets the management context's shutdown phase to 3 s and lowers the main context's from 20 s
  to 7 s. That matches the approved 10 s web shutdown budget.
- **Out-of-scope work got its own issue.** During #27, the engineer asked whether the flat package
  layout still made sense. That became #35, which wasn't folded into #27. #35 later removed #27's
  workaround (a condition that kept the management configuration out of the main context) by
  excluding management-context classes from the main component scan.
- **#28 (click drain) was descoped.** It was closed before any work started, with no reason
  recorded at the time, and the frozen path to done left it out. This close-out records the
  decision instead of reporting the drain as delivered. As merged, on shutdown the click recorder
  stops its executor, and every event still queued is counted as lost under
  `clicks.lost{reason=shutdown}`, not flushed. So shutdown losses are counted but not avoided. The
  5 s drain, the `unflushed_at_shutdown` tag that #28 specified, and the loss-ratio target are
  future work.

## Validation

- **Each SLO is backed by a test or a scripted check.** Each new test failed first, except where
  the table says otherwise:

  | SLO | Evidence | Red before the fix |
  |---|---|---|
  | Bounded failure (`503` within 3 s) | `DatabaseOutageIT`, `DatabaseHangIT`, `DatabaseHangAtPoolStartIT`, each also checking one log line per failure | a `500`; no answer within the 10 s backstop; 5.2 s at pool start |
  | Truthful readiness (2 s) | `ReadinessDuringOutageIT`: `DOWN` within the bound, no thread build-up, liveness stays `UP`, `UP` on the first probe after the database resumes | 2.01 s |
  | Clean shutdown (`143` inside 30 s) | `ShutdownTimeoutBudgetIT` (asserts the management phase ends within 5 s: its 3 s budget plus margin), plus three scripted checks in CI's `reviewer-path` job against the compose stack, each asserting `143` within 15 s: C1b (under load; 30 requests, every `201` found in the database), C2 (hung database, idle; observed 1 s), C3 (hung database, request in flight; observed 3 s, and the job log shows the request got a `503`, though the check doesn't assert it) | `ShutdownTimeoutBudgetIT`: about 10.4 s, Spring Framework's 10 s default. C2 and C3 were the SIGKILL (`137`) cases in the characterization. C1b already passed on `v0.1.0`; it guards against a regression |
  | Analytics loss counted | `clicks.lost` by reason, read on `/actuator/metrics` next to the `302` redirect count (`MetricsIT` checks the meters are listed) | Not applicable. Counting already existed from #8; the target ratio is **not set**, because the baseline run was part of #28 |

- **The validated-borrow path was reached without sleeping.** Hikari skips validating a connection
  that was used in the last 500 ms. The integration tests set that window to 0, so every borrow is
  validated, the slow path production hits between healthcheck probes. The opposite case, a query
  on a just-used connection that skips validation (2.5 s worst case for a request, by the
  arithmetic in `application.properties`), isn't exercised by the tests.
- **Guards were checked by breaking them.** With metrics exposure temporarily set to `*`, 11 of
  `MetricsIT`'s 21 cases failed. With the body-limit filter unwired, the raw chunked-body test got
  a `201`.
- **Review findings were checked, not taken on authority.** `pr-reviewer`'s claim that readiness
  could exceed 2 s didn't reproduce (about 1.51 s measured). The timeouts were tightened anyway, which
  brings the worst case for borrowing a connection to 1.5 s (1 s connection timeout plus 0.5 s
  validation, written down in `application.properties`). The `server.shutdown` claim was disproved from the bytecode, as noted
  under Execution.
- **Mutation score held at 89%** (threshold 85%) through #24, #25 and #26.
- **Known gaps, stated plainly:**
  - #24's `503` and readiness bounds were tested against Testcontainers, not against an outage of
    the `docker compose` stack. On the compose stack, C3's job log shows a `503`, but no check
    asserts it.
  - The main context's 7 s shutdown share is configured but has never been tested with a bean
    that hangs, the way the management context's 3 s was.
  - Recovery after a real database stop and start isn't tested; only pause and resume is.
  - The just-used-connection path (above) isn't exercised.
  - The click drain and the loss-ratio target were not built (#28, above).
  - Rate limiting was rejected for now.

  Each of these is on #10's future-work list.
