package com.recoverai.outbox.infrastructure;

import com.recoverai.outbox.domain.OutboxEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

  @Query(
      "select e from OutboxEvent e where e.status = 'PENDING' and e.nextAttemptAt <= :now order by e.createdAt asc")
  List<OutboxEvent> findPendingDue(@Param("now") Instant now, org.springframework.data.domain.Pageable pageable);

  org.springframework.data.domain.Page<OutboxEvent> findByStatus(String status, org.springframework.data.domain.Pageable pageable);

  /** Replay: DEAD → PENDING with attempt counter reset. Returns rows updated. */
  @Modifying
  @Query(
      "update OutboxEvent e set e.status = 'PENDING', e.nextAttemptAt = :now, e.attempts = 0, e.lastError = null "
          + "where e.id = :id and e.status = 'DEAD'")
  int resetForReplay(@Param("id") UUID id, @Param("now") Instant now);

  @Modifying
  @Query("update OutboxEvent e set e.status = 'PUBLISHED', e.publishedAt = :now where e.id = :id and e.status = 'PENDING'")
  int markPublished(@Param("id") UUID id, @Param("now") Instant now);

  @Modifying
  @Query(
      "update OutboxEvent e set e.attempts = e.attempts + 1, e.lastError = :error, "
          + "e.nextAttemptAt = :retryAt, e.status = case when e.attempts + 1 >= :maxAttempts then 'DEAD' else 'FAILED' end "
          + "where e.id = :id")
  int markFailed(@Param("id") UUID id, @Param("error") String error, @Param("retryAt") Instant retryAt, @Param("maxAttempts") int maxAttempts);
}
