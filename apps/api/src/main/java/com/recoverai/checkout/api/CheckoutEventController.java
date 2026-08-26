package com.recoverai.checkout.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.recoverai.common.tenant.CurrentUser;
import com.recoverai.outbox.application.OutboxService;
import com.recoverai.webhook.application.EventDispatcher;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demo checkout lifecycle endpoint (P1 — checkout abandonment). Accepts lifecycle
 * events (CART_CREATED → CHECKOUT_STARTED → PAYMENT_NOT_ATTEMPTED → ABANDONED →
 * RECOVERY_SCHEDULED → NUDGE_SENT → CHECKOUT_RESUMED → PAYMENT_COMPLETED) and feeds the
 * same event pipeline as webhooks.
 */
@RestController
@RequestMapping("/api/v1/checkout/events")
@RequiredArgsConstructor
public class CheckoutEventController {

  private final OutboxService outbox;
  private final EventDispatcher dispatcher;
  private final ObjectMapper mapper;

  @PostMapping
  @Transactional
  public Map<String, Object> emit(Authentication authentication, @Valid @RequestBody CheckoutEventRequest req) {
    CurrentUser user = (CurrentUser) authentication.getPrincipal();
    ObjectNode session = mapper.createObjectNode();
    session.put("id", req.sessionId());
    session.put("status", req.status());
    session.put("amount_minor", req.amountMinor());
    session.put("currency", req.currency());
    if (req.cartRef() != null) {
      session.put("cart_ref", req.cartRef());
    }
    if (req.customerId() != null) {
      session.put("customer_id", req.customerId());
    }

    String eventType = switch (req.status()) {
      case "CHECKOUT_STARTED" -> "checkout.started";
      case "PAYMENT_NOT_ATTEMPTED" -> "checkout.payment_not_attempted";
      case "ABANDONED" -> "checkout.abandoned";
      case "PAYMENT_COMPLETED" -> "checkout.completed";
      default -> "checkout.started";
    };
    // Canonical outbox envelope: { payload: { checkout_session: {...} } }
    ObjectNode payload = mapper.createObjectNode();
    payload.set("payload", mapper.createObjectNode().set("checkout_session", session));
    outbox.enqueue(user.orgId(), "checkout", req.sessionId(), "checkout-events:" + eventType, payload);
    dispatcher.dispatch(user.orgId(), "checkout-events:" + eventType, payload);
    return Map.of("accepted", true, "eventType", eventType);
  }

  public record CheckoutEventRequest(
      @NotBlank String sessionId,
      @NotBlank String status,
      @NotNull @Positive Long amountMinor,
      String currency,
      String cartRef,
      String customerId) {}
}
