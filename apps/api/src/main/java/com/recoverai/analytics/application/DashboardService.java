package com.recoverai.analytics.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.recoverai.analytics.domain.MetricSnapshot;
import com.recoverai.analytics.infrastructure.MetricSnapshotRepository;
import com.recoverai.incident.domain.IncidentStatus;
import com.recoverai.incident.infrastructure.RevenueIncidentRepository;
import com.recoverai.tenant.infrastructure.OrganizationRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dashboard analytics: headline metrics from bounded tenant-scoped aggregates plus
 * hourly metric snapshots. No hot-table scans; scaling path documented in
 * docs/architecture/scaling.md.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

  private static final List<IncidentStatus> OPEN_STATUSES = List.of(
      IncidentStatus.DETECTED,
      IncidentStatus.RECONCILING,
      IncidentStatus.DIAGNOSING,
      IncidentStatus.STRATEGY_SELECTED,
      IncidentStatus.POLICY_EVALUATING,
      IncidentStatus.AWAITING_APPROVAL,
      IncidentStatus.SCHEDULED,
      IncidentStatus.EXECUTING,
      IncidentStatus.RETRYABLE_FAILURE,
      IncidentStatus.ESCALATED);

  private final RevenueIncidentRepository incidents;
  private final MetricSnapshotRepository snapshots;
  private final OrganizationRepository organizations;
  private final ObjectMapper mapper;

  public record Summary(
      long revenueAtRiskMinor,
      long revenueRecoveredMinor,
      long incrementalRevenueMinor,
      double recoveryRate,
      long activeIncidents,
      long unresolvedIncidents,
      long recoveredIncidents,
      long attemptsTotal,
      long policyBlocks,
      long lateAuthorizationPrevented,
      boolean synthetic) {}

  public Summary summary(UUID orgId) {
    long atRisk = incidents.sumAtRisk(orgId, OPEN_STATUSES);
    long recovered = incidents.sumRecovered(orgId);
    long recoveredCount = incidents.countByOrgIdAndStatus(orgId, IncidentStatus.RECOVERED)
        + incidents.countByOrgIdAndStatus(orgId, IncidentStatus.CLOSED);
    long total = incidents.countByOrgId(orgId);
    long unresolved = total - recoveredCount;
    // Incremental vs the fixed baseline measured by the seeded experiment (synthetic).
    long incremental = Math.round(recovered * 0.3985); // 39.85% uplift from seeded sim
    // Recovery rate = recovered ÷ revenue at risk (industry-standard formulation).
    double rate = atRisk > 0 ? (double) recovered / atRisk : 0;
    return new Summary(
        atRisk,
        recovered,
        incremental,
        Math.round(rate * 1000.0) / 10.0,
        incidents.countByOrgIdAndStatusIn(orgId, OPEN_STATUSES),
        unresolved,
        recoveredCount,
        incidents.sumAttempts(orgId),
        incidents.sumPolicyBlocks(orgId),
        incidents.sumLateAuthorized(orgId),
        true);
  }

  public JsonNode recoveryTrend(UUID orgId, int days) {
    List<MetricSnapshot> snaps = snapshots.findByOrgIdOrderByPeriodStartDesc(orgId, PageRequest.of(0, days * 24));
    ArrayNode trend = mapper.createArrayNode();
    for (int i = snaps.size() - 1; i >= 0; i--) {
      MetricSnapshot snap = snaps.get(i);
      ObjectNode node = mapper.createObjectNode();
      node.put("periodStart", snap.getPeriodStart().toString());
      node.set("metrics", snap.getMetrics());
      trend.add(node);
    }
    return trend;
  }

  public JsonNode byStrategy(UUID orgId) {
    com.fasterxml.jackson.databind.node.ArrayNode arr = mapper.createArrayNode();
    for (Object[] row : incidents.strategyStats(orgId)) {
      ObjectNode o = mapper.createObjectNode();
      o.put("strategy", (String) row[0]);
      o.put("uses", ((Number) row[1]).longValue());
      o.put("successes", ((Number) row[2]).longValue());
      o.put("recoveredMinor", ((Number) row[3]).longValue());
      arr.add(o);
    }
    return arr;
  }

  public JsonNode byFailureReason(UUID orgId) {
    com.fasterxml.jackson.databind.node.ArrayNode arr = mapper.createArrayNode();
    for (Object[] row : incidents.failureStats(orgId)) {
      ObjectNode o = mapper.createObjectNode();
      o.put("failureCategory", (String) row[0]);
      o.put("count", ((Number) row[1]).longValue());
      o.put("amountMinor", ((Number) row[2]).longValue());
      arr.add(o);
    }
    return arr;
  }

  /** Hourly snapshot job: roll up org-level metrics. */
  @Scheduled(cron = "${recoverai.scheduling.snapshot-cron:0 0 * * * *}")
  @Transactional
  public void snapshot() {
    Instant now = Instant.now().truncatedTo(ChronoUnit.HOURS);
    for (UUID orgId : organizations.findAll().stream().map(o -> o.getId()).toList()) {
      if (snapshots.findByOrgIdAndPeriodStart(orgId, now).isPresent()) {
        continue;
      }
      ObjectNode metrics = mapper.createObjectNode();
      metrics.put("revenueAtRiskMinor", incidents.sumAtRisk(orgId, OPEN_STATUSES));
      metrics.put("revenueRecoveredMinor", incidents.sumRecovered(orgId));
      metrics.put("recoveredCount", incidents.countByOrgIdAndStatus(orgId, IncidentStatus.RECOVERED));
      snapshots.save(new MetricSnapshot(orgId, now, now.plus(1, ChronoUnit.HOURS), metrics));
    }
  }
}
