package com.recoverai.policy.infrastructure;

import com.recoverai.policy.domain.PolicySet;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicySetRepository extends JpaRepository<PolicySet, UUID> {

  Optional<PolicySet> findByOrgIdAndActiveTrue(UUID orgId);

  Optional<PolicySet> findByOrgIdAndId(UUID orgId, UUID id);
}
