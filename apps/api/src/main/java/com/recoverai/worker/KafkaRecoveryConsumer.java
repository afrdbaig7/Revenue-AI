package com.recoverai.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recoverai.common.tenant.TenantContext;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Recovery worker: consumes outboxed events from Kafka and drives the SAME handler
 * code as inline dispatch (reconciliation-style, idempotent, order-tolerant).
 *
 * <p>Active only when {@code recoverai.event-dispatch-mode=kafka} — the production
 * topology (event backbone + independently scaled worker fleet). Tenant and correlation
 * id travel with the event envelope; the consumer thread sets them for the duration of
 * processing (never trusts ambient context).
 */
@Component
@EnableKafka
@Slf4j
@ConditionalOnProperty(prefix = "recoverai", name = "event-dispatch-mode", havingValue = "kafka")
public class KafkaRecoveryConsumer {

  private static final List<String> TOPICS = List.of(
      "payment-events", "subscription-events", "recovery-incidents",
      "recovery-actions", "recovery-results", "audit-events", "notification-events");

  private final com.recoverai.outbox.application.OutboxPublisher.EventHandlerRegistry handlers;
  private final ObjectMapper mapper;

  public KafkaRecoveryConsumer(
      com.recoverai.outbox.application.OutboxPublisher.EventHandlerRegistry handlers, ObjectMapper mapper) {
    this.handlers = handlers;
    this.mapper = mapper;
  }

  @KafkaListener(topics = "#{@kafkaRecoveryConsumer.topics}")
  public void onEvent(ConsumerRecord<String, String> record, @Payload String body) {
    try {
      JsonNode envelope = mapper.readTree(body);
      UUID orgId = UUID.fromString(envelope.path("tenantId").asText());
      String eventType = envelope.path("eventType").asText();
      String correlationId = envelope.path("correlationId").asText(null);
      JsonNode payload = envelope.path("payload");

      TenantContext.setOrgId(orgId);
      MDC.put("tenantId", orgId.toString());
      if (correlationId != null) {
        MDC.put("correlationId", correlationId);
      }
      MDC.put("topic", record.topic());
      MDC.put("offset", String.valueOf(record.offset()));

      handlers.dispatch(orgId, eventType, payload);
    } catch (Exception e) {
      // At-least-once delivery: handler-level idempotency makes retries safe.
      log.error("EVENT_CONSUME_FAILED topic={} offset={} error={}", record.topic(), record.offset(), e.getMessage());
      throw new RuntimeException(e);
    } finally {
      TenantContext.clear();
      MDC.clear();
    }
  }

  public List<String> topics() {
    return TOPICS;
  }
}
