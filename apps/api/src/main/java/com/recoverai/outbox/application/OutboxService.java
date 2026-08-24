package com.recoverai.outbox.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.recoverai.outbox.domain.OutboxEvent;
import com.recoverai.outbox.infrastructure.OutboxEventRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional outbox enqueue — called within the same DB transaction as the business
 * mutation (ADR-005). Publication happens asynchronously by {@link OutboxPublisher}.
 */
@Service
@RequiredArgsConstructor
public class OutboxService {

  private final OutboxEventRepository repository;

  @Transactional(propagation = Propagation.MANDATORY)
  public void enqueue(UUID orgId, String aggregateType, String aggregateId, String eventType, JsonNode payload) {
    OutboxEvent event = new OutboxEvent(orgId, aggregateType, aggregateId, eventType, payload);
    event.setCorrelationId(MDC.get("correlationId"));
    repository.save(event);
  }
}
