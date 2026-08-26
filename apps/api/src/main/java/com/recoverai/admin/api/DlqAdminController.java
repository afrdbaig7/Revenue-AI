package com.recoverai.admin.api;

import com.recoverai.common.api.PageResponse;
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

  @GetMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
  public PageResponse<OutboxEvent> list(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
    Page<OutboxEvent> result = outbox.findByStatus("DEAD", PageRequest.of(page, Math.min(size, 100)));
    return PageResponse.of(result.getContent(), page, Math.min(size, 100), result.getTotalElements());
  }

  /** Replay a single dead event: back to PENDING; the publisher re-drives it. */
  @PostMapping("/{id}/replay")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
  public Map<String, Object> replay(@PathVariable UUID id) {
    int updated = outbox.resetForReplay(id, Instant.now());
    return Map.of("replayed", updated > 0, "id", id.toString());
  }

  /** Replay all dead events (bounded). Idempotent by design. */
  @PostMapping("/replay-all")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
  public Map<String, Object> replayAll(@RequestParam(defaultValue = "500") int max) {
    List<OutboxEvent> dead = outbox.findByStatus("DEAD", PageRequest.of(0, Math.min(max, 5000))).getContent();
    int replayed = 0;
    for (OutboxEvent event : dead) {
      replayed += outbox.resetForReplay(event.getId(), Instant.now());
    }
    return Map.of("replayed", replayed, "matched", dead.size());
  }
}
