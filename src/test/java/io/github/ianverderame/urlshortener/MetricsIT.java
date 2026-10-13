package io.github.ianverderame.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The built-in metrics endpoint is served on the management port only, without a key, and lists the
 * meters the SLOs in the README are read from. Health and metrics are the only exposed actuator
 * endpoints.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class MetricsIT {

  // http.server.requests is recorded after the response is written, so a listing taken right
  // after a request can still miss it. Re-reading the listing a bounded number of times (no
  // sleeping) gives the server time to finish recording.
  private static final int LISTING_ATTEMPTS = 50;

  @DynamicPropertySource
  static void apiKey(DynamicPropertyRegistry registry) {
    TestApiKey.register(registry);
  }

  @LocalServerPort int serverPort;

  @LocalManagementPort int managementPort;

  RestTestClient management;

  RestTestClient app;

  @BeforeEach
  void setUp() {
    management =
        RestTestClient.bindToServer().baseUrl("http://localhost:" + managementPort).build();
    app = RestTestClient.bindToServer().baseUrl("http://localhost:" + serverPort).build();
  }

  record MetricNames(List<String> names) {}

  @Test
  void metricsAreServedOnTheManagementPortWithoutAKey() {
    management.get().uri("/actuator/metrics").exchange().expectStatus().isOk();
  }

  @Test
  void metricsAreNotServedOnTheApplicationPort() {
    app.get().uri("/actuator/metrics").exchange().expectStatus().isNotFound();
  }

  @Test
  void listsTheMetersTheSlosAreReadFrom() {
    // An unknown code in the valid format is looked up in the database, so this request starts the
    // connection pool if nothing has yet (the hikaricp meters are registered when it starts; with
    // a @ServiceConnection, Flyway migrates over its own connection, not the pool) and is itself
    // recorded in http.server.requests.
    app.get().uri("/Zz9Zz9Zz").exchange().expectStatus().isNotFound();

    List<String> names = List.of();
    for (int attempt = 0;
        attempt < LISTING_ATTEMPTS && !names.contains("http.server.requests");
        attempt++) {
      names = metricNames();
    }

    assertThat(names)
        .contains(
            "links.code.generation.exhausted",
            "clicks.lost",
            "http.server.requests",
            "hikaricp.connections",
            "hikaricp.connections.active",
            "hikaricp.connections.pending",
            "hikaricp.connections.timeout");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "auditevents",
        "beans",
        "caches",
        "conditions",
        "configprops",
        "env",
        "flyway",
        "heapdump",
        "httpexchanges",
        "info",
        "loggers",
        "mappings",
        "prometheus",
        "sbom",
        "scheduledtasks",
        "shutdown",
        "startup",
        "threaddump"
      })
  void noOtherActuatorEndpointIsExposed(String endpoint) {
    ErrorContractIT.assertProblem(management.get().uri("/actuator/" + endpoint).exchange(), 404);
    ErrorContractIT.assertProblem(management.post().uri("/actuator/" + endpoint).exchange(), 404);
  }

  private List<String> metricNames() {
    MetricNames body =
        management
            .get()
            .uri("/actuator/metrics")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(MetricNames.class)
            .returnResult()
            .getResponseBody();
    assertThat(body).isNotNull();
    return body.names();
  }
}
