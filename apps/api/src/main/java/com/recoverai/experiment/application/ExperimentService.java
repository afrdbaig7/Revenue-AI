package com.recoverai.experiment.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.recoverai.common.api.ApiException;
import com.recoverai.experiment.application.ExperimentSimulator.ArmResult;
import com.recoverai.experiment.application.ExperimentSimulator.RunResult;
import com.recoverai.experiment.application.ExperimentSimulator.SimResult;
import com.recoverai.experiment.domain.Experiment;
import com.recoverai.experiment.domain.ExperimentAssignment;
import com.recoverai.experiment.domain.ExperimentAssignment.Arm;
import com.recoverai.experiment.infrastructure.ExperimentAssignmentRepository;
import com.recoverai.experiment.infrastructure.ExperimentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Runs and persists batch experiments (baseline vs RecoverAI) on seeded populations. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExperimentService {

  private final ExperimentRepository experiments;
  private final ExperimentAssignmentRepository assignments;
  private final ExperimentSimulator simulator;
  private final ObjectMapper mapper;

  @Transactional
  public Experiment run(
      UUID orgId, String name, String description, long seed, int populationSize,
      JsonNode baselineConfig, JsonNode treatmentConfig) {
    RunResult run = simulator.run(seed, populationSize, baselineConfig, treatmentConfig);

    Experiment experiment = new Experiment(orgId, name, seed, populationSize);
    experiment.setDescription(description);
    experiment.setBaselineConfig(baselineConfig == null ? mapper.createObjectNode() : baselineConfig);
    experiment.setTreatmentConfig(treatmentConfig == null ? mapper.createObjectNode() : treatmentConfig);
    experiment.setResults(buildResultsJson(run));
    experiment.setStatus("COMPLETED");
    experiment.setCompletedAt(Instant.now());
    experiment.setReportFormat("json");
    Experiment saved = experiments.save(experiment);

    persistAssignments(orgId, saved.getId(), run);
    log.info("EXPERIMENT_COMPLETED id={} seed={} population={}", saved.getId(), seed, populationSize);
    return saved;
  }

  private void persistAssignments(UUID orgId, UUID experimentId, RunResult run) {
    for (SimResult r : run.controlResults()) {
      ExperimentAssignment a = new ExperimentAssignment(orgId, experimentId, r.key(), Arm.CONTROL);
      fill(a, r, run);
      assignments.save(a);
    }
    for (SimResult r : run.treatmentResults()) {
      ExperimentAssignment a = new ExperimentAssignment(orgId, experimentId, r.key(), Arm.TREATMENT);
      fill(a, r, run);
      assignments.save(a);
    }
  }

  private void fill(ExperimentAssignment a, SimResult r, RunResult run) {
    a.setAmountMinor(r.amountMinor());
    a.setFailureCategory(run.population().stream()
        .filter(p -> p.key().equals(r.key()))
        .map(ExperimentSimulator.PopulationIncident::failureCategory)
        .findFirst()
        .orElse(null));
    a.setRecovered(r.recovered());
    a.setRecoveredAmountMinor(r.recoveredMinor());
    a.setAttempts(r.attempts());
    a.setContacts(r.contacts());
    a.setTimeToRecoveryHours(r.timeToRecoveryHours() > 0 ? BigDecimal.valueOf(r.timeToRecoveryHours()) : null);
    a.setPolicyBlocks(r.policyBlocks());
  }

  private JsonNode buildResultsJson(RunResult run) {
    ObjectNode root = mapper.createObjectNode();
    root.put("synthetic", true);
    root.put("label", "SIMULATED / SYNTHETIC TEST-MODE RESULTS");
    root.put("methodology", "Same seeded incident population through CONTROL (fixed baseline) and TREATMENT (RecoverAI). No real-world causal claims.");
    root.set("control", armJson(run.control()));
    root.set("treatment", armJson(run.treatment()));

    ArmResult c = run.control();
    ArmResult t = run.treatment();
    ObjectNode delta = root.putObject("delta");
    delta.put("recoveryRatePoints", Math.round((t.recoveryRate() - c.recoveryRate()) * 10.0) / 10.0);
    delta.put("grossRecoveredMinor", t.grossRecoveredMinor() - c.grossRecoveredMinor());
    delta.put("netRecoveredMinor", t.netRecoveredMinor() - c.netRecoveredMinor());
    delta.put("incrementalRecoveredMinor", t.netRecoveredMinor() - c.netRecoveredMinor());
    delta.put("attemptsSaved", c.totalAttempts() - t.totalAttempts());
    delta.put("contactsSaved", c.totalContacts() - t.totalContacts());
    delta.put("unnecessaryContactsSaved", c.unnecessaryContacts() - t.unnecessaryContacts());
    return root;
  }

  private ObjectNode armJson(ArmResult arm) {
    ObjectNode node = mapper.createObjectNode();
    node.put("arm", arm.arm());
    node.put("population", arm.population());
    node.put("recoveredCount", arm.recovered());
    node.put("recoveryRatePercent", arm.recoveryRate());
    node.put("recoveryRateLowerCI", Math.round(arm.rateLower() * 1000.0) / 10.0);
    node.put("recoveryRateUpperCI", Math.round(arm.rateUpper() * 1000.0) / 10.0);
    node.put("grossRecoveredMinor", arm.grossRecoveredMinor());
    node.put("netRecoveredMinor", arm.netRecoveredMinor());
    node.put("totalAttempts", arm.totalAttempts());
    node.put("avgAttempts", arm.avgAttempts());
    node.put("totalContacts", arm.totalContacts());
    node.put("avgContacts", arm.avgContacts());
    node.put("avgTimeToRecoveryHours", arm.avgTimeToRecoveryHours());
    node.put("interventionCostMinor", arm.interventionCostMinor());
    node.put("policyBlocks", arm.policyBlocks());
    node.put("unnecessaryContacts", arm.unnecessaryContacts());
    return node;
  }

  @Transactional(readOnly = true)
  public Experiment get(UUID orgId, UUID experimentId) {
    return experiments
        .findByOrgIdAndId(orgId, experimentId)
        .orElseThrow(() -> ApiException.notFound("Experiment not found"));
  }

  @Transactional(readOnly = true)
  public List<Experiment> list(UUID orgId) {
    return experiments.findByOrgIdOrderByCreatedAtDesc(orgId);
  }

  /** CSV report of per-incident assignments (downloadable). */
  @Transactional(readOnly = true)
  public String csv(UUID orgId, UUID experimentId) {
    Experiment experiment = get(orgId, experimentId);
    StringBuilder sb = new StringBuilder(
        "incident_key,arm,amount_minor,currency,failure_category,recovered,recovered_amount_minor,attempts,contacts,time_to_recovery_hours,policy_blocks\n");
    for (ExperimentAssignment a : assignments.findByExperimentId(experiment.getId())) {
      sb.append(a.getIncidentKey()).append(',')
          .append(a.getArm()).append(',')
          .append(a.getAmountMinor()).append(',')
          .append(a.getCurrency()).append(',')
          .append(a.getFailureCategory() == null ? "" : a.getFailureCategory()).append(',')
          .append(a.isRecovered()).append(',')
          .append(a.getRecoveredAmountMinor()).append(',')
          .append(a.getAttempts()).append(',')
          .append(a.getContacts()).append(',')
          .append(a.getTimeToRecoveryHours() == null ? "" : a.getTimeToRecoveryHours()).append(',')
          .append(a.getPolicyBlocks())
          .append('\n');
    }
    return sb.toString();
  }
}
