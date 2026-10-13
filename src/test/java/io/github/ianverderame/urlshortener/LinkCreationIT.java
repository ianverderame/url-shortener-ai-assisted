package io.github.ianverderame.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Tracer bullet for issue #4: creating a link via the public API.
 *
 * <p>Grows one vertical slice at a time; only the "happy path" create is covered so far.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, LinkCreationIT.FixedClockConfig.class})
@TestPropertySource(
    properties = {
      "app.api-key=test-api-key-for-link-creation-it",
      "app.public-base-url=http://localhost:8080"
    })
class LinkCreationIT {

  private static final String API_KEY = "test-api-key-for-link-creation-it";
  private static final String PUBLIC_BASE_URL = "http://localhost:8080";
  // Microsecond precision: PostgreSQL timestamptz stores no finer.
  private static final Instant NOW = Instant.parse("2026-01-02T03:04:05.123456Z");

  @LocalServerPort int serverPort;

  RestTestClient app;

  @BeforeEach
  void setUp() {
    app = RestTestClient.bindToServer().baseUrl("http://localhost:" + serverPort).build();
  }

  @Test
  void creatingALinkWithAValidKeyAndDestinationReturnsCreatedShape() {
    String destination = "https://example.com/some/path";
    AtomicReference<String> capturedCode = new AtomicReference<>();

    app.post()
        .uri("/api/v1/links")
        .header("X-API-Key", API_KEY)
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"destination\":\"" + destination + "\"}")
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody()
        .jsonPath("$.code")
        .value(
            (String code) -> {
              assertThat(code)
                  .as("code is an 8-character base62 string")
                  .matches("^[0-9A-Za-z]{8}$");
              capturedCode.set(code);
            })
        .jsonPath("$.destination")
        .isEqualTo(destination)
        .jsonPath("$.createdAt")
        .isEqualTo(NOW.toString())
        .jsonPath("$.shortUrl")
        .value(
            (String shortUrl) -> {
              assertThat(capturedCode.get())
                  .as("code must have been captured before shortUrl is checked")
                  .isNotNull();
              assertThat(shortUrl)
                  .as("shortUrl is the configured public base URL plus the code")
                  .isEqualTo(PUBLIC_BASE_URL + "/" + capturedCode.get());
            });
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
