package com.recoverai.chaos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recoverai.incident.infrastructure.RevenueIncidentRepository;
import com.recoverai.merchant.infrastructure.MerchantRepository;
import com.recoverai.recovery.domain.RecoveryAction;
import com.recoverai.recovery.infrastructure.RecoveryActionRepository;
import com.recoverai.support.PersistenceTestFixtures;
import com.recoverai.tenant.infrastructure.OrganizationRepository;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Chaos test: Concurrent Action Execution.
 *
 * Verifies that when two threads attempt to execute the same recovery action
 * simultaneously, only one succeeds. Tests both idempotency key constraints
 * (preventing duplicate inserts) and optimistic locking (@Version) rejecting
 * stale updates.
 */
@Testcontainers
@SpringBootTest(properties = {
    "recoverai.event-dispatch-mode=inline",
    "recoverai.razorpay.mock-mode=true",
    "recoverai.ai.enabled=false",
    "spring.jpa.hibernate.ddl-auto=validate"
})
class ConcurrentActionExecutionIT {

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

  @Autowired RecoveryActionRepository actions;
  @Autowired OrganizationRepository organizations;
  @Autowired MerchantRepository merchants;
  @Autowired RevenueIncidentRepository incidents;

  @Test
  void idempotencyKeyPreventsDuplicateInserts() throws InterruptedException {
    var fixture = PersistenceTestFixtures.tenantIncident(
        organizations, merchants, incidents, "Concurrent Idempotency");
    UUID orgId = fixture.orgId();
    UUID incidentId = fixture.incidentId();
    String idempotencyKey = RecoveryAction.idempotencyKeyFor(incidentId, "SMS_NUDGE", 1);
    
    int threadCount = 2;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch endLatch = new CountDownLatch(threadCount);
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failureCount = new AtomicInteger(0);

    for (int i = 0; i < threadCount; i++) {
      executor.submit(() -> {
        try {
          startLatch.await(); // Wait for all threads to be ready
          RecoveryAction action = new RecoveryAction(orgId, incidentId, "SMS_NUDGE", 1, Instant.now(), idempotencyKey);
          actions.saveAndFlush(action);
          successCount.incrementAndGet();
        } catch (DataIntegrityViolationException e) {
          failureCount.incrementAndGet();
        } catch (Exception e) {
          // Other exceptions
        } finally {
          endLatch.countDown();
        }
      });
    }

    startLatch.countDown(); // Start all threads at once
    endLatch.await(); // Wait for all threads to finish
    executor.shutdown();

    assertThat(successCount.get()).isEqualTo(1);
    assertThat(failureCount.get()).isEqualTo(1);
  }

  @Test
  void optimisticLockingPreventsConcurrentUpdates() {
    var fixture = PersistenceTestFixtures.tenantIncident(
        organizations, merchants, incidents, "Concurrent Optimistic Lock");
    UUID orgId = fixture.orgId();
    UUID incidentId = fixture.incidentId();
    String idempotencyKey = RecoveryAction.idempotencyKeyFor(incidentId, "EMAIL_NUDGE", 1);
    
    RecoveryAction action = new RecoveryAction(orgId, incidentId, "EMAIL_NUDGE", 1, Instant.now(), idempotencyKey);
    actions.saveAndFlush(action);
    
    // Simulate two threads reading the same entity version
    RecoveryAction thread1View = actions.findById(action.getId()).get();
    RecoveryAction thread2View = actions.findById(action.getId()).get();
    
    // Thread 1 updates and saves successfully
    thread1View.setStatus(RecoveryAction.Status.EXECUTING);
    actions.saveAndFlush(thread1View);
    
    // Thread 2 attempts to update the stale view
    thread2View.setStatus(RecoveryAction.Status.FAILED);
    
    assertThatThrownBy(() -> actions.saveAndFlush(thread2View))
        .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        
    // Verify final state
    RecoveryAction finalAction = actions.findById(action.getId()).get();
    assertThat(finalAction.getStatus()).isEqualTo(RecoveryAction.Status.EXECUTING);
  }
}
