package com.recoverai.diagnosis.application;

import com.recoverai.payment.domain.FailureCategory;
import com.recoverai.payment.domain.Payment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Layer-1 deterministic classification: provider codes → normalized failure category,
 * with conservative confidence and evidence. This is the floor — the AI may refine, but
 * the system never claims perfect diagnosis.
 */
public final class DeterministicClassifier {

  private DeterministicClassifier() {}

  public record Result(FailureCategory category, BigDecimal confidence, List<String> evidence, String recommendedStep) {}

  /** Classification from a payment record (webhook or reconciled state). */
  public static Result classify(Payment payment) {
    if (payment == null) {
      // e.g. checkout-abandonment incidents with no payment entity yet
      return new Result(
          FailureCategory.CHECKOUT_ABANDONED,
          new BigDecimal("0.90"),
          List.of("no_payment_entity", "incident_type"),
          "EMAIL_NUDGE");
    }
    FailureCategory category = payment.getFailureCategory() != null
        ? payment.getFailureCategory()
        : com.recoverai.payment.domain.FailureTaxonomy.classify(payment.getFailureCode(), payment.getPaymentMethod());

    List<String> evidence = new ArrayList<>();
    evidence.add("provider_code_mapping");

    BigDecimal confidence;
    switch (category) {
      case INSUFFICIENT_FUNDS -> confidence = new BigDecimal("0.82");
      case CARD_EXPIRED -> confidence = new BigDecimal("0.95");
      case CARD_BLOCKED -> confidence = new BigDecimal("0.85");
      case MANDATE_CANCELLED -> confidence = new BigDecimal("0.90");
      case MANDATE_FAILURE -> confidence = new BigDecimal("0.80");
      case NETWORK_TIMEOUT -> confidence = new BigDecimal("0.70");
      case AUTHENTICATION_FAILURE -> confidence = new BigDecimal("0.88");
      case CHECKOUT_ABANDONED -> confidence = new BigDecimal("0.90");
      case BANK_DECLINE, PROCESSOR_ERROR, CUSTOMER_ABORTED -> confidence = new BigDecimal("0.60");
      default -> confidence = new BigDecimal("0.45");
    }
    if (payment.getFailureCode() == null || payment.getFailureCode().isBlank()) {
      confidence = confidence.min(new BigDecimal("0.50"));
      evidence.add("no_provider_code");
    }

    String recommended = switch (category) {
      case INSUFFICIENT_FUNDS, NETWORK_TIMEOUT, PROCESSOR_ERROR -> "DELAYED_RETRY";
      case CARD_EXPIRED, CARD_BLOCKED, AUTHENTICATION_FAILURE, MANDATE_CANCELLED, MANDATE_FAILURE -> "PAYMENT_LINK";
      case BANK_DECLINE -> "ALTERNATE_PAYMENT_METHOD";
      case CHECKOUT_ABANDONED -> "EMAIL_NUDGE";
      default -> "NO_ACTION";
    };
    return new Result(category, confidence, List.copyOf(evidence), recommended);
  }
}
