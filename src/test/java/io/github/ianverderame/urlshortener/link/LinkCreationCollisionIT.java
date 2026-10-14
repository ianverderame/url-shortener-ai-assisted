package io.github.ianverderame.urlshortener.link;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.ianverderame.urlshortener.ErrorContractIT;
import io.github.ianverderame.urlshortener.TestApiKey;
import io.github.ianverderame.urlshortener.TestcontainersConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
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
 * Issue #4: create retries on a short-code conflict and gives up with a 503 after a bounded number
 * of attempts. The code generator is the injectable seam; a scripted generator makes collisions
 * deterministic.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, LinkCreationCollisionIT.ScriptedGeneratorConfig.class})
@TestPropertySource(properties = "app.public-base-url=http://localhost:8080")
class LinkCreationCollisionIT {

  private static final String EXHAUSTION_METRIC = "links.code.generation.exhausted";

  @DynamicPropertySource
  static void apiKey(DynamicPropertyRegistry registry) {
    TestApiKey.register(registry);
  }

  @LocalServerPort int serverPort;

  @Autowired ScriptedShortCodeGenerator generator;

  @Autowired MeterRegistry meterRegistry;

  RestTestClient app;

  @BeforeEach
  void setUp() {
    app = RestTestClient.bindToServer().baseUrl("http://localhost:" + serverPort).build();
  }

  @Test
  void aCodeConflictIsRetriedWithAFreshCode() {
    generator.willReturn("Retry001");
    create().expectStatus().isCreated();

    generator.willReturn("Retry001", "Retry002");
    create().expectStatus().isCreated().expectBody().jsonPath("$.code").isEqualTo("Retry002");
  }

  @Test
  void persistentConflictsReturnServiceUnavailableAndCountTheExhaustion() {
    generator.willReturn("Taken001");
    create().expectStatus().isCreated();
    double before = exhaustionCount();

    generator.willAlwaysReturn("Taken001");
    ErrorContractIT.assertExactProblem(
        create(),
        503,
        """
        {"type": "about:blank", "title": "Service Unavailable", "status": 503,
         "detail": "Could not allocate a short code. Try again.", "instance": "/api/v1/links"}
        """);

    assertThat(exhaustionCount()).isEqualTo(before + 1);
  }

  private RestTestClient.ResponseSpec create() {
    return app.post()
        .uri("/api/v1/links")
        .header("X-API-Key", TestApiKey.VALUE)
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"destination\":\"https://example.com/some/path\"}")
        .exchange();
  }

  private double exhaustionCount() {
    return meterRegistry.get(EXHAUSTION_METRIC).counter().count();
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ScriptedGeneratorConfig {

    @Bean
    @Primary
    ScriptedShortCodeGenerator scriptedShortCodeGenerator() {
      return new ScriptedShortCodeGenerator();
    }
  }

  /** Returns scripted codes in order, then repeats the "always" code if one is set. */
  static class ScriptedShortCodeGenerator implements ShortCodeGenerator {

    private final Deque<String> codes = new ArrayDeque<>();
    private String always;

    synchronized void willReturn(String... next) {
      codes.clear();
      codes.addAll(List.of(next));
      always = null;
    }

    synchronized void willAlwaysReturn(String code) {
      codes.clear();
      always = code;
    }

    @Override
    public synchronized String generate() {
      String next = codes.poll();
      if (next != null) {
        return next;
      }
      if (always == null) {
        throw new IllegalStateException("No scripted code left");
      }
      return always;
    }
  }
}
