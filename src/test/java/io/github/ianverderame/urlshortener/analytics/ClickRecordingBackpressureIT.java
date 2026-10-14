package io.github.ianverderame.urlshortener.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import io.github.ianverderame.urlshortener.TestApiKey;
import io.github.ianverderame.urlshortener.TestcontainersConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Issue #8 performance guard, deterministic rather than wall-clock: with the recorder's only worker
 * held on a latch, redirects still return 302, and once its one-slot queue is full each further
 * click is dropped and counted as an overflow. The timeout is only a hang backstop.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, ClickRecordingBackpressureIT.HeldRecorderConfig.class})
@TestPropertySource(properties = "app.public-base-url=http://localhost:8080")
class ClickRecordingBackpressureIT {

  private static final Duration HANG_BACKSTOP = Duration.ofSeconds(10);

  @DynamicPropertySource
  static void apiKey(DynamicPropertyRegistry registry) {
    TestApiKey.register(registry);
  }

  @LocalServerPort int serverPort;

  @Autowired HeldWriter writer;

  @Autowired MeterRegistry meterRegistry;

  RestTestClient app;

  @BeforeEach
  void setUp() {
    app = RestTestClient.bindToServer().baseUrl("http://localhost:" + serverPort).build();
  }

  @AfterEach
  void releaseTheWorker() {
    writer.release.countDown();
  }

  @Test
  void redirectsStillSucceedWhileTheRecorderIsBlockedAndOverflowIsCounted() throws Exception {
    String code = createLink();
    double overflowBefore = overflows();

    // The worker takes the first click and blocks on the latch.
    assertRedirects(code);
    assertThat(writer.blocked.await(HANG_BACKSTOP.toSeconds(), TimeUnit.SECONDS))
        .as("worker reached the latch")
        .isTrue();

    // The recorder is now held: the next click fills the queue, and the redirect is unaffected.
    assertRedirects(code);
    assertThat(overflows()).isEqualTo(overflowBefore);

    // The queue is full: this click is dropped and counted, and the redirect is still unaffected.
    assertRedirects(code);
    assertThat(overflows()).isEqualTo(overflowBefore + 1);
  }

  private void assertRedirects(String code) {
    assertTimeoutPreemptively(
        HANG_BACKSTOP, () -> app.get().uri("/" + code).exchange().expectStatus().isFound());
  }

  private double overflows() {
    return meterRegistry.get("clicks.lost").tag("reason", "overflow").counter().count();
  }

  private String createLink() {
    AtomicReference<String> code = new AtomicReference<>();
    app.post()
        .uri("/api/v1/links")
        .header("X-API-Key", TestApiKey.VALUE)
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"destination\":\"https://example.com/some/path\"}")
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody()
        .jsonPath("$.code")
        .value((String c) -> code.set(c));
    return code.get();
  }

  /** Blocks every write until released; signals when the worker has reached the latch. */
  static final class HeldWriter implements ClickEventWriter {

    final CountDownLatch blocked = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);

    @Override
    public void write(long linkId, Instant occurredAt) {
      blocked.countDown();
      try {
        release.await();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class HeldRecorderConfig {

    @Bean
    @Primary
    HeldWriter heldWriter() {
      return new HeldWriter();
    }

    /** The real recorder on a real pool, shrunk to one worker and a one-slot queue. */
    @Bean(destroyMethod = "shutdown")
    @Primary
    ClickRecorder heldClickRecorder(HeldWriter writer, Clock clock, MeterRegistry meterRegistry) {
      ThreadPoolExecutor pool =
          new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1));
      return new ClickRecorder(pool, writer, clock, meterRegistry);
    }
  }
}
