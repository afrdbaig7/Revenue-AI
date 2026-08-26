package com.recoverai.experiment.infrastructure;

import com.recoverai.experiment.domain.ExperimentAssignment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExperimentAssignmentRepository extends JpaRepository<ExperimentAssignment, UUID> {

  List<ExperimentAssignment> findByExperimentId(UUID experimentId);
}
