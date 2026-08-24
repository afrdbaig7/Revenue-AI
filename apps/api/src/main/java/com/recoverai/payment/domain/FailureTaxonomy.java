package com.recoverai.payment.domain;

import java.util.List;
import java.util.Map;

/**
 * Layer-1 deterministic classification: maps known provider codes/statuses and payment
 * methods into normalized failure categories. Layer-2 AI reasoning may refine this;
 * the deterministic mapping is always the floor.
 */
public final class FailureTaxonomy {

  private FailureTaxonomy() {}

  private static final Map<String, FailureCategory> CODE_MAP = Map.ofEntries(
      Map.entry("BAD_REQUEST_ERROR", FailureCategory.PROCESSOR_ERROR),
      Map.entry("BANK_DECLINED", FailureCategory.BANK_DECLINE),
      Map.entry("BANK_DECLINED_PAYMENT", FailureCategory.BANK_DECLINE),
      Map.entry("BANK_DECLINED_UNAUTHORIZED", FailureCategory.BANK_DECLINE),
      Map.entry("CARD_ISSUER_DECLINED", FailureCategory.BANK_DECLINE),
      Map.entry("CARD_DECLINED", FailureCategory.BANK_DECLINE),
      Map.entry("CARD_EXPIRED", FailureCategory.CARD_EXPIRED),
      Map.entry("CARD_EXPIRED_AUTH", FailureCategory.CARD_EXPIRED),
      Map.entry("CARD_EXPIRED_CHARGE", FailureCategory.CARD_EXPIRED),
      Map.entry("CARD_BLOCKED", FailureCategory.CARD_BLOCKED),
      Map.entry("INSUFFICIENT_FUNDS", FailureCategory.INSUFFICIENT_FUNDS),
      Map.entry("INSUFFICIENT_BALANCE", FailureCategory.INSUFFICIENT_FUNDS),
      Map.entry("NETWORK_TIMEOUT", FailureCategory.NETWORK_TIMEOUT),
      Map.entry("TIMEOUT", FailureCategory.NETWORK_TIMEOUT),
      Map.entry("GATEWAY_TIMEOUT", FailureCategory.NETWORK_TIMEOUT),
      Map.entry("PROCESSING_ERROR", FailureCategory.PROCESSOR_ERROR),
      Map.entry("PROCESSOR_ERROR", FailureCategory.PROCESSOR_ERROR),
      Map.entry("AUTHENTICATION_FAILED", FailureCategory.AUTHENTICATION_FAILURE),
      Map.entry("AUTH_FAILED", FailureCategory.AUTHENTICATION_FAILURE),
      Map.entry("OTP_VERIFICATION_FAILED", FailureCategory.AUTHENTICATION_FAILURE),
      Map.entry("MANDATE_CANCELLED", FailureCategory.MANDATE_CANCELLED),
      Map.entry("MANDATE_INVALID", FailureCategory.MANDATE_FAILURE),
      Map.entry("MANDATE_FAILED", FailureCategory.MANDATE_FAILURE),
      Map.entry("UNAUTHORIZED_TRANSACTION", FailureCategory.BANK_DECLINE),
      Map.entry("CUSTOMER_ABORTED", FailureCategory.CUSTOMER_ABORTED),
      Map.entry("PAYMENT_CANCELLED", FailureCategory.CUSTOMER_ABORTED),
      Map.entry("PAYMENT_ABORTED", FailureCategory.CUSTOMER_ABORTED),
      Map.entry("LOST_CARD", FailureCategory.CARD_BLOCKED),
      Map.entry("STOLEN_CARD", FailureCategory.CARD_BLOCKED),
      Map.entry("SUSPECTED_FRAUD", FailureCategory.BANK_DECLINE),
      Map.entry("CONTACT_SUPPORT", FailureCategory.PROCESSOR_ERROR),
      Map.entry("UNABLE_TO_PROCESS", FailureCategory.PROCESSOR_ERROR),
      Map.entry("NOT_ENABLED_FOR_ONLINE_PAYMENTS", FailureCategory.CARD_BLOCKED),
      Map.entry("ISSUER_UNAVAILABLE", FailureCategory.PROCESSOR_ERROR),
      Map.entry("SERVICE_UNAVAILABLE", FailureCategory.PROCESSOR_ERROR),
      Map.entry("CUSTOMER_NOT_ELIGIBLE_FOR_PAYMENT", FailureCategory.BANK_DECLINE),
      Map.entry("PAYMENT_METHOD_NOT_AVAILABLE", FailureCategory.PROCESSOR_ERROR));

  private static final Map<String, FailureCategory> METHOD_MAP = Map.of(
      "upi", FailureCategory.MANDATE_FAILURE,
      "card", FailureCategory.BANK_DECLINE,
      "netbanking", FailureCategory.BANK_DECLINE,
      "wallet", FailureCategory.BANK_DECLINE);

  /**
   * Classify from a provider error code (and optional method). Never claims perfect
   * diagnosis: unknown codes map to {@link FailureCategory#UNKNOWN}.
   */
  public static FailureCategory classify(String providerCode, String paymentMethod) {
    if (providerCode != null && !providerCode.isBlank()) {
      FailureCategory byCode = CODE_MAP.get(providerCode.toUpperCase().replace(' ', '_'));
      if (byCode != null) {
        return byCode;
      }
      // substring matching for verbose Razorpay-style error descriptions
      String upper = providerCode.toUpperCase();
      if (upper.contains("INSUFFICIENT") || upper.contains("FUNDS")) {
        return FailureCategory.INSUFFICIENT_FUNDS;
      }
      if (upper.contains("EXPIRED")) {
        return FailureCategory.CARD_EXPIRED;
      }
      if (upper.contains("BLOCKED") || upper.contains("FROZEN")) {
        return FailureCategory.CARD_BLOCKED;
      }
      if (upper.contains("TIMEOUT") || upper.contains("TIMED OUT")) {
        return FailureCategory.NETWORK_TIMEOUT;
      }
      if (upper.contains("DECLINED") || upper.contains("DECLINE")) {
        return FailureCategory.BANK_DECLINE;
      }
      if (upper.contains("MANDATE")) {
        return FailureCategory.MANDATE_FAILURE;
      }
      if (upper.contains("OTP") || upper.contains("AUTH")) {
        return FailureCategory.AUTHENTICATION_FAILURE;
      }
      if (upper.contains("ABORT") || upper.contains("CANCELLED")) {
        return FailureCategory.CUSTOMER_ABORTED;
      }
      // Code present but unknown: be honest — UNKNOWN, do not guess from method.
      return FailureCategory.UNKNOWN;
    }
    // No code at all: a weak method-level prior is better than nothing.
    if (paymentMethod != null) {
      return METHOD_MAP.getOrDefault(paymentMethod.toLowerCase(), FailureCategory.UNKNOWN);
    }
    return FailureCategory.UNKNOWN;
  }

  /** Evidence trail for the deterministic classification (stored per diagnosis). */
  public static List<String> evidenceFor(String providerCode) {
    if (providerCode != null && CODE_MAP.containsKey(providerCode.toUpperCase().replace(' ', '_'))) {
      return List.of("provider_code_mapping");
    }
    return List.of("provider_code_unknown");
  }
}
