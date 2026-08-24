package com.recoverai.webhook.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.recoverai.audit.application.AuditService;
import com.recoverai.common.api.ApiException;
import com.recoverai.common.api.ErrorCode;
import com.recoverai.merchant.domain.MerchantIntegration;
import com.recoverai.outbox.application.OutboxService;
import com.recoverai.webhook.domain.WebhookInbox;
import com.recoverai.webhook.infrastructure.WebhookInboxRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent webhook ingestion: insert webhook_inbox row (unique on
 * provider+provider_event_id) and enqueue an outbox event in the SAME transaction.
 * Duplicates are absorbed with a conflict-free retry.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookIngestionService {

  private final WebhookInboxRepository inbox;
  private final OutboxService outbox;
  private final ObjectMapper mapper;
  private final AuditService audit;

  public record IngestResult(boolean duplicate) {}

  @Transactional(noRollbackFor = DataIntegrityViolationException.class)
  public IngestResult ingest(
      MerchantIntegration integration, String eventId, String eventType, byte[] rawBody, JsonNode event) {

    String payloadHash = sha256(rawBody);
    JsonNode redacted = redact(event);

    // Duplicate delivery — expected and absorbed BEFORE any insert (avoids dirtying the
    // transaction). The unique (provider, provider_event_id) constraint remains as the
    // hard backstop for concurrent duplicates.
    if (inbox.findByProviderAndProviderEventId("razorpay", eventId).isPresent()) {
      return new IngestResult(true);
    }

    try {
      WebhookInbox row = new WebhookInbox(
          integration.getOrgId(), "razorpay", eventId, eventType, payloadHash, redacted);
      inbox.saveAndFlush(row);

      String topic = topicFor(eventType);
      ObjectNode outPayload = mapper.createObjectNode();
      outPayload.put("provider", "razorpay");
      outPayload.put("providerEventId", eventId);
      outPayload.put("eventType", eventType);
      outPayload.set("payload", event.path("payload"));
      outbox.enqueue(integration.getOrgId(), "webhook", eventId, topic + ":" + eventType, outPayload);
      return new IngestResult(false);
    } catch (DataIntegrityViolationException e) {
      // Concurrent duplicate racing the check — absorbed, treated as duplicate.
      return new IngestResult(true);
    }
  }

  private String topicFor(String eventType) {
    if (eventType.startsWith("subscription")) {
      return "subscription-events";
    }
    if (eventType.startsWith("checkout") || eventType.contains("checkout")) {
      return "checkout-events";
    }
    return "payment-events";
  }

  /** Redact sensitive fields before persistence (never store card data/tokens). */
  private JsonNode redact(JsonNode event) {
    ObjectNode copy = event.deepCopy();
    JsonNode payload = copy.path("payload");
    if (payload.isObject()) {
      JsonNode payment = payload.path("payment");
      if (payment.isObject()) {
        ObjectNode p = (ObjectNode) payment;
        p.remove("card");
        p.remove("token");
        p.remove("bank");
        p.remove("vpa");
        p.remove("wallet");
      }
    }
    return copy;
  }

  private static String sha256(byte[] data) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
      return HexFormat.of().formatHex(digest);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
