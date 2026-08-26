package com.recoverai.analytics.infrastructure;

import com.recoverai.analytics.domain.MetricSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetricSnapshotRepository extends JpaRepository<MetricSnapshot, UUID> {

  List<MetricSnapshot> findByOrgIdOrderByPeriodStartDesc(UUID orgId, Pageable pageable);

  Optional<MetricSnapshot> findByOrgIdAndPeriodStart(UUID orgId, Instant periodStart);
}
