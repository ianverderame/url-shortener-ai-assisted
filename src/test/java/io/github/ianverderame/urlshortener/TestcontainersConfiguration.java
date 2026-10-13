package io.github.ianverderame.urlshortener;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

  // Keep in step with the postgres image in compose.yaml.
  static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:18.6-alpine3.24");

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgresContainer() {
    return new PostgreSQLContainer(POSTGRES_IMAGE);
  }
}
