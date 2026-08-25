package com.recoverai.audit.api;

import com.recoverai.audit.domain.AuditEvent;
import com.recoverai.audit.infrastructure.AuditEventRepository;
import com.recoverai.common.api.PageResponse;
import com.recoverai.common.tenant.CurrentUser;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Immutable, searchable audit ledger (read-only). */
@RestController
@RequestMapping("/api/v1/audit-events")
@RequiredArgsConstructor
public class AuditController {

  private final AuditEventRepository auditEvents;

  @GetMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR','ANALYST')")
  public PageResponse<AuditEvent> list(
      Authentication authentication,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "25") int size,
      @RequestParam(required = false) UUID incidentId,
      @RequestParam(required = false) String eventType,
      @RequestParam(required = false) String entityType) {
    UUID orgId = ((CurrentUser) authentication.getPrincipal()).orgId();
    Specification<AuditEvent> spec = (root, query, cb) -> {
      var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
      predicates.add(cb.equal(root.get("orgId"), orgId));
      if (incidentId != null) {
        predicates.add(cb.equal(root.get("incidentId"), incidentId));
      }
      if (eventType != null && !eventType.isBlank()) {
        predicates.add(cb.equal(root.get("eventType"), eventType));
      }
      if (entityType != null && !entityType.isBlank()) {
        predicates.add(cb.equal(root.get("entityType"), entityType));
      }
      return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
    };
    Page<AuditEvent> result =
        auditEvents.findAll(spec, PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "timestamp")));
    return PageResponse.of(result.getContent(), page, Math.min(size, 100), result.getTotalElements());
  }
}
