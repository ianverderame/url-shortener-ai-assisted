package io.github.ianverderame.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Issue #41: a minimal demo page is served at the root, on the application port, without a key. It
 * calls the API from the same origin, so it needs no CORS, and the user pastes the key into it.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class DemoPageIT {

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
  void theDemoPageIsServedAtTheRootWithoutAKey() {
    app.get()
        .uri("/")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.TEXT_HTML);
  }

  // Every deployment serves this page, so it must never carry a key. It is a static file, so the
  // published demo key (pasted in under compose) is the realistic leak; the configured key is a
  // cheap guard against the page ever being templated.
  @Test
  void theDemoPageEmbedsNoApiKey() {
    assertThat(demoPage())
        .doesNotContain(TestApiKey.VALUE)
        .doesNotContainIgnoringCase("local-demo-key");
  }

  // CSRF stays off only while there are no cookies (CsrfAssumptionsIT); the page adds none.
  @Test
  void theDemoPageSetsNoCookie() {
    app.get()
        .uri("/")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .doesNotExist(HttpHeaders.SET_COOKIE);
  }

  // Destinations are user input, so the page renders server data as text only. It keeps the key in
  // memory: no cookies (CsrfAssumptionsIT) and no browser storage. A source check, since the build
  // runs no browser.
  @Test
  void theDemoPageUsesNoHtmlSinksAndNoBrowserStorage() {
    assertThat(demoPage())
        .doesNotContain(
            "innerHTML",
            "outerHTML",
            "insertAdjacentHTML",
            "setHTMLUnsafe",
            "createContextualFragment",
            "DOMParser",
            "document.write",
            "document.cookie",
            "cookieStore",
            "localStorage",
            "sessionStorage",
            "indexedDB",
            "caches");
  }

  private String demoPage() {
    byte[] body =
        app.get()
            .uri("/")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .returnResult()
            .getResponseBody();
    assertThat(body).as("demo page body").isNotNull();
    return new String(body, StandardCharsets.UTF_8);
  }
}
