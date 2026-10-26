package com.recoverai.admin.api;

import com.recoverai.common.api.PageResponse;
import com.recoverai.common.api.ApiException;
import com.recoverai.common.tenant.CurrentUser;
import com.recoverai.audit.application.AuditService;
import com.recoverai.outbox.domain.OutboxEvent;
import com.recoverai.outbox.infrastructure.OutboxEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dead-letter queue administration: inspect dead outbox events (event, failure reason,
 * attempts, timestamps) and replay them. Replay is idempotent — handlers dedupe via
 * unique keys and state-machine CAS, so re-processing an already-handled event is a no-op.
 */
@RestController
@RequestMapping("/api/v1/admin/dlq")
@RequiredArgsConstructor
public class DlqAdminController {

  private final OutboxEventRepository outbox;
  private final AuditService audit;

  public record DeadLetterView(UUID id, String eventType, int attempts, Instant createdAt, Instant nextAttemptAt) {
    static DeadLetterView of(OutboxEvent event) {
      return new DeadLetterView(event.getId(), event.getEventType(), event.getAttempts(), event.getCreatedAt(), event.getNextAttemptAt());
    }
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
  public PageResponse<DeadLetterView> list(Authentication authentication,
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
    if (page < 0 || size < 1) throw ApiException.badRequest("Invalid pagination");
    UUID orgId = ((CurrentUser) authentication.getPrincipal()).orgId();
    Page<OutboxEvent> result = outbox.findByOrgIdAndStatus(orgId, "DEAD", PageRequest.of(page, Math.min(size, 100)));
    return PageResponse.of(result.getContent().stream().map(DeadLetterView::of).toList(), page, Math.min(size, 100), result.getTotalElements());
  }

  /** Replay a single dead event: back to PENDING; the publisher re-drives it. */
  @PostMapping("/{id}/replay")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
  @Transactional
  public Map<String, Object> replay(Authentication authentication, @PathVariable UUID id) {
    UUID orgId = ((CurrentUser) authentication.getPrincipal()).orgId();
    outbox.findByOrgIdAndId(orgId, id).orElseThrow(() -> ApiException.notFound("Event not found"));
    int updated = outbox.resetForTenantReplay(orgId, id, Instant.now());
    if (updated > 0) audit.record("DLQ_REPLAY_REQUESTED", "outbox_event", id.toString());
    return Map.of("replayed", updated > 0, "id", id.toString());
  }

  /** Replay all dead events (bounded). Idempotent by design. */
  @PostMapping("/replay-all")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
  @Transactional
  public Map<String, Object> replayAll(Authentication authentication, @RequestParam(defaultValue = "500") int max) {
    if (max < 1) throw ApiException.badRequest("max must be positive");
    UUID orgId = ((CurrentUser) authentication.getPrincipal()).orgId();
    List<OutboxEvent> dead = outbox.findByOrgIdAndStatus(orgId, "DEAD", PageRequest.of(0, Math.min(max, 5000))).getContent();
    int replayed = 0;
    for (OutboxEvent event : dead) {
      replayed += outbox.resetForTenantReplay(orgId, event.getId(), Instant.now());
      audit.record("DLQ_REPLAY_REQUESTED", "outbox_event", event.getId().toString());
    }
    return Map.of("replayed", replayed, "matched", dead.size());
  }
}
