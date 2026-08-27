package com.recoverai.seed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.recoverai.analytics.domain.MetricSnapshot;
import com.recoverai.analytics.infrastructure.MetricSnapshotRepository;
import com.recoverai.audit.application.AuditService;
import com.recoverai.auth.domain.Membership;
import com.recoverai.auth.domain.Role;
import com.recoverai.auth.domain.User;
import com.recoverai.auth.infrastructure.MembershipRepository;
import com.recoverai.auth.infrastructure.UserRepository;
import com.recoverai.incident.domain.IncidentStatus;
import com.recoverai.incident.domain.IncidentType;
import com.recoverai.incident.domain.RevenueIncident;
import com.recoverai.incident.infrastructure.RevenueIncidentRepository;
import com.recoverai.merchant.application.SecretCipher;
import com.recoverai.merchant.domain.Merchant;
import com.recoverai.merchant.domain.MerchantIntegration;
import com.recoverai.merchant.infrastructure.MerchantIntegrationRepository;
import com.recoverai.merchant.infrastructure.MerchantRepository;
import com.recoverai.customer.domain.Customer;
import com.recoverai.customer.infrastructure.CustomerRepository;
import com.recoverai.experiment.application.ExperimentService;
import com.recoverai.payment.domain.Payment;
import com.recoverai.payment.domain.PaymentStatus;
import com.recoverai.payment.infrastructure.PaymentRepository;
import com.recoverai.policy.domain.PolicySet;
import com.recoverai.policy.infrastructure.PolicySetRepository;
import com.recoverai.recovery.domain.RecoveryAction;
import com.recoverai.recovery.infrastructure.RecoveryActionRepository;
import com.recoverai.tenant.domain.Organization;
import com.recoverai.tenant.infrastructure.OrganizationRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deterministic demo seed data (seeded RNG). Produces a demo organization with incidents
 * whose aggregates match the demo scene targets:
 * revenue at risk ₹5,24,000 · recovered ₹2,84,300 · recovery rate 54.3% ·
 * incremental ₹1,13,300. Every figure is SIMULATED / SYNTHETIC TEST-MODE.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DemoDataSeeder {

  static final long AT_RISK_TARGET = 52_400_000; // paise (₹5,24,000)
  static final long RECOVERED_TARGET = 28_430_000; // paise (₹2,84,300)

  private final OrganizationRepository organizations;
  private final UserRepository users;
  private final MembershipRepository memberships;
  private final MerchantRepository merchants;
  private final MerchantIntegrationRepository integrations;
  private final CustomerRepository customers;
  private final PaymentRepository payments;
  private final RevenueIncidentRepository incidents;
  private final RecoveryActionRepository actions;
  private final com.recoverai.diagnosis.infrastructure.IncidentDiagnosisRepository diagnoses;
  private final com.recoverai.recovery.infrastructure.RecoveryDecisionRepository decisions;
  private final com.recoverai.approval.infrastructure.ApprovalRepository approvals;
  private final PolicySetRepository policySets;
  private final MetricSnapshotRepository snapshots;
  private final ExperimentService experiments;
  private final PasswordEncoder passwordEncoder;
  private final SecretCipher cipher;
  private final AuditService audit;
  private final ObjectMapper mapper;

  private static final String[] FIRST_NAMES = {
    "Aarav", "Diya", "Rohan", "Ananya", "Vivaan", "Ishita", "Kabir", "Meera", "Arjun", "Saanvi",
    "Rahul", "Priya", "Aditya", "Nisha", "Karan", "Pooja", "Vikram", "Sneha", "Amit", "Ritika",
    "Sameer", "Kavya", "Nikhil", "Tanvi", "Ravi", "Shreya", "Manoj", "Divya", "Suresh", "Anjali",
    "Deepak", "Neha", "Harish", "Gauri", "Yash", "Maya", "Rajat", "Ira", "Siddharth", "Lakshmi"
  };

  private static final long[] TYPICAL_AMOUNTS_PAISE = {
    49_900, 99_900, 149_900, 199_900, 249_900, 349_900, 499_900, 799_900, 999_900, 1_249_900,
    1_499_900, 1_999_900, 2_499_900, 3_499_900, 4_999_900, 7_999_900, 9_999_900, 12_499_900,
    14_999_900, 19_999_900, 24_999_900, 34_999_900, 49_999_900
  };

  private static final String[] CATEGORIES = {
    "INSUFFICIENT_FUNDS", "CARD_EXPIRED", "CARD_BLOCKED", "BANK_DECLINE", "NETWORK_TIMEOUT",
    "MANDATE_FAILURE", "CHECKOUT_ABANDONED", "AUTHENTICATION_FAILURE", "PROCESSOR_ERROR"
  };

  private static final String[] STRATEGIES = {
    "PAYMENT_LINK", "DELAYED_RETRY", "EMAIL_NUDGE", "PAYMENT_LINK", "WHATSAPP_NUDGE", "DELAYED_RETRY"
  };

  private static final List<IncidentStatus> OPEN_STATUSES = List.of(
      IncidentStatus.DETECTED, IncidentStatus.RECONCILING, IncidentStatus.DIAGNOSING,
      IncidentStatus.STRATEGY_SELECTED, IncidentStatus.POLICY_EVALUATING, IncidentStatus.AWAITING_APPROVAL,
      IncidentStatus.SCHEDULED, IncidentStatus.EXECUTING, IncidentStatus.RETRYABLE_FAILURE,
      IncidentStatus.ESCALATED);

  @Transactional
  public void seed() {
    if (organizations.findBySlug("acme-retail").isPresent()) {
      log.info("SEED_SKIPPED demo data already present");
      return;
    }
    Random rng = new Random(20260822L);
    // Audit writes resolve the tenant from the request context; the seeder runs outside
    // a request, so pin the org explicitly for the duration of seeding.
    UUID seededOrg = null;
    try {
      seededOrg = seedInternal(rng);
    } finally {
      com.recoverai.common.tenant.TenantContext.clear();
    }
    log.info("SEED_COMPLETE org={}", seededOrg == null ? "none" : seededOrg);
  }

  private UUID seedInternal(Random rng) {

    // --- Org, users, merchant, integration, policy --------------------------------
    Organization org = organizations.save(new Organization("Acme Retail", "acme-retail"));
    com.recoverai.common.tenant.TenantContext.setOrgId(org.getId());
    User owner = users.save(new User(
        "demo@recoverai.dev", passwordEncoder.encode("DemoPass!123"), "Priya Sharma"));
    User operator = users.save(new User(
        "operator@recoverai.dev", passwordEncoder.encode("DemoPass!123"), "Rahul Verma"));
    User analyst = users.save(new User(
        "analyst@recoverai.dev", passwordEncoder.encode("DemoPass!123"), "Meera Iyer"));
    memberships.save(new Membership(org.getId(), owner.getId(), Role.OWNER));
    memberships.save(new Membership(org.getId(), operator.getId(), Role.OPERATOR));
    memberships.save(new Membership(org.getId(), analyst.getId(), Role.ANALYST));

    Merchant merchant = merchants.save(new Merchant(org.getId(), "Acme Retail"));
    MerchantIntegration integration = new MerchantIntegration(org.getId(), merchant.getId(), "razorpay", "TEST");
    integration.setWebhookSecretEncrypted(cipher.encrypt("recoverai_demo_webhook_secret"));
    integrations.save(integration);

    PolicySet policy = new PolicySet(org.getId(), "Acme Retail — default");
    policy.setMaxRetries(3);
    policy.setMaxContactAttempts(2);
    policy.setMaxDiscountPercent(10);
    policy.setRecoveryWindowHours(72);
    policy.setMinimumRecoverableAmount(10_000); // ₹100
    policy.setContactCooldownHours(12);
    policy.setRequireApprovalAboveAmount(1_000_000); // ₹10,000
    policySets.save(policy);

    // --- Customers -----------------------------------------------------------------
    List<UUID> customerIds = new ArrayList<>();
    for (int i = 0; i < 40; i++) {
      Customer c = new Customer();
      c.setOrgId(org.getId());
      c.setMerchantId(merchant.getId());
      c.setCustomerRef("cust_" + (1000 + i));
      c.setFullName(FIRST_NAMES[i] + " " + FIRST_NAMES[(i * 7) % 40]);
      c.setEmail(FIRST_NAMES[i].toLowerCase() + "." + (100 + i) + "@example.com");
      c.setPhone("+91" + (9000000000L + i * 137));
      c.setSegment(i % 4 == 0 ? "PREMIUM" : "STANDARD");
      c.setPreferredChannel("WHATSAPP");
      c.setContactCount(rng.nextInt(3));
      customerIds.add(customers.save(c).getId());
    }
    // One opted-out customer for the OPTED_OUT story.
    Customer optedOut = customers.findById(customerIds.get(7)).orElseThrow();
    optedOut.setOptOutAt(Instant.now().minus(3, ChronoUnit.DAYS));
    optedOut.setOptOutReason("CUSTOMER_REQUEST");
    customers.save(optedOut);

    // --- Recovered incidents (sum = RECOVERED_TARGET) ------------------------------
    UUID heroRecoveredId = seedRecoveredBlock(rng, org, merchant, customerIds, 90, RECOVERED_TARGET);

    // --- Open / at-risk incidents (sum = AT_RISK_TARGET) ---------------------------
    // The open block must leave room for the special story incidents that are also
    // "at risk" (hero ₹3,499 + approval ₹25,000), so the grand total is exactly ₹5,24,000.
    UUID heroAtRiskId = seedOpenBlock(rng, org, merchant, customerIds, 100, AT_RISK_TARGET - 349_900 - 2_500_000, policy);

    // --- Special story incidents ----------------------------------------------------
    seedSpecialIncidents(rng, org, merchant, customerIds, policy, heroAtRiskId, heroRecoveredId);

    // --- Metric snapshots (daily, 14 days) ------------------------------------------
    seedSnapshots(org, rng);

    // --- One canonical experiment run -----------------------------------------------
    try {
      experiments.run(org.getId(), "Baseline vs RecoverAI — 1,000 incidents (seed 42)", "Canonical buildathon comparison on a seeded population.", 42, 1000, null, null);
    } catch (Exception e) {
      log.warn("SEED_EXPERIMENT_FAILED error={}", e.getMessage());
    }

    log.info("SEED_DATA_READY org={} atRisk={} recovered={}", org.getSlug(),
        incidents.sumAtRisk(org.getId(), OPEN_STATUSES),
        incidents.sumRecovered(org.getId()));
    return org.getId();
  }

  // ---------------------------------------------------------------------------
  // Recovered block
  // ---------------------------------------------------------------------------

  private UUID seedRecoveredBlock(Random rng, Organization org, Merchant merchant, List<UUID> customerIds, int count, long targetMinor) {
    long[] amounts = amountsSummingTo(rng, count, targetMinor, 499_00, 25_000_00);
    UUID lastIncidentId = null;
    for (int i = 0; i < count; i++) {
      UUID customerId = customerIds.get(rng.nextInt(customerIds.size()));
      String category = CATEGORIES[rng.nextInt(CATEGORIES.length)];
      String strategy = STRATEGIES[rng.nextInt(STRATEGIES.length)];
      Instant detected = Instant.now().minus(6 + rng.nextInt(6), ChronoUnit.DAYS);
      Instant recoveredAt = detected.plus(3 + rng.nextInt(48), ChronoUnit.HOURS);
      long cost = strategyCost(strategy);

      Payment payment = newPayment(org, merchant, customerId, amounts[i], PaymentStatus.CAPTURED, category, detected);
      payments.save(payment);

      RevenueIncident inc = new RevenueIncident();
      inc.setOrgId(org.getId());
      inc.setMerchantId(merchant.getId());
      inc.setCustomerId(customerId);
      inc.setPaymentId(payment.getId());
      inc.setIncidentType(IncidentType.PAYMENT_FAILURE);
      inc.setStatus(IncidentStatus.RECOVERED);
      inc.setAmountMinor(amounts[i]);
      inc.setCurrency("INR");
      inc.setFailureCategory(category);
      inc.setDiagnosisConfidence(new BigDecimal("0.62").add(new BigDecimal(rng.nextInt(34)).movePointLeft(2)));
      inc.setDiagnosisLayer(rng.nextBoolean() ? "HYBRID" : "DETERMINISTIC");
      inc.setSelectedStrategy(strategy);
      inc.setAttemptsCount(1 + rng.nextInt(3));
      inc.setRecoveredAmountMinor(amounts[i]);
      inc.setInterventionCostMinor(cost);
      inc.setNetRecoveredMinor(amounts[i] - cost);
      inc.setDetectedAt(detected);
      inc.setRecoveredAt(recoveredAt);
      inc.setClosedAt(recoveredAt.plus(1, ChronoUnit.HOURS));
      inc.setPolicyResult("PASS");
      lastIncidentId = incidents.save(inc).getId();
    }
    return lastIncidentId;
  }

  // ---------------------------------------------------------------------------
  // Open / at-risk block
  // ---------------------------------------------------------------------------

  private UUID seedOpenBlock(Random rng, Organization org, Merchant merchant, List<UUID> customerIds, int count, long targetMinor, PolicySet policy) {
    long[] amounts = amountsSummingTo(rng, count, targetMinor, 499_00, 25_000_00);
    IncidentStatus[] openStates = {
      IncidentStatus.DETECTED, IncidentStatus.RECONCILING, IncidentStatus.DIAGNOSING,
      IncidentStatus.STRATEGY_SELECTED, IncidentStatus.POLICY_EVALUATING, IncidentStatus.SCHEDULED,
      IncidentStatus.EXECUTING, IncidentStatus.RETRYABLE_FAILURE
    };
    UUID heroId = null;
    for (int i = 0; i < count; i++) {
      UUID customerId = customerIds.get(rng.nextInt(customerIds.size()));
      String category = CATEGORIES[rng.nextInt(CATEGORIES.length)];
      IncidentStatus status = openStates[rng.nextInt(openStates.length)];
      Instant detected = Instant.now().minus(rng.nextInt(50), ChronoUnit.HOURS);
      String strategy = STRATEGIES[rng.nextInt(STRATEGIES.length)];

      Payment payment = newPayment(org, merchant, customerId, amounts[i], PaymentStatus.FAILED, category, detected);
      payments.save(payment);

      RevenueIncident inc = new RevenueIncident();
      inc.setOrgId(org.getId());
      inc.setMerchantId(merchant.getId());
      inc.setCustomerId(customerId);
      inc.setPaymentId(payment.getId());
      inc.setIncidentType(IncidentType.PAYMENT_FAILURE);
      inc.setStatus(status);
      inc.setAmountMinor(amounts[i]);
      inc.setCurrency("INR");
      inc.setFailureCategory(category);
      inc.setDiagnosisConfidence(new BigDecimal("0.55").add(new BigDecimal(rng.nextInt(40)).movePointLeft(2)));
      inc.setDiagnosisLayer(rng.nextBoolean() ? "HYBRID" : "DETERMINISTIC");
      inc.setSelectedStrategy(status == IncidentStatus.DETECTED || status == IncidentStatus.RECONCILING || status == IncidentStatus.DIAGNOSING
          ? null : strategy);
      inc.setAttemptsCount(status == IncidentStatus.RETRYABLE_FAILURE || status == IncidentStatus.EXECUTING ? 1 + rng.nextInt(2) : 0);
      inc.setDetectedAt(detected);
      inc.setDiagnosedAt(detected.plus(1, ChronoUnit.MINUTES));
      inc.setRecoveryWindowEndsAt(detected.plus(policy.getRecoveryWindowHours(), ChronoUnit.HOURS));
      if (status == IncidentStatus.SCHEDULED || status == IncidentStatus.RETRYABLE_FAILURE || status == IncidentStatus.EXECUTING) {
        inc.setScheduledAt(detected.plus(2, ChronoUnit.HOURS));
        inc.setNextActionAt(Instant.now().plus(1 + rng.nextInt(20), ChronoUnit.HOURS));
        inc.setPolicyResult("PASS");
      }
      if (status == IncidentStatus.EXECUTING) {
        inc.setExecutedAt(detected.plus(3, ChronoUnit.HOURS));
      }
      if (status == IncidentStatus.RETRYABLE_FAILURE) {
        inc.setNextActionAt(Instant.now().plus(4 + rng.nextInt(16), ChronoUnit.HOURS));
      }
      RevenueIncident savedInc = incidents.save(inc);
      heroId = savedInc.getId();
      if (status == IncidentStatus.SCHEDULED || status == IncidentStatus.RETRYABLE_FAILURE || status == IncidentStatus.EXECUTING) {
        RecoveryAction action = new RecoveryAction(
            org.getId(), savedInc.getId(), strategy, 1, savedInc.getNextActionAt(),
            RecoveryAction.idempotencyKeyFor(savedInc.getId(), strategy, 1));
        action.setStatus(status == IncidentStatus.EXECUTING ? RecoveryAction.Status.SUCCEEDED : RecoveryAction.Status.SCHEDULED);
        if (status == IncidentStatus.EXECUTING) {
          action.setResult("PAYMENT_LINK_CREATED");
          action.setExecutedAt(Instant.now().minus(1, ChronoUnit.HOURS));
          action.setProviderReference("plink_mock_demo_" + i);
        }
        actions.save(action);
      }
    }
    return heroId;
  }

  // ---------------------------------------------------------------------------
  // Special story incidents (hero, late-auth, blocked, approval)
  // ---------------------------------------------------------------------------

  private void seedSpecialIncidents(
      Random rng, Organization org, Merchant merchant, List<UUID> customerIds, PolicySet policy,
      UUID heroAtRiskId, UUID heroRecoveredId) {

    // HERO (Scene 2–8): ₹3,499 INSUFFICIENT_FUNDS, SCHEDULED, PAYMENT_LINK — the live demo
    // fires payment.authorized for it → RECOVERED.
    UUID heroCustomer = customerIds.get(3);
    Instant detected = Instant.now().minus(2, ChronoUnit.HOURS);
    Payment heroPayment = newPayment(org, merchant, heroCustomer, 349_900, PaymentStatus.FAILED, "INSUFFICIENT_FUNDS", detected);
    heroPayment.setFailureCode("INSUFFICIENT_FUNDS");
    heroPayment.setFailureReason("The bank reported insufficient funds");
    heroPayment.setProviderPaymentId("pay_hero_demo_3499");
    payments.save(heroPayment);

    RevenueIncident hero = new RevenueIncident();
    hero.setOrgId(org.getId());
    hero.setMerchantId(merchant.getId());
    hero.setCustomerId(heroCustomer);
    hero.setPaymentId(heroPayment.getId());
    hero.setIncidentType(IncidentType.PAYMENT_FAILURE);
    hero.setStatus(IncidentStatus.SCHEDULED);
    hero.setAmountMinor(349_900);
    hero.setCurrency("INR");
    hero.setFailureCategory("INSUFFICIENT_FUNDS");
    hero.setDiagnosisConfidence(new BigDecimal("0.9100"));
    hero.setDiagnosisLayer("HYBRID");
    hero.setSelectedStrategy("PAYMENT_LINK");
    hero.setAttemptsCount(0);
    hero.setDetectedAt(detected);
    hero.setDiagnosedAt(detected.plus(30, ChronoUnit.SECONDS));
    hero.setScheduledAt(detected.plus(1, ChronoUnit.MINUTES));
    hero.setNextActionAt(Instant.now().plus(30, ChronoUnit.MINUTES));
    hero.setRecoveryWindowEndsAt(detected.plus(policy.getRecoveryWindowHours(), ChronoUnit.HOURS));
    hero.setPolicyResult("PASS");
    RevenueIncident heroSaved = incidents.save(hero);
    actions.save(new RecoveryAction(
        org.getId(), heroSaved.getId(), "PAYMENT_LINK", 1, heroSaved.getNextActionAt(),
        RecoveryAction.idempotencyKeyFor(heroSaved.getId(), "PAYMENT_LINK", 1)));

    // Seeded diagnosis + decision so the detail screen shows the full explainability
    // story (Scene 2–4 of the demo script).
    com.recoverai.diagnosis.domain.IncidentDiagnosis diagnosis =
        new com.recoverai.diagnosis.domain.IncidentDiagnosis();
    diagnosis.setOrgId(org.getId());
    diagnosis.setIncidentId(heroSaved.getId());
    diagnosis.setLayer(com.recoverai.diagnosis.domain.IncidentDiagnosis.Layer.HYBRID);
    diagnosis.setFailureCategory("INSUFFICIENT_FUNDS");
    diagnosis.setConfidence(new BigDecimal("0.9100"));
    diagnosis.setSource("hybrid");
    diagnosis.setEvidence(java.util.List.of("provider_code_mapping", "customer_history", "temporal_pattern"));
    diagnosis.setRecommendedAction("DELAYED_RETRY");
    diagnosis.setModelVersion("recoverai-deterministic-v1");
    diagnosis.setPromptVersion("recoverai-prompts-v1");
    diagnoses.save(diagnosis);

    com.recoverai.recovery.domain.RecoveryDecision decision =
        new com.recoverai.recovery.domain.RecoveryDecision();
    decision.setOrgId(org.getId());
    decision.setIncidentId(heroSaved.getId());
    decision.setCandidates(java.util.List.of(
        new com.recoverai.recovery.domain.RecoveryDecision.CandidateView(
            "DELAYED_RETRY_NOW", 0.21, 71_400, 73_479, 0, 0, 150, 0, 0, "Retry now — low expected value"),
        new com.recoverai.recovery.domain.RecoveryDecision.CandidateView(
            "DELAYED_RETRY", 0.48, 210_200, 167_952, 0, 0, 150, 0, 30, "Retry tomorrow 18:30 — funds typically arrive by then"),
        new com.recoverai.recovery.domain.RecoveryDecision.CandidateView(
            "PAYMENT_LINK", 0.52, 224_800, 181_948, 100, 0, 0, 0, 6, "Payment-link message — customer can act immediately")));
    decision.setChosenStrategy("PAYMENT_LINK");
    decision.setConfidence(new BigDecimal("0.5200"));
    decision.setRankingSource("AI");
    decision.setReason("Payment-link message — customer can act immediately");
    decision.setPolicyResult("PASS");
    decisions.save(decision);

    seedHeroTimeline(org, heroSaved);

    // LATE-AUTH story (Scene 9): payment looks failed, recovery scheduled, then
    // payment.authorized arrives → RECOVERY CANCELLED, duplicate collection prevented.
    UUID lateCustomer = customerIds.get(12);
    Instant lateDetected = Instant.now().minus(5, ChronoUnit.HOURS);
    Payment latePayment = newPayment(org, merchant, lateCustomer, 1_499_900, PaymentStatus.FAILED, "BANK_DECLINE", lateDetected);
    latePayment.setProviderPaymentId("pay_late_auth_demo");
    payments.save(latePayment);
    RevenueIncident lateIncident = new RevenueIncident();
    lateIncident.setOrgId(org.getId());
    lateIncident.setMerchantId(merchant.getId());
    lateIncident.setCustomerId(lateCustomer);
    lateIncident.setPaymentId(latePayment.getId());
    lateIncident.setIncidentType(IncidentType.PAYMENT_FAILURE);
    lateIncident.setStatus(IncidentStatus.LATE_AUTHORIZED);
    lateIncident.setAmountMinor(1_499_900);
    lateIncident.setCurrency("INR");
    lateIncident.setFailureCategory("BANK_DECLINE");
    lateIncident.setDiagnosisConfidence(new BigDecimal("0.6400"));
    lateIncident.setDiagnosisLayer("DETERMINISTIC");
    lateIncident.setSelectedStrategy("DELAYED_RETRY");
    lateIncident.setAttemptsCount(0);
    lateIncident.setDetectedAt(lateDetected);
    lateIncident.setScheduledAt(lateDetected.plus(1, ChronoUnit.HOURS));
    lateIncident.setRecoveredAt(Instant.now().minus(2, ChronoUnit.HOURS));
    lateIncident.setClosedAt(Instant.now().minus(2, ChronoUnit.HOURS));
    lateIncident.setCancellationReason("Payment became authorized before recovery execution. Duplicate collection prevented.");
    lateIncident.setRecoveryWindowEndsAt(lateDetected.plus(policy.getRecoveryWindowHours(), ChronoUnit.HOURS));
    RevenueIncident lateSaved = incidents.save(lateIncident);
    RecoveryAction cancelledAction = new RecoveryAction(
        org.getId(), lateSaved.getId(), "DELAYED_RETRY", 1, lateDetected.plus(2, ChronoUnit.HOURS),
        RecoveryAction.idempotencyKeyFor(lateSaved.getId(), "DELAYED_RETRY", 1));
    cancelledAction.setStatus(RecoveryAction.Status.CANCELLED);
    cancelledAction.setError("Payment became authorized before recovery execution. Duplicate collection prevented.");
    actions.save(cancelledAction);

    // BLOCKED story: max retries exceeded → BLOCKED by policy.
    UUID blockedCustomer = customerIds.get(19);
    Payment blockedPayment = newPayment(org, merchant, blockedCustomer, 2_499_900, PaymentStatus.FAILED, "CARD_BLOCKED", Instant.now().minus(4, ChronoUnit.DAYS));
    payments.save(blockedPayment);
    RevenueIncident blocked = new RevenueIncident();
    blocked.setOrgId(org.getId());
    blocked.setMerchantId(merchant.getId());
    blocked.setCustomerId(blockedCustomer);
    blocked.setPaymentId(blockedPayment.getId());
    blocked.setIncidentType(IncidentType.PAYMENT_FAILURE);
    blocked.setStatus(IncidentStatus.BLOCKED);
    blocked.setAmountMinor(2_499_900);
    blocked.setCurrency("INR");
    blocked.setFailureCategory("CARD_BLOCKED");
    blocked.setSelectedStrategy("PAYMENT_LINK");
    blocked.setAttemptsCount(3);
    blocked.setPolicyResult("BLOCKED:MAX_RETRIES");
    blocked.setCancellationReason("Recovery retry limit exceeded (rule MAX_RETRIES).");
    blocked.setDetectedAt(Instant.now().minus(4, ChronoUnit.DAYS));
    blocked.setClosedAt(Instant.now().minus(3, ChronoUnit.DAYS));
    blocked.setRecoveryWindowEndsAt(Instant.now().minus(1, ChronoUnit.DAYS));
    incidents.save(blocked);

    // AWAITING_APPROVAL story: high-value ₹25,000 incident in the approval queue.
    UUID approvalCustomer = customerIds.get(25);
    Payment approvalPayment = newPayment(org, merchant, approvalCustomer, 2_500_000, PaymentStatus.FAILED, "INSUFFICIENT_FUNDS", Instant.now().minus(3, ChronoUnit.HOURS));
    payments.save(approvalPayment);
    RevenueIncident awaiting = new RevenueIncident();
    awaiting.setOrgId(org.getId());
    awaiting.setMerchantId(merchant.getId());
    awaiting.setCustomerId(approvalCustomer);
    awaiting.setPaymentId(approvalPayment.getId());
    awaiting.setIncidentType(IncidentType.PAYMENT_FAILURE);
    awaiting.setStatus(IncidentStatus.AWAITING_APPROVAL);
    awaiting.setAmountMinor(2_500_000);
    awaiting.setCurrency("INR");
    awaiting.setFailureCategory("INSUFFICIENT_FUNDS");
    awaiting.setDiagnosisConfidence(new BigDecimal("0.7800"));
    awaiting.setDiagnosisLayer("HYBRID");
    awaiting.setSelectedStrategy("BOUNDED_DISCOUNT");
    awaiting.setPolicyResult("APPROVAL_REQUIRED");
    awaiting.setDetectedAt(Instant.now().minus(3, ChronoUnit.HOURS));
    awaiting.setScheduledAt(Instant.now().minus(2, ChronoUnit.HOURS));
    awaiting.setRecoveryWindowEndsAt(Instant.now().plus(69, ChronoUnit.HOURS));
    RevenueIncident awaitingSaved = incidents.save(awaiting);

    // Approval request for the high-value discount proposal (human-in-the-loop).
    com.recoverai.approval.domain.Approval approval =
        new com.recoverai.approval.domain.Approval(org.getId(), awaitingSaved.getId(), null);
    ObjectNode proposal = mapper.createObjectNode();
    proposal.put("strategy", "BOUNDED_DISCOUNT");
    proposal.put("amountMinor", 2_500_000);
    proposal.put("failureCategory", "INSUFFICIENT_FUNDS");
    proposal.put("confidence", 0.78);
    proposal.put("lowConfidence", false);
    proposal.put("requestedAt", Instant.now().minus(2, ChronoUnit.HOURS).toString());
    approval.setProposal(proposal);
    approvals.save(approval);

    // OPTED_OUT story.
    Customer opted = customers.findById(customerIds.get(7)).orElseThrow();
    Payment optOutPayment = newPayment(org, merchant, opted.getId(), 999_900, PaymentStatus.FAILED, "MANDATE_FAILURE", Instant.now().minus(2, ChronoUnit.DAYS));
    payments.save(optOutPayment);
    RevenueIncident optedOutIncident = new RevenueIncident();
    optedOutIncident.setOrgId(org.getId());
    optedOutIncident.setMerchantId(merchant.getId());
    optedOutIncident.setCustomerId(opted.getId());
    optedOutIncident.setPaymentId(optOutPayment.getId());
    optedOutIncident.setIncidentType(IncidentType.PAYMENT_FAILURE);
    optedOutIncident.setStatus(IncidentStatus.OPTED_OUT);
    optedOutIncident.setAmountMinor(999_900);
    optedOutIncident.setCurrency("INR");
    optedOutIncident.setFailureCategory("MANDATE_FAILURE");
    optedOutIncident.setDetectedAt(Instant.now().minus(2, ChronoUnit.DAYS));
    optedOutIncident.setCancellationReason("Customer opted out — global opt-out overrides all recovery.");
    incidents.save(optedOutIncident);
  }

  private void seedHeroTimeline(Organization org, RevenueIncident hero) {
    Instant t = hero.getDetectedAt();
    audit.record("PAYMENT_EVENT_RECEIVED", "webhook", hero.getPaymentId().toString(), hero.getId(), null, null,
        audit.json(java.util.Map.of("event", "payment.failed", "providerPaymentId", "pay_hero_demo_3499")), null, null);
    audit.record("INCIDENT_CREATED", "revenue_incident", hero.getId().toString(), hero.getId(), null, "DETECTED",
        audit.json(java.util.Map.of("amountMinor", 349900, "failureCategory", "INSUFFICIENT_FUNDS")), null, null);
    audit.record("PAYMENT_RECONCILED", "payment", hero.getPaymentId().toString(), hero.getId(), "FAILED", "FAILED",
        audit.json(java.util.Map.of("source", "razorpay-get-payment")), null, null);
    audit.record("DIAGNOSIS_GENERATED", "revenue_incident", hero.getId().toString(), hero.getId(), "DIAGNOSING", "STRATEGY_SELECTED",
        audit.json(java.util.Map.of("layer", "HYBRID", "failureCategory", "INSUFFICIENT_FUNDS", "confidence", 0.91,
            "evidence", java.util.List.of("provider_code_mapping", "customer_history", "temporal_pattern"))), null, null);
    audit.record("STRATEGY_SELECTED", "revenue_incident", hero.getId().toString(), hero.getId(), null, null,
        audit.json(java.util.Map.of("selected", "PAYMENT_LINK", "rankingSource", "AI",
            "candidates", java.util.List.of("Retry now — EV ₹714", "Retry tomorrow 18:30 — EV ₹2,102", "Payment-link message — EV ₹2,248"))), null, null);
    audit.record("POLICY_PASSED", "revenue_incident", hero.getId().toString(), hero.getId(), null, null,
        audit.json(java.util.Map.of("checks", java.util.List.of("RETRY_BOUND", "CONTACT_BOUND", "WINDOW", "MINIMUM_AMOUNT"))), null, null);
    audit.record("ACTION_SCHEDULED", "recovery_action", hero.getId().toString(), hero.getId(), null, "SCHEDULED",
        audit.json(java.util.Map.of("strategy", "PAYMENT_LINK", "attempt", 1)), null, null);
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private Payment newPayment(Organization org, Merchant merchant, UUID customerId, long amountMinor, PaymentStatus status, String category, Instant createdAt) {
    Payment p = new Payment();
    p.setOrgId(org.getId());
    p.setMerchantId(merchant.getId());
    p.setCustomerId(customerId);
    p.setProvider("razorpay");
    p.setProviderPaymentId("pay_demo_" + UUID.randomUUID().toString().substring(0, 12));
    p.setAmountMinor(amountMinor);
    p.setCurrency("INR");
    p.setStatus(status);
    p.setPaymentMethod(status == PaymentStatus.CAPTURED ? "upi" : "card");
    p.setFailureCategory(status == PaymentStatus.FAILED ? java.util.Optional.ofNullable(category).map(c -> {
      try {
        return com.recoverai.payment.domain.FailureCategory.valueOf(c);
      } catch (Exception e) {
        return com.recoverai.payment.domain.FailureCategory.UNKNOWN;
      }
    }).orElse(null) : null);
    p.setFailureCode(status == PaymentStatus.FAILED ? category : null);
    p.setFailureReason(status == PaymentStatus.FAILED ? category + " reported by issuer" : null);
    p.setCreatedAt(createdAt);
    p.setUpdatedAt(createdAt);
    return p;
  }

  private long strategyCost(String strategy) {
    return switch (strategy) {
      case "PAYMENT_LINK", "WHATSAPP_NUDGE" -> 100;
      case "EMAIL_NUDGE" -> 50;
      case "SMS_NUDGE" -> 150;
      case "BOUNDED_DISCOUNT" -> 200;
      default -> 0;
    };
  }

  /**
   * Deterministic amounts (typical price points + jitter), proportionally scaled so the
   * population sums EXACTLY to the target minor-unit total.
   */
  private long[] amountsSummingTo(Random rng, int count, long targetMinor, long minMinor, long maxMinor) {
    long[] raw = new long[count];
    long rawSum = 0;
    for (int i = 0; i < count; i++) {
      long amount = TYPICAL_AMOUNTS_PAISE[rng.nextInt(TYPICAL_AMOUNTS_PAISE.length)];
      long jitter = (long) (rng.nextGaussian() * 15_000);
      raw[i] = Math.max(minMinor, Math.min(maxMinor, amount + jitter));
      rawSum += raw[i];
    }
    long[] amounts = new long[count];
    long assigned = 0;
    for (int i = 0; i < count - 1; i++) {
      amounts[i] = Math.max(minMinor, Math.round(raw[i] * targetMinor / (double) rawSum));
      assigned += amounts[i];
    }
    amounts[count - 1] = Math.max(minMinor, targetMinor - assigned);
    assigned += amounts[count - 1];

    // If min-clamping pushed the total over target, shave the overflow off the largest
    // amounts (never below the floor) so the population sums EXACTLY to the target.
    long overflow = assigned - targetMinor;
    if (overflow > 0) {
      int idx = 0;
      while (overflow > 0 && idx < count) {
        int largest = 0;
        for (int i = 1; i < count; i++) {
          if (amounts[i] > amounts[largest]) {
            largest = i;
          }
        }
        long shave = Math.min(overflow, amounts[largest] - minMinor);
        if (shave <= 0) {
          break;
        }
        amounts[largest] -= shave;
        overflow -= shave;
        idx++;
      }
    }
    return amounts;
  }

  private void seedSnapshots(Organization org, Random rng) {
    long atRisk = AT_RISK_TARGET;
    long recovered = RECOVERED_TARGET;
    for (int day = 13; day >= 0; day--) {
      Instant start = Instant.now().minus(day, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
      double progress = (13 - day) / 13.0;
      ObjectNode metrics = mapper.createObjectNode();
      metrics.put("revenueAtRiskMinor", Math.round(atRisk * (1 - progress * 0.55)));
      metrics.put("revenueRecoveredMinor", Math.round(recovered * progress));
      metrics.put("recoveredCount", Math.round(progress * 90));
      snapshots.save(new MetricSnapshot(org.getId(), start, start.plus(1, ChronoUnit.DAYS), metrics));
    }
  }
}
