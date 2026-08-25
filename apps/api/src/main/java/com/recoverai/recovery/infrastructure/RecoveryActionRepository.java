package com.recoverai.recovery.infrastructure;

import com.recoverai.recovery.domain.RecoveryAction;
import com.recoverai.recovery.domain.RecoveryAction.Status;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecoveryActionRepository extends JpaRepository<RecoveryAction, UUID> {

  Optional<RecoveryAction> findByIdempotencyKey(String idempotencyKey);

  List<RecoveryAction> findByIncidentIdOrderByCreatedAtAsc(UUID incidentId);

  long countByIncidentIdAndStatusIn(UUID incidentId, List<Status> statuses);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from RecoveryAction a where a.id = :id")
  Optional<RecoveryAction> findByIdForUpdate(@Param("id") UUID id);

  @Query("select a from RecoveryAction a where a.status = 'SCHEDULED' and a.scheduledFor <= :now order by a.scheduledFor asc")
  List<RecoveryAction> findDue(@Param("now") Instant now, Pageable pageable);
}
