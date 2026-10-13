package io.github.ianverderame.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
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
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Issue #8: a failed click write never reaches the visitor. The writer at the persistence boundary
 * always fails, and the recorder runs synchronously, so the failure happens on the request thread
 * itself: the redirect must still be a 302, and the loss is counted as a write failure.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, ClickWriteFailureIT.FailingWriterConfig.class})
@TestPropertySource(properties = "app.public-base-url=http://localhost:8080")
class ClickWriteFailureIT {

  @DynamicPropertySource
  static void apiKey(DynamicPropertyRegistry registry) {
    TestApiKey.register(registry);
  }

  @LocalServerPort int serverPort;

  @Autowired MeterRegistry meterRegistry;

  RestTestClient app;

  @BeforeEach
  void setUp() {
    app = RestTestClient.bindToServer().baseUrl("http://localhost:" + serverPort).build();
  }

  @Test
  void aFailedWriteStillRedirectsAndIsCountedAsAWriteFailure() {
    String code = createLink();
    double before = writeFailures();

    app.get().uri("/" + code).exchange().expectStatus().isFound();

    assertThat(writeFailures()).isEqualTo(before + 1);
  }

  private double writeFailures() {
    return meterRegistry.get("clicks.lost").tag("reason", "write_failure").counter().count();
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

  @TestConfiguration(proxyBeanMethods = false)
  static class FailingWriterConfig {

    @Bean
    @Primary
    ClickEventWriter failingClickEventWriter() {
      return (long linkId, Instant occurredAt) -> {
        throw new DataAccessResourceFailureException("database unavailable");
      };
    }

    @Bean
    @Primary
    ClickRecorder synchronousClickRecorder(
        ClickEventWriter writer, Clock clock, MeterRegistry meterRegistry) {
      return new ClickRecorder(TestExecutorService.synchronous(), writer, clock, meterRegistry);
    }
  }
}
