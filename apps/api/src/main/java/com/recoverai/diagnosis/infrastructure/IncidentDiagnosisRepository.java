package com.recoverai.diagnosis.infrastructure;

import com.recoverai.diagnosis.domain.IncidentDiagnosis;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentDiagnosisRepository extends JpaRepository<IncidentDiagnosis, UUID> {

  List<IncidentDiagnosis> findByIncidentIdOrderByCreatedAtDesc(UUID incidentId);

  List<IncidentDiagnosis> findByOrgIdAndIncidentIdOrderByCreatedAtDesc(UUID orgId, UUID incidentId);
}
