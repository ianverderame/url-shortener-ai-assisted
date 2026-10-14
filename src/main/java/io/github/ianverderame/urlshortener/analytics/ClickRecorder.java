package io.github.ianverderame.urlshortener.analytics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Records followed links off the redirect path. The time is read on the caller's thread; the write
 * runs on a bounded executor. Delivery is at-most-once: {@link #record} never blocks or throws, and
 * each event lost to a full queue, a failed write (an exception, not an {@code Error}) or shutdown
 * is counted once under {@code clicks.lost}, tagged by reason.
 */
public class ClickRecorder {

  private static final Logger log = LoggerFactory.getLogger(ClickRecorder.class);

  private static final String LOST_METRIC = "clicks.lost";

  private final ExecutorService executor;
  private final ClickEventWriter writer;
  private final Clock clock;
  private final Counter overflow;
  private final Counter writeFailure;
  private final Counter shutdown;

  ClickRecorder(
      ExecutorService executor, ClickEventWriter writer, Clock clock, MeterRegistry meterRegistry) {
    this.executor = executor;
    this.writer = writer;
    this.clock = clock;
    this.overflow = lostCounter("overflow", meterRegistry);
    this.writeFailure = lostCounter("write_failure", meterRegistry);
    this.shutdown = lostCounter("shutdown", meterRegistry);
  }

  public void record(long linkId) {
    Instant occurredAt = Instant.now(clock);
    try {
      executor.execute(() -> write(linkId, occurredAt));
    } catch (RejectedExecutionException e) {
      (executor.isShutdown() ? shutdown : overflow).increment();
    }
  }

  /**
   * Stops the executor without draining it (the drain, #28, was descoped). Each unrun click is
   * counted under {@code reason=shutdown}.
   */
  void shutdown() {
    int unrun = executor.shutdownNow().size();
    shutdown.increment(unrun);
  }

  private void write(long linkId, Instant occurredAt) {
    try {
      writer.write(linkId, occurredAt);
    } catch (RuntimeException e) {
      writeFailure.increment();
      // The type only: a driver message can quote statement values.
      log.warn("Click event lost: write failed ({})", e.getClass().getSimpleName());
    }
  }

  private static Counter lostCounter(String reason, MeterRegistry meterRegistry) {
    return Counter.builder(LOST_METRIC)
        .description("Click events that were not stored, by reason")
        .tag("reason", reason)
        .register(meterRegistry);
  }
}
