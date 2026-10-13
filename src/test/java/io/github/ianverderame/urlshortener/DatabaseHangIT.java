package io.github.ianverderame.urlshortener;

import static io.github.ianverderame.urlshortener.DatabaseOutageIT.assertLoggedOneLineWithoutStackTraceOrSecrets;
import static io.github.ianverderame.urlshortener.DatabaseOutageIT.assertServiceUnavailableAt;
import static io.github.ianverderame.urlshortener.DatabaseOutageIT.boundedClient;
import static io.github.ianverderame.urlshortener.DatabaseOutageIT.createRequest;
import static io.github.ianverderame.urlshortener.DatabaseOutageIT.exchangeWithinBound;
import static io.github.ianverderame.urlshortener.DatabaseOutageIT.loggedDuring;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Issue #24: with the database hung, requests still fail fast with a specific problem. Pausing the
 * container keeps its port accepting connections but stops all replies, so a query sent on a pooled
 * connection waits on the socket, and a new connection waits in its handshake. The defect was a
 * wait bounded only by Hikari's 30 s connection timeout, or not at all for a query already sent.
 *
 * <p>This class owns its database container, so pausing it can't affect any other test's context.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(DatabaseHangIT.OwnDatabaseConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class DatabaseHangIT {

  @DynamicPropertySource
  static void apiKey(DynamicPropertyRegistry registry) {
    TestApiKey.register(registry);
  }

  @LocalServerPort int serverPort;

  @Autowired PostgreSQLContainer postgres;

  RestTestClient app;

  String code;

  /**
   * A successful create first, so the pool is running with open connections when the database
   * hangs, as it is in the running service (where Flyway and the readiness probe use the same
   * pool). In tests Flyway has its own connection, so without this the pool would first start
   * against a hung database.
   */
  @BeforeEach
  void setUp() {
    app = boundedClient(serverPort);
    code = createLink();
    postgres.getDockerClient().pauseContainerCmd(postgres.getContainerId()).exec();
  }

  /** Releases any request thread still waiting, so the context can shut down cleanly. */
  @AfterEach
  void unpauseDatabase() {
    postgres.getDockerClient().unpauseContainerCmd(postgres.getContainerId()).exec();
  }

  @Test
  void createFailsFastWithAServiceUnavailableProblem() {
    assertServiceUnavailableAt(exchangeWithinBound(createRequest(app)), "/api/v1/links");
  }

  @Test
  void redirectFailsFastWithAServiceUnavailableProblem() {
    assertServiceUnavailableAt(exchangeWithinBound(app.get().uri("/" + code)), "/" + code);
  }

  @Test
  void aFailedCreateLogsOneLineWithoutAStackTraceDestinationOrKey(CapturedOutput output) {
    assertLoggedOneLineWithoutStackTraceOrSecrets(
        loggedDuring(output, () -> createRequest(app).exchange()));
  }

  @Test
  void aFailedRedirectLogsOneLineWithoutAStackTrace(CapturedOutput output) {
    assertLoggedOneLineWithoutStackTraceOrSecrets(
        loggedDuring(output, () -> app.get().uri("/" + code).exchange()));
  }

  private String createLink() {
    AtomicReference<String> created = new AtomicReference<>();
    createRequest(app)
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody()
        .jsonPath("$.code")
        .value((String c) -> created.set(c));
    return created.get();
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class OwnDatabaseConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
      return new PostgreSQLContainer(TestcontainersConfiguration.POSTGRES_IMAGE);
    }
  }
}
