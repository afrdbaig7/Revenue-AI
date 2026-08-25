package com.recoverai.auth.infrastructure;

import com.recoverai.auth.domain.Membership;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {

  List<Membership> findByUserId(UUID userId);

  Optional<Membership> findByOrgIdAndUserId(UUID orgId, UUID userId);

  boolean existsByOrgIdAndUserId(UUID orgId, UUID userId);
}
