package com.recoverai.integration.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Optional;

/**
 * PaymentProvider — the seam that makes Razorpay replaceable. Only documented provider
 * behavior is implemented; unknown fields are preserved for evidence, never invented.
 */
public interface PaymentProvider {

  String name();

  /** Fetch current payment state from the provider (reconciliation). */
  ProviderPayment fetchPayment(String providerPaymentId) throws ProviderException;

  /** Create an order (used by DELAYED_RETRY flows). */
  ProviderOrder createOrder(long amountMinor, String currency, String receipt) throws ProviderException;

  /** Create a payment link (used by PAYMENT_LINK / UPI_RECOVERY flows). */
  PaymentLink createPaymentLink(
      long amountMinor,
      String currency,
      String description,
      String customerEmail,
      String customerPhone,
      String notes)
      throws ProviderException;

  boolean isMock();

  final class ProviderException extends Exception {
    private final String category; // TRANSIENT | PERMANENT | AUTHENTICATION | RATE_LIMITED

    public ProviderException(String category, String message) {
      super(message);
      this.category = category;
    }

    public ProviderException(String category, String message, Throwable cause) {
      super(message, cause);
      this.category = category;
    }

    public String category() {
      return category;
    }
  }

  /** Normalized provider payment snapshot. */
  record ProviderPayment(
      String id,
      String orderId,
      String status, // razorpay: created|authorized|captured|failed|refunded
      long amountMinor,
      String currency,
      String method,
      String errorCode,
      String errorDescription,
      Optional<String> failureReason,
      Instant capturedAt,
      Optional<String> customerId,
      JsonNode raw) {}

  record ProviderOrder(String id, String receipt, long amountMinor, String currency, String status, JsonNode raw) {}

  record PaymentLink(String id, String shortUrl, String status, long amountMinor, String currency, JsonNode raw) {}
}
