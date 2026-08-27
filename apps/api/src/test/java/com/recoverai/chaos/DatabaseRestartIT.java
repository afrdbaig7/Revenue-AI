package com.recoverai.chaos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.recoverai.tenant.domain.Organization;
import com.recoverai.tenant.infrastructure.OrganizationRepository;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Chaos test: Database Restart.
 *
 * Verifies that the connection pool recovers after temporary database unavailability,
 * in-flight transactions fail safely with appropriate error handling, and the application
 * can resume normal operation after DB reconnection.
 */
@Testcontainers
@SpringBootTest(properties = {
    "recoverai.event-dispatch-mode=inline",
    "recoverai.razorpay.mock-mode=true",
    "recoverai.ai.enabled=false",
    "spring.datasource.hikari.connection-timeout=1000",
    "spring.jpa.hibernate.ddl-auto=validate"
})
class DatabaseRestartIT {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
      .withDatabaseName("recoverai_test")
      .withUsername("recoverai")
      .withPassword("recoverai_dev");

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired OrganizationRepository organizations;

  @Test
  void applicationRecoversAfterDatabaseRestart() {
    Organization org = organizations.save(new Organization("Pre-Crash Org", "pre-" + UUID.randomUUID()));
    assertThat(organizations.findById(org.getId())).isPresent();

    // Pause the existing container so its durable data remains available after recovery.
    String containerId = POSTGRES.getContainerId();
    DockerClientFactory.instance().client().pauseContainerCmd(containerId).exec();
    try {
      assertThatThrownBy(
              () ->
                  organizations.saveAndFlush(
                      new Organization("In-Flight Org", "mid-" + UUID.randomUUID())))
          .isInstanceOfAny(
              CannotCreateTransactionException.class, DataAccessResourceFailureException.class);
    } finally {
      DockerClientFactory.instance().client().unpauseContainerCmd(containerId).exec();
    }

    // Verify application resumes operation and connection pool is healthy
    await()
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(() -> assertThat(organizations.findById(org.getId())).isPresent());
    Organization recoveredOrg =
        organizations.save(
            new Organization("Post-Crash Org", "post-" + UUID.randomUUID()));
    assertThat(organizations.findById(recoveredOrg.getId())).isPresent();
    assertThat(organizations.findById(org.getId())).isPresent();
  }
}
