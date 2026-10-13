package io.github.ianverderame.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
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
 * Issue #8: a GET redirect to an existing link stores exactly one click; HEAD and 404 store none.
 * The recorder runs on a synchronous executor, so the stored rows can be read right after the
 * response, with the real writer and real PostgreSQL.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, ClickCaptureIT.SynchronousRecorderConfig.class})
@TestPropertySource(properties = "app.public-base-url=http://localhost:8080")
class ClickCaptureIT {

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
  void aGetRedirectStoresExactlyOneClickForThatLink() {
    String code = createLink();

    app.get().uri("/" + code).exchange().expectStatus().isFound();

    assertThat(clicksFor(code)).isEqualTo(1);
  }

  @Test
  void aHeadRequestRedirectsButStoresNoClick() {
    String code = createLink();

    app.head().uri("/" + code).exchange().expectStatus().isFound();

    assertThat(clicksFor(code)).isZero();
  }

  @Test
  void anUnknownCodeStoresNoClick() {
    long before = allClicks();

    app.get().uri("/Zz9Zz9Zz").exchange().expectStatus().isNotFound();

    assertThat(allClicks()).isEqualTo(before);
  }

  private long clicksFor(String code) {
    return jdbc.sql(
            "SELECT count(*) FROM click_events c JOIN links l ON l.id = c.link_id "
                + "WHERE l.code = :code")
        .param("code", code)
        .query(Long.class)
        .single();
  }

  private long allClicks() {
    return jdbc.sql("SELECT count(*) FROM click_events").query(Long.class).single();
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
  static class SynchronousRecorderConfig {

    @Bean
    @Primary
    ClickRecorder synchronousClickRecorder(
        ClickEventWriter writer, Clock clock, MeterRegistry meterRegistry) {
      return new ClickRecorder(TestExecutorService.synchronous(), writer, clock, meterRegistry);
    }
  }
}
