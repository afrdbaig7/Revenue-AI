package com.recoverai.outbox.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recoverai.common.config.RecoverAiProperties;
import com.recoverai.outbox.domain.OutboxEvent;
import com.recoverai.outbox.infrastructure.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Polls the outbox and publishes events. In {@code kafka} mode events go to the topic
 * derived from event_type; in {@code inline} mode (demo/dev without a broker) the same
 * handler code is invoked in-process after commit — identical semantics, no broker.
 */
@Component
@Slf4j
public class OutboxPublisher {

  private static final int MAX_ATTEMPTS = 5;
  private static final int BATCH_SIZE = 200;

  private final OutboxEventRepository repository;
  private final RecoverAiProperties props;
  private final ObjectMapper mapper;
  private final KafkaTemplate<String, String> kafka;
  private final EventHandlerRegistry handlers;
  private final Counter published;
  private final Counter failed;

  public OutboxPublisher(
      OutboxEventRepository repository,
      RecoverAiProperties props,
      ObjectMapper mapper,
      KafkaTemplate<String, String> kafka,
      EventHandlerRegistry handlers,
      MeterRegistry registry) {
    this.repository = repository;
    this.props = props;
    this.mapper = mapper;
    this.kafka = kafka;
    this.handlers = handlers;
    this.published = Counter.builder("outbox_published_total").register(registry);
    this.failed = Counter.builder("outbox_failed_total").register(registry);
  }

  @Scheduled(fixedDelayString = "${recoverai.scheduling.outbox-poll-ms:1000}")
  @Transactional
  public void poll() {
    List<OutboxEvent> due = repository.findPendingDue(Instant.now(), PageRequest.of(0, BATCH_SIZE));
    for (OutboxEvent event : due) {
      // Worker context: the tenant and correlation id travel WITH the event envelope,
      // never from a request thread (the poller runs outside any HTTP request).
      com.recoverai.common.tenant.TenantContext.setOrgId(event.getOrgId());
      org.slf4j.MDC.put("tenantId", event.getOrgId().toString());
      if (event.getCorrelationId() != null) {
        org.slf4j.MDC.put("correlationId", event.getCorrelationId());
      }
      try {
        if (props.kafkaDispatch()) {
          kafka.send(topicOf(event.getEventType()), event.getOrgId().toString(), serialize(event)).get();
        } else {
          handlers.dispatch(event.getOrgId(), event.getEventType(), event.getPayload());
        }
        repository.markPublished(event.getId(), Instant.now());
        published.increment();
      } catch (Exception e) {
        failed.increment();
        Instant retryAt = Instant.now().plus(Duration.ofSeconds(Math.min(60, 5L * (1L << event.getAttempts()))));
        repository.markFailed(event.getId(), truncate(e.getMessage(), 500), retryAt, MAX_ATTEMPTS);
        log.warn("OUTBOX_FAILED eventId={} type={} attempts={} error={}", event.getId(), event.getEventType(), event.getAttempts() + 1, e.getMessage());
      } finally {
        com.recoverai.common.tenant.TenantContext.clear();
        org.slf4j.MDC.remove("tenantId");
        org.slf4j.MDC.remove("correlationId");
      }
    }
  }

  private String topicOf(String eventType) {
    int colon = eventType.indexOf(':');
    return colon > 0 ? eventType.substring(0, colon) : "recovery-events";
  }

  private String serialize(OutboxEvent event) {
    try {
      Map<String, Object> envelope = new HashMap<>();
      envelope.put("schemaVersion", 1);
      envelope.put("eventId", event.getId().toString());
      envelope.put("eventType", event.getEventType());
      envelope.put("occurredAt", event.getCreatedAt().toString());
      envelope.put("tenantId", event.getOrgId().toString());
      envelope.put("correlationId", event.getCorrelationId());
      envelope.put("payload", event.getPayload());
      return mapper.writeValueAsString(envelope);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static String truncate(String s, int max) {
    if (s == null) {
      return null;
    }
    return s.length() <= max ? s : s.substring(0, max);
  }

  /** Registry of in-process event handlers used by inline dispatch (and tests). */
  public interface EventHandlerRegistry {
    void dispatch(UUID orgId, String eventType, JsonNode payload);
  }
}
