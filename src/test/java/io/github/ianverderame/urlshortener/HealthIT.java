package io.github.ianverderame.urlshortener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Starts the application against containerized PostgreSQL and checks the operational surface:
 * health lives on the management port only, and readiness includes the database.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class HealthIT {

	@LocalServerPort
	int serverPort;

	@LocalManagementPort
	int managementPort;

	RestTestClient management;

	RestTestClient app;

	@BeforeEach
	void setUp() {
		management = RestTestClient.bindToServer().baseUrl("http://localhost:" + managementPort).build();
		app = RestTestClient.bindToServer().baseUrl("http://localhost:" + serverPort).build();
	}

	@Test
	void readinessIsUpAndIncludesTheDatabase() {
		management.get().uri("/actuator/health/readiness").exchange()
			.expectStatus().isOk()
			.expectBody()
			.jsonPath("$.status").isEqualTo("UP")
			.jsonPath("$.components.db.status").isEqualTo("UP");
	}

	@Test
	void livenessIsUp() {
		management.get().uri("/actuator/health/liveness").exchange()
			.expectStatus().isOk()
			.expectBody()
			.jsonPath("$.status").isEqualTo("UP");
	}

	@Test
	void healthIsNotServedOnTheApplicationPort() {
		app.get().uri("/actuator/health").exchange()
			.expectStatus().isNotFound();
	}

	@Test
	void managementPortExposesOnlyHealth() {
		management.get().uri("/actuator/env").exchange()
			.expectStatus().isNotFound();
	}

	@Test
	void managementPortHasNoDiscoveryPage() {
		management.get().uri("/actuator").exchange()
			.expectStatus().isNotFound();
	}

}
