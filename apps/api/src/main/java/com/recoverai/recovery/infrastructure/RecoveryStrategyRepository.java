package com.recoverai.recovery.infrastructure;

import com.recoverai.recovery.domain.RecoveryStrategy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecoveryStrategyRepository extends JpaRepository<RecoveryStrategy, UUID> {

  List<RecoveryStrategy> findByActiveTrueOrderByCostMinorBaseAsc();

  Optional<RecoveryStrategy> findByCode(String code);
}
