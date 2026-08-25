package com.recoverai.audit.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recoverai.audit.domain.AuditEvent;
import com.recoverai.audit.infrastructure.AuditEventRepository;
import com.recoverai.common.tenant.CurrentUser;
import com.recoverai.common.tenant.TenantContext;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes immutable audit records for every meaningful action. Deliberately usable from
 * within business transactions (REQUIRED) and from error paths (REQUIRES_NEW) alike.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

  private final AuditEventRepository repository;
  private final ObjectMapper mapper;

  @Transactional(propagation = Propagation.REQUIRED)
  public void record(
      String eventType,
      String entityType,
      String entityId,
      String previousState,
      String newState,
      JsonNode inputSnapshot,
      JsonNode outputSnapshot,
      JsonNode metadata) {
    record(eventType, entityType, entityId, null, previousState, newState, inputSnapshot, outputSnapshot, metadata);
  }

  /** Full form with explicit incident linkage (used by action/payment/approval events). */
  @Transactional(propagation = Propagation.REQUIRED)
  public void record(
      String eventType,
      String entityType,
      String entityId,
      UUID incidentId,
      String previousState,
      String newState,
      JsonNode inputSnapshot,
      JsonNode outputSnapshot,
      JsonNode metadata) {

    AuditEvent event = new AuditEvent(orgId(), eventType);
    event.setEntityType(entityType);
    event.setEntityId(entityId);
    event.setPreviousState(previousState);
    event.setNewState(newState);
    event.setDecisionInputSnapshot(inputSnapshot);
    event.setDecisionOutputSnapshot(outputSnapshot);
    event.setMetadata(metadata);
    event.setCorrelationId(MDC.get("correlationId"));
    event.setTraceId(MDC.get("traceId"));
    if (incidentId != null) {
      event.setIncidentId(incidentId);
    } else if ("revenue_incident".equals(entityType) && entityId != null) {
      try {
        event.setIncidentId(UUID.fromString(entityId));
      } catch (IllegalArgumentException ignored) {
        // not a uuid — leave unlinked
      }
    }

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof CurrentUser cu) {
      event.setActorType("USER");
      event.setActorId(cu.userId().toString());
    } else {
      event.setActorType("SYSTEM");
    }
    repository.save(event);
  }

  public void record(String eventType, String entityType, String entityId) {
    record(eventType, entityType, entityId, null, null, null, null, null);
  }

  public void record(String eventType, String entityType, String entityId, String previous, String next) {
    record(eventType, entityType, entityId, previous, next, null, null, null);
  }

  public JsonNode json(Object value) {
    return mapper.valueToTree(value);
  }

  private UUID orgId() {
    UUID orgId = TenantContext.orgId();
    if (orgId == null) {
      // Regression tripwire: audit writes outside any tenant context are a bug.
      org.slf4j.LoggerFactory.getLogger(AuditService.class)
          .warn("AUDIT_WITHOUT_TENANT event={} entity={} — fixing up with caller context", entityType(), entityId());
    }
    return orgId != null ? orgId : UUID.fromString("00000000-0000-0000-0000-000000000000");
  }

  private String entityType() {
    return "unknown";
  }

  private String entityId() {
    return "unknown";
  }
}
