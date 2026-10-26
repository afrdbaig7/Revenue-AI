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
      IncidentStatus.AWAITING_PAYMENT,
      IncidentStatus.RETRYABLE_FAILURE,
      IncidentStatus.ESCALATED);

  private final RevenueIncidentRepository incidents;
  private final MetricSnapshotRepository snapshots;
  private final OrganizationRepository organizations;
  private final ObjectMapper mapper;
  private final SettlementLedger ledger;
  private final com.recoverai.common.config.RecoverAiProperties props;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;
  @org.springframework.beans.factory.annotation.Value("${recoverai.worker-enabled:true}")
  private boolean workerEnabled = true;

  public record Summary(
      long revenueAtRiskMinor,
      long revenueRecoveredMinor,
      Long incrementalRevenueMinor,
      double recoveryRate,
      long activeIncidents,
      long unresolvedIncidents,
      long recoveredIncidents,
      long attemptsTotal,
      long policyBlocks,
      long lateAuthorizationPrevented,
      boolean synthetic,
      String currency,
      String rateDefinition,
      Instant asOf) {}

  @Transactional
  public Summary summary(UUID orgId) {
    jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", (rs, row) -> 0, "summary:" + orgId);
    Instant hour = Instant.now().truncatedTo(ChronoUnit.HOURS);
    var existing = snapshots.findByOrgIdAndPeriodStart(orgId, hour);
    if (existing.isPresent() && existing.get().getMetrics().hasNonNull("summary")) {
      Summary cached = mapper.convertValue(existing.get().getMetrics().path("summary"), Summary.class);
      if (cached.asOf() != null && cached.asOf().isAfter(Instant.now().minusSeconds(300))) return cached;
    }
    Summary result = computeSummary(orgId);
    ObjectNode metrics = mapper.createObjectNode();
    metrics.put("revenueAtRiskMinor", result.revenueAtRiskMinor());
    metrics.put("revenueRecoveredMinor", result.revenueRecoveredMinor());
    metrics.put("recoveredCount", result.recoveredIncidents());
    metrics.set("summary", mapper.valueToTree(result));
    jdbc.update("""
        INSERT INTO metric_snapshots(org_id, period_start, period_end, metrics)
        VALUES (?, ?, ?, CAST(? AS jsonb)) ON CONFLICT(org_id, period_start) DO UPDATE SET metrics = excluded.metrics
        """, orgId, java.sql.Timestamp.from(hour), java.sql.Timestamp.from(hour.plus(1, ChronoUnit.HOURS)), metrics.toString());
    return result;
  }

  private Summary computeSummary(UUID orgId) {
    long atRisk = incidents.sumAtRisk(orgId, OPEN_STATUSES);
    long recovered = ledger.recovered(orgId);
    long recoveredCount = ledger.recoveredIncidents(orgId);
    long total = incidents.countByOrgId(orgId);
    long unresolved = total - recoveredCount;
    Long incremental = null; // Only a named experiment can establish incremental uplift.
    // Incident recovery rate uses the same all-time incident cohort in both terms.
    double rate = total > 0 ? (double) recoveredCount / total : 0;
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
        props.demoMode() || props.razorpay().mockMode(),
        "INR", "Confirmed non-refunded recovered incidents / all incidents", Instant.now());
  }

  public JsonNode recoveryTrend(UUID orgId, int days) {
    if (days < 1 || days > 90) throw com.recoverai.common.api.ApiException.badRequest("days must be between 1 and 90");
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
    if (!workerEnabled) return;
    Boolean leader = jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(hashtextextended('recoverai-snapshot', 0))", Boolean.class);
    if (!Boolean.TRUE.equals(leader)) return;
    Instant now = Instant.now().truncatedTo(ChronoUnit.HOURS);
    for (UUID orgId : organizations.findAll().stream().map(o -> o.getId()).toList()) {
      if (snapshots.findByOrgIdAndPeriodStart(orgId, now).isPresent()) {
        continue;
      }
      ObjectNode metrics = mapper.createObjectNode();
      metrics.put("revenueAtRiskMinor", incidents.sumAtRisk(orgId, OPEN_STATUSES));
      metrics.put("revenueRecoveredMinor", ledger.recovered(orgId));
      metrics.put("recoveredCount", ledger.recoveredIncidents(orgId));
      snapshots.save(new MetricSnapshot(orgId, now, now.plus(1, ChronoUnit.HOURS), metrics));
    }
  }
}
