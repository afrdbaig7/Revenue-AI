package com.recoverai;

import static org.assertj.core.api.Assertions.assertThat;

import com.recoverai.audit.application.AuditService;
import com.recoverai.incident.application.IncidentService;
import com.recoverai.incident.domain.IncidentStatus;
import com.recoverai.incident.domain.RevenueIncident;
import com.recoverai.incident.infrastructure.RevenueIncidentRepository;
import com.recoverai.merchant.application.SecretCipher;
import com.recoverai.merchant.domain.Merchant;
import com.recoverai.merchant.domain.MerchantIntegration;
import com.recoverai.merchant.infrastructure.MerchantIntegrationRepository;
import com.recoverai.merchant.infrastructure.MerchantRepository;
import com.recoverai.payment.domain.Payment;
import com.recoverai.payment.domain.PaymentStatus;
import com.recoverai.payment.infrastructure.PaymentRepository;
import com.recoverai.policy.domain.PolicySet;
import com.recoverai.policy.infrastructure.PolicySetRepository;
import com.recoverai.recovery.application.RecoveryOrchestrator;
import com.recoverai.recovery.domain.RecoveryAction;
import com.recoverai.recovery.infrastructure.RecoveryActionRepository;
import com.recoverai.recovery.infrastructure.RecoveryAttemptRepository;
import com.recoverai.recovery.infrastructure.RecoveryDecisionRepository;
import com.recoverai.tenant.domain.Organization;
import com.recoverai.tenant.infrastructure.OrganizationRepository;
import java.time.Instant;
import org.springframework.data.domain.PageRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test — the mandatory late-authorization scenario:
 *
 * <pre>
 * 12:00 payment appears failed → incident created
 * 12:10 recovery action scheduled
 * 12:06 payment.authorized arrives LATE
 * → payment updated, pending action CANCELLED, incident LATE_AUTHORIZED,
 *   duplicate collection prevented, audit events written
 * </pre>
 *
 * Requires Docker (Testcontainers). Run: {@code make test-it}.
 */
@Testcontainers
@SpringBootTest(properties = {
    "recoverai.event-dispatch-mode=inline",
    "recoverai.razorpay.mock-mode=true",
    "recoverai.ai.enabled=false",
    "recoverai.scheduling.action-poll-ms=3600000",
    "recoverai.scheduling.reconcile-poll-ms=3600000",
    "recoverai.scheduling.outbox-poll-ms=3600000",
    "spring.jpa.hibernate.ddl-auto=validate",
})
class LateAuthorizationIT {

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
  @Autowired MerchantRepository merchants;
  @Autowired MerchantIntegrationRepository integrations;
  @Autowired SecretCipher cipher;
  @Autowired PaymentRepository payments;
  @Autowired RevenueIncidentRepository incidents;
  @Autowired RecoveryActionRepository actions;
  @Autowired RecoveryDecisionRepository decisions;
  @Autowired RecoveryAttemptRepository attempts;
  @Autowired PolicySetRepository policySets;
  @Autowired IncidentService incidentService;
  @Autowired RecoveryOrchestrator orchestrator;
  @Autowired AuditService audit;
  @Autowired com.recoverai.audit.infrastructure.AuditEventRepository auditEvents;

  @Test
  void lateAuthorizationCancelsRecoveryAndPreventsDoubleCollection() {
    Organization org = organizations.save(new Organization("IT Org", "it-org-" + UUID.randomUUID()));
    Merchant merchant = merchants.save(new Merchant(org.getId(), "IT Merchant"));
    MerchantIntegration integration = new MerchantIntegration(org.getId(), merchant.getId(), "razorpay", "TEST");
    integration.setWebhookSecretEncrypted(cipher.encrypt("it-secret"));
    integrations.save(integration);
    PolicySet policy = new PolicySet(org.getId(), "it-policy");
    policy.setRecoveryWindowHours(72);
    policySets.save(policy);

    // 1. A payment that "failed" at 12:00…
    Payment payment = new Payment();
    payment.setOrgId(org.getId());
    payment.setMerchantId(merchant.getId());
    payment.setProvider("razorpay");
    payment.setProviderPaymentId("pay_it_lateauth");
    payment.setAmountMinor(349_900);
    payment.setCurrency("INR");
    payment.setStatus(PaymentStatus.FAILED);
    payment.setFailureCategory(com.recoverai.payment.domain.FailureCategory.INSUFFICIENT_FUNDS);
    payment.setFailureCode("INSUFFICIENT_FUNDS");
    payments.save(payment);

    // 2. …produces an incident and the pipeline advances to SCHEDULED with an action.
    RevenueIncident incident = incidentService.createPaymentFailureIncident(
        org.getId(), merchant.getId(), null, payment.getId(), 349_900, "INR", "INSUFFICIENT_FUNDS", policy);
    orchestrator.runPipeline(org.getId(), incident.getId());

    RevenueIncident scheduled = incidents.findById(incident.getId()).orElseThrow();
    assertThat(scheduled.getStatus()).isEqualTo(IncidentStatus.SCHEDULED);
    assertThat(actions.findByIncidentIdOrderByCreatedAtAsc(incident.getId()))
        .extracting(RecoveryAction::getStatus)
        .contains(RecoveryAction.Status.SCHEDULED);

    // 3. Late `payment.authorized` arrives — the money is now safe.
    Payment authorized = payments.findById(payment.getId()).orElseThrow();
    authorized.setStatus(PaymentStatus.AUTHORIZED);
    authorized.setCapturedAt(Instant.now());
    payments.save(authorized);
    orchestrator.onPaymentCollected(org.getId(), authorized);

    // 4. Recovery is cancelled; no double collection.
    RevenueIncident after = incidents.findById(incident.getId()).orElseThrow();
    assertThat(after.getStatus()).isEqualTo(IncidentStatus.LATE_AUTHORIZED);
    assertThat(actions.findByIncidentIdOrderByCreatedAtAsc(incident.getId()))
        .extracting(RecoveryAction::getStatus)
        .doesNotContain(RecoveryAction.Status.SCHEDULED, RecoveryAction.Status.EXECUTING);
    assertThat(after.getCancellationReason()).contains("Duplicate collection prevented");

    // 5. The whole story is audited (LATE_AUTHORIZATION_RECEIVED + RECOVERY_CANCELLED).
    var auditPage = auditEvents.findByOrgIdAndIncidentIdOrderByTimestampDesc(
        org.getId(), incident.getId(), PageRequest.of(0, 100));
    assertThat(auditPage.getContent())
        .extracting(com.recoverai.audit.domain.AuditEvent::getEventType)
        .contains("LATE_AUTHORIZATION_RECEIVED", "RECOVERY_CANCELLED", "ACTION_CANCELLED");
  }
}
