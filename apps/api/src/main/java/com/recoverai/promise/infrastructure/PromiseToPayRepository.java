package com.recoverai.promise.infrastructure;

import com.recoverai.promise.domain.PromiseToPay;
import com.recoverai.promise.domain.PromiseToPay.Status;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromiseToPayRepository extends JpaRepository<PromiseToPay, UUID> {

  List<PromiseToPay> findByOrgIdAndStatusIn(UUID orgId, List<Status> statuses);

  Optional<PromiseToPay> findByOrgIdAndId(UUID orgId, UUID id);

  List<PromiseToPay> findByStatusInAndPromisedAtBefore(List<Status> statuses, Instant before);
}
