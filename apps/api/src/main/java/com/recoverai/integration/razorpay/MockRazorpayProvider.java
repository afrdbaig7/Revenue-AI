package com.recoverai.integration.razorpay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.recoverai.integration.domain.PaymentProvider;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fixture-based mock Razorpay provider used when test credentials are absent
 * (RAZORPAY_MOCK_MODE=true / DEMO_MODE=true). All results are deterministic and clearly
 * labeled SIMULATED in the UI. It is NOT a real provider and is never presented as one.
 */
@Component
@Slf4j
public class MockRazorpayProvider implements PaymentProvider {

  private final ObjectMapper mapper;

  public MockRazorpayProvider(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public String name() {
    return "razorpay-mock";
  }

  @Override
  public boolean isMock() {
    return true;
  }

  @Override
  public ProviderPayment fetchPayment(String providerPaymentId) throws ProviderException {
    ObjectNode raw = mapper.createObjectNode();
    raw.put("id", providerPaymentId);
    raw.put("entity", "payment");
    raw.put("status", "failed");
    raw.put("amount", 349900);
    raw.put("currency", "INR");
    raw.put("method", "card");
    raw.put("error_code", "INSUFFICIENT_FUNDS");
    raw.put("error_description", "The bank reported insufficient funds");
    raw.put("failure_reason", "bank_declined");
    log.info("MOCK_RAZORPAY fetchPayment id={} status=failed (simulated)", providerPaymentId);
    return new ProviderPayment(
        providerPaymentId,
        "order_" + providerPaymentId,
        "failed",
        349900,
        "INR",
        "card",
        "INSUFFICIENT_FUNDS",
        "The bank reported insufficient funds",
        Optional.of("bank_declined"),
        null,
        Optional.empty(),
        raw);
  }

  @Override
  public ProviderOrder createOrder(long amountMinor, String currency, String receipt) throws ProviderException {
    ObjectNode raw = mapper.createObjectNode();
    String id = "order_mock_" + UUID.randomUUID().toString().substring(0, 8);
    raw.put("id", id);
    raw.put("amount", amountMinor);
    raw.put("currency", currency);
    raw.put("receipt", receipt);
    raw.put("status", "created");
    log.info("MOCK_RAZORPAY createOrder amount={} (simulated)", amountMinor);
    return new ProviderOrder(id, receipt, amountMinor, currency, "created", raw);
  }

  @Override
  public PaymentLink createPaymentLink(
      long amountMinor, String currency, String description, String customerEmail, String customerPhone, String notes)
      throws ProviderException {
    ObjectNode raw = mapper.createObjectNode();
    String id = "plink_mock_" + UUID.randomUUID().toString().substring(0, 8);
    raw.put("id", id);
    raw.put("amount", amountMinor);
    raw.put("currency", currency);
    raw.put("description", description);
    raw.put("status", "created");
    raw.put("short_url", "https://rzp.io/i/mock-" + id.substring(id.length() - 6));
    log.info("MOCK_RAZORPAY createPaymentLink id={} amount={} (simulated)", id, amountMinor);
    return new PaymentLink(id, raw.path("short_url").asText(), "created", amountMinor, currency, raw);
  }
}
