package com.recoverai.webhook.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.recoverai.common.api.ApiException;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Provider wrappers are removed once; persistence uses an allowlist, never raw entities. */
@Component
@RequiredArgsConstructor
public class ProviderEventNormalizer {
  private final ObjectMapper mapper;
  private static final Set<String> FIELDS = Set.of("id", "amount", "amount_minor", "currency", "status",
      "method", "order_id", "payment_id", "customer_id", "email", "contact", "description",
      "error_code", "error_description", "error_reason", "failure_reason", "created_at", "cart_ref", "amount_refunded");

  public ObjectNode normalize(JsonNode event, UUID merchantId) {
    ObjectNode normalized = mapper.createObjectNode();
    normalized.put("event", event.path("event").asText());
    ObjectNode payload = normalized.putObject("payload");
    for (String kind : Set.of("payment", "order", "subscription", "refund", "payment_link", "checkout_session")) {
      JsonNode source = event.path("payload").path(kind);
      if (!source.isObject()) continue;
      if (source.has("entity") && source.path("entity").isObject()) source = source.path("entity");
      if (source.path("id").asText().isBlank()) throw ApiException.badRequest("Missing " + kind + " identifier");
      ObjectNode entity = payload.putObject(kind);
      for (String field : FIELDS) if (source.hasNonNull(field)) entity.set(field, source.get(field));
      entity.putObject("notes").put("merchant_id", merchantId.toString());
      if (kind.equals("payment")) {
        if (!source.path("amount").canConvertToLong() || source.path("amount").asLong() < 0
            || !source.path("currency").asText().equals("INR")
            || !Set.of("created", "pending", "authorized", "captured", "failed", "refunded", "partially_refunded").contains(source.path("status").asText())) {
          throw ApiException.badRequest("Invalid payment amount, currency or status");
        }
      }
    }
    if (event.path("event").asText().startsWith("payment.") && !payload.has("payment")) {
      throw ApiException.badRequest("Missing payment entity");
    }
    return normalized;
  }
}
