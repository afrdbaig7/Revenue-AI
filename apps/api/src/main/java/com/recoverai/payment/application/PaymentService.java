package com.recoverai.payment.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recoverai.audit.application.AuditService;
import com.recoverai.common.api.ApiException;
import com.recoverai.common.api.ErrorCode;
import com.recoverai.integration.domain.PaymentProvider;
import com.recoverai.integration.domain.PaymentProvider.ProviderPayment;
import com.recoverai.payment.domain.FailureCategory;
import com.recoverai.payment.domain.FailureTaxonomy;
import com.recoverai.payment.domain.Payment;
import com.recoverai.payment.domain.PaymentAttempt;
import com.recoverai.payment.domain.PaymentStateMachine;
import com.recoverai.payment.domain.PaymentStatus;
import com.recoverai.payment.infrastructure.PaymentAttemptRepository;
import com.recoverai.payment.infrastructure.PaymentRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payment reconciliation: upsert from webhook payloads and provider fetches, applying
 * the validated payment state machine (out-of-order tolerant, late-authorization aware).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

  private final PaymentRepository payments;
  private final PaymentAttemptRepository attempts;
  private final PaymentProvider provider;
  private final ObjectMapper mapper;
  private final AuditService audit;

  /** Upsert a payment from a normalized webhook payload (Razorpay payment entity). */
  @Transactional
  public Payment upsertFromWebhook(UUID orgId, UUID merchantId, UUID customerId, JsonNode paymentNode) {
    String providerPaymentId = paymentNode.path("id").asText();
    Payment payment = providerPaymentId.isBlank()
        ? null
        : payments.findByProviderAndProviderPaymentId("razorpay", providerPaymentId).orElse(null);

    boolean isNew = payment == null;
    if (isNew) {
      payment = new Payment();
      payment.setOrgId(orgId);
      payment.setMerchantId(merchantId);
      payment.setCustomerId(customerId);
      payment.setProvider("razorpay");
      payment.setProviderPaymentId(providerPaymentId);
      payment.setProviderOrderId(emptyToNull(paymentNode.path("order_id").asText(null)));
      payment.setProviderAccountReference(emptyToNull(paymentNode.path("account_id").asText(null)));
      payment.setAmountMinor(paymentNode.path("amount").asLong(0));
      payment.setCurrency(paymentNode.path("currency").asText("INR"));
      payment.setPaymentMethod(emptyToNull(paymentNode.path("method").asText(null)));
      payment.setDescription(emptyToNull(paymentNode.path("description").asText(null)));
      payment.setStatus(PaymentStatus.CREATED);
    }

    PaymentStatus newStatus = mapStatus(paymentNode.path("status").asText());
    PaymentStatus previousStatus = payment.getStatus();
    applyTransition(payment, newStatus);

    if (!isNew) {
      payment.setAmountMinor(paymentNode.path("amount").asLong(payment.getAmountMinor()));
      payment.setCurrency(paymentNode.path("currency").asText(payment.getCurrency()));
      payment.setPaymentMethod(emptyToNull(paymentNode.path("method").asText(payment.getPaymentMethod())));
      if (payment.getCustomerId() == null) {
        payment.setCustomerId(customerId);
      }
    }

    String errorCode = emptyToNull(paymentNode.path("error_code").asText(null));
    String errorDescription = emptyToNull(paymentNode.path("error_description").asText(null));
    String failureReason = emptyToNull(paymentNode.path("failure_reason").asText(null));
    payment.setFailureCode(errorCode);
    payment.setFailureReason(errorDescription != null ? errorDescription : failureReason);
    if (newStatus == PaymentStatus.FAILED) {
      payment.setFailureCategory(FailureTaxonomy.classify(errorCode != null ? errorCode : failureReason, payment.getPaymentMethod()));
      payment.setFailedAt(Instant.now());
    } else if (newStatus == PaymentStatus.CAPTURED || newStatus == PaymentStatus.AUTHORIZED) {
      payment.setCapturedAt(newStatus == PaymentStatus.CAPTURED ? Instant.now() : payment.getCapturedAt());
    }
    payment.setProviderFailureDetails(paymentNode);
    payment.setUpdatedAt(Instant.now());

    try {
      Payment saved = payments.saveAndFlush(payment);
      recordAttempt(saved, paymentNode);
      audit.record(
          "PAYMENT_RECONCILED",
          "payment",
          saved.getId().toString(),
          isNew ? null : previousStatus.name(),
          newStatus.name(),
          null,
          audit.json(java.util.Map.of(
              "providerPaymentId", providerPaymentId,
              "status", newStatus.name(),
              "failureCategory", payment.getFailureCategory() == null ? null : payment.getFailureCategory().name())),
          null);
      return saved;
    } catch (DataIntegrityViolationException e) {
      // concurrent duplicate webhook — re-read and return existing
      return payments.findByProviderAndProviderPaymentId("razorpay", providerPaymentId).orElse(payment);
    }
  }

  /** Reconcile with the provider (fetch current state) before any recovery execution. */
  @Transactional
  public Payment reconcileFromProvider(UUID orgId, Payment payment) {
    if (payment.getProviderPaymentId() == null || payment.getProviderPaymentId().isBlank()) {
      return payment; // nothing to reconcile (e.g. checkout-only incidents)
    }
    try {
      ProviderPayment fetched = provider.fetchPayment(payment.getProviderPaymentId());
      PaymentStatus newStatus = mapStatus(fetched.status());
      applyTransition(payment, newStatus);
      if (fetched.errorCode() != null) {
        payment.setFailureCode(fetched.errorCode());
        payment.setFailureCategory(FailureTaxonomy.classify(fetched.errorCode(), fetched.method()));
      }
      if (fetched.capturedAt() != null) {
        payment.setCapturedAt(fetched.capturedAt());
      }
      payment.setUpdatedAt(Instant.now());
      return payments.save(payment);
    } catch (PaymentProvider.ProviderException e) {
      log.warn("RECONCILE_FAILED payment={} category={} error={}", payment.getId(), e.category(), e.getMessage());
      return payment;
    }
  }

  private void applyTransition(Payment payment, PaymentStatus newStatus) {
    if (payment.getStatus() == newStatus) {
      return;
    }
    PaymentStatus previous = payment.getStatus();
    PaymentStateMachine.transition(previous, newStatus);
    payment.setStatus(newStatus);
    if (PaymentStateMachine.isLateAuthorization(previous, newStatus)) {
      log.info("LATE_AUTHORIZATION payment={} {} -> {}", payment.getId(), previous, newStatus);
    }
  }

  private void recordAttempt(Payment payment, JsonNode paymentNode) {
    String attemptId = emptyToNull(paymentNode.path("id").asText(null));
    if (attemptId == null) {
      return;
    }
    long existing = attempts.countByPaymentId(payment.getId());
    PaymentAttempt attempt = new PaymentAttempt(
        payment.getOrgId(),
        payment.getId(),
        (int) existing + 1,
        payment.getAmountMinor(),
        payment.getCurrency(),
        payment.getStatus().name());
    attempt.setProviderAttemptId(attemptId);
    attempt.setFailureCategory(payment.getFailureCategory());
    attempt.setFailureReason(payment.getFailureReason());
    attempt.setRawDetails(paymentNode);
    attempts.save(attempt);
  }

  public static PaymentStatus mapStatus(String razorpayStatus) {
    if (razorpayStatus == null) {
      return PaymentStatus.CREATED;
    }
    return switch (razorpayStatus) {
      case "authorized" -> PaymentStatus.AUTHORIZED;
      case "captured" -> PaymentStatus.CAPTURED;
      case "failed" -> PaymentStatus.FAILED;
      case "refunded" -> PaymentStatus.REFUNDED;
      case "partially_refunded" -> PaymentStatus.PARTIALLY_REFUNDED;
      case "pending" -> PaymentStatus.PENDING;
      default -> PaymentStatus.CREATED;
    };
  }

  private static String emptyToNull(String s) {
    return (s == null || s.isBlank()) ? null : s;
  }
}
