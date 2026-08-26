package com.recoverai.experiment.infrastructure;

import com.recoverai.experiment.domain.Experiment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExperimentRepository extends JpaRepository<Experiment, UUID> {

  List<Experiment> findByOrgIdOrderByCreatedAtDesc(UUID orgId);

  Optional<Experiment> findByOrgIdAndId(UUID orgId, UUID id);
}
