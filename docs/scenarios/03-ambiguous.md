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
| [#28](https://github.com/ianverderame/url-shortener-ai-assisted/issues/28) Drain click events | Flush queued clicks within 5 s; count what can't be flushed | Blocked by #8, #27 and #25 |

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

To be completed by the implementation issues (#24 to #28).

## Validation

To be completed by the implementation issues (#24 to #28).
