package com.recoverai.webhook.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.recoverai.audit.application.AuditService;
import com.recoverai.checkout.domain.CheckoutSession;
import com.recoverai.checkout.infrastructure.CheckoutSessionRepository;
import com.recoverai.common.api.ApiException;
import com.recoverai.customer.domain.Customer;
import com.recoverai.customer.infrastructure.CustomerRepository;
import com.recoverai.incident.application.IncidentService;
import com.recoverai.incident.domain.RevenueIncident;
import com.recoverai.incident.infrastructure.RevenueIncidentRepository;
import com.recoverai.merchant.domain.Merchant;
import com.recoverai.merchant.infrastructure.MerchantRepository;
import com.recoverai.outbox.application.OutboxPublisher.EventHandlerRegistry;
import com.recoverai.payment.application.PaymentService;
import com.recoverai.payment.domain.Payment;
import com.recoverai.payment.domain.PaymentStatus;
import com.recoverai.payment.infrastructure.PaymentRepository;
import com.recoverai.policy.domain.PolicySet;
import com.recoverai.policy.infrastructure.PolicySetRepository;
import com.recoverai.recovery.application.RecoveryOrchestrator;
import com.recoverai.subscription.domain.Subscription;
import com.recoverai.subscription.infrastructure.SubscriptionRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Routes outboxed events (webhook-derived or demo API events) to domain handlers. All
 * handlers are reconciliation-style: idempotent, out-of-order tolerant, and they never
 * run inside the webhook HTTP acknowledgement (inline dispatch runs in the outbox
 * publisher; kafka dispatch runs in the worker).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventDispatcher implements EventHandlerRegistry {

  private final PaymentService paymentService;
  private final PaymentRepository payments;
  private final MerchantRepository merchants;
  private final CustomerRepository customers;
  private final SubscriptionRepository subscriptions;
  private final CheckoutSessionRepository checkouts;
  private final IncidentService incidentService;
  private final RecoveryOrchestrator orchestrator;
  private final PolicySetRepository policySets;
  private final AuditService audit;

  @Override
  @Transactional
  public void dispatch(UUID orgId, String eventType, JsonNode payload) {
    String type = eventType.substring(eventType.indexOf(':') + 1);
    JsonNode eventPayload = payload.path("payload");
    switch (type) {
      case "payment.failed", "payment.authorized", "payment.captured", "payment.pending",
           "payment.refunded", "order.paid" -> handlePaymentEvent(orgId, eventPayload, type);
      case "subscription.charged.failed", "subscription.charged", "subscription.halted",
           "subscription.resumed", "subscription.activated", "subscription.completed" ->
          handleSubscriptionEvent(orgId, eventPayload, type);
      case "checkout.abandoned", "checkout.completed", "checkout.started", "checkout.payment_not_attempted" ->
          handleCheckoutEvent(orgId, eventPayload, type);
      default -> log.debug("UNHANDLED_EVENT type={}", type);
    }
  }

  private void handlePaymentEvent(UUID orgId, JsonNode eventPayload, String eventType) {
    JsonNode paymentNode = eventPayload.path("payment");
    if (paymentNode.isMissingNode()) {
      paymentNode = eventPayload.path("entity");
    }
    if (!paymentNode.isObject()) {
      log.warn("PAYMENT_EVENT_NO_PAYMENT type={} org={}", eventType, orgId);
      return;
    }

    UUID merchantId = resolveMerchantId(orgId, paymentNode);
    UUID customerId = resolveCustomerId(orgId, merchantId, paymentNode);
    Payment payment = paymentService.upsertFromWebhook(orgId, merchantId, customerId, paymentNode);

    if (payment.getStatus() == PaymentStatus.FAILED) {
      maybeCreateIncident(orgId, merchantId, customerId, payment, null, "PAYMENT_FAILURE");
      UUID incidentId = incidentIdFor(payment, orgId);
      if (incidentId != null) {
        orchestrator.runPipeline(orgId, incidentId);
      }
    } else if (payment.getStatus() == PaymentStatus.CAPTURED || payment.getStatus() == PaymentStatus.AUTHORIZED) {
      orchestrator.onPaymentCollected(orgId, payment);
    }
    // PENDING / REFUNDED: nothing to do beyond reconciliation.
  }

  private void handleSubscriptionEvent(UUID orgId, JsonNode eventPayload, String eventType) {
    JsonNode subscriptionNode = eventPayload.path("subscription");
    if (!subscriptionNode.isObject()) {
      return;
    }
    String providerSubId = subscriptionNode.path("id").asText(null);
    if (providerSubId == null) {
      return;
    }
    UUID merchantId = resolveMerchantId(orgId, subscriptionNode);
    UUID customerId = resolveCustomerId(orgId, merchantId, subscriptionNode);
    Subscription subscription = subscriptions.findByProviderSubscriptionId(providerSubId).orElse(null);

    if ("subscription.charged.failed".equals(eventType)) {
      JsonNode paymentNode = eventPayload.path("payment");
      boolean hasPayment = paymentNode.isObject() && paymentNode.path("id").isTextual();
      long amount = paymentNode.isObject()
          ? paymentNode.path("amount").asLong(subscription == null ? 0 : subscription.getAmountMinor())
          : (subscription == null ? 0 : subscription.getAmountMinor());
      String currency = paymentNode.isObject() ? paymentNode.path("currency").asText("INR") : "INR";
      String failureCode = paymentNode.path("error_code").asText(null);
      String failureCategory = com.recoverai.payment.domain.FailureTaxonomy
          .classify(failureCode, paymentNode.path("method").asText(null))
          .name();
      UUID paymentId = null;
      if (hasPayment) {
        Payment payment = paymentService.upsertFromWebhook(orgId, merchantId, customerId, paymentNode);
        paymentId = payment.getId();
      }
      if (subscription == null && paymentId == null) {
        log.info("SUBSCRIPTION_UNKNOWN id={} type={} — nothing to reconcile", providerSubId, eventType);
        return;
      }
      if (incidentService.findOpenIncidentForSubscription(orgId, subscription == null ? null : subscription.getId())
          .isPresent()) {
        return; // already tracked
      }
      maybeCreateIncident(orgId, merchantId, customerId, null, subscription, "SUBSCRIPTION_FAILURE",
          amount, currency, failureCategory, paymentId);
      UUID incidentId = incidentIdForPayment(paymentId, orgId);
      if (incidentId != null) {
        orchestrator.runPipeline(orgId, incidentId);
      }
    }
  }

  private void handleCheckoutEvent(UUID orgId, JsonNode eventPayload, String eventType) {
    JsonNode sessionNode = eventPayload.path("checkout_session");
    if (!sessionNode.isObject()) {
      sessionNode = eventPayload.path("entity");
    }
    if (!sessionNode.isObject()) {
      return;
    }
    String providerSessionId = sessionNode.path("id").asText(null);
    if (providerSessionId == null) {
      return;
    }
    UUID merchantId = resolveMerchantId(orgId, sessionNode);
    UUID customerId = resolveCustomerId(orgId, merchantId, sessionNode);
    CheckoutSession session = checkouts.findByProviderSessionId(providerSessionId).orElse(null);
    if (session == null) {
      session = new CheckoutSession();
      session.setOrgId(orgId);
      session.setMerchantId(merchantId);
      session.setCustomerId(customerId);
      session.setProviderSessionId(providerSessionId);
      session.setAmountMinor(sessionNode.path("amount_minor").asLong(0));
      session.setCurrency(sessionNode.path("currency").asText("INR"));
      session.setCartRef(sessionNode.path("cart_ref").asText(null));
    }
    session.setStatus(sessionNode.path("status").asText(session.getStatus()));
    switch (session.getStatus()) {
      case "ABANDONED" -> session.setAbandonedAt(java.time.Instant.now());
      case "CHECKOUT_RESUMED" -> session.setResumedAt(java.time.Instant.now());
      case "PAYMENT_COMPLETED" -> session.setCompletedAt(java.time.Instant.now());
      default -> {}
    }
    checkouts.save(session);

    if ("checkout.abandoned".equals(eventType) || "ABANDONED".equals(session.getStatus())) {
      if (incidentService.findOpenIncidentForCheckout(orgId, session.getId()).isEmpty()) {
        maybeCreateCheckoutIncident(orgId, merchantId, customerId, session);
      }
      UUID incidentId = incidentIdForCheckout(session, orgId);
      if (incidentId != null) {
        orchestrator.runPipeline(orgId, incidentId);
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Incident creation (dedup by payment/subscription/checkout + open state)
  // ---------------------------------------------------------------------------

  private void maybeCreateIncident(UUID orgId, UUID merchantId, UUID customerId, Payment payment, Subscription subscription, String type) {
    maybeCreateIncident(orgId, merchantId, customerId, payment, subscription, type,
        payment.getAmountMinor(), payment.getCurrency(),
        payment.getFailureCategory() == null ? "UNKNOWN" : payment.getFailureCategory().name(), payment.getId());
  }

  private void maybeCreateIncident(
      UUID orgId, UUID merchantId, UUID customerId, Payment payment, Subscription subscription,
      String type, long amount, String currency, String failureCategory, UUID paymentId) {
    if (payment != null && payment.getStatus() != PaymentStatus.FAILED) {
      return;
    }
    if (payment != null && incidentService.findOpenIncidentForPayment(orgId, payment.getId()).isPresent()) {
      return; // already tracked
    }
    PolicySet policy = policySets.findByOrgIdAndActiveTrue(orgId).orElseGet(() -> defaultPolicy(orgId));
    if (amount < policy.getMinimumRecoverableAmount()) {
      log.info("INCIDENT_SKIPPED reason=below_minimum amount={} min={}", amount, policy.getMinimumRecoverableAmount());
      return;
    }
    RevenueIncident incident = "SUBSCRIPTION_FAILURE".equals(type)
        ? incidentService.createSubscriptionFailureIncident(orgId, merchantId, customerId, subscription == null ? null : subscription.getId(), paymentId, amount, currency, failureCategory, policy)
        : incidentService.createPaymentFailureIncident(orgId, merchantId, customerId, paymentId, amount, currency, failureCategory, policy);
    audit.record("INCIDENT_CREATED", "revenue_incident", incident.getId().toString(), incident.getId(), null, "DETECTED", null, null, null);
  }

  private void maybeCreateCheckoutIncident(UUID orgId, UUID merchantId, UUID customerId, CheckoutSession session) {
    PolicySet policy = policySets.findByOrgIdAndActiveTrue(orgId).orElseGet(() -> defaultPolicy(orgId));
    if (session.getAmountMinor() < policy.getMinimumRecoverableAmount()) {
      return;
    }
    incidentService.createCheckoutAbandonmentIncident(orgId, merchantId, customerId, session.getId(), session.getAmountMinor(), session.getCurrency(), policy);
  }

  private PolicySet defaultPolicy(UUID orgId) {
    PolicySet policy = new PolicySet(orgId, "default");
    policy.setActive(true);
    return policySets.save(policy);
  }

  // ---------------------------------------------------------------------------
  // Reference resolution helpers
  // ---------------------------------------------------------------------------

  private UUID resolveMerchantId(UUID orgId, JsonNode entity) {
    String notesMerchant = entity.path("notes").path("merchant_id").asText(null);
    if (notesMerchant != null) {
      try {
        UUID id = UUID.fromString(notesMerchant);
        if (merchants.existsById(id)) {
          return id;
        }
      } catch (IllegalArgumentException ignored) {
        // fall through
      }
    }
    return merchants.findByOrgId(orgId).stream()
        .findFirst()
        .map(Merchant::getId)
        .orElseThrow(() -> ApiException.conflict("No merchant configured for org " + orgId));
  }

  private UUID resolveCustomerId(UUID orgId, UUID merchantId, JsonNode entity) {
    String providerCustomerId = entity.path("customer_id").asText(null);
    String email = entity.path("email").asText(null);
    if (providerCustomerId != null && !providerCustomerId.isBlank()) {
      return customers.findByOrgIdAndCustomerRef(orgId, providerCustomerId)
          .map(Customer::getId)
          .orElseGet(() -> createCustomer(orgId, merchantId, providerCustomerId, email));
    }
    if (email != null && !email.isBlank()) {
      return customers.findByOrgIdAndEmail(orgId, email)
          .map(Customer::getId)
          .orElseGet(() -> createCustomer(orgId, merchantId, null, email));
    }
    return null;
  }

  private UUID createCustomer(UUID orgId, UUID merchantId, String customerRef, String email) {
    Customer customer = new Customer();
    customer.setOrgId(orgId);
    customer.setMerchantId(merchantId);
    customer.setCustomerRef(customerRef);
    customer.setEmail(email);
    customer.setFullName(email == null ? "Customer " + (customerRef == null ? "" : customerRef) : email.split("@")[0]);
    return customers.save(customer).getId();
  }

  private UUID incidentIdFor(Payment payment, UUID orgId) {
    return incidentService.findOpenIncidentForPayment(orgId, payment.getId())
        .map(RevenueIncident::getId)
        .orElse(null);
  }

  private UUID incidentIdForPayment(UUID paymentId, UUID orgId) {
    return incidentService.findOpenIncidentForPayment(orgId, paymentId)
        .map(RevenueIncident::getId)
        .orElse(null);
  }

  private UUID incidentIdForCheckout(CheckoutSession session, UUID orgId) {
    return incidents
        .findByOrgIdAndCheckoutSessionId(orgId, session.getId())
        .map(RevenueIncident::getId)
        .orElse(null);
  }

  private final RevenueIncidentRepository incidents;
}
