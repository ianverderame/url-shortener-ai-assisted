package io.github.ianverderame.urlshortener;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Issue #8: the stats endpoint through the HTTP API on real PostgreSQL. "Today" is a fixed clock,
 * and clicks are stored with exact timestamps, so the UTC day boundaries and the half-open range
 * are pinned without waiting on anything.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, LinkStatsIT.FixedClockConfig.class})
@TestPropertySource(properties = "app.public-base-url=http://localhost:8080")
class LinkStatsIT {

  private static final Instant NOW = Instant.parse("2026-10-13T12:00:00Z");

  @DynamicPropertySource
  static void apiKey(DynamicPropertyRegistry registry) {
    TestApiKey.register(registry);
  }

  @LocalServerPort int serverPort;

  @Autowired JdbcClient jdbc;

  RestTestClient app;

  @BeforeEach
  void setUp() {
    app = RestTestClient.bindToServer().baseUrl("http://localhost:" + serverPort).build();
  }

  @Test
  void theDefaultWindowIsTheThirtyDaysEndingTodayWithEveryDayListed() {
    String code = createLink();

    stats("/api/v1/links/" + code + "/stats")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo(code)
        .jsonPath("$.from")
        .isEqualTo("2026-09-14")
        .jsonPath("$.to")
        .isEqualTo("2026-10-13")
        .jsonPath("$.total")
        .isEqualTo(0)
        .jsonPath("$.days.length()")
        .isEqualTo(30)
        .jsonPath("$.days[0].date")
        .isEqualTo("2026-09-14")
        .jsonPath("$.days[0].clicks")
        .isEqualTo(0)
        .jsonPath("$.days[29].date")
        .isEqualTo("2026-10-13");
  }

  @Test
  void clicksFallOnTheirUtcDayAndOnlyInsideTheInclusiveRange() {
    String code = createLink();
    click(code, "2026-10-09T23:59:59.999Z"); // the day before 'from': excluded
    click(code, "2026-10-10T00:00:00Z"); // first instant of 'from'
    click(code, "2026-10-11T23:59:59.999Z"); // last instant of the 11th...
    click(code, "2026-10-12T00:00:00Z"); // ...and the 12th starts a new day
    click(code, "2026-10-12T23:59:59.999Z"); // last instant of 'to'
    click(code, "2026-10-13T00:00:00Z"); // the day after 'to': excluded

    stats("/api/v1/links/" + code + "/stats?from=2026-10-10&to=2026-10-12")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.total")
        .isEqualTo(4)
        .jsonPath("$.days.length()")
        .isEqualTo(3)
        .jsonPath("$.days[0].date")
        .isEqualTo("2026-10-10")
        .jsonPath("$.days[0].clicks")
        .isEqualTo(1)
        .jsonPath("$.days[1].date")
        .isEqualTo("2026-10-11")
        .jsonPath("$.days[1].clicks")
        .isEqualTo(1)
        .jsonPath("$.days[2].date")
        .isEqualTo("2026-10-12")
        .jsonPath("$.days[2].clicks")
        .isEqualTo(2);
  }

  @Test
  void anotherLinksClicksAreNotCounted() {
    String code = createLink();
    String other = createLink();
    click(other, "2026-10-13T01:00:00Z");

    stats("/api/v1/links/" + code + "/stats?from=2026-10-13&to=2026-10-13")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.total")
        .isEqualTo(0);
  }

  @Test
  void aSpanOverThe366DayLimitIsABadRequest() {
    String code = createLink();
    ErrorContractIT.assertProblem(
        stats("/api/v1/links/" + code + "/stats?from=2025-10-12&to=2026-10-13"), 400);
  }

  @Test
  void fromAfterToIsABadRequest() {
    String code = createLink();
    ErrorContractIT.assertProblem(
        stats("/api/v1/links/" + code + "/stats?from=2026-10-13&to=2026-10-12"), 400);
  }

  @Test
  void toAfterTodayIsABadRequest() {
    String code = createLink();
    ErrorContractIT.assertProblem(stats("/api/v1/links/" + code + "/stats?to=2026-10-14"), 400);
  }

  @Test
  void aMalformedDateIsABadRequest() {
    String code = createLink();
    ErrorContractIT.assertProblem(stats("/api/v1/links/" + code + "/stats?from=13-10-2026"), 400);
  }

  @Test
  void anUnknownCodeIsNotFound() {
    ErrorContractIT.assertProblem(stats("/api/v1/links/Zz9Zz9Zz/stats"), 404);
  }

  @Test
  void statsRequireTheApiKey() {
    String code = createLink();
    ErrorContractIT.assertProblem(
        app.get().uri("/api/v1/links/" + code + "/stats").exchange(), 401);
  }

  private RestTestClient.ResponseSpec stats(String uri) {
    return app.get().uri(uri).header("X-API-Key", TestApiKey.VALUE).exchange();
  }

  private void click(String code, String occurredAt) {
    jdbc.sql(
            "INSERT INTO click_events (link_id, occurred_at) "
                + "SELECT id, :occurredAt FROM links WHERE code = :code")
        .param("code", code)
        .param("occurredAt", OffsetDateTime.ofInstant(Instant.parse(occurredAt), ZoneOffset.UTC))
        .update();
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
  static class FixedClockConfig {

    @Bean
    @Primary
    Clock fixedClock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }
}
