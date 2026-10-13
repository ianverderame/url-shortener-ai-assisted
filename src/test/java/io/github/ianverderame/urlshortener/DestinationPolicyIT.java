package io.github.ianverderame.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Issue #6: the destination policy end to end. What is stored, returned, and redirected to is
 * exactly the policy's normalized string; a rejection is a 400 problem carrying the reason code.
 * The full case table is {@link DestinationPolicyTest}; these rows exercise the HTTP path.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "app.public-base-url=https://short.example")
class DestinationPolicyIT {

  @DynamicPropertySource
  static void apiKey(DynamicPropertyRegistry registry) {
    TestApiKey.register(registry);
  }

  @LocalServerPort int serverPort;

  RestTestClient app;

  @BeforeEach
  void setUp() {
    app = RestTestClient.bindToServer().baseUrl("http://localhost:" + serverPort).build();
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "HTTPS://Example.COM/Path/To?Query=Value#Frag | https://example.com/Path/To?Query=Value#Frag",
        "https://example.com/%0D%0ASet-Cookie:x=y     | https://example.com/%0D%0ASet-Cookie:x=y",
        "https://example.com/?next=user@evil.example  | https://example.com/?next=user@evil.example",
        "http://[2001:DB8::A]:8080/x                  | http://[2001:db8::a]:8080/x",
        "https://example.com:443/a/./b/../c?          | https://example.com:443/a/./b/../c?"
      })
  void theStoredDestinationAndTheRedirectAreTheNormalizedString(
      String submitted, String normalized) {
    AtomicReference<String> code = new AtomicReference<>();

    app.post()
        .uri("/api/v1/links")
        .header("X-API-Key", TestApiKey.VALUE)
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"destination\":\"" + submitted + "\"}")
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody()
        .jsonPath("$.destination")
        .isEqualTo(normalized)
        .jsonPath("$.code")
        .value((String c) -> code.set(c));

    app.get()
        .uri("/" + code.get())
        .exchange()
        .expectStatus()
        .isFound()
        .expectHeader()
        .valueEquals(HttpHeaders.LOCATION, normalized);
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "javascript:alert(1)                    | UNSUPPORTED_SCHEME",
        "https://trusted.example@evil.example/  | USERINFO_NOT_ALLOWED",
        "https://SHORT.EXAMPLE.:8443/abc12345   | SELF_HOST",
        "https://xn--80ak6aa92e.com/            | NON_ASCII_HOST",
        "http://2130706433/                     | MALFORMED_URL"
      })
  void aRejectedDestinationIsABadRequestWithItsReason(String submitted, String reason) {
    byte[] body =
        app.post()
            .uri("/api/v1/links")
            .header("X-API-Key", TestApiKey.VALUE)
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"destination\":\"" + submitted + "\"}")
            .exchange()
            .expectStatus()
            .isBadRequest()
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.reason")
            .isEqualTo(reason)
            .jsonPath("$.detail")
            .isEqualTo(RejectionReason.valueOf(reason).detail())
            .returnResult()
            .getResponseBodyContent();

    assertThat(new String(body, StandardCharsets.UTF_8)).doesNotContain(submitted);
  }
}
