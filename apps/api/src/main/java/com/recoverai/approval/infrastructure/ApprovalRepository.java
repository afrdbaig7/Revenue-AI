package com.recoverai.approval.infrastructure;

import com.recoverai.approval.domain.Approval;
import com.recoverai.approval.domain.Approval.Status;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalRepository extends JpaRepository<Approval, UUID> {

  Page<Approval> findByOrgIdAndStatusOrderByCreatedAtAsc(UUID orgId, Status status, Pageable pageable);

  Page<Approval> findByOrgIdOrderByCreatedAtDesc(UUID orgId, Pageable pageable);

  Optional<Approval> findByOrgIdAndId(UUID orgId, UUID id);

  List<Approval> findByIncidentIdAndStatus(UUID incidentId, Status status);
}
