package com.recoverai.recovery.infrastructure;

import com.recoverai.recovery.domain.RecoveryDecision;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecoveryDecisionRepository extends JpaRepository<RecoveryDecision, UUID> {

  Optional<RecoveryDecision> findByIncidentId(UUID incidentId);
}
