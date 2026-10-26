package com.recoverai.integration;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recoverai.RecoverAiApplication;
import com.recoverai.analytics.application.SettlementLedger;
import com.recoverai.incident.infrastructure.RevenueIncidentRepository;
import com.recoverai.merchant.domain.MerchantIntegration;
import com.recoverai.merchant.infrastructure.MerchantRepository;
import com.recoverai.outbox.application.OutboxDeliveryService;
import com.recoverai.outbox.domain.OutboxEvent;
import com.recoverai.outbox.infrastructure.OutboxEventRepository;
import com.recoverai.payment.domain.Payment;
import com.recoverai.payment.domain.PaymentStatus;
import com.recoverai.payment.infrastructure.PaymentRepository;
import com.recoverai.support.PersistenceTestFixtures;
import com.recoverai.tenant.infrastructure.OrganizationRepository;
import com.recoverai.webhook.application.WebhookIngestionService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Executes migrations and concurrency SQL on real PostgreSQL; requires Docker. */
@Testcontainers
@SpringBootTest(classes = RecoverAiApplication.class, properties = {
    "recoverai.worker-enabled=false", "recoverai.ai.enabled=false", "recoverai.temporal.enabled=false",
    "recoverai.demo-mode=false", "spring.jpa.hibernate.ddl-auto=validate"
})
class DurableDeliveryIT {
  @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
  @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper mapper;
  @Autowired OrganizationRepository organizations;
  @Autowired MerchantRepository merchants;
  @Autowired RevenueIncidentRepository incidents;
  @Autowired OutboxEventRepository events;
  @Autowired OutboxDeliveryService delivery;
  @Autowired WebhookIngestionService ingestion;
  @Autowired SettlementLedger ledger;
  @Autowired PaymentRepository payments;
  private PersistenceTestFixtures.TenantIncident tenant;

  @BeforeEach void setup() {
    // This connection is exclusively the disposable container above.
    jdbc.update("DELETE FROM outbox_events");
    tenant = PersistenceTestFixtures.tenantIncident(organizations, merchants, incidents, "Delivery");
  }

  @Test void retryableDeliveryCanBeClaimedAgainAndEventuallyBecomesDead() {
    events.saveAndFlush(new OutboxEvent(tenant.orgId(), "test", "1", "payment-events:test", mapper.createObjectNode()));
    for (int attempt = 0; attempt < 3; attempt++) {
      var claim = delivery.claim().orElseThrow();
      delivery.fail(claim, Instant.now().minusSeconds(1), 3);
    }
    assertThat(delivery.claim()).isEmpty();
    assertThat(events.countByOrgIdAndStatus(tenant.orgId(), "DEAD")).isEqualTo(1);
  }

  @Test void twoPublishersCannotOwnTheSameClaimAndExpiredOwnerCannotCompleteIt() throws Exception {
    var event = events.saveAndFlush(new OutboxEvent(tenant.orgId(), "test", "2", "payment-events:test", mapper.createObjectNode()));
    try (var executor = Executors.newFixedThreadPool(2)) {
      var results = executor.invokeAll(List.of((Callable<java.util.Optional<OutboxDeliveryService.Claim>>) delivery::claim, delivery::claim));
      assertThat(results.stream().filter(result -> { try { return result.get().isPresent(); } catch (Exception e) { throw new RuntimeException(e); } }).count()).isEqualTo(1);
    }
    var oldToken = jdbc.queryForObject("SELECT claim_token FROM outbox_events WHERE id = ?", UUID.class, event.getId());
    jdbc.update("UPDATE outbox_events SET lease_until = now() - interval '1 minute' WHERE id = ?", event.getId());
    var newClaim = delivery.claim().orElseThrow();
    delivery.complete(new OutboxDeliveryService.Claim(event, oldToken));
    assertThat(events.findById(event.getId()).orElseThrow().getStatus()).isEqualTo("PROCESSING");
    delivery.complete(newClaim);
    assertThat(events.findById(event.getId()).orElseThrow().getStatus()).isEqualTo("PUBLISHED");
  }

  @Test void concurrentDuplicateProviderEventsCommitOneInboxAndOneOutbox() throws Exception {
    var incident = incidents.findById(tenant.incidentId()).orElseThrow();
    var integration = new MerchantIntegration(tenant.orgId(), incident.getMerchantId(), "razorpay", "TEST");
    String body = "{\"event\":\"payment.failed\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_test\",\"amount\":10000,\"currency\":\"INR\",\"status\":\"failed\",\"card\":{\"number\":\"sensitive\"}}}}}";
    var payload = mapper.readTree(body);
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    try (var executor = Executors.newFixedThreadPool(2)) {
      for (var result : executor.invokeAll(List.of(
          (Callable<WebhookIngestionService.IngestResult>) () -> ingestion.ingest(integration, "evt_duplicate", "payment.failed", bytes, payload),
          () -> ingestion.ingest(integration, "evt_duplicate", "payment.failed", bytes, payload)))) result.get();
    }
    assertThat(jdbc.queryForObject("SELECT count(*) FROM webhook_inbox WHERE org_id = ?", Long.class, tenant.orgId())).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE org_id = ?", Long.class, tenant.orgId())).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT raw_payload::text FROM webhook_inbox WHERE org_id = ?", String.class, tenant.orgId())).doesNotContain("sensitive", "card");
  }

  @Test void capturedRevenueAndRefundsAreAttributedOnlyOnce() {
    var incident = incidents.findById(tenant.incidentId()).orElseThrow();
    Payment payment = new Payment();
    payment.setOrgId(tenant.orgId()); payment.setMerchantId(incident.getMerchantId());
    payment.setProvider("razorpay"); payment.setProviderPaymentId("pay_" + UUID.randomUUID());
    payment.setAmountMinor(incident.getAmountMinor()); payment.setCurrency("INR"); payment.setStatus(PaymentStatus.CAPTURED);
    payment = payments.saveAndFlush(payment);
    assertThat(ledger.capture(tenant.orgId(), incident, payment)).isTrue();
    assertThat(ledger.capture(tenant.orgId(), incident, payment)).isFalse();
    ledger.refundEvent(tenant.orgId(), payment.getId(), "refund_1", 4000);
    ledger.refundEvent(tenant.orgId(), payment.getId(), "refund_1", 4000);
    assertThat(ledger.recovered(tenant.orgId())).isEqualTo(6000);
    assertThat(ledger.recovered(UUID.randomUUID())).isZero();
  }
}
