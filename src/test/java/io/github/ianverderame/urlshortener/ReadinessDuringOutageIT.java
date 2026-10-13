package io.github.ianverderame.urlshortener;

import static io.github.ianverderame.urlshortener.DatabaseOutageIT.boundedClient;
import static io.github.ianverderame.urlshortener.DatabaseOutageIT.exchangeWithin;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Issue #24: readiness tells the truth about the database quickly, and liveness doesn't depend on
 * it. The defect was a readiness probe that took 30 s to report the database as down, against the
 * compose healthcheck's 3 s timeout.
 *
 * <p>The database is hung by pausing its container, which also lets it recover on the same port.
 * This class owns that container, so pausing it can't affect any other test's context.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(ReadinessDuringOutageIT.OwnDatabaseConfig.class)
class ReadinessDuringOutageIT {

  /** The "Truthful readiness" SLO from #9. */
  static final Duration READINESS_BOUND = Duration.ofSeconds(2);

  static final String READINESS = "/actuator/health/readiness";

  static final String LIVENESS = "/actuator/health/liveness";

  /** Hikari's connection adder, plus one for pgJDBC's own connect thread during its attempt. */
  static final long MAX_THREADS_IN_THE_DRIVER = 2;

  @DynamicPropertySource
  static void apiKey(DynamicPropertyRegistry registry) {
    TestApiKey.register(registry);
  }

  @LocalManagementPort int managementPort;

  @Autowired PostgreSQLContainer postgres;

  RestTestClient management;

  boolean paused;

  /**
   * Ready first, so the pool is running with open connections when the database hangs, as it is in
   * the running service, where the healthcheck probes readiness all along.
   */
  @BeforeEach
  void setUp() {
    management = boundedClient(managementPort);
    probe(READINESS).expectStatus().isOk();
  }

  @AfterEach
  void unpauseDatabase() {
    if (paused) {
      resumeDatabase();
    }
  }

  @Test
  void readinessReportsTheDatabaseDownWithinTheBound() {
    hangDatabase();

    probe(READINESS)
        .expectStatus()
        .isEqualTo(503)
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("DOWN")
        .jsonPath("$.components.db.status")
        .isEqualTo("DOWN");
  }

  @Test
  void livenessStaysUpDuringTheOutage() {
    hangDatabase();
    probe(READINESS).expectStatus().isEqualTo(503);

    probe(LIVENESS).expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("UP");
  }

  @Test
  void readinessRecoversOnTheFirstProbeAfterTheDatabaseDoes() {
    hangDatabase();
    probe(READINESS).expectStatus().isEqualTo(503);

    resumeDatabase();

    probe(READINESS).expectStatus().isOk();
  }

  /**
   * A fixed ceiling rather than a before/after comparison, which would flake as Hikari's one
   * connection-adder thread moves in and out of the driver between attempts. Each probe has
   * returned, so none of its threads should still be there; the adder may be.
   */
  @Test
  void repeatedProbesDoNotBuildUpThreadsWaitingOnTheDatabase() {
    hangDatabase();

    for (int i = 0; i < 10; i++) {
      probe(READINESS).expectStatus().isEqualTo(503);
    }

    assertThat(threadsInTheDatabaseDriver()).isLessThanOrEqualTo(MAX_THREADS_IN_THE_DRIVER);
  }

  /** A readiness or liveness probe, which must answer within the readiness bound. */
  private RestTestClient.ResponseSpec probe(String path) {
    return exchangeWithin(management.get().uri(path), READINESS_BOUND);
  }

  /** Threads anywhere inside the PostgreSQL driver: request threads, pool threads, or its own. */
  private static long threadsInTheDatabaseDriver() {
    return Thread.getAllStackTraces().values().stream()
        .filter(
            stack ->
                Arrays.stream(stack)
                    .anyMatch(frame -> frame.getClassName().startsWith("org.postgresql.")))
        .count();
  }

  private void hangDatabase() {
    postgres.getDockerClient().pauseContainerCmd(postgres.getContainerId()).exec();
    paused = true;
  }

  private void resumeDatabase() {
    postgres.getDockerClient().unpauseContainerCmd(postgres.getContainerId()).exec();
    paused = false;
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
