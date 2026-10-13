package io.github.ianverderame.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Issue #5: submitted URLs and API keys never appear in an error response body or in a log line.
 * Create logs the code and the destination host only.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class SensitiveDataLeakIT {

  private static final String HOST = "leak-check.example";
  // The path and query are what must never leak; the host alone may be logged.
  private static final String SECRET_PART = "/private-path-7f3a?token=s3cr3t-marker";
  private static final String URL = "https://" + HOST + SECRET_PART;
  private static final String WRONG_KEY = "wrong-key-that-must-not-leak-0123456789abcdef";

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

  @Test
  void createLogsTheCodeAndHostButNotTheUrlOrKey(CapturedOutput output) {
    AtomicReference<String> code = new AtomicReference<>();
    create(TestApiKey.VALUE, MediaType.APPLICATION_JSON, json(URL))
        .expectStatus()
        .isCreated()
        .expectBody()
        .jsonPath("$.code")
        .value((String c) -> code.set(c));

    assertThat(output.getAll()).contains(code.get()).contains(HOST);
    assertNoLeak(output.getAll());
  }

  @Test
  void aRejectedDestinationLeaksNothing(CapturedOutput output) {
    String rejected = "ftp://" + HOST + SECRET_PART;
    String body = errorBody(create(TestApiKey.VALUE, MediaType.APPLICATION_JSON, json(rejected)));

    assertThat(body).doesNotContain(rejected);
    assertNoLeak(body);
    assertThat(output.getAll()).doesNotContain(rejected);
    assertNoLeak(output.getAll());
  }

  @Test
  void malformedJsonLeaksNothing(CapturedOutput output) {
    String body =
        errorBody(
            create(TestApiKey.VALUE, MediaType.APPLICATION_JSON, "{\"destination\":\"" + URL));

    assertNoLeak(body);
    assertNoLeak(output.getAll());
  }

  // #26: an unknown property's name and value stay out of the response and the logs.
  @Test
  void anUnknownPropertyLeaksNothing(CapturedOutput output) {
    String unknown = "{\"destination\":\"" + URL + "\",\"s3cr3t-marker\":\"" + URL + "\"}";
    String body = errorBody(create(TestApiKey.VALUE, MediaType.APPLICATION_JSON, unknown));

    assertNoLeak(body);
    assertNoLeak(output.getAll());
  }

  // #26: an oversized body's content stays out of the response and the logs.
  @Test
  void anOversizedBodyLeaksNothing(CapturedOutput output) {
    String json = json(URL);
    String oversized = json.substring(0, json.length() - 1) + " ".repeat(8 * 1024) + "}";
    String body = errorBody(create(TestApiKey.VALUE, MediaType.APPLICATION_JSON, oversized));

    assertNoLeak(body);
    assertNoLeak(output.getAll());
  }

  @Test
  void anUnsupportedContentTypeLeaksNothing(CapturedOutput output) {
    String body = errorBody(create(TestApiKey.VALUE, MediaType.TEXT_PLAIN, URL));

    assertNoLeak(body);
    assertNoLeak(output.getAll());
  }

  @Test
  void aWrongKeyLeaksNeitherKey(CapturedOutput output) {
    String body = errorBody(create(WRONG_KEY, MediaType.APPLICATION_JSON, json(URL)));

    assertThat(body).doesNotContain(WRONG_KEY);
    assertNoLeak(body);
    assertThat(output.getAll()).doesNotContain(WRONG_KEY);
    assertNoLeak(output.getAll());
  }

  private RestTestClient.ResponseSpec create(String key, MediaType contentType, String body) {
    return app.post()
        .uri("/api/v1/links")
        .header("X-API-Key", key)
        .contentType(contentType)
        .body(body)
        .exchange();
  }

  private static String errorBody(RestTestClient.ResponseSpec response) {
    byte[] body =
        response
            .expectStatus()
            .is4xxClientError()
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .returnResult()
            .getResponseBody();
    return body == null ? "" : new String(body, StandardCharsets.UTF_8);
  }

  private static void assertNoLeak(String text) {
    // Path and query checked separately, so a leak of either alone is caught.
    assertThat(text)
        .doesNotContain("private-path-7f3a")
        .doesNotContain("s3cr3t-marker")
        .doesNotContain(TestApiKey.VALUE);
  }

  private static String json(String destination) {
    return "{\"destination\":\"" + destination + "\"}";
  }
}
