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
 * Issue #24: the database hangs before the connection pool has ever started. In tests Flyway has
 * its own connection, so the pool starts on the first request; here that is against a hung
 * database. Starting the pool must be bounded like any other wait: a {@code 503} within the bound,
 * logged as one line.
 *
 * <p>The pool starts only once per context, so this class has a single test. It owns its database
 * container, so pausing it can't affect any other test's context.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(DatabaseHangAtPoolStartIT.OwnDatabaseConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class DatabaseHangAtPoolStartIT {

  @DynamicPropertySource
  static void apiKey(DynamicPropertyRegistry registry) {
    TestApiKey.register(registry);
  }

  @LocalServerPort int serverPort;

  @Autowired PostgreSQLContainer postgres;

  RestTestClient app;

  @BeforeEach
  void setUp() {
    app = boundedClient(serverPort);
    postgres.getDockerClient().pauseContainerCmd(postgres.getContainerId()).exec();
  }

  /** Releases any thread still waiting, so the context can shut down cleanly. */
  @AfterEach
  void unpauseDatabase() {
    postgres.getDockerClient().unpauseContainerCmd(postgres.getContainerId()).exec();
  }

  @Test
  void theFirstRequestFailsFastAndLogsOneLine(CapturedOutput output) {
    AtomicReference<RestTestClient.ResponseSpec> response = new AtomicReference<>();
    String logged =
        loggedDuring(output, () -> response.set(exchangeWithinBound(createRequest(app))));

    assertServiceUnavailableAt(response.get(), "/api/v1/links");
    assertLoggedOneLineWithoutStackTraceOrSecrets(logged);
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
