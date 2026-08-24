package com.recoverai.incident.infrastructure;

import com.recoverai.incident.domain.IncidentStatus;
import com.recoverai.incident.domain.RevenueIncident;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface RevenueIncidentRepository
    extends JpaRepository<RevenueIncident, UUID>, JpaSpecificationExecutor<RevenueIncident> {

  Optional<RevenueIncident> findByOrgIdAndId(UUID orgId, UUID id);

  Optional<RevenueIncident> findByOrgIdAndPaymentId(UUID orgId, UUID paymentId);

  Optional<RevenueIncident> findByOrgIdAndSubscriptionId(UUID orgId, UUID subscriptionId);

  Optional<RevenueIncident> findByOrgIdAndCheckoutSessionId(UUID orgId, UUID checkoutSessionId);

  List<RevenueIncident> findByOrgIdAndStatusIn(UUID orgId, List<IncidentStatus> statuses);

  List<RevenueIncident> findByStatusIn(List<IncidentStatus> statuses, org.springframework.data.domain.Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from RevenueIncident i where i.id = :id")
  Optional<RevenueIncident> findByIdForUpdate(@Param("id") UUID id);

  @Query(
      """
      select i from RevenueIncident i
      where i.orgId = :orgId and i.status in :statuses and i.nextActionAt is not null
      order by i.nextActionAt asc
      """)
  List<RevenueIncident> findDueForAction(@Param("orgId") UUID orgId, @Param("statuses") List<IncidentStatus> statuses);

  Page<RevenueIncident> findByOrgId(UUID orgId, Pageable pageable);

  long countByOrgId(UUID orgId);

  long countByOrgIdAndStatus(UUID orgId, IncidentStatus status);

  long countByOrgIdAndStatusIn(UUID orgId, List<IncidentStatus> statuses);

  @Query(
      "select coalesce(sum(i.amountMinor), 0) from RevenueIncident i where i.orgId = :orgId and i.status in :openStatuses")
  long sumAtRisk(@Param("orgId") UUID orgId, @Param("openStatuses") List<IncidentStatus> openStatuses);

  @Query(
      "select coalesce(sum(i.recoveredAmountMinor), 0) from RevenueIncident i "
          + "where i.orgId = :orgId and i.status in ('RECOVERED','CLOSED')")
  long sumRecovered(@Param("orgId") UUID orgId);

  @Query("select coalesce(sum(i.attemptsCount), 0) from RevenueIncident i where i.orgId = :orgId")
  long sumAttempts(@Param("orgId") UUID orgId);

  @Query("select coalesce(sum(case when i.policyResult like 'BLOCKED%' then 1 else 0 end), 0) from RevenueIncident i where i.orgId = :orgId")
  long sumPolicyBlocks(@Param("orgId") UUID orgId);

  @Query("select coalesce(count(i), 0) from RevenueIncident i where i.orgId = :orgId and i.status = 'LATE_AUTHORIZED'")
  long sumLateAuthorized(@Param("orgId") UUID orgId);

  @Query(
      "select i.selectedStrategy as strategy, count(i) as uses, "
          + "coalesce(sum(case when i.status = 'RECOVERED' then 1 else 0 end), 0) as successes, "
          + "coalesce(sum(i.recoveredAmountMinor), 0) as recoveredMinor "
          + "from RevenueIncident i where i.orgId = :orgId and i.selectedStrategy is not null "
          + "group by i.selectedStrategy order by uses desc")
  List<Object[]> strategyStats(@Param("orgId") UUID orgId);

  @Query(
      "select i.failureCategory as failureCategory, count(i) as count, "
          + "coalesce(sum(i.amountMinor), 0) as amountMinor "
          + "from RevenueIncident i where i.orgId = :orgId and i.failureCategory is not null "
          + "group by i.failureCategory order by count desc")
  List<Object[]> failureStats(@Param("orgId") UUID orgId);
}
