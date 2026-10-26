package com.recoverai.outbox.application;

import com.recoverai.outbox.domain.OutboxEvent;
import com.recoverai.outbox.infrastructure.OutboxEventRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Short claim/completion transactions; network I/O belongs to the publisher. */
@Service
@RequiredArgsConstructor
public class OutboxDeliveryService {
  private final JdbcTemplate jdbc;
  private final OutboxEventRepository events;

  public record Claim(OutboxEvent event, UUID token) {}

  @Transactional
  public Optional<Claim> claim() {
    UUID token = UUID.randomUUID();
    var ids = jdbc.query("""
        WITH due AS (
          SELECT id FROM outbox_events
          WHERE (status = 'PENDING' AND next_attempt_at <= now())
             OR (status = 'PROCESSING' AND lease_until < now())
          ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED
        ) UPDATE outbox_events e SET status = 'PROCESSING', claim_token = ?,
            lease_until = now() + interval '2 minutes'
          FROM due WHERE e.id = due.id RETURNING e.id
        """, (rs, row) -> rs.getObject(1, UUID.class), token);
    return ids.stream().findFirst().flatMap(events::findById).map(e -> new Claim(e, token));
  }

  @Transactional
  public void complete(Claim claim) {
    jdbc.update("UPDATE outbox_events SET status = 'PUBLISHED', published_at = now(), claim_token = null, lease_until = null WHERE id = ? AND claim_token = ?",
        claim.event().getId(), claim.token());
  }

  @Transactional
  public void fail(Claim claim, Instant retryAt, int maxAttempts) {
    // Persist only a safe category; exception messages can include provider payloads/secrets.
    jdbc.update("""
        UPDATE outbox_events SET attempts = attempts + 1,
          status = CASE WHEN attempts + 1 >= ? THEN 'DEAD' ELSE 'PENDING' END,
          next_attempt_at = ?, last_error = 'DELIVERY_FAILED', claim_token = null, lease_until = null
        WHERE id = ? AND claim_token = ?
        """, maxAttempts, java.sql.Timestamp.from(retryAt), claim.event().getId(), claim.token());
  }
}
