package io.github.ianverderame.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * The click recorder's contract, against a fake writer at the persistence boundary: the time is
 * read on the caller's thread, recording never throws, and every lost event is counted once under
 * its reason. Overflow and shutdown use a real bounded pool held on a latch, so nothing sleeps.
 */
class ClickRecorderTest {

  private static final Instant NOW = Instant.parse("2026-10-13T12:00:00Z");
  private static final String LOST_METRIC = "clicks.lost";

  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final RecordingWriter writer = new RecordingWriter();
  private final CountDownLatch release = new CountDownLatch(1);
  private ThreadPoolExecutor pool;

  @AfterEach
  void releaseBlockedWriter() {
    release.countDown();
    if (pool != null) {
      pool.shutdownNow();
    }
  }

  @Test
  void recordWritesTheLinkAtTheClocksTime() {
    ClickRecorder recorder = recorder(TestExecutorService.synchronous(), fixedClock(NOW));

    recorder.record(7L);

    assertThat(writer.written).containsExactly(new Click(7L, NOW));
    assertThat(lost("overflow") + lost("write_failure") + lost("shutdown")).isZero();
  }

  @Test
  void theTimeIsReadWhenTheClickIsRecordedNotWhenItIsWritten() {
    MutableClock clock = new MutableClock(NOW);
    TestExecutorService executor = TestExecutorService.deferred();
    ClickRecorder recorder = recorder(executor, clock);

    recorder.record(7L);
    clock.now = NOW.plusSeconds(90);
    executor.runPending();

    assertThat(writer.written).containsExactly(new Click(7L, NOW));
  }

  @Test
  void aFailedWriteIsSwallowedAndCountedAsAWriteFailure() {
    writer.failWith(new DataAccessResourceFailureException("database unavailable"));
    ClickRecorder recorder = recorder(TestExecutorService.synchronous(), fixedClock(NOW));

    assertThatNoException().isThrownBy(() -> recorder.record(7L));

    assertThat(lost("write_failure")).isEqualTo(1);
    assertThat(lost("overflow") + lost("shutdown")).isZero();
  }

  @Test
  void aFullQueueRejectsTheClickWithoutThrowingAndCountsAnOverflow() throws InterruptedException {
    writer.blockUntil(release);
    ClickRecorder recorder = recorder(boundedPool(1), fixedClock(NOW));

    recorder.record(1L); // runs on the worker and blocks
    writer.awaitBlocked();
    recorder.record(2L); // fills the queue
    assertThatNoException().isThrownBy(() -> recorder.record(3L));

    assertThat(lost("overflow")).isEqualTo(1);
    assertThat(lost("write_failure") + lost("shutdown")).isZero();
  }

  @Test
  void clicksStillQueuedAtShutdownAreCountedAsShutdownLosses() throws InterruptedException {
    writer.blockUntil(release);
    ClickRecorder recorder = recorder(boundedPool(5), fixedClock(NOW));
    recorder.record(1L);
    writer.awaitBlocked();
    recorder.record(2L);
    recorder.record(3L);

    recorder.shutdown();

    assertThat(lost("shutdown")).isEqualTo(2);
    assertThat(lost("overflow")).isZero();
  }

  @Test
  void aClickRecordedAfterShutdownIsCountedAsAShutdownLoss() {
    ClickRecorder recorder = recorder(TestExecutorService.synchronous(), fixedClock(NOW));
    recorder.shutdown();

    assertThatNoException().isThrownBy(() -> recorder.record(7L));

    assertThat(writer.written).isEmpty();
    assertThat(lost("shutdown")).isEqualTo(1);
    assertThat(lost("overflow")).isZero();
  }

  @Test
  void everyReasonIsRegisteredAtZeroBeforeAnythingIsLost() {
    recorder(TestExecutorService.synchronous(), fixedClock(NOW));

    assertThat(meters.find(LOST_METRIC).counters())
        .extracting(counter -> counter.getId().getTag("reason"))
        .containsExactlyInAnyOrder("overflow", "write_failure", "shutdown");
  }

  private ClickRecorder recorder(ExecutorService executor, Clock clock) {
    return new ClickRecorder(executor, writer, clock, meters);
  }

  private ThreadPoolExecutor boundedPool(int capacity) {
    pool =
        new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(capacity));
    return pool;
  }

  private double lost(String reason) {
    var counter = meters.find(LOST_METRIC).tag("reason", reason).counter();
    return counter == null ? 0 : counter.count();
  }

  private static Clock fixedClock(Instant instant) {
    return Clock.fixed(instant, ZoneOffset.UTC);
  }

  record Click(long linkId, Instant occurredAt) {}

  /** Records writes; can be made to fail, or to block on a latch to hold the worker busy. */
  static final class RecordingWriter implements ClickEventWriter {

    final List<Click> written = new CopyOnWriteArrayList<>();
    private final CountDownLatch blocked = new CountDownLatch(1);
    private RuntimeException failure;
    private CountDownLatch gate;

    void failWith(RuntimeException failure) {
      this.failure = failure;
    }

    void blockUntil(CountDownLatch gate) {
      this.gate = gate;
    }

    void awaitBlocked() throws InterruptedException {
      assertThat(blocked.await(10, TimeUnit.SECONDS)).as("writer reached the latch").isTrue();
    }

    @Override
    public void write(long linkId, Instant occurredAt) {
      if (gate != null) {
        blocked.countDown();
        try {
          gate.await();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        }
      }
      if (failure != null) {
        throw failure;
      }
      written.add(new Click(linkId, occurredAt));
    }
  }

  static final class MutableClock extends Clock {

    Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    @Override
    public Instant instant() {
      return now;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      throw new UnsupportedOperationException();
    }
  }
}
