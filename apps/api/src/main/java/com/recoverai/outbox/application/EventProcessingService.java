package com.recoverai.outbox.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.recoverai.outbox.application.OutboxPublisher.EventHandlerRegistry;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Deduplication is committed atomically with domain changes, never before them. */
@Service
@RequiredArgsConstructor
public class EventProcessingService {
  private final JdbcTemplate jdbc;
  private final EventHandlerRegistry handlers;

  @Transactional
  public void process(UUID orgId, UUID eventId, String eventType, JsonNode payload) {
    int inserted = jdbc.update("INSERT INTO processed_events(org_id, event_id, handler) VALUES (?, ?, 'domain-v1') ON CONFLICT DO NOTHING", orgId, eventId);
    if (inserted == 0) return;
    handlers.dispatch(orgId, eventType, payload);
    if (payload.hasNonNull("providerEventId")) {
      jdbc.update("UPDATE webhook_inbox SET processing_status = 'PROCESSED', processed_at = now(), error = null WHERE org_id = ? AND provider = ? AND provider_event_id = ?",
          orgId, payload.path("provider").asText(), payload.path("providerEventId").asText());
    }
  }
}
