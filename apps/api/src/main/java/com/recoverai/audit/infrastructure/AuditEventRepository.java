package com.recoverai.audit.infrastructure;

import com.recoverai.audit.domain.AuditEvent;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID>, JpaSpecificationExecutor<AuditEvent> {

  Page<AuditEvent> findByOrgIdOrderByTimestampDesc(UUID orgId, Pageable pageable);

  Page<AuditEvent> findByOrgIdAndIncidentIdOrderByTimestampDesc(UUID orgId, UUID incidentId, Pageable pageable);
}
