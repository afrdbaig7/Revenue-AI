package com.recoverai.experiment.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.recoverai.common.api.ApiException;
import com.recoverai.common.tenant.CurrentUser;
import com.recoverai.experiment.application.ExperimentService;
import com.recoverai.experiment.domain.Experiment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Batch experiments: baseline vs RecoverAI on seeded populations (synthetic results). */
@RestController
@RequestMapping("/api/v1/experiments")
@RequiredArgsConstructor
public class ExperimentController {

  private final ExperimentService experiments;

  @GetMapping
  public List<Experiment> list(Authentication authentication) {
    return experiments.list(((CurrentUser) authentication.getPrincipal()).orgId());
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','ANALYST')")
  public Experiment run(Authentication authentication, @Valid @RequestBody RunExperimentRequest req) {
    UUID orgId = ((CurrentUser) authentication.getPrincipal()).orgId();
    return experiments.run(
        orgId,
        req.name(),
        req.description(),
        req.seed(),
        req.populationSize(),
        req.baselineConfig(),
        req.treatmentConfig());
  }

  @GetMapping("/{id}")
  public Experiment get(Authentication authentication, @PathVariable UUID id) {
    return experiments.get(((CurrentUser) authentication.getPrincipal()).orgId(), id);
  }

  @GetMapping("/{id}/report")
  public ResponseEntity<String> report(
      Authentication authentication, @PathVariable UUID id, @RequestParam(defaultValue = "json") String format) {
    UUID orgId = ((CurrentUser) authentication.getPrincipal()).orgId();
    Experiment experiment = experiments.get(orgId, id);
    if ("csv".equalsIgnoreCase(format)) {
      return ResponseEntity.ok()
          .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=experiment-" + id + ".csv")
          .contentType(MediaType.parseMediaType("text/csv"))
          .body(experiments.csv(orgId, id));
    }
    if (experiment.getResults() == null) {
      throw ApiException.conflict("Experiment has no results yet");
    }
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .body(experiment.getResults().toString());
  }

  public record RunExperimentRequest(
      @NotBlank String name,
      String description,
      @Min(0) long seed,
      @Min(50) @Max(100_000) int populationSize,
      JsonNode baselineConfig,
      JsonNode treatmentConfig) {}
}
