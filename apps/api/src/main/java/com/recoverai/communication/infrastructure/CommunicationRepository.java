package com.recoverai.communication.infrastructure;

import com.recoverai.communication.domain.Communication;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunicationRepository extends JpaRepository<Communication, UUID> {

  Page<Communication> findByOrgIdOrderByCreatedAtDesc(UUID orgId, Pageable pageable);

  List<Communication> findByIncidentIdOrderByCreatedAtAsc(UUID incidentId);
}
