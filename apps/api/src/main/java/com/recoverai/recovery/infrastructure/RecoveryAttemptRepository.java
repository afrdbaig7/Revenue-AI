package com.recoverai.recovery.infrastructure;

import com.recoverai.recovery.domain.RecoveryAttempt;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecoveryAttemptRepository extends JpaRepository<RecoveryAttempt, UUID> {

  List<RecoveryAttempt> findByIncidentIdOrderByOccurredAtAsc(UUID incidentId);
}
